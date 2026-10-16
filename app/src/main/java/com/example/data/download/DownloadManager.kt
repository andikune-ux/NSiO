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
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

class VideoDownloadManager private constructor(private val context: Context) {
    private val database = AppDatabase.getInstance(context)
    private val downloadDao = database.downloadDao()
    private val _activeDownloads = kotlinx.coroutines.flow.MutableStateFlow<Map<String, Int>>(emptyMap())
    val activeDownloads: kotlinx.coroutines.flow.StateFlow<Map<String, Int>> = _activeDownloads

    companion object {
        const val KEY_URL = "download_url"
        const val KEY_TITLE = "download_title"
        const val KEY_ID = "download_id"
        const val KEY_MEDIA_ID = "download_media_id"
        const val KEY_PATH = "download_path"
        @Volatile private var INSTANCE: VideoDownloadManager? = null

        fun getInstance(context: Context): VideoDownloadManager = INSTANCE ?: synchronized(this) {
            INSTANCE ?: VideoDownloadManager(context.applicationContext).also { INSTANCE = it }
        }
    }

    suspend fun enqueueDownload(mediaId: String, title: String, url: String) = withContext(Dispatchers.IO) {
        require(url.startsWith("http://") || url.startsWith("https://")) { "URL unduhan tidak valid" }
        val safeTitle = title.trim().replace(Regex("[^a-zA-Z0-9._-]+"), "_").take(80).ifEmpty { "video" }
        val folder = File(context.getExternalFilesDir(null) ?: context.filesDir, "NSiO/Downloads")
        folder.mkdirs()
        val localFile = File(folder, "$safeTitle-${UUID.randomUUID()}.mp4")
        val id = UUID.randomUUID().toString()
        downloadDao.insertDownload(DownloadItem(id, mediaId, title, url, localFile.absolutePath, "PENDING"))
        val input = Data.Builder()
            .putString(KEY_ID, id).putString(KEY_MEDIA_ID, mediaId)
            .putString(KEY_TITLE, title).putString(KEY_URL, url)
            .putString(KEY_PATH, localFile.absolutePath).build()
        val request = OneTimeWorkRequestBuilder<DownloadFileWorkManager>().setInputData(input).addTag("DOWNLOAD_$id").build()
        WorkManager.getInstance(context).enqueue(request)
    }

    suspend fun pauseDownload(id: String) = withContext(Dispatchers.IO) {
        WorkManager.getInstance(context).cancelAllWorkByTag("DOWNLOAD_$id")
        downloadDao.updateProgress(id, "PAUSED", 0, 0, 0, "")
        _activeDownloads.value = _activeDownloads.value - id
    }

    suspend fun cancelDownload(id: String) = withContext(Dispatchers.IO) {
        WorkManager.getInstance(context).cancelAllWorkByTag("DOWNLOAD_$id")
        downloadDao.deleteById(id)
        _activeDownloads.value = _activeDownloads.value - id
    }

    fun updateProgress(id: String, percent: Int) {
        _activeDownloads.value = _activeDownloads.value + (id to percent.coerceIn(0, 100))
    }
}

class DownloadFileWorkManager(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString(VideoDownloadManager.KEY_ID) ?: return@withContext Result.failure()
        val mediaId = inputData.getString(VideoDownloadManager.KEY_MEDIA_ID) ?: ""
        val title = inputData.getString(VideoDownloadManager.KEY_TITLE) ?: "Video"
        val url = inputData.getString(VideoDownloadManager.KEY_URL) ?: return@withContext Result.failure()
        val path = inputData.getString(VideoDownloadManager.KEY_PATH) ?: return@withContext Result.failure()
        val db = AppDatabase.getInstance(applicationContext).downloadDao()
        val manager = VideoDownloadManager.getInstance(applicationContext)
        val target = File(path)
        val partial = File("$path.part")
        try {
            val response = client.newCall(Request.Builder().url(url).header("User-Agent", "NSiO/2.0").build()).execute()
            if (!response.isSuccessful) throw IOException("Server mengembalikan HTTP ${response.code}")
            val body = response.body ?: throw IOException("Respons unduhan kosong")
            val total = body.contentLength()
            db.updateProgress(id, "DOWNLOADING", 0, 0, total, target.absolutePath)
            var downloaded = 0L
            body.byteStream().use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read: Int
                    while (input.read(buffer).also { read = it } >= 0) {
                        if (isStopped) return@withContext Result.retry()
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        downloaded += read
                        val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                        manager.updateProgress(id, percent)
                        db.updateProgress(id, "DOWNLOADING", percent, downloaded, total, target.absolutePath)
                    }
                }
            }
            if (!partial.renameTo(target)) throw IOException("Gagal menyimpan file hasil unduhan")
            db.updateProgress(id, "COMPLETED", 100, downloaded, total, target.absolutePath)
            manager.updateProgress(id, 100)
            Result.success()
        } catch (e: Exception) {
            if (isStopped) return@withContext Result.retry()
            db.updateProgress(id, "FAILED", 0, 0, 0, target.absolutePath)
            Result.failure(Data.Builder().putString("error", e.message ?: "Unduhan gagal").build())
        }
    }
}
