package com.photogallery.db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "skipped_images")
data class SkippedImage(
    @PrimaryKey val uri: String,
    val reason: String
)