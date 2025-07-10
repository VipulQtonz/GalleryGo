package com.photogallery.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.photogallery.db.model.FaceEmbedding
import com.photogallery.db.model.MediaDataEntity
import com.photogallery.db.model.MediaFavoriteData
import com.photogallery.db.model.SkippedImage
import com.photogallery.utils.FloatArrayConverter

@Database(
    entities = [MediaDataEntity::class, MediaFavoriteData::class, FaceEmbedding::class, SkippedImage::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(FloatArrayConverter::class)
abstract class PhotoGalleryDatabase : RoomDatabase() {
    abstract fun photoGalleryDao(): PhotoGalleryDao

    companion object {
        @Volatile
        private var INSTANCE: PhotoGalleryDatabase? = null

        @JvmStatic
        fun getDatabase(context: Context): PhotoGalleryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PhotoGalleryDatabase::class.java,
                    "photo_gallery_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}