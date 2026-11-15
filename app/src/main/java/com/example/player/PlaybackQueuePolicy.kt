package com.example.player

import com.example.data.model.MediaItem
import com.example.data.model.MediaSourceClassifier
import kotlin.random.Random

/** Queue order policy for video playback; the active item remains first on shuffle activation. */
internal object PlaybackQueuePolicy {
    /**
     * Keeps the player queue inside the source that owns the current item.
     * Drive is recognized by explicit source type, playlist identity, or trusted Drive hosts.
     */
    fun filterForCurrentSource(
        items: List<MediaItem>,
        current: MediaItem,
        drivePlaylistIds: Set<String> = emptySet()
    ): List<MediaItem> {
        val sourceItems = when {
            current.sourceType.equals("CLOUDSTREAM", ignoreCase = true) -> {
                items.filter { candidate ->
                    candidate.sourceType.equals("CLOUDSTREAM", ignoreCase = true) &&
                        (current.pluginId.isBlank() || candidate.pluginId == current.pluginId) &&
                        (current.pluginId.isNotBlank() || current.repositoryId.isBlank() || candidate.repositoryId == current.repositoryId)
                }
            }
            MediaSourceClassifier.isDriveMedia(current, drivePlaylistIds) -> {
                items.filter { candidate ->
                    MediaSourceClassifier.isDriveMedia(candidate, drivePlaylistIds) &&
                        (current.playlistId.isBlank() || candidate.playlistId == current.playlistId)
                }
            }
            current.playlistId.isNotBlank() -> {
                items.filter { candidate ->
                    candidate.playlistId == current.playlistId &&
                        candidate.sourceType.equals(current.sourceType, ignoreCase = true)
                }
            }
            else -> items.filter { candidate ->
                candidate.sourceType.equals(current.sourceType, ignoreCase = true)
            }
        }
        return (sourceItems.filterNot { it.type.equals("PHOTO", ignoreCase = true) } +
            listOf(current).filterNot { it.type.equals("PHOTO", ignoreCase = true) })
            .distinctBy { it.id }
    }

    fun build(items: List<MediaItem>, currentId: String, shuffle: Boolean, random: Random = Random.Default): List<MediaItem> {
        val videos = items.asSequence()
            .filterNot { it.type.equals("PHOTO", ignoreCase = true) }
            .distinctBy { it.id }
            .toList()
        val current = videos.firstOrNull { it.id == currentId } ?: return videos
        return if (shuffle) listOf(current) + videos.filterNot { it.id == currentId }.shuffled(random) else videos
    }

    fun adjacentIndex(itemCount: Int, currentIndex: Int, direction: Int, repeatAll: Boolean): Int? {
        if (itemCount <= 0 || currentIndex !in 0 until itemCount || direction == 0) return null
        val target = currentIndex + if (direction > 0) 1 else -1
        if (target in 0 until itemCount) return target
        if (!repeatAll) return null
        return if (direction > 0) 0 else itemCount - 1
    }
}
