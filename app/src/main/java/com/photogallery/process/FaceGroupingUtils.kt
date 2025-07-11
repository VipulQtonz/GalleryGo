package com.photogallery.process

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import com.photogallery.db.PhotoGalleryDatabase
import com.photogallery.db.model.FaceEmbedding
import com.photogallery.utils.UriUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.sqrt

object FaceGroupingUtils {
    private const val TAG = "FaceGroupingUtils"
    private const val BASE_SELFIE_SIMILARITY_THRESHOLD = 0.60f
    private const val BASE_GENERAL_SIMILARITY_THRESHOLD = 0.65f
    private const val MIN_SIMILARITY_THRESHOLD = 0.85f
    private const val MERGE_THRESHOLD = 0.75f

    private val databaseDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    data class FaceGroup(
        val groupId: String = UUID.randomUUID().toString(),
        val uris: MutableSet<String> = mutableSetOf(),
        val embeddings: MutableList<FloatArray> = mutableListOf(),
        var representativeUri: String? = null,
        val referenceEmbedding: FloatArray? = null
    ) {
        override fun toString(): String {
            return "FaceGroup(id=$groupId, imageCount=${uris.size}, representativeUri=$representativeUri, uris=[${
                uris.joinToString(
                    limit = 3,
                    truncated = "..."
                )
            }])"
        }
    }

    suspend fun groupSimilarFaces(context: Context): List<FaceGroup> {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Starting face grouping process")
            val database = PhotoGalleryDatabase.getDatabase(context)
            val allEmbeddings = database.photoGalleryDao().getAllEmbeddings()
            Log.i(TAG, "Retrieved ${allEmbeddings.size} face embeddings from database")

            if (allEmbeddings.isEmpty()) {
                Log.w(TAG, "No embeddings found in database")
                return@withContext emptyList()
            }

            // Filter valid embeddings
            val (validEmbeddings, invalidUris) = filterValidEmbeddings(context, allEmbeddings)
            Log.i(
                TAG,
                "Found ${validEmbeddings.size} valid embeddings, ${invalidUris.size} invalid URIs"
            )

            // Delete invalid embeddings
            if (invalidUris.isNotEmpty()) {
                deleteInvalidEmbeddings(database, invalidUris)
            }

            if (validEmbeddings.isEmpty()) {
                Log.w(TAG, "No valid embeddings after URI validation")
                return@withContext emptyList()
            }

            // Separate selfies (single face) from multi-face images
            val (selfieEmbeddings, multiFaceEmbeddings) = separateSelfiesFromMultiFace(
                validEmbeddings
            )
            Log.i(
                TAG,
                "Identified ${selfieEmbeddings.size} selfie embeddings and ${multiFaceEmbeddings.size} multi-face embeddings"
            )

            if (selfieEmbeddings.isEmpty()) {
                Log.w(TAG, "No selfies found in database")
                return@withContext emptyList()
            }

            // Get expected embedding size from first valid embedding
            val expectedEmbeddingSize = getExpectedEmbeddingSize(selfieEmbeddings)
                ?: return@withContext emptyList()

            // Step 1: Group selfies
            val selfieGroups = groupSelfies(selfieEmbeddings, expectedEmbeddingSize)

            // Step 2: Assign multi-face images to groups
            val faceGroups =
                assignMultiFaceImages(multiFaceEmbeddings, selfieGroups, expectedEmbeddingSize)

            // Step 3: Merge similar groups
            val mergedGroups = mergeSimilarGroups(faceGroups)

            // Filter and validate final groups
            val validGroups = filterAndValidateGroups(mergedGroups)

            logGroupingResults(validGroups)
            validGroups
        }
    }

    private fun filterValidEmbeddings(
        context: Context,
        embeddings: List<FaceEmbedding>
    ): Pair<List<FaceEmbedding>, Set<String>> {
        val validEmbeddings = mutableListOf<FaceEmbedding>()
        val invalidUris = mutableSetOf<String>()

        embeddings.forEach { embedding ->
            val uri = embedding.uri.toUri()
            if (UriUtils.isUriValid(context, uri)) {
                validEmbeddings.add(embedding)
            } else {
                invalidUris.add(embedding.uri)
                Log.w(TAG, "Invalid URI found: ${embedding.uri}, faceId: ${embedding.faceId}")
            }
        }
        return Pair(validEmbeddings, invalidUris)
    }

    private suspend fun deleteInvalidEmbeddings(
        database: PhotoGalleryDatabase,
        invalidUris: Set<String>
    ) {
        withContext(databaseDispatcher) {
            invalidUris.forEach { uri ->
                try {
                    database.photoGalleryDao().deleteEmbeddingsByUri(uri)
                    Log.i(TAG, "Deleted embeddings for invalid URI: $uri")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete embeddings for URI: $uri", e)
                }
            }
        }
    }

    private fun separateSelfiesFromMultiFace(
        embeddings: List<FaceEmbedding>
    ): Pair<List<FaceEmbedding>, List<FaceEmbedding>> {
        val embeddingsByUri = embeddings.groupBy { it.uri }
        val selfieEmbeddings = embeddingsByUri.filter { it.value.size == 1 }.flatMap { it.value }
        val multiFaceEmbeddings = embeddings.filter { it !in selfieEmbeddings }
        return Pair(selfieEmbeddings, multiFaceEmbeddings)
    }

    private fun getExpectedEmbeddingSize(selfieEmbeddings: List<FaceEmbedding>): Int? {
        return selfieEmbeddings.firstOrNull { !it.embedding.all { v -> v == 0f } }?.embedding?.size
            ?: run {
                Log.e(TAG, "No valid embeddings to determine size")
                null
            }
    }

    private fun groupSelfies(
        selfieEmbeddings: List<FaceEmbedding>,
        expectedEmbeddingSize: Int
    ): MutableList<FaceGroup> {
        val selfieGroups = mutableListOf<FaceGroup>()
        var zeroEmbeddingCount = 0
        var invalidSizeEmbeddingCount = 0

        selfieEmbeddings.forEach { selfieEmbedding ->
            val embeddingVector = normalizeEmbedding(selfieEmbedding.embedding)

            when {
                embeddingVector.all { it == 0f } -> {
                    zeroEmbeddingCount++
                    Log.w(
                        TAG,
                        "Skipping zero-filled selfie embedding for URI: ${selfieEmbedding.uri}"
                    )
                }

                embeddingVector.size != expectedEmbeddingSize -> {
                    invalidSizeEmbeddingCount++
                    Log.w(
                        TAG,
                        "Skipping selfie embedding with size ${embeddingVector.size} (expected $expectedEmbeddingSize)"
                    )
                }

                else -> {
                    var bestGroup: FaceGroup? = null
                    var bestSimilarity = -1f

                    for (group in selfieGroups) {
                        val centroid = calculateCentroid(group.embeddings)
                        val threshold = getAdaptiveThreshold(group.embeddings.size)
                        val similarity = calculateCosineSimilarity(centroid, embeddingVector)

                        if (similarity >= threshold && similarity > bestSimilarity) {
                            bestSimilarity = similarity
                            bestGroup = group
                        }
                    }

                    if (bestGroup != null) {
                        bestGroup.uris.add(selfieEmbedding.uri)
                        bestGroup.embeddings.add(embeddingVector)
                        Log.d(TAG, "Matched selfie to group ${bestGroup.groupId}")
                    } else {
                        val newGroup = FaceGroup(
                            uris = mutableSetOf(selfieEmbedding.uri),
                            embeddings = mutableListOf(embeddingVector),
                            representativeUri = selfieEmbedding.uri,
                            referenceEmbedding = embeddingVector
                        )
                        selfieGroups.add(newGroup)
                        Log.d(TAG, "Created new selfie group ${newGroup.groupId}")
                    }
                }
            }
        }

        Log.i(
            TAG,
            "Skipped $zeroEmbeddingCount zero-filled and $invalidSizeEmbeddingCount invalid-sized selfie embeddings"
        )
        return selfieGroups
    }

    private fun assignMultiFaceImages(
        multiFaceEmbeddings: List<FaceEmbedding>,
        faceGroups: MutableList<FaceGroup>,
        expectedEmbeddingSize: Int
    ): MutableList<FaceGroup> {
        var zeroEmbeddingCount = 0
        var invalidSizeEmbeddingCount = 0
        val assignedUris = mutableSetOf<String>()

        multiFaceEmbeddings.forEach { embedding ->
            val embeddingVector = normalizeEmbedding(embedding.embedding)

            when {
                embeddingVector.all { it == 0f } -> {
                    zeroEmbeddingCount++
                    Log.w(TAG, "Skipping zero-filled embedding for URI: ${embedding.uri}")
                }

                embeddingVector.size != expectedEmbeddingSize -> {
                    invalidSizeEmbeddingCount++
                    Log.w(
                        TAG,
                        "Skipping embedding with size ${embeddingVector.size} (expected $expectedEmbeddingSize)"
                    )
                }

                assignedUris.contains(embedding.uri) -> {
                    Log.d(TAG, "URI ${embedding.uri} already assigned to a group, skipping")
                }

                else -> {
                    var bestGroup: FaceGroup? = null
                    var bestSimilarity = -1f

                    for (group in faceGroups) {
                        val centroid = calculateCentroid(group.embeddings)
                        val threshold = getAdaptiveThreshold(group.embeddings.size)
                        val similarity = calculateCosineSimilarity(centroid, embeddingVector)

                        if (similarity >= threshold && similarity > bestSimilarity) {
                            bestSimilarity = similarity
                            bestGroup = group
                        }
                    }

                    if (bestGroup != null) {
                        bestGroup.uris.add(embedding.uri)
                        bestGroup.embeddings.add(embeddingVector)
                        assignedUris.add(embedding.uri)
                        Log.d(TAG, "Assigned multi-face to group ${bestGroup.groupId}")
                    } else {
                        Log.d(TAG, "No match found for multi-face URI: ${embedding.uri}")
                    }
                }
            }
        }

        Log.i(
            TAG,
            "Skipped $zeroEmbeddingCount zero-filled and $invalidSizeEmbeddingCount invalid-sized multi-face embeddings"
        )
        return faceGroups
    }

    private fun mergeSimilarGroups(faceGroups: MutableList<FaceGroup>): MutableList<FaceGroup> {
        var didMerge = true
        var currentGroups = faceGroups.toMutableList()

        while (didMerge) {
            didMerge = false
            val newGroups = mutableListOf<FaceGroup>()
            val mergedGroupIds = mutableSetOf<String>()

            for (i in currentGroups.indices) {
                val group1 = currentGroups[i]
                if (group1.groupId in mergedGroupIds) continue

                val mergedGroup = group1.copy(
                    embeddings = group1.embeddings.toMutableList(),
                    uris = group1.uris.toMutableSet()
                )

                for (j in (i + 1) until currentGroups.size) {
                    val group2 = currentGroups[j]
                    if (group2.groupId in mergedGroupIds) continue

                    val centroid1 = calculateCentroid(group1.embeddings)
                    val centroid2 = calculateCentroid(group2.embeddings)
                    val similarity = calculateCosineSimilarity(centroid1, centroid2)

                    if (similarity >= MERGE_THRESHOLD) {
                        mergedGroup.uris.addAll(group2.uris)
                        mergedGroup.embeddings.addAll(group2.embeddings)
                        mergedGroupIds.add(group2.groupId)
                        didMerge = true
                        Log.d(TAG, "Merged group ${group2.groupId} into ${group1.groupId}")
                    }
                }

                newGroups.add(mergedGroup)
            }

            currentGroups = newGroups
        }

        return currentGroups
    }

    private fun filterAndValidateGroups(groups: List<FaceGroup>): List<FaceGroup> {
        return groups.filter { it.uris.size > 1 }.map { group ->
            // Ensure representative URI is valid
            val validRepresentativeUri = group.uris.firstOrNull { uri ->
                group.representativeUri == uri || group.representativeUri == null
            } ?: group.uris.firstOrNull()

            group.copy(
                representativeUri = validRepresentativeUri,
                referenceEmbedding = calculateCentroid(group.embeddings)
            )
        }
    }

    private fun logGroupingResults(groups: List<FaceGroup>) {
        Log.i(TAG, "Face grouping complete: ${groups.size} distinct face groups formed")
        groups.forEachIndexed { index, group ->
            Log.i(TAG, "Group $index: $group")
            Log.d(TAG, "Embedding Count: ${group.embeddings.size}")
            Log.d(TAG, "Representative URI: ${group.representativeUri ?: "None"}")
        }
    }

    private fun getAdaptiveThreshold(groupSize: Int): Float {
        return when {
            groupSize <= 2 -> MIN_SIMILARITY_THRESHOLD
            groupSize <= 5 -> BASE_SELFIE_SIMILARITY_THRESHOLD
            else -> BASE_GENERAL_SIMILARITY_THRESHOLD
        }
    }

    private fun calculateCentroid(
        embeddings: List<FloatArray>,
        isSelfieGroup: Boolean = false
    ): FloatArray {
        if (embeddings.isEmpty()) return floatArrayOf()
        val size = embeddings.first().size
        val centroid = FloatArray(size)
        var totalWeight = 0f

        embeddings.forEach { embedding ->
            val weight = if (isSelfieGroup) 2.0f else 1.0f // Give selfies double weight
            for (i in embedding.indices) {
                centroid[i] += embedding[i] * weight
            }
            totalWeight += weight
        }
        return normalizeEmbedding(centroid.map { it / totalWeight }.toFloatArray())
    }

    private fun normalizeEmbedding(embedding: FloatArray): FloatArray {
        var norm = 0f
        embedding.forEach { norm += it * it }
        norm = sqrt(norm)
        return if (norm == 0f) embedding else embedding.map { it / norm }.toFloatArray()
    }

    private fun calculateCosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        if (embedding1.size != embedding2.size) {
            Log.w(TAG, "Embedding size mismatch: ${embedding1.size} vs ${embedding2.size}")
            return 0f
        }
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f
        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            norm1 += embedding1[i] * embedding1[i]
            norm2 += embedding2[i] * embedding2[i]
        }
        norm1 = sqrt(norm1)
        norm2 = sqrt(norm2)
        if (norm1 == 0f || norm2 == 0f) {
            Log.w(TAG, "Zero norm detected, returning 0 similarity")
            return 0f
        }
        return (dotProduct / (norm1 * norm2)).coerceIn(-1f, 1f)
    }
}