package com.photogallery.model

import android.net.Uri

data class DuplicateImageGroup(
    val representativeUri: Uri,
    val allUris: MutableList<Uri>
)