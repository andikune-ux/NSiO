package com.example.data.model

import java.util.UUID

/**
 * Reconciles refreshed catalog rows with locally persisted playback and favorite state.
 * Matching is deliberately scoped to the source playlist or profile/plugin identity.
 */
object MediaRefreshPolicy {
    fun mergePlaylist(existing: List<MediaItem>, incoming: List<MediaItem>): List<MediaItem> {
        val oldByKey = existing.groupBy { playlistKey(it) }
            .mapValues { (_, rows) -> rows.toMutableList() }
            .toMutableMap()

        val merged = incoming.map { fresh ->
            val matches = oldByKey[playlistKey(fresh)]
            val previous = if (matches.isNullOrEmpty()) null else matches.removeAt(0)
            if (previous == null) fresh else fresh.copy(
                id = previous.id,
                durationMs = previous.durationMs,
                progressMs = previous.progressMs,
                lastWatchedAt = previous.lastWatchedAt,
                isFavorite = previous.isFavorite,
                createdAt = previous.createdAt
            )
        }
        // Keep removed source entries when they still represent user-owned history or favorites.
        val retainedHistory = oldByKey.values.flatten().filter {
            it.isFavorite || it.lastWatchedAt > 0L || it.progressMs > 0L
        }
        return (merged + retainedHistory).distinctBy { it.id }
    }

    fun cloudStreamId(profileId: String, pluginId: String, providerName: String, url: String): String {
        val stableKey = listOf(profileId, pluginId, providerName, url).joinToString("|")
        return UUID.nameUUIDFromBytes(stableKey.toByteArray(Charsets.UTF_8)).toString()
    }

    fun mergeCloudStream(
        profileId: String,
        pluginId: String,
        incoming: List<MediaItem>,
        existing: List<MediaItem>
    ): List<MediaItem> {
        val previousBySource = existing.associateBy { cloudStreamKey(it) }
        return incoming.map { fresh ->
            val previous = previousBySource[cloudStreamKey(fresh)]
            fresh.copy(
                id = cloudStreamId(profileId, pluginId, fresh.extraInfo, fresh.url),
                category = ((previous?.category.orEmpty().split(" • ")) + fresh.category.split(" • "))
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(" • ")
                    .ifBlank { "CloudStream" },
                durationMs = previous?.durationMs ?: fresh.durationMs,
                progressMs = previous?.progressMs ?: fresh.progressMs,
                lastWatchedAt = previous?.lastWatchedAt ?: fresh.lastWatchedAt,
                isFavorite = previous?.isFavorite ?: fresh.isFavorite,
                createdAt = previous?.createdAt ?: fresh.createdAt
            )
        }
    }

    private fun playlistKey(item: MediaItem): String =
        "${item.url.trim()}|${item.type.trim().uppercase()}"

    private fun cloudStreamKey(item: MediaItem): String =
        "${item.pluginId}|${item.extraInfo}|${item.url}"
}
