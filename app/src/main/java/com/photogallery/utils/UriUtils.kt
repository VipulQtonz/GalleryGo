package com.photogallery.utils


import android.content.Context
import android.net.Uri
import java.io.IOException

object UriUtils {
    fun isUriValid(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.close() }
            true
        } catch (_: IOException) {
            false
        }
    }
}