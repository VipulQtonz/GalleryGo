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
import kotlin.math.pow
import kotlin.math.sqrt

object FaceGroupingUtils {
    private const val TAG = "FaceGroupingUtils"
    private const val BASE_SIMILARITY_THRESHOLD = 0.98f
    private const val MERGE_THRESHOLD = 0.98f
    private const val DBSCAN_EPS = 0.70f
    private const val MIN_SAMPLES = 4

    private val databaseDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    data class FaceGroup(
        val groupId: String = UUID.randomUUID().toString(),
        val uris: MutableSet<String> = mutableSetOf(),
        val embeddings: MutableList<FloatArray> = mutableListOf(),
        var representativeUri: String? = null,
        var referenceEmbedding: FloatArray? = null
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
            Log.d(TAG, "Starting improved face grouping process")
            val database = PhotoGalleryDatabase.getDatabase(context)
            val allEmbeddings = database.photoGalleryDao().getAllEmbeddings()
            Log.i(TAG, "Retrieved ${allEmbeddings.size} face embeddings from database")

            if (allEmbeddings.isEmpty()) {
                Log.w(TAG, "No embeddings found in database")
                return@withContext emptyList()
            }

            val (validEmbeddings, invalidUris) = filterValidEmbeddings(context, allEmbeddings)
            Log.i(
                TAG,
                "Found ${validEmbeddings.size} valid embeddings, ${invalidUris.size} invalid URIs"
            )

            if (invalidUris.isNotEmpty()) {
                deleteInvalidEmbeddings(database, invalidUris)
            }

            if (validEmbeddings.isEmpty()) {
                Log.w(TAG, "No valid embeddings after URI validation")
                return@withContext emptyList()
            }

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

            val expectedEmbeddingSize = getExpectedEmbeddingSize(selfieEmbeddings)
                ?: return@withContext emptyList()

            // Normalize all embeddings first
            val normalizedSelfies = selfieEmbeddings.map {
                it.copy(embedding = normalizeEmbedding(it.embedding))
            }
            val normalizedMultiFace = multiFaceEmbeddings.map {
                it.copy(embedding = normalizeEmbedding(it.embedding))
            }

            // Use DBSCAN for initial clustering
            val selfieGroups = dbscanClustering(normalizedSelfies, DBSCAN_EPS, MIN_SAMPLES)

            // Assign multi-face images to existing groups
            val faceGroups = assignMultiFaceImages(
                normalizedMultiFace,
                selfieGroups,
                expectedEmbeddingSize
            )

            // Merge similar groups
            val mergedGroups = mergeSimilarGroups(faceGroups)

            // Final processing and validation
            val validGroups = processFinalGroups(mergedGroups)

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

    private fun dbscanClustering(
        embeddings: List<FaceEmbedding>,
        eps: Float = DBSCAN_EPS,
        minSamples: Int = MIN_SAMPLES
    ): MutableList<FaceGroup> {
        val visited = mutableSetOf<Int>()
        val clusters = mutableListOf<FaceGroup>()
        var clusterId = 0

        embeddings.forEachIndexed { index, _ ->
            if (index !in visited) {
                visited.add(index)
                val neighbors = regionQuery(embeddings, index, eps)

                if (neighbors.size >= minSamples) {
                    // Expand cluster
                    val cluster = FaceGroup(groupId = (clusterId++).toString())
                    expandCluster(embeddings, visited, cluster, index, eps, minSamples)

                    // Only keep meaningful clusters
                    if (cluster.uris.size >= minSamples) {
                        clusters.add(cluster)
                    }
                }
            }
        }

        // Assign representative for each cluster
        clusters.forEach { group ->
            group.representativeUri = selectBestRepresentative(group)
            group.referenceEmbedding = calculateCentroid(group.embeddings)
        }

        Log.d(TAG, "DBSCAN clustering formed ${clusters.size} clusters")
        return clusters
    }

    private fun regionQuery(embeddings: List<FaceEmbedding>, index: Int, eps: Float): List<Int> {
        val neighbors = mutableListOf<Int>()
        val current = embeddings[index]

        embeddings.forEachIndexed { i, embedding ->
            if (calculateCombinedSimilarity(current.embedding, embedding.embedding) >= eps) {
                neighbors.add(i)
            }
        }

        return neighbors
    }

    private fun expandCluster(
        embeddings: List<FaceEmbedding>,
        visited: MutableSet<Int>,
        cluster: FaceGroup,
        index: Int,
        eps: Float,
        minSamples: Int
    ) {
        val seeds = mutableListOf(index)

        while (seeds.isNotEmpty()) {
            val current = seeds.removeAt(0)
            cluster.uris.add(embeddings[current].uri)
            cluster.embeddings.add(embeddings[current].embedding)

            val neighbors = regionQuery(embeddings, current, eps)
            if (neighbors.size >= minSamples) {
                neighbors.forEach { neighbor ->
                    if (neighbor !in visited) {
                        visited.add(neighbor)
                        seeds.add(neighbor)
                    }
                }
            }
        }
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
            val embeddingVector = embedding.embedding

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
                    val threshold = getDynamicThreshold(faceGroups.size)

                    for (group in faceGroups) {
                        val similarity = calculateCombinedSimilarity(
                            group.referenceEmbedding ?: calculateCentroid(group.embeddings),
                            embeddingVector
                        )

                        if (similarity >= threshold && similarity > bestSimilarity) {
                            bestSimilarity = similarity
                            bestGroup = group
                        }
                    }

                    if (bestGroup != null) {
                        bestGroup.uris.add(embedding.uri)
                        bestGroup.embeddings.add(embeddingVector)
                        assignedUris.add(embedding.uri)
                        Log.d(
                            TAG,
                            "Assigned multi-face to group ${bestGroup.groupId} with similarity $bestSimilarity"
                        )
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

                    val centroid1 =
                        group1.referenceEmbedding ?: calculateCentroid(group1.embeddings)
                    val centroid2 =
                        group2.referenceEmbedding ?: calculateCentroid(group2.embeddings)
                    val similarity = calculateCombinedSimilarity(centroid1, centroid2)

                    if (similarity >= MERGE_THRESHOLD) {
                        mergedGroup.uris.addAll(group2.uris)
                        mergedGroup.embeddings.addAll(group2.embeddings)
                        mergedGroupIds.add(group2.groupId)
                        didMerge = true
                        Log.d(
                            TAG,
                            "Merged group ${group2.groupId} into ${group1.groupId} with similarity $similarity"
                        )
                    }
                }

                newGroups.add(mergedGroup)
            }

            currentGroups = newGroups
        }

        return currentGroups
    }

    private fun processFinalGroups(groups: List<FaceGroup>): List<FaceGroup> {
        return groups.filter { it.uris.size >= MIN_SAMPLES }.map { group ->
            val centroid = calculateCentroid(group.embeddings)
            group.copy(
                representativeUri = selectBestRepresentative(group),
                referenceEmbedding = centroid
            )
        }
    }

    private fun selectBestRepresentative(group: FaceGroup): String {
        if (group.uris.isEmpty()) return ""
        if (group.embeddings.isEmpty()) return group.uris.first()

        val centroid = group.referenceEmbedding ?: calculateCentroid(group.embeddings)
        var bestUri = group.uris.first()
        var bestSimilarity = -1f

        group.embeddings.forEachIndexed { index, embedding ->
            val similarity = calculateCombinedSimilarity(centroid, embedding)
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestUri = group.uris.elementAt(index)
            }
        }

        return bestUri
    }

    private fun calculateCombinedSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        if (embedding1.size != embedding2.size) {
            Log.w(TAG, "Embedding size mismatch: ${embedding1.size} vs ${embedding2.size}")
            return 0f
        }

        // Calculate cosine similarity
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
        val cosineSim = if (norm1 == 0f || norm2 == 0f) 0f else (dotProduct / (norm1 * norm2))

        // Calculate normalized Euclidean distance
        var sum = 0f
        for (i in embedding1.indices) {
            sum += (embedding1[i] - embedding2[i]).pow(2)
        }
        val euclideanDist = sqrt(sum)
        val normalizedEuclidean = 1 - (euclideanDist / 2) // Normalize to [0,1]

        // Combined metric (weighted average)
        return 0.7f * cosineSim + 0.3f * normalizedEuclidean
    }

    private fun calculateCentroid(embeddings: List<FloatArray>): FloatArray {
        if (embeddings.isEmpty()) return floatArrayOf()
        val size = embeddings.first().size
        val centroid = FloatArray(size)

        embeddings.forEach { embedding ->
            for (i in embedding.indices) {
                centroid[i] += embedding[i]
            }
        }

        for (i in centroid.indices) {
            centroid[i] /= embeddings.size
        }

        return normalizeEmbedding(centroid)
    }

    private fun normalizeEmbedding(embedding: FloatArray): FloatArray {
        var norm = 0f
        embedding.forEach { norm += it * it }
        norm = sqrt(norm)
        return if (norm == 0f) embedding else embedding.map { it / norm }.toFloatArray()
    }

    private fun getDynamicThreshold(groupCount: Int): Float {
        // Adjust threshold based on number of groups
        return when {
            groupCount < 5 -> BASE_SIMILARITY_THRESHOLD - 0.05f // More permissive for few groups
            groupCount > 20 -> BASE_SIMILARITY_THRESHOLD + 0.05f // More strict for many groups
            else -> BASE_SIMILARITY_THRESHOLD
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
}