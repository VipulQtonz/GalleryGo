package com.photogallery.model

import android.net.Uri
import android.os.Parcelable


data class Album(
    val name: String,
    val photoUris: MutableList<Uri>,
    val isAddAlbum: Boolean = false
)