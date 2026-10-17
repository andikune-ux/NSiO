package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.SourcePlaylist
import kotlinx.coroutines.flow.Flow

@Dao
interface SourcePlaylistDao {
    @Query("SELECT * FROM source_playlists WHERE profileId = :profileId ORDER BY updatedAt DESC")
    fun observeForProfile(profileId: String): Flow<List<SourcePlaylist>>

    @Query("SELECT * FROM source_playlists WHERE profileId = :profileId AND url = :url LIMIT 1")
    suspend fun findByUrl(profileId: String, url: String): SourcePlaylist?

    @Query("SELECT * FROM source_playlists WHERE profileId = :profileId ORDER BY updatedAt DESC")
    suspend fun getForProfile(profileId: String): List<SourcePlaylist>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: SourcePlaylist)

    @Update
    suspend fun update(playlist: SourcePlaylist)

    @Delete
    suspend fun delete(playlist: SourcePlaylist)

    @Query("DELETE FROM source_playlists WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE source_playlists SET itemCount = :count, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCount(id: String, count: Int, updatedAt: Long = System.currentTimeMillis())
}
