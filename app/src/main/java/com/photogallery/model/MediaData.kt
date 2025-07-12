package com.photogallery.model

import android.net.Uri

data class MediaData(
    val id: Long,
    val name: String,
    val path: String,
    val uri: Uri,
    val dateTaken: Long,
    val isVideo: Boolean = false,
    val duration: Long = 0L,
    var daysRemaining: Int = 0,
    var isFavorite: Boolean = false,
)