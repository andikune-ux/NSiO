package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.DownloadItem
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(item: DownloadItem)

    @Update
    suspend fun updateDownload(item: DownloadItem)

    @Query("UPDATE downloads SET status = :status, progressPercent = :progressPercent, downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, localPath = :localPath WHERE id = :id")
    suspend fun updateProgress(
        id: String,
        status: String,
        progressPercent: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        localPath: String
    )

    @Delete
    suspend fun deleteDownload(item: DownloadItem)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: String)
}
