package com.photogallery.model

import android.net.Uri

data class DocumentGroup(
    val name: String,
    val allUris: MutableList<Uri> = mutableListOf()
)