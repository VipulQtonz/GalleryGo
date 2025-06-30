package com.photogallery.model

import android.net.Uri
import java.util.*

data class Moment(
    val id: String,
    val title: String,
    val date: Date,
    val location: String?,
    val representativeUri: Uri,
    val allUris: List<Uri>,
    val momentType: MomentType
)

enum class MomentType {
    TRIP,
    SPECIAL_OCCASION,
    EVENT_BASED,
    CONTENT_BASED,
    LOCATION_BASED,
    TIME_BASED,
    SINGLE
}

data class MomentGroup(
    val title: String,
    val moments: List<Moment>,
    val representativeUri: Uri
)