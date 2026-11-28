package com.example.data.cache

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.model.MediaItem
import com.example.player.DriveMediaCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Clears disposable media only. Room, PINs, credentials, TDLib database,
 * profiles, playlists, watch history, plugin installations and user downloads survive.
 */
object AppCacheCleaner {
    suspend fun clear(context: Context): Boolean = withContext(Dispatchers.IO) {
        val app = context.applicationContext

        // Refuse the whole operation while Media3 has an active reader.
        if (!DriveMediaCache.clear(app)) return@withContext false

        val database = AppDatabase.getInstance(app)
        val importedDir = File(app.filesDir, "telegram/imported")
        val before = database.mediaDao().getAllMediaList()

        // Convert known legacy Telegram file references to stable TDLib stream URLs
        // before deleting their old local copies. Rows without a valid Telegram identity
        // retain their source file and therefore never become broken references.
        database.withTransaction {
            before.forEach { item ->
                val oldVideo = fileInside(item.url, importedDir)
                val streamUrl = if (oldVideo != null) telegramStreamUrl(item) else null
                val thumbnail = if (isTelegramCacheUri(item.thumbnailUrl, app)) "" else item.thumbnailUrl
                if ((streamUrl != null && streamUrl != item.url) || thumbnail != item.thumbnailUrl) {
                    database.mediaDao().updateMedia(
                        item.copy(url = streamUrl ?: item.url, thumbnailUrl = thumbnail)
                    )
                }
            }
            database.mediaDao().clearTelegramCachedThumbnails()
            database.sourcePlaylistDao().clearTelegramCachedThumbnails()
        }

        val after = database.mediaDao().getAllMediaList()
        val protectedPaths = after.mapNotNull { fileInside(it.url, importedDir)?.canonicalPath }.toSet()
        val importedCleared = deleteUnreferencedFiles(importedDir, protectedPaths)

        // These are disposable copies. The persistent TDLib database is deliberately
        // not included; only its old downloaded-files directory is removed.
        val legacyDirectories = listOf(
            File(app.filesDir, "telegram/thumbnails"),
            File(app.filesDir, "telegram/profiles"),
            File(app.filesDir, "telegram/tdlib/files"),
            File(app.cacheDir, "telegram_media_cache"),
            File(app.cacheDir, "telegram/thumbnails"),
            File(app.cacheDir, "telegram/profiles")
        )
        val legacyCachesCleared = legacyDirectories.all(::deleteDirectory)

        // The new TDLib files directory is under cacheDir. TDLib performs its own
        // deletion so its live file database is not raced by recursive filesystem removal.
        val tdlibCacheCleared =
            com.example.data.telegram.TelegramClientManager.clearDisposableStorage(app)

        importedCleared && legacyCachesCleared && tdlibCacheCleared
    }

    private fun telegramStreamUrl(item: MediaItem): String? {
        if (!item.sourceType.equals("TELEGRAM", true)) return null
        val values = item.extraInfo.split('&').mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) null else part.substring(0, separator) to part.substring(separator + 1)
        }.toMap()
        val chatId = values["chatId"]?.toLongOrNull() ?: return null
        val messageId = values["messageId"]?.toLongOrNull() ?: return null
        val fileId = values["fileId"]?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val extension = when (values["mimeType"]?.lowercase()) {
            "video/webm" -> "webm"
            "video/x-matroska" -> "mkv"
            "video/quicktime" -> "mov"
            "video/x-msvideo" -> "avi"
            else -> "mp4"
        }
        return "telegram://media/" + chatId + "/" + messageId + "/" + fileId + "." + extension
    }

    private fun pathInside(rawUri: String, directory: File): File? {
        if (rawUri.isBlank()) return null
        val candidate = runCatching {
            val uri = Uri.parse(rawUri)
            if (uri.scheme != "file") null else uri.path?.let(::File)?.canonicalFile
        }.getOrNull() ?: return null
        val root = runCatching { directory.canonicalFile }.getOrNull() ?: return null
        return candidate.takeIf { it.path.startsWith(root.path + File.separator) }
    }

    private fun fileInside(rawUri: String, directory: File): File? =
        pathInside(rawUri, directory)?.takeIf { it.isFile }

    private fun isTelegramCacheUri(rawUri: String, context: Context): Boolean {
        val directories = listOf(
            File(context.filesDir, "telegram/thumbnails"),
            File(context.filesDir, "telegram/profiles"),
            File(context.cacheDir, "telegram/thumbnails"),
            File(context.cacheDir, "telegram/profiles")
        )
        return directories.any { pathInside(rawUri, it) != null }
    }

    private fun deleteUnreferencedFiles(directory: File, protectedPaths: Set<String>): Boolean {
        if (!directory.exists()) return true
        var success = true
        directory.walkBottomUp().forEach { file ->
            if (file.isFile) {
                val canonical = runCatching { file.canonicalPath }.getOrNull()
                if (canonical != null && canonical !in protectedPaths && !file.delete()) success = false
            } else if (file.isDirectory && file != directory && file.list()?.isEmpty() == true) {
                if (!file.delete()) success = false
            }
        }
        if (directory.exists() && directory.list()?.isEmpty() == true && !directory.delete()) success = false
        return success
    }

    private fun deleteDirectory(directory: File): Boolean {
        if (!directory.exists()) return true
        return runCatching { directory.deleteRecursively() }.getOrDefault(false)
    }
}
