package com.photogallery.model

import android.net.Uri

data class DuplicateImageGroup(
    val representativeUri: Uri, // Representative image for the group
    val allUris: MutableList<Uri> // List of all duplicate image URIs
)