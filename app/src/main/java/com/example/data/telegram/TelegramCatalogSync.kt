package com.example.data.telegram

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream

/**
 * Background reconciliation for saved Telegram channel/group playlists.
 * History is streamed page-by-page so imported media and playlist counts become visible
 * while older messages are still being scanned.
 */
object TelegramCatalogSync {
    private const val TAG = "NSiO.TelegramSync"
    private val syncMutex = Mutex()

    suspend fun run(context: Context, database: AppDatabase, onlyPlaylistId: String? = null): Int =
        syncMutex.withLock { runLocked(context, database, onlyPlaylistId) }

    private suspend fun runLocked(context: Context, database: AppDatabase, onlyPlaylistId: String?): Int {
        TelegramClientManager.initialize(context.applicationContext)
        val ready = withTimeoutOrNull(20_000L) {
            TelegramClientManager.state.first {
                it.stage == TelegramClientManager.AuthStage.READY ||
                    it.stage == TelegramClientManager.AuthStage.ERROR ||
                    it.stage == TelegramClientManager.AuthStage.WAITING_PHONE ||
                    it.stage == TelegramClientManager.AuthStage.WAITING_CODE ||
                    it.stage == TelegramClientManager.AuthStage.WAITING_PASSWORD ||
                    it.stage == TelegramClientManager.AuthStage.NOT_CONFIGURED
            }.stage == TelegramClientManager.AuthStage.READY
        } ?: false
        if (!ready) return 0

        var addedTotal = 0
        val profiles = database.profileDao().getAllProfilesList()
        for (profile in profiles) {
            val playlists = database.sourcePlaylistDao().getForProfile(profile.id)
                .filter { it.sourceType.startsWith("TELEGRAM", ignoreCase = true) }
                .filter { onlyPlaylistId == null || it.id == onlyPlaylistId }
            for (playlist in playlists) {
                val chatId = playlist.url.removePrefix("telegram://chat/").toLongOrNull() ?: continue
                val pages = Channel<Pair<List<TelegramClientManager.TelegramMedia>, () -> Unit>>(capacity = 1)
                val scanFinished = CompletableDeferred<String?>()
                TelegramClientManager.loadAllChatMedia(
                    chatId,
                    { page, acknowledge ->
                        if (pages.trySend(page to acknowledge).isFailure) acknowledge()
                    },
                    { failure ->
                        scanFinished.complete(failure)
                        pages.close()
                    }
                )

                val existing = database.mediaDao().getPlaylistItems(playlist.id).associateBy { it.id }.toMutableMap()
                try {
                    for ((page, acknowledge) in pages) {
                        try {
                            for (media in page) {
                                val id = "telegram:${profile.id}:${media.chatId}:${media.messageId}"
                                val previous = existing[id]
                                try {
                                    if (previous != null) continue
                                    // Persist only Telegram metadata. Full media is fetched on first playback.
                                    val extension = if (media.isPhoto) "jpg" else when {
                                        media.mimeType.contains("webm", true) -> "webm"
                                        media.mimeType.contains("matroska", true) -> "mkv"
                                        else -> "mp4"
                                    }
                                    val deferredUrl = "telegram://media/${media.chatId}/${media.messageId}/${media.fileId}"
                                    val imported = MediaItem(
                                        id = id,
                                        profileId = profile.id,
                                        title = media.caption.takeIf { it.isNotBlank() }?.lineSequence()?.firstOrNull()?.take(120)
                                            ?: media.title.ifBlank { if (media.isPhoto) "Foto Telegram" else "Video Telegram" },
                                        url = deferredUrl,
                                        thumbnailUrl = "",
                                        type = if (media.isPhoto) "PHOTO" else "VIDEO",
                                        sourceType = "TELEGRAM",
                                        category = "Telegram",
                                        playlistId = playlist.id,
                                        sourceUrl = playlist.url,
                                        createdAt = media.createdAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                                        extraInfo = "chatId=${media.chatId}&messageId=${media.messageId}&fileId=${media.fileId}&mimeType=${media.mimeType}",
                                        caption = media.caption
                                    )
                                    database.mediaDao().insertMedia(imported)
                                    existing[id] = imported
                                    addedTotal++
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    Log.w(TAG, "Gagal mengimpor/memperbaiki pesan ${media.messageId} dari chat $chatId", error)
                                }
                            }
                            // Publish the current count after each page, not after the full history.
                            database.sourcePlaylistDao().updateCount(
                                playlist.id,
                                database.mediaDao().countForPlaylist(playlist.id)
                            )
                        } finally {
                            acknowledge()
                        }
                    }
                    scanFinished.await()?.let { Log.w(TAG, "Riwayat chat $chatId tidak selesai: $it") }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(TAG, "Sinkronisasi playlist ${playlist.name} gagal", error)
                }
            }
        }
        return addedTotal
    }

    private suspend fun downloadTelegramMedia(media: TelegramClientManager.TelegramMedia): String? =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            TelegramClientManager.downloadMedia(media) { path ->
                if (continuation.isActive) continuation.resumeWith(Result.success(path))
            }
        }

    private fun isThumbnailUsable(raw: String): Boolean {
        if (raw.isBlank()) return false
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return false
        if (uri.scheme != "file") return true
        val file = uri.path?.let(::File) ?: return false
        return file.isFile && file.length() > 0L &&
            runCatching { android.graphics.BitmapFactory.decodeFile(file.absolutePath) != null }.getOrDefault(false)
    }

    private suspend fun repairVideoThumbnail(
        context: Context,
        mediaId: String,
        existing: MediaItem,
        media: TelegramClientManager.TelegramMedia
    ): String? {
        val uri = runCatching { Uri.parse(existing.url) }.getOrNull()
        var videoFile = if (uri?.scheme == "file") uri.path?.let(::File) else null
        if (videoFile == null || !videoFile.isFile || videoFile.length() <= 0L) {
            val downloaded = downloadTelegramMedia(media) ?: return null
            val source = File(downloaded)
            if (!source.isFile || source.length() <= 0L) return null
            val extension = source.extension.takeIf { it.isNotBlank() } ?: "mp4"
            val target = File(context.filesDir, "telegram/imported/$mediaId.$extension")
            target.parentFile?.mkdirs()
            if (!target.exists() || target.length() != source.length()) source.copyTo(target, overwrite = true)
            videoFile = target
        }
        val resolvedVideoFile = videoFile ?: return null
        return createVideoThumbnail(context, mediaId, resolvedVideoFile)
            .takeIf { it.isNotBlank() && isThumbnailUsable(it) }
    }

    private fun createVideoThumbnail(context: Context, mediaId: String, videoFile: File): String {
        val thumbnailDir = File(context.filesDir, "telegram/thumbnails").apply { mkdirs() }
        val thumbnailFile = File(thumbnailDir, "$mediaId.jpg")
        if (thumbnailFile.isFile && thumbnailFile.length() > 0L &&
            runCatching { android.graphics.BitmapFactory.decodeFile(thumbnailFile.absolutePath) != null }.getOrDefault(false)
        ) return Uri.fromFile(thumbnailFile).toString()
        if (thumbnailFile.exists()) thumbnailFile.delete()

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFile.absolutePath)
            val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return ""
            FileOutputStream(thumbnailFile).use { output ->
                if (!frame.compress(Bitmap.CompressFormat.JPEG, 82, output)) {
                    thumbnailFile.delete()
                    return ""
                }
            }
            Uri.fromFile(thumbnailFile).toString()
        } catch (error: Exception) {
            thumbnailFile.delete()
            Log.w(TAG, "Gagal membuat ulang thumbnail untuk $mediaId", error)
            ""
        } finally {
            retriever.release()
        }
    }
}
