package com.photogallery.process

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
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
import androidx.core.graphics.createBitmap
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.atan2

object FaceEmbeddingUtils {
    private const val TAG = "FaceEmbeddingUtils"
    private const val TF_OD_API_INPUT_SIZE = 112
    private const val TF_OD_API_IS_QUANTIZED = false
    private const val TF_OD_API_MODEL_FILE = "mobile_face_net.tflite"
    private const val TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt"
    private const val MAX_BITMAP_SIZE = 2048
    private const val MAX_FACES_PER_IMAGE = 3

    private val databaseDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val faceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE) // Switched to accurate
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
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
            val processedUris = withContext(databaseDispatcher) {
                database.photoGalleryDao().getAllUrisWithEmbeddings().toSet()
            }
            val skippedUris = withContext(databaseDispatcher) {
                database.photoGalleryDao().getAllSkippedUris().toSet()
            }

            val unprocessedUris = photoUris.filter { uri ->
                val uriString = uri.toString()
                uriString !in processedUris && uriString !in skippedUris
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
            } catch (e: IOException) {
                Log.e(TAG, "Failed to initialize TFLite detector", e)
                return@withContext
            }

            try {
                unprocessedUris.forEachIndexed { index, uri ->
                    val startTime = System.currentTimeMillis()

                    Log.d(TAG, "Processing image ${index + 1}/${unprocessedUris.size}: $uri")
                    try {
                        val bitmap =
                            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                BitmapFactory.decodeStream(inputStream)
                            } ?: run {
                                withContext(databaseDispatcher) {
                                    database.photoGalleryDao().insertSkippedImage(
                                        SkippedImage(uri.toString(), "Failed to load bitmap")
                                    )
                                }
                                return@forEachIndexed
                            }

                        val resizedBitmap = resizeBitmap(bitmap)
                        val inputImage = InputImage.fromBitmap(resizedBitmap, 0)
                        val faces = faceDetector.process(inputImage).await()

                        if (faces.isEmpty()) {
                            resizedBitmap.recycle()
                            withContext(databaseDispatcher) {
                                database.photoGalleryDao().insertSkippedImage(
                                    SkippedImage(uri.toString(), "No faces detected")
                                )
                            }
                            return@forEachIndexed
                        }

                        if (faces.size > MAX_FACES_PER_IMAGE) {
                            resizedBitmap.recycle()
                            withContext(databaseDispatcher) {
                                database.photoGalleryDao().insertSkippedImage(
                                    SkippedImage(uri.toString(), "Too many faces (${faces.size})")
                                )
                            }
                            return@forEachIndexed
                        }

                        faces.forEachIndexed { faceIndex, face ->
                            val faceBitmap = cropFaceFromBitmap(resizedBitmap, face)
                            val embedding = generateFaceEmbedding(detector, faceBitmap)

                            if (embedding.all { it == 0f }) {
                                faceBitmap.recycle()
                                withContext(databaseDispatcher) {
                                    database.photoGalleryDao().insertSkippedImage(
                                        SkippedImage(uri.toString(), "Invalid face embedding")
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

                            withContext(databaseDispatcher) {
                                database.photoGalleryDao().insert(faceEmbedding)
                            }
                            faceBitmap.recycle()
                        }

                        resizedBitmap.recycle()
                        val duration = System.currentTimeMillis() - startTime
                        Log.i(TAG, "Processed $uri in ${duration}ms")

                    } catch (e: OutOfMemoryError) {
                        Log.e(TAG, "OOM Error", e)
                        withContext(databaseDispatcher) {
                            database.photoGalleryDao().insertSkippedImage(
                                SkippedImage(uri.toString(), "OutOfMemoryError")
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing image $uri", e)
                        withContext(databaseDispatcher) {
                            database.photoGalleryDao().insertSkippedImage(
                                SkippedImage(uri.toString(), "Processing error: ${e.message}")
                            )
                        }
                    }
                }
            } finally {
                detector.close()
                Log.i(TAG, "Closed TFLite detector")
            }
        }
    }

    private fun cropFaceFromBitmap(bitmap: Bitmap, face: Face): Bitmap {
        val boundingBox = face.boundingBox
        val padding = (boundingBox.width() * 0.2).toInt()

        val x = max(boundingBox.left - padding, 0)
        val y = max(boundingBox.top - padding, 0)
        val width = min(boundingBox.width() + 2 * padding, bitmap.width - x)
        val height = min(boundingBox.height() + 2 * padding, bitmap.height - y)

        val croppedBitmap = Bitmap.createBitmap(bitmap, x, y, width, height)
        val alignedBitmap = alignFaceBitmap(croppedBitmap, face)
        croppedBitmap.recycle()
        return alignedBitmap
    }

    private fun alignFaceBitmap(bitmap: Bitmap, face: Face): Bitmap {
        val landmarks = face.allLandmarks
        if (landmarks.isEmpty()) return bitmap

        val leftEye = landmarks.find { it.landmarkType == FaceLandmark.LEFT_EYE }
        val rightEye = landmarks.find { it.landmarkType == FaceLandmark.RIGHT_EYE }
        if (leftEye == null || rightEye == null) return bitmap

        val leftEyePos = leftEye.position
        val rightEyePos = rightEye.position
        val angle = atan2(
            (rightEyePos.y - leftEyePos.y).toDouble(),
            (rightEyePos.x - leftEyePos.x).toDouble()
        ).toFloat() * 180 / Math.PI.toFloat()

        val matrix = Matrix()
        matrix.postRotate(-angle, bitmap.width / 2f, bitmap.height / 2f)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun resizeBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxSize = MAX_BITMAP_SIZE

        if (width <= maxSize && height <= maxSize) return bitmap

        val scale = max(width, height).toFloat() / maxSize
        val newWidth = (width / scale).toInt()
        val newHeight = (height / scale).toInt()

        return bitmap.scale(newWidth, newHeight)
    }

    private suspend fun <T> Task<T>.await(): T {
        return withContext(Dispatchers.IO) {
            Tasks.await(this@await)
        }
    }

    private fun generateFaceEmbedding(
        detector: SimilarityClassifier,
        faceBitmap: Bitmap
    ): FloatArray {
        val preprocessedBitmap = preprocessBitmap(faceBitmap)
        val resizedBitmap = preprocessedBitmap.scale(TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE)
        val results = detector.recognizeImage(resizedBitmap, true)
        resizedBitmap.recycle()
        preprocessedBitmap.recycle()

        if (results.isNotEmpty()) {
            val result = results[0]
            val extra = result.extra
            if (extra is Array<*> && extra.isNotEmpty() && extra[0] is FloatArray) {
                return extra[0] as FloatArray
            }
        }
        return FloatArray(128)
    }

    private fun preprocessBitmap(bitmap: Bitmap): Bitmap {
        val grayscaleBitmap = createBitmap(bitmap.width, bitmap.height)
        val canvas = Canvas(grayscaleBitmap)
        val paint = Paint()
        val colorMatrix = ColorMatrix().apply { setSaturation(0f) } // Grayscale
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        val contrastedBitmap = createBitmap(bitmap.width, bitmap.height)
        val contrastPaint = Paint()
        val contrastMatrix = ColorMatrix().apply {
            set(
                floatArrayOf(
                    1.2f, 0f, 0f, 0f, 0f,
                    0f, 1.2f, 0f, 0f, 0f,
                    0f, 0f, 1.2f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }
        contrastPaint.colorFilter = ColorMatrixColorFilter(contrastMatrix)
        Canvas(contrastedBitmap).drawBitmap(grayscaleBitmap, 0f, 0f, contrastPaint)
        grayscaleBitmap.recycle()
        return contrastedBitmap
    }
}