package com.photogallery.db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "face_embeddings")
data class FaceEmbedding(
    @PrimaryKey val faceId: String,
    val uri: String,
    val embedding: FloatArray,
    val timestamp: Long = System.currentTimeMillis()
)