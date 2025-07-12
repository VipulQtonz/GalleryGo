package com.photogallery.process

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.photogallery.model.Moment
import com.photogallery.model.MomentGroup
import com.photogallery.model.MomentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

object MomentsUtils {
    private const val TAG = "MomentsUtils"
    private const val MIN_IMAGES_FOR_MOMENT = 3
    private const val DAY_IN_MILLIS = 24 * 60 * 60 * 1000L
    private const val EVENT_TIME_GAP_HOURS = 4 * 60 * 60 * 1000L
    private const val CONTENT_SIMILARITY_THRESHOLD = 1f
    private const val LOCATION_DISTANCE_THRESHOLD = 500 // meters
    private const val TRIP_MAX_DAY_GAP = 3 // days

    private data class PhotoMetadata(
        val uri: Uri,
        val date: Date,
        val location: Pair<Double, Double>?,
        val detectedObjects: List<String>?,
        val isHdr: Boolean = false,
        val isFavorite: Boolean = false,
        val width: Int = 0,
        val height: Int = 0
    )

    suspend fun groupPhotosIntoMoments(context: Context, uris: List<Uri>): List<MomentGroup> {
        if (uris.isEmpty()) return emptyList()

        return withContext(Dispatchers.IO) {
            val metadataList = fetchPhotoMetadata(context, uris)
            if (metadataList.isEmpty()) return@withContext emptyList()

            val usedUris = mutableSetOf<Uri>()
            val moments = mutableListOf<Moment>()

            moments.addAll(createSpecialOccasionMoments(context, metadataList, usedUris))
            moments.addAll(createTripMoments(context, metadataList, usedUris))
            moments.addAll(createEventBasedMoments(context, metadataList, usedUris))
            moments.addAll(createContentBasedMoments(context, metadataList, usedUris))
            moments.addAll(createLocationBasedMoments(context, metadataList, usedUris))
            moments.addAll(createTimeBasedMoments(context, metadataList, usedUris))

            val remainingPhotos = metadataList.filter { it.uri !in usedUris }
                .groupBy { SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(it.date) }

            remainingPhotos.forEach { (_, photos) ->
                if (photos.size >= MIN_IMAGES_FOR_MOMENT) {
                    moments.add(
                        createMoment(
                            context,
                            photos = photos,
                            type = MomentType.TIME_BASED,
                            customTitle = generateAITitle(context, photos, MomentType.TIME_BASED)
                        )
                    )
                } else {
                    photos.forEach { photo ->
                        moments.add(
                            createMoment(
                                context,
                                photos = listOf(photo),
                                type = MomentType.SINGLE,
                                customTitle = generateAITitle(
                                    context,
                                    listOf(photo),
                                    MomentType.SINGLE
                                )
                            )
                        )
                    }
                }
            }

            groupMomentsIntoCategories(moments.sortedByDescending { it.date.time })
        }
    }

    private suspend fun fetchPhotoMetadata(context: Context, uris: List<Uri>): List<PhotoMetadata> {
        return withContext(Dispatchers.IO) {
            val metadataList = mutableListOf<PhotoMetadata>()
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.LATITUDE,
                MediaStore.Images.Media.LONGITUDE,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.IS_FAVORITE
            )

            uris.forEach { uri ->
                try {
                    context.contentResolver.query(uri, projection, null, null, null)
                        ?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val dateTakenCol =
                                    cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                                val dateAddedCol =
                                    cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                                val latCol = cursor.getColumnIndex(MediaStore.Images.Media.LATITUDE)
                                val lonCol =
                                    cursor.getColumnIndex(MediaStore.Images.Media.LONGITUDE)
                                val widthCol = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                                val heightCol =
                                    cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                                val favoriteCol =
                                    cursor.getColumnIndex(MediaStore.Images.Media.IS_FAVORITE)

                                val dateMillis = cursor.getLong(dateTakenCol).takeIf { it > 0 }
                                    ?: (cursor.getLong(dateAddedCol) * 1000)
                                val date = Date(dateMillis)
                                val location =
                                    if (!cursor.isNull(latCol) && !cursor.isNull(lonCol)) {
                                        Pair(cursor.getDouble(latCol), cursor.getDouble(lonCol))
                                    } else null
                                val width = cursor.getInt(widthCol)
                                val height = cursor.getInt(heightCol)
                                val isFavorite = cursor.getInt(favoriteCol) == 1

                                val isHdr = try {
                                    context.contentResolver.openInputStream(uri)?.use { stream ->
                                        val exif = ExifInterface(stream)
                                        exif.getAttribute("Software")
                                            ?.contains("HDR", ignoreCase = true) == true ||
                                                exif.getAttribute(ExifInterface.TAG_MODEL)
                                                    ?.let { model ->
                                                        model.contains("HDR", ignoreCase = true) ||
                                                                model.contains(
                                                                    "Pixel",
                                                                    ignoreCase = true
                                                                ) // Pixels often use HDR+
                                                    } == true
                                    } == true
                                } catch (_: Exception) {
                                    false
                                }

                                val detectedObjects = try {
                                    val image = InputImage.fromFilePath(context, uri)
                                    val labeler =
                                        ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
                                    labeler.process(image).await()
                                        .map { it.text.lowercase(Locale.getDefault()) }
                                        .filter { it.length > 3 }
                                } catch (_: Exception) {
                                    null
                                }

                                metadataList.add(
                                    PhotoMetadata(
                                        uri = uri,
                                        date = date,
                                        location = location,
                                        detectedObjects = detectedObjects,
                                        isHdr = isHdr,
                                        isFavorite = isFavorite,
                                        width = width,
                                        height = height
                                    )
                                )
                            }
                        }
                } catch (e: Exception) {
                    Log.w(TAG, "Error processing photo $uri: ${e.message}")
                }
            }
            metadataList
        }
    }

    private suspend fun createSpecialOccasionMoments(
        context: Context,
        metadataList: List<PhotoMetadata>,
        usedUris: MutableSet<Uri>
    ): List<Moment> {
        val holidayGroups = metadataList
            .filter { it.uri !in usedUris }
            .groupBy { HolidayDetector.getHoliday(it.date) }
            .filter { it.key != null && it.value.size >= MIN_IMAGES_FOR_MOMENT }

        return holidayGroups.map { (holiday, photos) ->
            usedUris.addAll(photos.map { it.uri })
            createMoment(
                context,
                photos = photos,
                type = MomentType.SPECIAL_OCCASION,
                customTitle = holiday!!
            )
        }
    }

    private suspend fun createTripMoments(
        context: Context,
        metadataList: List<PhotoMetadata>,
        usedUris: MutableSet<Uri>
    ): List<Moment> {
        val availablePhotos = metadataList
            .filter { it.uri !in usedUris && it.location != null }
            .sortedBy { it.date.time }

        if (availablePhotos.isEmpty()) return emptyList()

        val tripMoments = mutableListOf<Moment>()
        var currentTrip = mutableListOf<PhotoMetadata>()
        var lastLocation: Pair<Double, Double>? = null

        for (photo in availablePhotos) {
            if (currentTrip.isEmpty()) {
                currentTrip.add(photo)
                lastLocation = photo.location
            } else {
                val distance = lastLocation?.let { loc ->
                    LocationUtils.distanceBetween(
                        loc.first, loc.second,
                        photo.location!!.first, photo.location.second
                    )
                } ?: Float.MAX_VALUE

                val timeDiff = (photo.date.time - currentTrip.last().date.time) / DAY_IN_MILLIS

                if (distance > LOCATION_DISTANCE_THRESHOLD && timeDiff <= TRIP_MAX_DAY_GAP) {
                    currentTrip.add(photo)
                    lastLocation = photo.location
                } else {
                    if (currentTrip.size >= MIN_IMAGES_FOR_MOMENT) {
                        tripMoments.add(createTripMoment(context, currentTrip))
                        usedUris.addAll(currentTrip.map { it.uri })
                    }
                    currentTrip = mutableListOf(photo)
                    lastLocation = photo.location
                }
            }
        }

        if (currentTrip.size >= MIN_IMAGES_FOR_MOMENT) {
            tripMoments.add(createTripMoment(context, currentTrip))
            usedUris.addAll(currentTrip.map { it.uri })
        }

        return tripMoments
    }

    private suspend fun createTripMoment(
        context: Context,
        photos: List<PhotoMetadata>
    ): Moment {
        val locations = photos.mapNotNull { it.location }
        val startDate = photos.first().date
        val endDate = photos.last().date
        val durationDays = ((endDate.time - startDate.time) / DAY_IN_MILLIS).toInt() + 1

        val locationNames = locations.distinct().mapNotNull { loc ->
            LocationUtils.getLocationName(context, loc.first, loc.second)
        }.distinct()

        val title = when {
            locationNames.size == 1 -> "${locationNames[0]} Trip"
            durationDays <= 1 -> "Day Trip"
            durationDays <= 3 -> "Weekend Getaway"
            else -> "${durationDays}-Day Trip"
        }

        return createMoment(
            context,
            photos = photos,
            type = MomentType.TRIP,
            customTitle = title
        )
    }

    private suspend fun createEventBasedMoments(
        context: Context,
        metadataList: List<PhotoMetadata>,
        usedUris: MutableSet<Uri>
    ): List<Moment> {
        val availablePhotos = metadataList
            .filter { it.uri !in usedUris }
            .sortedBy { it.date.time }

        if (availablePhotos.isEmpty()) return emptyList()

        val moments = mutableListOf<Moment>()
        var currentEvent = mutableListOf<PhotoMetadata>()
        var lastLocation: Pair<Double, Double>? = null

        for (i in 0 until availablePhotos.size) {
            val photo = availablePhotos[i]
            if (currentEvent.isEmpty()) {
                currentEvent.add(photo)
                lastLocation = photo.location
            } else {
                val prevPhoto = currentEvent.last()
                val timeDiff = photo.date.time - prevPhoto.date.time
                val distance = if (photo.location != null && lastLocation != null) {
                    LocationUtils.distanceBetween(
                        lastLocation.first, lastLocation.second,
                        photo.location.first, photo.location.second
                    )
                } else Float.MAX_VALUE

                val contentSimilar = isContentSimilar(prevPhoto, photo)

                if (timeDiff <= EVENT_TIME_GAP_HOURS &&
                    distance <= LOCATION_DISTANCE_THRESHOLD &&
                    contentSimilar
                ) {
                    currentEvent.add(photo)
                } else {
                    if (currentEvent.size >= MIN_IMAGES_FOR_MOMENT) {
                        moments.add(createEventMoment(context, currentEvent))
                        usedUris.addAll(currentEvent.map { it.uri })
                    }
                    currentEvent = mutableListOf(photo)
                }
                lastLocation = photo.location ?: lastLocation
            }
        }

        if (currentEvent.size >= MIN_IMAGES_FOR_MOMENT) {
            moments.add(createEventMoment(context, currentEvent))
            usedUris.addAll(currentEvent.map { it.uri })
        }

        return moments
    }

    private suspend fun createEventMoment(
        context: Context,
        photos: List<PhotoMetadata>
    ): Moment {
        val objects = photos.flatMap { it.detectedObjects ?: emptyList() }
        val objectCounts = objects.groupingBy { it }.eachCount()
        val topObjects = objectCounts.entries.sortedByDescending { it.value }.take(3).map { it.key }

        val title = when {
            topObjects.contains("cake") -> "Celebration"
            topObjects.contains("beach") -> "Beach Day"
            else -> "Event"
        }

        return createMoment(
            context,
            photos = photos,
            type = MomentType.EVENT_BASED,
            customTitle = title
        )
    }

    private suspend fun createContentBasedMoments(
        context: Context,
        metadataList: List<PhotoMetadata>,
        usedUris: MutableSet<Uri>
    ): List<Moment> {
        val availablePhotos = metadataList
            .filter { it.uri !in usedUris && it.detectedObjects != null }
            .groupBy { photo ->
                val primaryObject = photo.detectedObjects?.firstOrNull() ?: "other"
                val calendar = Calendar.getInstance().apply { time = photo.date }
                "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH)}-$primaryObject"
            }
            .filter { it.value.size >= MIN_IMAGES_FOR_MOMENT }

        return availablePhotos.map { (_, photos) ->
            usedUris.addAll(photos.map { it.uri })
            val objects = photos.flatMap { it.detectedObjects ?: emptyList() }
            val topObject = objects.groupingBy { it }.eachCount()
                .maxByOrNull { it.value }?.key ?: ""

            createMoment(
                context,
                photos = photos,
                type = MomentType.CONTENT_BASED,
                customTitle = when (topObject) {
                    "food" -> "Food Photos"
                    "dog", "cat", "pet" -> "Pet Photos"
                    "beach" -> "Beach Photos"
                    "mountain" -> "Mountain Views"
                    else -> "${topObject.replaceFirstChar { it.titlecase() }} Photos"
                }
            )
        }
    }

    private suspend fun createLocationBasedMoments(
        context: Context,
        metadataList: List<PhotoMetadata>,
        usedUris: MutableSet<Uri>
    ): List<Moment> {
        val availablePhotos = metadataList
            .filter { it.uri !in usedUris && it.location != null }
            .groupBy { photo ->
                val locationName = LocationUtils.getLocationName(
                    context,
                    photo.location!!.first,
                    photo.location.second
                ) ?: "Unknown"
                val calendar = Calendar.getInstance().apply { time = photo.date }
                "${calendar.get(Calendar.YEAR)}-$locationName"
            }
            .filter { it.value.size >= MIN_IMAGES_FOR_MOMENT }

        return availablePhotos.map { (_, photos) ->
            usedUris.addAll(photos.map { it.uri })
            val locationName = LocationUtils.getLocationName(
                context,
                photos.first().location!!.first,
                photos.first().location!!.second
            ) ?: "Location"

            createMoment(
                context,
                photos = photos,
                type = MomentType.LOCATION_BASED,
                customTitle = "$locationName Photos"
            )
        }
    }

    private suspend fun createTimeBasedMoments(
        context: Context,
        metadataList: List<PhotoMetadata>,
        usedUris: MutableSet<Uri>
    ): List<Moment> {
        return metadataList
            .filter { it.uri !in usedUris }
            .groupBy { photo ->
                val calendar = Calendar.getInstance().apply { time = photo.date }
                "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH)}"
            }
            .filter { it.value.size >= MIN_IMAGES_FOR_MOMENT }
            .map { (_, photos) ->
                usedUris.addAll(photos.map { it.uri })
                createMoment(
                    context,
                    photos = photos,
                    type = MomentType.TIME_BASED,
                    customTitle = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                        .format(photos.first().date)
                )
            }
    }

    private fun isContentSimilar(photo1: PhotoMetadata, photo2: PhotoMetadata): Boolean {
        val objects1 = photo1.detectedObjects?.toSet() ?: emptySet()
        val objects2 = photo2.detectedObjects?.toSet() ?: emptySet()
        val commonObjects = objects1.intersect(objects2).size
        val totalObjects = objects1.union(objects2).size
        val similarityScore = if (totalObjects > 0) commonObjects.toFloat() / totalObjects else 0f
        return similarityScore >= CONTENT_SIMILARITY_THRESHOLD
    }

    private suspend fun createMoment(
        context: Context,
        photos: List<PhotoMetadata>,
        type: MomentType,
        customTitle: String? = null,
        customLocation: String? = null
    ): Moment {
        val date = photos.first().date
        val title = customTitle ?: generateAITitle(context, photos, type)
        val location = customLocation ?: photos.firstNotNullOfOrNull {
            it.location?.let { loc ->
                LocationUtils.getLocationName(
                    context,
                    loc.first,
                    loc.second
                )
            }
        }

        return Moment(
            id = "${type.name.lowercase()}_${UUID.randomUUID()}",
            title = title,
            date = date,
            location = location,
            representativeUri = selectBestPhoto(photos),
            allUris = photos.map { it.uri },
            momentType = type
        )
    }

    private suspend fun generateAITitle(
        context: Context,
        photos: List<PhotoMetadata>,
        type: MomentType
    ): String {
        if (photos.size == 1) {
            val photo = photos[0]
            return when {
                photo.detectedObjects?.contains("sunset") == true -> "Sunset"
                photo.detectedObjects?.contains("food") == true -> "Food"
                photo.detectedObjects?.contains("pet") == true -> "Pet"
                else -> "Photo"
            }
        }

        val date = photos.first().date
        val dateFormat = when (type) {
            MomentType.TIME_BASED -> SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        }
        val dateString = dateFormat.format(date)

        val objects = photos.flatMap { it.detectedObjects ?: emptyList() }
        val objectCounts = objects.groupingBy { it }.eachCount()
        val topObjects = objectCounts.entries.sortedByDescending { it.value }.take(3).map { it.key }

        return when (type) {
            MomentType.TRIP -> "Trip"
            MomentType.SPECIAL_OCCASION -> "Celebration"
            MomentType.EVENT_BASED -> when {
                topObjects.contains("cake") -> "Celebration"
                topObjects.contains("beach") -> "Beach Day"
                else -> "Event"
            }

            MomentType.CONTENT_BASED -> when {
                topObjects.contains("food") -> "Food Photos"
                topObjects.contains("pet") -> "Pet Photos"
                else -> "Photos"
            }

            MomentType.LOCATION_BASED -> {
                val location = photos.firstNotNullOfOrNull { it.location }?.let { loc ->
                    LocationUtils.getLocationName(context, loc.first, loc.second)
                } ?: "Location"
                "$location Photos"
            }

            else -> "$dateString Photos"
        }
    }

    private fun selectBestPhoto(photos: List<PhotoMetadata>): Uri {
        return photos.maxWithOrNull(
            compareBy<PhotoMetadata> { it.isFavorite }
                .thenByDescending { it.isHdr }
                .thenByDescending { (it.width * it.height) } // Higher resolution
                .thenByDescending { it.detectedObjects?.size ?: 0 }
                .thenByDescending { it.date.time }
        )?.uri ?: photos.first().uri
    }

    private fun groupMomentsIntoCategories(moments: List<Moment>): List<MomentGroup> {
        val now = Calendar.getInstance()
        val thisYear = now.get(Calendar.YEAR)
        val thisMonth = now.get(Calendar.MONTH)

        return moments.groupBy { moment ->
            val cal = Calendar.getInstance().apply { time = moment.date }
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH)

            when {
                year == thisYear && month == thisMonth -> "This month"
                year == thisYear -> SimpleDateFormat(
                    "MMMM",
                    Locale.getDefault()
                ).format(moment.date)

                else -> SimpleDateFormat("yyyy", Locale.getDefault()).format(moment.date)
            }
        }.map { (title, moments) ->
            MomentGroup(
                title = title,
                moments = moments,
                representativeUri = moments.first().representativeUri
            )
        }.sortedByDescending { group ->
            group.moments.first().date.time
        }
    }
}

object HolidayDetector {
    private val holidays = mapOf(
        "12-25" to "Christmas",
        "12-31" to "New Year's Eve",
        "01-01" to "New Year's Day",
        "07-04" to "Independence Day",
        "10-31" to "Halloween",
        "11-28" to "Thanksgiving",
        "02-14" to "Valentine's Day",
        "05-14" to "Mother's Day",
        "06-18" to "Father's Day"
    )

    fun getHoliday(date: Date): String? {
        val calendar = Calendar.getInstance().apply { time = date }
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val key = String.format("%02d-%02d", month, day)
        return holidays[key]
    }
}
