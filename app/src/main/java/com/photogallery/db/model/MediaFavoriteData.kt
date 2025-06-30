package com.photogallery.db.model

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.photogallery.utils.UriConverter
import kotlin.time.Duration

@Entity(tableName = "media_favorite_data")
@TypeConverters(UriConverter::class)
data class MediaFavoriteData(
    @PrimaryKey val id: Long,
    val name: String,
    val originalPath: String,
    val uri: Uri,
    val dateTaken: Long ,
    val duration: Long,
    val isVideo: Boolean = false,
    val isFavorite: Boolean = false
)


