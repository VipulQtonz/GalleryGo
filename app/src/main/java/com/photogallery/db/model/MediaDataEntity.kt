package com.photogallery.db.model

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.photogallery.utils.UriConverter

@Entity(tableName = "media_data")
@TypeConverters(UriConverter::class)
data class MediaDataEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val originalPath: String,
    val recyclePath: String,
    val uri: Uri,
    val dateTaken: Long,
    val isVideo: Boolean = false,
    val duration: Long = 0L,
    val deletedAt: Long = 0,
)


