package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.MediaItem
import com.example.data.model.MediaRefreshPolicy
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_items WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun getMediaForProfile(profileId: String): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE profileId = :profileId AND sourceType = :sourceType ORDER BY createdAt DESC")
    fun getMediaBySource(profileId: String, sourceType: String): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE profileId = :profileId AND pluginId = :pluginId ORDER BY createdAt DESC")
    fun getMediaByPlugin(profileId: String, pluginId: String): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE profileId = :profileId AND repositoryId = :repositoryId ORDER BY createdAt DESC")
    fun getMediaByRepository(profileId: String, repositoryId: String): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE profileId = :profileId AND pluginId = :pluginId")
    suspend fun getCloudStreamCatalog(profileId: String, pluginId: String): List<MediaItem>

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

    @Query("UPDATE media_items SET thumbnailUrl = :thumbnailUrl WHERE id = :id")
    suspend fun updateThumbnailUrl(id: String, thumbnailUrl: String)

    @Query("UPDATE media_items SET progressMs = :progressMs, lastWatchedAt = CASE WHEN :progressMs > 0 THEN :watchedAt ELSE lastWatchedAt END WHERE id = :id")
    suspend fun updateProgress(id: String, progressMs: Long, watchedAt: Long = System.currentTimeMillis())

    @Query("UPDATE media_items SET progressMs = :progressMs, durationMs = CASE WHEN :durationMs > 0 THEN :durationMs ELSE durationMs END, lastWatchedAt = CASE WHEN :progressMs > 0 THEN :watchedAt ELSE lastWatchedAt END WHERE id = :id")
    suspend fun updatePlaybackState(id: String, progressMs: Long, durationMs: Long, watchedAt: Long = System.currentTimeMillis())

    @Query("UPDATE media_items SET durationMs = :durationMs WHERE id = :id AND durationMs <= 0 AND :durationMs > 0")
    suspend fun updateDurationIfMissing(id: String, durationMs: Long)

    @Delete
    suspend fun deleteMedia(item: MediaItem)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteMediaById(id: String)

    @Query("DELETE FROM media_items WHERE profileId = :profileId")
    suspend fun clearProfileMedia(profileId: String)

    @Query("SELECT * FROM media_items WHERE playlistId = :playlistId")
    suspend fun getPlaylistItems(playlistId: String): List<MediaItem>

    @Query("DELETE FROM media_items WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: String)

    @Query("DELETE FROM media_items WHERE profileId = :profileId AND pluginId = :pluginId")
    suspend fun clearCloudStreamCatalog(profileId: String, pluginId: String)

    @Transaction
    suspend fun replaceCloudStreamCatalog(profileId: String, pluginId: String, items: List<MediaItem>) {
        require(items.all { it.profileId == profileId && it.pluginId == pluginId }) {
            "Identitas katalog CloudStream tidak konsisten."
        }
        clearCloudStreamCatalog(profileId, pluginId)
        if (items.isNotEmpty()) insertAll(items)
    }

    @Transaction
    suspend fun replacePlaylist(playlistId: String, items: List<MediaItem>) {
        require(items.all { it.playlistId == playlistId }) {
            "Identitas playlist tidak konsisten."
        }
        val existing = getPlaylistItems(playlistId)
        val merged = MediaRefreshPolicy.mergePlaylist(existing, items)
        clearPlaylist(playlistId)
        if (merged.isNotEmpty()) insertAll(merged)
    }

    @Query("SELECT COUNT(*) FROM media_items WHERE playlistId = :playlistId")
    suspend fun countForPlaylist(playlistId: String): Int

    @Query("SELECT COUNT(*) FROM media_items WHERE url = :url AND id != :excludedId")
    suspend fun countOtherReferencesToUrl(url: String, excludedId: String): Int

    @Query("SELECT COUNT(*) FROM media_items WHERE url = :url AND playlistId != :excludedPlaylistId")
    suspend fun countReferencesOutsidePlaylist(url: String, excludedPlaylistId: String): Int
}
