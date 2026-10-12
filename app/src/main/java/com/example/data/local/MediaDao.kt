package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.MediaItem
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_items WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun getMediaForProfile(profileId: String): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE profileId = :profileId AND sourceType = :sourceType ORDER BY createdAt DESC")
    fun getMediaBySource(profileId: String, sourceType: String): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE profileId = :profileId")
    suspend fun getMediaListForProfile(profileId: String): List<MediaItem>

    @Query("SELECT * FROM media_items")
    suspend fun getAllMediaList(): List<MediaItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(item: MediaItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MediaItem>)

    @Update
    suspend fun updateMedia(item: MediaItem)

    @Query("UPDATE media_items SET progressMs = :progressMs WHERE id = :id")
    suspend fun updateProgress(id: String, progressMs: Long)

    @Delete
    suspend fun deleteMedia(item: MediaItem)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteMediaById(id: String)

    @Query("DELETE FROM media_items WHERE profileId = :profileId")
    suspend fun clearProfileMedia(profileId: String)
}
