package com.photogallery.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.photogallery.db.model.MediaDataEntity
import com.photogallery.db.model.MediaFavoriteData

@Dao
interface PhotoGalleryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedMedia(media: MediaDataEntity)

    @Query("SELECT * FROM media_data")
    suspend fun getAllDeletedMedia(): List<MediaDataEntity>

    @Query("DELETE FROM media_data WHERE id = :id")
    suspend fun deleteDeletedMediaById(id: Long)

    @Insert
    suspend fun insertFavorite(media: MediaFavoriteData)

    @Delete
    suspend fun deleteFavorite(media: MediaFavoriteData)

    @Query("SELECT * FROM media_favorite_data")
    suspend fun getAllFavorites(): List<MediaFavoriteData>

    @Query("SELECT * FROM media_favorite_data WHERE id = :id")
    suspend fun getFavoriteById(id: Long): MediaFavoriteData?

    @Query("SELECT * FROM media_favorite_data WHERE uri = :uriString")
    suspend fun getFavoriteByUri(uriString: String): MediaFavoriteData?


    @Update
    suspend fun updateFavorite(favorite: MediaFavoriteData)
}