package com.example.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.extractor.LinkExtractor
import com.example.data.extractor.ParseResult
import com.example.data.local.AppDatabase
import com.example.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Periodically refreshes saved HTTP catalogs. A failed source never clears its cached items.
 * Each source is isolated so one broken URL cannot stop the remaining refreshes.
 */
class CatalogRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = AppDatabase.getInstance(applicationContext)
        val playlists = runCatching {
            database.profileDao().getAllProfilesList().flatMap { profile ->
                database.sourcePlaylistDao().getForProfile(profile.id)
            }
        }.getOrElse { return@withContext Result.retry() }

        var failures = 0
        var refreshed = 0
        playlists
            .filter { it.sourceType in REFRESHABLE_SOURCE_TYPES && it.url.startsWith("http", ignoreCase = true) }
            .forEach { playlist ->
                if (isStopped) return@withContext Result.retry()
                try {
                    when (val parsed = LinkExtractor.parseUrl(playlist.url, playlist.profileId)) {
                        is ParseResult.Single -> {
                            val sourceType = when {
                                parsed.item.type == "STREAM" -> "STREAM"
                                playlist.url.substringBefore('?').endsWith(".json", ignoreCase = true) -> "REPO"
                                else -> "LINK"
                            }
                            val item = parsed.item.copy(
                                sourceType = sourceType,
                                playlistId = playlist.id,
                                sourceUrl = playlist.url
                            )
                            database.mediaDao().replacePlaylist(playlist.id, listOf(item))
                            database.sourcePlaylistDao().updateCount(playlist.id, 1)
                            refreshed++
                        }
                        is ParseResult.Multiple -> {
                            if (parsed.items.isEmpty()) {
                                failures++
                            } else {
                                val sourceType = if (parsed.title.startsWith("M3U", ignoreCase = true)) "STREAM" else "REPO"
                                val items = parsed.items.map { item ->
                                    item.copy(
                                        sourceType = sourceType,
                                        playlistId = playlist.id,
                                        sourceUrl = playlist.url
                                    )
                                }
                                database.mediaDao().replacePlaylist(playlist.id, items)
                                database.sourcePlaylistDao().updateCount(playlist.id, items.size)
                                refreshed++
                            }
                        }
                        is ParseResult.Error -> failures++
                    }
                } catch (_: Exception) {
                    // Preserve the last successful catalog and continue with other sources.
                    failures++
                }
            }

        if (failures > 0 && refreshed == 0) Result.retry() else Result.success()
    }

    companion object {
        private val REFRESHABLE_SOURCE_TYPES = setOf("LINK", "STREAM", "REPO")
    }
}
