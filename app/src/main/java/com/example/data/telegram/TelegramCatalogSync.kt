package com.example.data.telegram

import android.content.Context
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
                                    if (previous != null) {
                                        var current = previous
                                        // Migrate legacy full-file Telegram entries to progressive playback.
                                        // Only files inside telegram/imported are eligible; manual downloads
                                        // and unrelated file:// media are never touched.
                                        if (!media.isPhoto && isLegacyImportedVideo(context, previous.url)) {
                                            val extension = when {
                                                media.mimeType.contains("webm", true) -> "webm"
                                                media.mimeType.contains("matroska", true) -> "mkv"
                                                else -> "mp4"
                                            }
                                            val streamUrl = "telegram://media/${media.chatId}/${media.messageId}/${media.fileId}.$extension"
                                            val migrated = previous.copy(
                                                url = streamUrl,
                                                extraInfo = "chatId=${media.chatId}&messageId=${media.messageId}&fileId=${media.fileId}&mimeType=${media.mimeType}"
                                            )
                                            // Commit the new stream reference before touching the legacy file.
                                            database.mediaDao().updateMedia(migrated)
                                            current = migrated
                                            // Keep the legacy file as a rollback copy. A cloud metadata/range
                                            // probe is not proof that Media3 successfully read and committed
                                            // bytes to SimpleCache. Deletion is intentionally deferred until a
                                            // playback path can verify an actual cached span.
                                            Log.i(TAG, "Referensi video Telegram dimigrasikan ke streaming; file lama dipertahankan sampai cache terverifikasi: $id")
                                        }
                                        if (current.thumbnailUrl.isBlank() && media.thumbnailFileId > 0) {
                                            val thumbnail = downloadTelegramThumbnail(context, id, media.thumbnailFileId)
                                            if (!thumbnail.isNullOrBlank()) {
                                                database.mediaDao().updateThumbnailUrl(id, thumbnail)
                                                current = current.copy(thumbnailUrl = thumbnail)
                                            }
                                        }
                                        existing[id] = current
                                        continue
                                    }
                                    // Persist only Telegram metadata. Full media is fetched on first playback.
                                    val extension = if (media.isPhoto) "jpg" else if (media.mimeType.contains("webm", true)) "webm" else if (media.mimeType.contains("matroska", true)) "mkv" else "mp4"
                                    val deferredUrl = "telegram://media/${media.chatId}/${media.messageId}/${media.fileId}.$extension"
                                    val thumbnail = if (media.thumbnailFileId > 0)
                                        downloadTelegramThumbnail(context, id, media.thumbnailFileId).orEmpty() else ""
                                    val imported = MediaItem(
                                        id = id,
                                        profileId = profile.id,
                                        title = media.caption.takeIf { it.isNotBlank() }?.lineSequence()?.firstOrNull()?.take(120)
                                            ?: media.title.ifBlank { if (media.isPhoto) "Foto Telegram" else "Video Telegram" },
                                        url = if (media.isPhoto) thumbnail else deferredUrl,
                                        thumbnailUrl = thumbnail,
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


    private fun legacyImportedFile(context: Context, rawUrl: String): File? {
        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return null
        if (uri.scheme != "file") return null
        val file = uri.path?.let(::File) ?: return null
        val importedDir = File(context.filesDir, "telegram/imported")
        val canonicalFile = runCatching { file.canonicalFile }.getOrNull() ?: return null
        val canonicalDir = runCatching { importedDir.canonicalFile }.getOrNull() ?: return null
        return canonicalFile.takeIf {
            it.isFile && it.parentFile == canonicalDir && it.length() > 0L
        }
    }

    private fun isLegacyImportedVideo(context: Context, rawUrl: String): Boolean =
        legacyImportedFile(context, rawUrl) != null

    private suspend fun downloadTelegramThumbnail(
        context: Context,
        mediaId: String,
        fileId: Int
    ): String? {
        val downloaded = kotlinx.coroutines.suspendCancellableCoroutine<String?> { continuation ->
            TelegramClientManager.downloadThumbnail(fileId) { path ->
                if (continuation.isActive) continuation.resumeWith(Result.success(path))
            }
        } ?: return null
        val source = File(downloaded)
        if (!source.isFile || source.length() <= 0L) return null
        val directory = File(context.filesDir, "telegram/thumbnails").apply { mkdirs() }
        val target = File(directory, "$mediaId.jpg")
        return runCatching {
            if (!target.isFile || target.length() != source.length()) source.copyTo(target, overwrite = true)
            TelegramClientManager.deleteDownloadedFile(fileId)
            Uri.fromFile(target).toString()
        }.getOrNull()
    }

}
