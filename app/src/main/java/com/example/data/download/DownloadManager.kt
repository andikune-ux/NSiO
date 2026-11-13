package com.example.data.download

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.Data
import androidx.work.NetworkType
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
import java.io.FileOutputStream
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
        const val KEY_HEADERS = "download_headers"
        @Volatile private var INSTANCE: VideoDownloadManager? = null

        fun getInstance(context: Context): VideoDownloadManager = INSTANCE ?: synchronized(this) {
            INSTANCE ?: VideoDownloadManager(context.applicationContext).also { INSTANCE = it }
        }
    }

    suspend fun enqueueDownload(
        mediaId: String,
        title: String,
        url: String,
        requestHeaders: Map<String, String> = emptyMap()
    ) = withContext(Dispatchers.IO) {
        require(url.startsWith("http://") || url.startsWith("https://")) { "URL unduhan tidak valid" }
        val parsedUri = Uri.parse(url)
        val urlExtension = parsedUri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
        require(urlExtension != "m3u8" && urlExtension != "m3u") {
            "Unduhan HLS belum didukung. Tautan ini adalah playlist streaming, bukan file video langsung."
        }

        // Reuse an unfinished row for the same media URL instead of creating duplicate files/jobs.
        val existing = downloadDao.getResumableDownloadForMedia(mediaId, url)
        val item = if (existing != null) {
            val partial = File("${existing.localPath}.part")
            val bytes = if (partial.exists()) partial.length() else 0L
            if (existing.status != "DOWNLOADING") {
                downloadDao.updateProgress(
                    existing.id, "PENDING", existing.progressPercent.coerceIn(0, 99),
                    bytes, existing.totalBytes, existing.localPath
                )
            }
            existing
        } else {
            val safeTitle = title.trim().replace(Regex("[^a-zA-Z0-9._-]+"), "_").take(80).ifEmpty { "video" }
            val folder = File(context.getExternalFilesDir(null) ?: context.filesDir, "NSiO/Downloads")
            if (!folder.exists() && !folder.mkdirs()) throw IOException("Folder unduhan tidak dapat dibuat")
            val extension = urlExtension.takeIf { it.matches(Regex("[a-z0-9]{1,8}")) } ?: "mp4"
            val localFile = File(folder, "$safeTitle-${UUID.randomUUID()}.$extension")
            DownloadItem(UUID.randomUUID().toString(), mediaId, title, url, localFile.absolutePath, "PENDING")
                .also { downloadDao.insertDownload(it) }
        }

        val input = Data.Builder()
            .putString(KEY_ID, item.id).putString(KEY_MEDIA_ID, mediaId)
            .putString(KEY_TITLE, title).putString(KEY_URL, url)
            .putString(KEY_PATH, item.localPath)
            .putString(KEY_HEADERS, JSONObject(requestHeaders).toString()).build()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<DownloadFileWorkManager>()
            .setInputData(input)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("DOWNLOAD_${item.id}")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "NSIO_DOWNLOAD_${item.id}",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    suspend fun pauseDownload(id: String) = withContext(Dispatchers.IO) {
        val item = downloadDao.getDownloadById(id)
        WorkManager.getInstance(context).cancelAllWorkByTag("DOWNLOAD_$id")
        // Keep partial bytes so a later worker attempt can resume.
        downloadDao.updateProgress(id, "PAUSED", 0, item?.localPath?.let { File("$it.part").length() } ?: 0L, 0, item?.localPath.orEmpty())
        _activeDownloads.value = _activeDownloads.value - id
    }

    suspend fun cancelDownload(id: String) = withContext(Dispatchers.IO) {
        val item = downloadDao.getDownloadById(id)
        WorkManager.getInstance(context).cancelAllWorkByTag("DOWNLOAD_$id")
        item?.localPath?.takeIf { it.isNotBlank() }?.let { path ->
            File("$path.part").delete()
            if (item.status != "COMPLETED") File(path).delete()
        }
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
            val requestBuilder = Request.Builder().url(url).header("User-Agent", "NSiO/2.0")
            val headersJson = runCatching { JSONObject(inputData.getString(VideoDownloadManager.KEY_HEADERS) ?: "{}") }.getOrDefault(JSONObject())
            headersJson.keys().forEach { key ->
                val value = headersJson.optString(key)
                if (key.isNotBlank() && value.isNotBlank() && !key.equals("Host", true) &&
                    !key.equals("Content-Length", true) && !key.equals("Connection", true) &&
                    !key.equals("Range", true) && !key.equals("If-Range", true)) {
                    requestBuilder.header(key, value)
                }
            }
            val downloadItem = db.getDownloadById(id)
            var existingBytes = if (partial.exists()) partial.length() else 0L
            val savedEtag = downloadItem?.etag.orEmpty()
            val savedLastModified = downloadItem?.lastModified.orEmpty()

            // A partial file without a validator cannot safely be joined to a later response.
            if (existingBytes > 0L && savedEtag.isBlank() && savedLastModified.isBlank()) {
                partial.delete()
                existingBytes = 0L
            }
            if (existingBytes > 0L) {
                requestBuilder.header("Range", "bytes=$existingBytes-")
                requestBuilder.header("If-Range", savedEtag.ifBlank { savedLastModified })
            }
            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                val responseCode = response.code
                response.close()
                throw DownloadHttpException(responseCode)
            }
            val contentType = response.header("Content-Type").orEmpty().substringBefore(';').trim().lowercase()
            val finalPath = response.request.url.encodedPath.lowercase()
            if (contentType.contains("mpegurl") || contentType.contains("vnd.apple.mpegurl") ||
                finalPath.endsWith(".m3u8") || finalPath.endsWith(".m3u")) {
                response.close()
                throw DownloadPermanentException("Server mengirim playlist HLS, bukan file video langsung. Unduhan HLS belum didukung.")
            }
            val append = existingBytes > 0L && response.code == 206
            if (existingBytes == 0L && response.code == 206) {
                response.close()
                throw DownloadPermanentException("Server mengirim potongan file tanpa permintaan resume; unduhan dibatalkan agar file tidak terpotong.")
            }
            if (existingBytes > 0L && response.code != 206 && response.code != 200) {
                response.close()
                throw DownloadPermanentException("Server tidak mendukung kelanjutan unduhan")
            }
            var rangeTotalBytes: Long? = null
            var rangeExpectedBytes: Long? = null
            if (append) {
                val contentRange = response.header("Content-Range").orEmpty()
                val rangeMatch = Regex("^bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)$", RegexOption.IGNORE_CASE)
                    .matchEntire(contentRange)
                val rangeStart = rangeMatch?.groupValues?.get(1)?.toLongOrNull()
                val rangeEnd = rangeMatch?.groupValues?.get(2)?.toLongOrNull()
                rangeTotalBytes = rangeMatch?.groupValues?.get(3)?.takeUnless { it == "*" }?.toLongOrNull()
                if (rangeStart != existingBytes || rangeEnd == null || rangeEnd < existingBytes ||
                    rangeTotalBytes == null || rangeEnd >= rangeTotalBytes) {
                    response.close()
                    throw DownloadPermanentException("Server mengirim rentang byte yang tidak sesuai; unduhan dihentikan agar file tidak rusak.")
                }
                rangeExpectedBytes = rangeEnd - existingBytes + 1L

                val responseEtag = response.header("ETag").orEmpty()
                val responseLastModified = response.header("Last-Modified").orEmpty()
                val validatorMatches = when {
                    savedEtag.isNotBlank() -> responseEtag.isNotBlank() && savedEtag == responseEtag
                    savedLastModified.isNotBlank() -> responseLastModified.isNotBlank() && savedLastModified == responseLastModified
                    else -> false
                }
                if (!validatorMatches) {
                    response.close()
                    partial.delete()
                    db.updateValidators(id, "", "")
                    throw IOException("Versi file di server berubah atau tidak dapat diverifikasi. Unduhan akan dimulai ulang agar file tidak tercampur.")
                }
            }

            val responseEtag = response.header("ETag").orEmpty()
            val responseLastModified = response.header("Last-Modified").orEmpty()
            db.updateValidators(id, responseEtag, responseLastModified)
            val body = response.body ?: run {
                response.close()
                throw IOException("Respons unduhan kosong")
            }
            val bodyLength = body.contentLength()
            if (append && bodyLength > 0L && rangeExpectedBytes != bodyLength) {
                response.close()
                throw DownloadPermanentException("Ukuran respons tidak cocok dengan Content-Range; unduhan dihentikan agar file tidak rusak.")
            }
            val total = when {
                append && rangeTotalBytes != null -> rangeTotalBytes!!
                append && bodyLength > 0L -> existingBytes + bodyLength
                else -> bodyLength
            }
            var downloaded = if (append) existingBytes else 0L
            db.updateProgress(id, "DOWNLOADING", if (total > 0L) ((downloaded * 100) / total).toInt() else 0, downloaded, total, target.absolutePath)
            body.byteStream().use { input ->
                (if (append) FileOutputStream(partial, true) else partial.outputStream()).buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read: Int
                    while (input.read(buffer).also { read = it } >= 0) {
                        if (isStopped) {
                            // Keep partial bytes when WorkManager stops this worker so it can resume later.
                            manager.updateProgress(id, 0)
                            db.updateProgress(id, "PAUSED", 0, partial.length(), 0, target.absolutePath)
                            return@withContext Result.retry()
                        }
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        downloaded += read
                        val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                        manager.updateProgress(id, percent)
                        db.updateProgress(id, "DOWNLOADING", percent, downloaded, total, target.absolutePath)
                    }
                }
            }
            if (total > 0L && downloaded != total) {
                throw IOException("Unduhan tidak lengkap: ${downloaded} dari ${total} byte diterima")
            }
            if (!partial.renameTo(target)) throw IOException("Gagal menyimpan file hasil unduhan")
            db.updateProgress(id, "COMPLETED", 100, downloaded, total, target.absolutePath)
            manager.updateProgress(id, 100)
            Result.success()
        } catch (e: Exception) {
            manager.updateProgress(id, 0)
            if (isStopped) {
                db.updateProgress(id, "PAUSED", 0, partial.length(), 0, target.absolutePath)
                return@withContext Result.retry()
            }
            val retryable = when (e) {
                is DownloadPermanentException -> false
                is DownloadHttpException -> e.statusCode == 408 || e.statusCode == 429 || e.statusCode in 500..599
                is IOException -> true // transient network/read failure
                else -> false
            }
            if (retryable) {
                db.updateProgress(id, "PAUSED", 0, partial.length(), 0, target.absolutePath)
                Result.retry()
            } else {
                partial.delete()
                db.updateProgress(id, "FAILED", 0, 0, 0, target.absolutePath)
                Result.failure(Data.Builder().putString("error", e.message ?: "Unduhan gagal").build())
            }
        }
    }
}


private class DownloadHttpException(val statusCode: Int) :
    IOException("Server mengembalikan HTTP $statusCode")

private class DownloadPermanentException(message: String) : Exception(message)
