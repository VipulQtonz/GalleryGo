package com.photogallery.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.graphics.get
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.photogallery.MyApplication.Companion.duplicateImageGroupsLiveData
import com.photogallery.model.DocumentGroup
import com.photogallery.model.DuplicateImageGroup
import java.io.IOException
import java.text.SimpleDateFormat

object ClassificationUtils {
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    private val imageHashMap = mutableMapOf<String, MutableList<Uri>>()

    private val allDocumentCategories = listOf(
        "Selfie", "Beard", "Portraits", "Handsome", "Macro", "Travel",
        "Festivals", "BlackAndWhite", "Sky", "Water", "FoodAndDrink",
        "Abstract", "Vintage", "Sports", "Vehicles", "Nature", "Buildings",
        "Fashion", "Night", "Screenshot", "Events", "Art", "People", "Places",
        "Receipts", "Notes", "IDs", "Letters", "Documents", "Animals",
        "Objects", "Other"
    )

    fun initializeDocumentCategories(): Map<String, DocumentGroup> {
        return allDocumentCategories.associateWith { DocumentGroup(name = it) }
    }

    fun classifyPhoto(context: Context, uri: Uri, onDocumentClassified: (String) -> Unit) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            val resizedBitmap = bitmap.scale(224, 224)
            classifyDocument(resizedBitmap, onDocumentClassified)
        } catch (e: IOException) {
            Log.e("ClassificationUtils", "IOException during classification: $uri", e)
            onDocumentClassified("Other")
        }
    }

    private fun classifyDocument(bitmap: Bitmap, onDocumentClassified: (String) -> Unit) {
        val image = InputImage.fromBitmap(bitmap, 0)
        labeler.process(image)
            .addOnSuccessListener { labels ->
                val filteredLabels = labels.filter { it.confidence > 0.60f }.map { it.text }
                val category = determineDocumentCategory(filteredLabels)
                onDocumentClassified(category)
            }
            .addOnFailureListener {
                Log.e("ClassificationUtils", "Image labeling failed", it)
                onDocumentClassified("Other")
            }
    }

    private fun determineDocumentCategory(labels: List<String>): String {
        if (labels.isEmpty()) return "Other"
        val primaryLabel = labels.first().lowercase()
            .replace("[^a-zA-Z0-9]".toRegex(), "")
            .replaceFirstChar { it.uppercase() }
        return primaryLabel
    }

    data class ImageMetadata(val fileSize: Long, val dateTaken: Long?, val dateModified: Long?)

    fun getImageMetadata(context: Context, uri: Uri): ImageMetadata? {
        return try {
            val projection = arrayOf(
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_MODIFIED
            )
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
            var fileSize = 0L
            var dateModified: Long? = null
            cursor?.use {
                if (it.moveToFirst()) {
                    fileSize = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE))
                    dateModified =
                        it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)) * 1000
                }
            }
            val dateTaken = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let {
                    SimpleDateFormat("yyyy:MM:dd HH:mm:ss").parse(it)?.time
                }
            }
            ImageMetadata(fileSize, dateTaken, dateModified)
        } catch (_: Exception) {
            null
        }
    }

    fun computePerceptualHash(context: Context, uri: Uri): String? {
        if (!isValidImageUri(context, uri)) {
            return null
        }
        return try {
            val bitmap = safelyDecodeBitmap(context, uri) ?: run {
                return null
            }
            val hash = computePHash(bitmap)
            bitmap.recycle()
            hash
        } catch (_: Exception) {
            null
        }
    }

    private fun isValidImageUri(context: Context, uri: Uri): Boolean {
        return try {
            val type = context.contentResolver.getType(uri)
            type?.startsWith("image/") == true && type in listOf(
                "image/jpeg", "image/png", "image/bmp", "image/gif", "image/webp", "image/heif"
            )
        } catch (_: Exception) {
            false
        }
    }

    private fun computePHash(bitmap: Bitmap): String {
        val resized = bitmap.scale(32, 32, false)
        val pixels = IntArray(32 * 32)
        var sum = 0.0
        for (y in 0 until 32) {
            for (x in 0 until 32) {
                val pixel = resized[x, y]
                val gray = (pixel shr 16 and 0xff + pixel shr 8 and 0xff + pixel and 0xff) / 3
                pixels[y * 32 + x] = gray
                sum += gray
            }
        }
        resized.recycle()
        val dct = pixels.map { it.toDouble() }.toDoubleArray()
        val lowFreq = DoubleArray(64)
        for (i in 0 until 8) {
            for (j in 0 until 8) {
                lowFreq[i * 8 + j] = dct[i * 32 + j]
            }
        }
        val median = lowFreq.sorted()[31]
        return lowFreq.joinToString("") { if (it > median) "1" else "0" }
    }

    private fun safelyDecodeBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                return bitmap
            }
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inSampleSize = calculateInSampleSize()
            }
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                return bitmap?.let {
                    if (it.config == Bitmap.Config.HARDWARE) {
                        it.copy(Bitmap.Config.ARGB_8888, true)?.also { softwareBitmap ->
                            it.recycle()
                            return softwareBitmap
                        } ?: throw IllegalStateException("Failed to copy hardware bitmap")
                    } else {
                        it
                    }
                }
            }
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val bitmap = BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, options)
                return bitmap?.let {
                    if (it.config == Bitmap.Config.HARDWARE) {
                        it.copy(Bitmap.Config.ARGB_8888, true)?.also { softwareBitmap ->
                            it.recycle()
                            return softwareBitmap
                        } ?: throw IllegalStateException("Failed to copy hardware bitmap")
                    } else {
                        it
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(
        width: Int = 1024,
        height: Int = 1024,
        reqWidth: Int = 224,
        reqHeight: Int = 224
    ): Int {
        var inSampleSize = 1
        if (width > reqWidth || height > reqHeight) {
            val halfWidth = width / 2
            val halfHeight = height / 2
            while ((halfWidth / inSampleSize) > reqWidth && (halfHeight / inSampleSize) > reqHeight) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    fun checkAndStoreDuplicate(context: Context, uri: Uri, hammingThreshold: Int = 5) {
        val metadata = getImageMetadata(context, uri) ?: run {
            return
        }
        val metadataKey = "${metadata.fileSize}|${metadata.dateTaken ?: metadata.dateModified ?: 0}"
        synchronized(imageHashMap) {
            val list = imageHashMap.getOrPut(metadataKey) { mutableListOf() }
            if (!list.contains(uri)) {
                list.add(uri)
                if (list.size >= 2) {
                    confirmDuplicatesWithPHash(context, list, hammingThreshold)
                }
                updateDuplicateGroupsLiveData()
            }
        }
    }

    private fun confirmDuplicatesWithPHash(
        context: Context,
        uris: List<Uri>,
        hammingThreshold: Int
    ) {
        val hashMap = mutableMapOf<Uri, String>()
        uris.forEach { uri ->
            val hash = computePerceptualHash(context, uri)
            if (hash != null) {
                hashMap[uri] = hash
            }
        }
        val groupedByHash = mutableMapOf<String, MutableList<Uri>>()
        hashMap.entries.forEach { (uri, hash) ->
            val matchingHash = groupedByHash.keys.find { existingHash ->
                hammingDistance(hash, existingHash) <= hammingThreshold
            }
            if (matchingHash != null) {
                groupedByHash[matchingHash]!!.add(uri)
            } else {
                groupedByHash[hash] = mutableListOf(uri)
            }
        }
        synchronized(imageHashMap) {
            groupedByHash.forEach { (hash, uriList) ->
                if (uriList.size >= 2) {
                    imageHashMap[hash] = uriList
                }
            }
        }
    }

    private fun hammingDistance(hash1: String, hash2: String): Int {
        return hash1.zip(hash2).count { (a, b) -> a != b }
    }

    fun updateDuplicateGroupsLiveData() {
        synchronized(imageHashMap) {
            val duplicateGroups = imageHashMap.values
                .filter { it.size > 1 }
                .map { uris ->
                    DuplicateImageGroup(
                        representativeUri = uris.first(),
                        allUris = uris.toMutableList()
                    )
                }
                .distinctBy { it.allUris.toSet() } // Deduplicate based on URI set
            duplicateImageGroupsLiveData.postValue(duplicateGroups)
        }
    }

    fun clearImageHashMap() {
        synchronized(imageHashMap) {
            imageHashMap.clear()
        }
    }
}