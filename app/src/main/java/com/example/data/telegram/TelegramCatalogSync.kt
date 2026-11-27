package com.example.data.telegram

import android.content.Context
import android.net.Uri
import com.example.data.local.AppDatabase
import com.example.data.model.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.coroutines.resume

/**
 * Background reconciliation for saved Telegram channel/group playlists.
 * Downloads media into app-private storage and keeps stable message IDs in Room.
 */
object TelegramCatalogSync {
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
                try {
                    val (history, _) = suspendCancellableCoroutine<Pair<List<TelegramClientManager.TelegramMedia>, String?>> { continuation ->
                        TelegramClientManager.loadAllChatMedia(chatId) { items, failure ->
                            if (continuation.isActive) continuation.resume(items to failure)
                        }
                    }
                    // Import every successfully read page even if an older page later fails.
                    val existing = database.mediaDao().getPlaylistItems(playlist.id).mapTo(hashSetOf()) { it.id }
                    for (media in history) {
                        val id = "telegram:${profile.id}:${media.chatId}:${media.messageId}"
                        if (id in existing) continue
                        try {
                            val downloaded = suspendCancellableCoroutine<String?> { continuation ->
                                TelegramClientManager.downloadMedia(media) { filePath ->
                                    if (continuation.isActive) continuation.resume(filePath)
                                }
                            } ?: continue
                            val source = File(downloaded)
                            if (!source.isFile || source.length() <= 0L) continue
                            val extension = source.extension.takeIf { it.isNotBlank() } ?: if (media.isPhoto) "jpg" else "mp4"
                            val directory = File(context.filesDir, "telegram/imported").apply { mkdirs() }
                            val target = File(directory, "$id.$extension")
                            if (!target.exists() || target.length() != source.length()) source.copyTo(target, overwrite = true)
                            database.mediaDao().insertMedia(
                                MediaItem(
                                    id = id,
                                    profileId = profile.id,
                                    title = media.title.ifBlank { if (media.isPhoto) "Foto Telegram" else "Video Telegram" },
                                    url = Uri.fromFile(target).toString(),
                                    thumbnailUrl = if (media.isPhoto) Uri.fromFile(target).toString() else "",
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
                        } catch (_: Exception) {
                            // Keep previously imported media and continue with the next message.
                        }
                    }
                    database.sourcePlaylistDao().updateCount(
                        playlist.id,
                        database.mediaDao().countForPlaylist(playlist.id)
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // One inaccessible channel must not block other saved Telegram sources.
                }
            }
        }
        return addedTotal
    }
}
