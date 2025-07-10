package com.photogallery.process

import android.content.Context
import android.database.Cursor
import android.location.Geocoder
import android.location.Location
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

object LocationUtils {

    fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    fun getPhotosFromGallery(context: Context): List<Uri> {
        val photoUris = mutableListOf<Uri>()
        try {
            val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.MIME_TYPE)
            val cursor: Cursor? = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )

            if (cursor == null) {
                return emptyList()
            }

            cursor.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val mimeTypeColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                while (it.moveToNext()) {
                    val mimeType = it.getString(mimeTypeColumn)
                    if (mimeType?.startsWith("image/") == true) {
                        val id = it.getLong(idColumn)
                        val uri = Uri.withAppendedPath(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id.toString()
                        )
                        photoUris.add(uri)
                    }
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }
        return photoUris
    }

    fun getImageLocationFromUri(context: Context, uri: Uri): Pair<Double, Double>? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                val latRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)
                val lonRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)
                val latStr = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
                val lonStr = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)

                if (latStr != null && lonStr != null && latRef != null && lonRef != null) {
                    val lat = convertToDegree(latStr) * if (latRef == "N") 1 else -1
                    val lon = convertToDegree(lonStr) * if (lonRef == "E") 1 else -1
                    Pair(lat, lon)
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun convertToDegree(stringDMS: String): Double {
        try {
            val split = stringDMS.split(",").map { it.trim().split("/") }
            if (split.size != 3) return 0.0

            val degrees = split[0][0].toDouble() / split[0][1].toDouble()
            val minutes = split[1][0].toDouble() / split[1][1].toDouble()
            val seconds = split[2][0].toDouble() / split[2][1].toDouble()

            return degrees + (minutes / 60) + (seconds / 3600)
        } catch (e: Exception) {
            e.printStackTrace()
            return 0.0
        }
    }

    suspend fun getLocationName(context: Context, lat: Double, lng: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                if (addresses?.isNotEmpty() == true) {
                    val address = addresses[0]
                    if (address.locality != null && address.countryName != null) {
                        "${address.locality}, ${address.countryName}"
                    } else if (address.adminArea != null && address.countryName != null) {
                        "${address.adminArea}, ${address.countryName}"
                    } else {
                        address.countryName ?: "Unknown Location"
                    }
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
}