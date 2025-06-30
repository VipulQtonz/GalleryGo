package com.photogallery.utils

internal fun formatImageFileSize(sizeInBytes: Long): String {
    val kb = 1024
    val mb = kb * 1024
    val gb = mb * 1024

    return when {
        sizeInBytes >= gb -> String.format("%.2f GB", sizeInBytes.toFloat() / gb)
        sizeInBytes >= mb -> String.format("%.2f MB", sizeInBytes.toFloat() / mb)
        sizeInBytes >= kb -> String.format("%.2f KB", sizeInBytes.toFloat() / kb)
        else -> "$sizeInBytes B"
    }
}
