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

    suspend fun run(context: Context, database: AppDatabase): Int =
        syncMutex.withLock { runLocked(context, database) }

    private suspend fun runLocked(context: Context, database: AppDatabase): Int {
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

                val existing = database.mediaDao().getPlaylistItems(playlist.id).mapTo(hashSetOf()) { it.id }
                try {
                    for ((page, acknowledge) in pages) {
                        try {
                            for (media in page) {
                                val id = "telegram:${profile.id}:${media.chatId}:${media.messageId}"
                                if (id in existing) continue
                                try {
                                    val downloaded = kotlinx.coroutines.suspendCancellableCoroutine<String?> { continuation ->
                                        TelegramClientManager.downloadMedia(media) { filePath ->
                                            if (continuation.isActive) continuation.resumeWith(Result.success(filePath))
                                        }
                                    } ?: continue
                                    val source = File(downloaded)
                                    if (!source.isFile || source.length() <= 0L) continue
                                    val extension = source.extension.takeIf { it.isNotBlank() }
                                        ?: if (media.isPhoto) "jpg" else "mp4"
                                    val directory = File(context.filesDir, "telegram/imported").apply { mkdirs() }
                                    val target = File(directory, "$id.$extension")
                                    if (!target.exists() || target.length() != source.length()) {
                                        source.copyTo(target, overwrite = true)
                                    }
                                    val thumbnail = if (media.isPhoto) {
                                        Uri.fromFile(target).toString()
                                    } else {
                                        createVideoThumbnail(context, id, target)
                                    }
                                    database.mediaDao().insertMedia(
                                        MediaItem(
                                            id = id,
                                            profileId = profile.id,
                                            title = media.title.ifBlank {
                                                if (media.isPhoto) "Foto Telegram" else "Video Telegram"
                                            },
                                            url = Uri.fromFile(target).toString(),
                                            thumbnailUrl = thumbnail,
                                            type = if (media.isPhoto) "PHOTO" else "VIDEO",
                                            sourceType = "TELEGRAM",
                                            category = "Telegram",
                                            playlistId = playlist.id,
                                            sourceUrl = playlist.url,
                                            createdAt = media.createdAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                                            extraInfo = "chatId=${media.chatId}&messageId=${media.messageId}",
                                            caption = media.caption
                                        )
                                    )
                                    existing.add(id)
                                    addedTotal++
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    Log.w(TAG, "Gagal mengimpor pesan ${media.messageId} dari chat $chatId", error)
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

    private fun createVideoThumbnail(context: Context, mediaId: String, videoFile: File): String {
        val thumbnailDir = File(context.filesDir, "telegram/thumbnails").apply { mkdirs() }
        val thumbnailFile = File(thumbnailDir, "$mediaId.jpg")
        if (thumbnailFile.isFile && thumbnailFile.length() > 0L) return Uri.fromFile(thumbnailFile).toString()

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFile.absolutePath)
            val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return ""
            FileOutputStream(thumbnailFile).use { output ->
                if (!frame.compress(Bitmap.CompressFormat.JPEG, 82, output)) return ""
            }
            Uri.fromFile(thumbnailFile).toString()
        } catch (error: Exception) {
            Log.w(TAG, "Gagal membuat thumbnail untuk $mediaId", error)
            ""
        } finally {
            retriever.release()
        }
    }
}
