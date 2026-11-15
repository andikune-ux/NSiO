package com.example.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.extractor.LinkExtractor
import com.example.data.extractor.ParseResult
import com.example.data.local.AppDatabase
import com.example.data.model.MediaItem
import com.example.data.model.MediaRefreshPolicy
import com.example.data.plugin.PluginManager
import kotlinx.coroutines.CancellationException
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
        val playlists = try {
            database.profileDao().getAllProfilesList().flatMap { profile ->
                database.sourcePlaylistDao().getForProfile(profile.id)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return@withContext Result.retry()
        }

        var failures = 0
        var refreshed = 0
        playlists
            .filter {
                it.sourceType in REFRESHABLE_SOURCE_TYPES &&
                    (it.url.startsWith("https://", ignoreCase = true) ||
                        it.url.startsWith("http://", ignoreCase = true))
            }
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
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Preserve the last successful catalog and continue with other sources.
                    failures++
                }
            }

        // Refresh CloudStream main catalogs for every local profile as part of the same
        // six-hour connected-network job. Plugin failures never erase their cached rows.
        try {
            val pluginManager = PluginManager(applicationContext)
            // Keep repository metadata fresh before importing provider catalogs.
            // Individual failures preserve the last-known-good repository snapshot.
            val (repositoriesRefreshed, repositoryFailures) = pluginManager.refreshAllRepositoryCatalogs()
            val (pluginsUpdated, pluginUpdateFailures) = pluginManager.autoUpdateInstalledCloudStreamPlugins()
            refreshed += pluginsUpdated
            failures += repositoryFailures + pluginUpdateFailures
            val entries = pluginManager.repositories.value
                .flatMap { it.plugins }
                .filter { it.status != 0 && pluginManager.isCloudStreamPluginInstalled(it) }
                .distinctBy { pluginManager.cloudStreamPluginId(it) }
            val profiles = database.profileDao().getAllProfilesList()
            profiles.forEach { profile ->
                entries.forEach entryLoop@ { entry ->
                    if (isStopped) return@withContext Result.retry()
                    try {
                        val sections = pluginManager.loadCloudStreamHomePage(entry, 1)
                        val pluginId = pluginManager.cloudStreamPluginId(entry)
                        val repositoryId = pluginManager.cloudStreamRepositoryId(entry)
                        val fetched = sections.flatMap { section ->
                            section.items.map { item ->
                                MediaItem(
                                    id = MediaRefreshPolicy.cloudStreamId(profile.id, pluginId, item.providerName, item.url),
                                    profileId = profile.id,
                                    title = item.title,
                                    url = item.url,
                                    thumbnailUrl = item.posterUrl.orEmpty(),
                                    type = "CLOUDSTREAM",
                                    sourceType = "CLOUDSTREAM",
                                    category = section.sectionName.ifBlank { "CloudStream" },
                                    sourceUrl = entry.url,
                                    pluginId = pluginId,
                                    repositoryId = repositoryId,
                                    extraInfo = item.providerName
                                )
                            }
                        }.distinctBy { it.id }
                        if (fetched.isEmpty()) {
                            failures++
                            return@entryLoop
                        }
                        val previous = database.mediaDao()
                            .getCloudStreamCatalog(profile.id, pluginId)
                        val refreshedRows = MediaRefreshPolicy.mergeCloudStream(
                            profileId = profile.id,
                            pluginId = pluginId,
                            incoming = fetched,
                            existing = previous
                        )
                        val refreshedSourceKeys = refreshedRows.mapTo(hashSetOf()) {
                            "${it.pluginId}|${it.extraInfo}|${it.url}"
                        }
                        val mergedRows = (refreshedRows + previous.filterNot {
                            "${it.pluginId}|${it.extraInfo}|${it.url}" in refreshedSourceKeys
                        }).distinctBy { it.id }
                        database.mediaDao().replaceCloudStreamCatalog(profile.id, pluginId, mergedRows)
                        refreshed += mergedRows.size
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        failures++
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failures++
        }

        if (failures > 0 && refreshed == 0) Result.retry() else Result.success()
    }

    companion object {
        private val REFRESHABLE_SOURCE_TYPES = setOf("LINK", "STREAM", "REPO")
    }
}
