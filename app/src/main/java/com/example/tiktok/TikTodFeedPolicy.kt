package com.example.tiktok

import com.example.data.model.MediaItem
import com.example.data.model.MediaSourceClassifier
import java.net.URI
import java.net.URLDecoder

/** Feed policy: Drive videos and photos, randomized without repetition inside one cycle. */
internal object TikTodFeedPolicy {
    fun eligible(items: List<MediaItem>, profileId: String, drivePlaylistIds: Set<String>): List<MediaItem> =
        items.asSequence()
            .filter { it.profileId == profileId }
            .filter { MediaSourceClassifier.isDriveMedia(it, drivePlaylistIds) }
            .distinctBy { it.id }
            .toList()

    /**
     * Drive media is keyed by its actual file ID so two imported rows pointing to
     * the same Drive video cannot be assigned to different Avatars.
     */
    fun assignmentKey(item: MediaItem): String {
        val driveId = listOf(item.url, item.sourceUrl, item.thumbnailUrl)
            .firstNotNullOfOrNull(::googleDriveFileId)
        return driveId?.let { "drive:$it" } ?: item.id
    }

    fun assignmentOwner(item: MediaItem, assignments: Map<String, String>): String? =
        assignments[assignmentKey(item)] ?: assignments[item.id]

    /** Counts unique Drive media per Avatar; every requested Avatar is returned, including empty ones. */
    fun avatarVideoCounts(
        items: List<MediaItem>,
        assignments: Map<String, String>,
        avatarIds: Collection<String>
    ): Map<String, Int> {
        val uniqueVideos = items.distinctBy(::assignmentKey)
        return avatarIds.associateWith { avatarId ->
            uniqueVideos.count { assignmentOwner(it, assignments) == avatarId }
        }
    }

    private fun googleDriveFileId(rawUrl: String): String? {
        if (!MediaSourceClassifier.isGoogleDriveUrl(rawUrl)) return null
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return null
        val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)
        val markers = setOf("folders", "d", "document", "spreadsheets", "presentation")
        val markerIndex = segments.indexOfFirst { it in markers }
        if (markerIndex >= 0 && markerIndex + 1 < segments.size) {
            return segments[markerIndex + 1].takeIf(String::isNotBlank)
        }
        val idValue = uri.rawQuery.orEmpty().split('&')
            .firstOrNull { it.substringBefore('=') == "id" }
            ?.substringAfter('=', "")
            ?.takeIf(String::isNotBlank)
        return idValue?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
    }

    /** A Mau Mu feed is restricted to the single selected avatar, including page-two JJ avatars. */
    fun forAvatar(items: List<MediaItem>, assignments: Map<String, String>, avatarId: String): List<MediaItem> =
        if (avatarId.isBlank()) emptyList() else items.filter { assignmentOwner(it, assignments) == avatarId }

    /** Jedag Jedug aggregates all videos owned by any page-two avatar, and nothing from page one. */
    fun jedagJedug(items: List<MediaItem>, assignments: Map<String, String>): List<MediaItem> =
        items.filter { assignmentOwner(it, assignments)?.startsWith("p2:") == true }

    fun newCycle(items: List<MediaItem>, previousFirstId: String? = null, random: kotlin.random.Random = kotlin.random.Random.Default): List<MediaItem> {
        if (items.isEmpty()) return emptyList()
        val shuffled = items.shuffled(random)
        if (shuffled.size > 1 && shuffled.first().id == previousFirstId) {
            return shuffled.drop(1) + shuffled.first()
        }
        return shuffled
    }

    /** New items are prioritized at session start, while the previous session's last item is avoided. */
    fun sessionStartOrder(
        items: List<MediaItem>,
        pendingNewIds: Set<String>,
        previousVideoId: String?,
        random: kotlin.random.Random = kotlin.random.Random.Default
    ): List<MediaItem> {
        if (items.isEmpty()) return emptyList()
        val fresh = items.filter { it.id in pendingNewIds }.shuffled(random).toMutableList()
        val regular = items.filterNot { it.id in pendingNewIds }.shuffled(random).toMutableList()
        val priority = if (fresh.isNotEmpty()) fresh else regular
        if (priority.size > 1 && priority.first().id == previousVideoId) {
            val alternative = priority.indexOfFirst { it.id != previousVideoId }
            if (alternative > 0) {
                val first = priority[0]
                priority[0] = priority[alternative]
                priority[alternative] = first
            }
        } else if (priority.size == 1 && priority.first().id == previousVideoId && regular.isNotEmpty()) {
            val alternative = regular.firstOrNull { it.id != previousVideoId }
            if (alternative != null) {
                return listOf(alternative) + priority + regular.filterNot { it.id == alternative.id }
            }
        }
        return fresh + regular
    }

    fun appendNewItems(current: List<MediaItem>, discovered: List<MediaItem>, playedIds: Set<String>): List<MediaItem> {
        val known = (current.map { it.id } + playedIds).toHashSet()
        return discovered.filter { it.id !in known } + current
    }
}
