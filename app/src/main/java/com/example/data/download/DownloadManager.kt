package com.example.data.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.model.DownloadItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class VideoDownloadManager private constructor(private val context: Context) {

    private val database = AppDatabase.getInstance(context)
    private val downloadDao = database.downloadDao()

    private val _activeDownloads = MutableStateFlow<Map<String, Int>>(emptyMap()) // id to percent
    val activeDownloads: StateFlow<Map<String, Int>> = _activeDownloads.asStateFlow()

    companion object {
        const val MAX_CONCURRENT_DOWNLOADS = 3
        const val KEY_URL = "download_url"
        const val KEY_TITLE = "download_title"
        const val KEY_ID = "download_id"

        @Volatile
        private var INSTANCE: VideoDownloadManager? = null

        fun getInstance(context: Context): VideoDownloadManager {
            return INSTANCE ?: synchronized(this) {
                val instance = VideoDownloadManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    suspend fun enqueueDownload(mediaId: String, title: String, url: String) = withContext(Dispatchers.IO) {
        val downloadId = UUID.randomUUID().toString()
        val destFolder = File(context.getExternalFilesDir(null) ?: context.filesDir, "NSiO/Downloads/$title")
        if (!destFolder.exists()) destFolder.mkdirs()
        val localFile = File(destFolder, "$title.mp4")

        val item = DownloadItem(
            id = downloadId,
            mediaId = mediaId,
            title = title,
            url = url,
            localPath = localFile.absolutePath,
            status = "DOWNLOADING",
            progressPercent = 0
        )
        downloadDao.insertDownload(item)

        // Queue with WorkManager
        val inputData = Data.Builder()
            .putString(KEY_ID, downloadId)
            .putString(KEY_TITLE, title)
            .putString(KEY_URL, url)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<DownloadFileWorkManager>()
            .setInputData(inputData)
            .addTag("DOWNLOAD_$downloadId")
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }

    suspend fun pauseDownload(id: String) = withContext(Dispatchers.IO) {
        WorkManager.getInstance(context).cancelAllWorkByTag("DOWNLOAD_$id")
        val map = _activeDownloads.value.toMutableMap()
        map.remove(id)
        _activeDownloads.value = map
    }

    suspend fun cancelDownload(id: String) = withContext(Dispatchers.IO) {
        WorkManager.getInstance(context).cancelAllWorkByTag("DOWNLOAD_$id")
        downloadDao.deleteById(id)
        val map = _activeDownloads.value.toMutableMap()
        map.remove(id)
        _activeDownloads.value = map
    }

    fun updateProgress(id: String, percent: Int) {
        val map = _activeDownloads.value.toMutableMap()
        map[id] = percent
        _activeDownloads.value = map
    }
}

class DownloadFileWorkManager(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString(VideoDownloadManager.KEY_ID) ?: return@withContext Result.failure()
        val title = inputData.getString(VideoDownloadManager.KEY_TITLE) ?: "Video"
        val manager = VideoDownloadManager.getInstance(applicationContext)
        val db = AppDatabase.getInstance(applicationContext)

        try {
            // Simulate chunked streaming download loop with progress notification
            for (p in 10..100 step 15) {
                if (isStopped) {
                    return@withContext Result.retry()
                }
                delay(400) // Chunk download pacing
                manager.updateProgress(id, p)
            }

            // Mark complete
            val item = DownloadItem(
                id = id,
                mediaId = id,
                title = title,
                url = inputData.getString(VideoDownloadManager.KEY_URL) ?: "",
                localPath = File(applicationContext.filesDir, "NSiO/Downloads/$title/$title.mp4").absolutePath,
                status = "COMPLETED",
                progressPercent = 100
            )
            db.downloadDao().insertDownload(item)

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
