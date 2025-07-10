package com.photogallery.process

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.graphics.scale
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.photogallery.db.PhotoGalleryDatabase
import com.photogallery.db.model.FaceEmbedding
import com.photogallery.db.model.SkippedImage
import com.photogallery.utils.SimilarityClassifier
import com.photogallery.utils.TFLiteObjectDetectionAPIModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

object FaceEmbeddingUtils {
    private const val TAG = "FaceEmbeddingUtils"
    private const val TF_OD_API_INPUT_SIZE = 112
    private const val TF_OD_API_IS_QUANTIZED = false
    private const val TF_OD_API_MODEL_FILE = "mobile_face_net.tflite"
    private const val TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt"
    private const val MAX_BITMAP_SIZE =
        2048 // Max dimension (width or height) to avoid memory issues
    private const val MAX_FACES_PER_IMAGE = 3 // Maximum number of faces allowed per image

    private val databaseDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val faceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .enableTracking()
        .build()

    private val faceDetector: FaceDetector = FaceDetection.getClient(faceDetectorOptions)

    suspend fun processImagesForFaceEmbeddings(context: Context) {
        withContext(Dispatchers.IO) {
            val photoUris = LocationUtils.getPhotosFromGallery(context)
            Log.d(TAG, "Starting face embedding process for ${photoUris.size} images")
            if (photoUris.isEmpty()) {
                Log.i(TAG, "No images found in gallery")
                return@withContext
            }

            val database = PhotoGalleryDatabase.getDatabase(context)
            // Get URIs that already have embeddings or were skipped
            val processedUris = withContext(databaseDispatcher) {
                database.photoGalleryDao().getAllUrisWithEmbeddings().toSet()
            }
            val skippedUris = withContext(databaseDispatcher) {
                database.photoGalleryDao().getAllSkippedUris().toSet()
            }
            Log.d(TAG, "Found ${processedUris.size} images with existing embeddings")
            Log.d(TAG, "Found ${skippedUris.size} images previously skipped")

            val unprocessedUris = photoUris.filter { uri ->
                val uriString = uri.toString()
                uriString !in processedUris && uriString !in skippedUris
            }
            Log.d(TAG, "Processing ${unprocessedUris.size} unprocessed images")

            if (unprocessedUris.isEmpty()) {
                Log.i(TAG, "All images already processed or skipped, skipping embedding process")
                return@withContext
            }

            var detector: SimilarityClassifier
            try {
                detector = TFLiteObjectDetectionAPIModel.create(
                    context.assets,
                    TF_OD_API_MODEL_FILE,
                    TF_OD_API_LABELS_FILE,
                    TF_OD_API_INPUT_SIZE,
                    TF_OD_API_IS_QUANTIZED
                )
                Log.i(TAG, "Successfully initialized TFLite detector")
            } catch (e: IOException) {
                Log.e(TAG, "Failed to initialize TFLite detector", e)
                return@withContext
            }

            try {
                unprocessedUris.forEachIndexed { index, uri ->
                    Log.d(TAG, "Processing image ${index + 1}/${unprocessedUris.size}: $uri")
                    try {
                        // Load bitmap
                        val bitmap =
                            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                BitmapFactory.decodeStream(inputStream)
                            } ?: run {
                                Log.w(TAG, "Failed to load bitmap for URI: $uri")
                                withContext(databaseDispatcher) {
                                    database.photoGalleryDao().insertSkippedImage(
                                        SkippedImage(
                                            uri = uri.toString(),
                                            reason = "Failed to load bitmap"
                                        )
                                    )
                                }
                                return@forEachIndexed
                            }

                        // Create input image for face detection
                        val inputImage = InputImage.fromBitmap(bitmap, 0)
                        val faces = faceDetector.process(inputImage).await()
                        Log.d(TAG, "Detected ${faces.size} faces in image: $uri")

                        // Skip images with no faces or too many faces
                        if (faces.isEmpty()) {
                            bitmap.recycle()
                            Log.i(TAG, "No faces detected, skipping image: $uri")
                            withContext(databaseDispatcher) {
                                database.photoGalleryDao().insertSkippedImage(
                                    SkippedImage(
                                        uri = uri.toString(),
                                        reason = "No faces detected"
                                    )
                                )
                            }
                            return@forEachIndexed
                        }

                        if (faces.size > MAX_FACES_PER_IMAGE) {
                            bitmap.recycle()
                            Log.i(
                                TAG,
                                "Image has ${faces.size} faces, exceeding max ($MAX_FACES_PER_IMAGE), skipping: $uri"
                            )
                            withContext(databaseDispatcher) {
                                database.photoGalleryDao().insertSkippedImage(
                                    SkippedImage(
                                        uri = uri.toString(),
                                        reason = "Too many faces (${faces.size})"
                                    )
                                )
                            }
                            return@forEachIndexed
                        }

                        // Process all detected faces
                        faces.forEachIndexed { faceIndex, face ->
                            Log.d(
                                TAG,
                                "Processing face $faceIndex (trackingId: ${face.trackingId}) for image: $uri"
                            )
                            val faceBitmap = cropFaceFromBitmap(bitmap, face)
                            val embedding = generateFaceEmbedding(detector, faceBitmap)
                            if (embedding.all { it == 0f }) {
                                faceBitmap.recycle()
                                Log.w(
                                    TAG,
                                    "Invalid embedding generated for face $faceIndex in image: $uri, skipping"
                                )
                                withContext(databaseDispatcher) {
                                    database.photoGalleryDao().insertSkippedImage(
                                        SkippedImage(
                                            uri = uri.toString(),
                                            reason = "Invalid face embedding"
                                        )
                                    )
                                }
                                return@forEachIndexed
                            }

                            val faceId =
                                "${UUID.randomUUID()}${face.trackingId?.let { "_$it" } ?: ""}"
                            val faceEmbedding = FaceEmbedding(
                                faceId = faceId,
                                uri = uri.toString(),
                                embedding = embedding
                            )

                            // Insert using single-threaded dispatcher
                            withContext(databaseDispatcher) {
                                database.photoGalleryDao().insert(faceEmbedding)
                                Log.i(
                                    TAG,
                                    "Stored embedding for face $faceIndex (faceId: $faceId) in image: $uri"
                                )
                            }
                            faceBitmap.recycle()
                        }
                        bitmap.recycle()
                        Log.d(
                            TAG,
                            "Completed processing image ${index + 1}/${unprocessedUris.size}: $uri"
                        )
                    } catch (e: OutOfMemoryError) {
                        Log.e(
                            TAG,
                            "OutOfMemoryError processing image ${index + 1}/${unprocessedUris.size}: $uri",
                            e
                        )
                        withContext(databaseDispatcher) {
                            database.photoGalleryDao().insertSkippedImage(
                                SkippedImage(
                                    uri = uri.toString(),
                                    reason = "OutOfMemoryError"
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(
                            TAG,
                            "Error processing image ${index + 1}/${unprocessedUris.size}: $uri",
                            e
                        )
                        withContext(databaseDispatcher) {
                            database.photoGalleryDao().insertSkippedImage(
                                SkippedImage(
                                    uri = uri.toString(),
                                    reason = "Processing error: ${e.message}"
                                )
                            )
                        }
                    }
                }
            } finally {
                detector.close()
                Log.i(TAG, "Closed TFLite detector")
            }
            Log.i(
                TAG,
                "Face embedding process completed for ${unprocessedUris.size} unprocessed images"
            )
        }
    }

    private fun cropFaceFromBitmap(bitmap: Bitmap, face: Face): Bitmap {
        val boundingBox = face.boundingBox
        val x = max(boundingBox.left, 0)
        val y = max(boundingBox.top, 0)
        val width = min(boundingBox.width(), bitmap.width - x)
        val height = min(boundingBox.height(), bitmap.height - y)

        val croppedBitmap = Bitmap.createBitmap(bitmap, x, y, width, height)
        return croppedBitmap
    }

    private suspend fun <T> Task<T>.await(): T {
        return withContext(Dispatchers.IO) {
            val result = Tasks.await(this@await)
            result
        }
    }

    private fun generateFaceEmbedding(
        detector: SimilarityClassifier,
        faceBitmap: Bitmap
    ): FloatArray {
        // Resize bitmap to model input size (112x112 for MobileFaceNet)
        val resizedBitmap = faceBitmap.scale(TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE)

        // Run inference
        val results = detector.recognizeImage(
            resizedBitmap,
            true
        ) // Set add=true to ensure embedding is generated

        // Recycle resized bitmap to free memory
        resizedBitmap.recycle()

        if (results.isNotEmpty()) {
            val result = results[0]
            val extra = result.extra
            if (extra is Array<*> && extra.isNotEmpty() && extra[0] is FloatArray) {
                return extra[0] as FloatArray
            }
        }
        return FloatArray(128)
    }
}