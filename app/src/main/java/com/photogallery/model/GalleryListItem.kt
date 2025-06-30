package com.photogallery.model

sealed class GalleryListItem {
    data class DateHeader(val date: String) : GalleryListItem()
    data class MediaItem(val media: MediaData) : GalleryListItem()
}