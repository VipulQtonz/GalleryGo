package com.photogallery.model

import android.net.Uri

data class GroupedLocationPhoto(
    val locationName: String?,
    val representativeUri: Uri,
    val allUris: MutableList<Uri>,
    val latitude: Double?, // Add latitude
    val longitude: Double? // Add longitude
)