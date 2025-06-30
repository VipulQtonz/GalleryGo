package com.photogallery.model

import android.net.Uri

data class PeopleGroup(
    val groupId: Int,
    val allUris: MutableList<Uri>,
    var thumbnailUri: Uri? = null // Thumbnail for the face group
)