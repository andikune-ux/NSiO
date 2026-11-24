package com.example.player

/**
 * Converts Google Drive file preview/view URLs to Drive's download endpoint.
 *
 * Do not hard-code a confirmation token: Drive may issue a per-response token
 * and cookie for files that require a download confirmation. Starting at the
 * canonical uc endpoint lets Drive issue its current redirect/confirmation
 * response instead of sending Media3 a stale, fabricated token.
 */
internal fun normalizePlaybackUrl(url: String): String {
    val normalized = url.trim()
    val uri = runCatching { java.net.URI(normalized) }.getOrNull()
    if (uri == null || uri.scheme?.lowercase() !in setOf("http", "https") ||
        uri.host?.lowercase() != "drive.google.com"
    ) return normalized

    val pathFileId = Regex(
        "^/file/d/([A-Za-z0-9_-]+)/(?:preview|view)/?$",
        RegexOption.IGNORE_CASE
    ).matchEntire(uri.path.orEmpty())?.groupValues?.getOrNull(1)
    val queryFileId = Regex("(?:^|&)id=([A-Za-z0-9_-]+)(?:&|$)", RegexOption.IGNORE_CASE)
        .find(uri.rawQuery.orEmpty())?.groupValues?.getOrNull(1)
    val fileId = pathFileId ?: if (uri.path.equals("/open", true) || uri.path.equals("/uc", true)) {
        queryFileId
    } else null

    return if (!fileId.isNullOrBlank()) {
        "https://drive.google.com/uc?export=download&id=$fileId"
    } else normalized
}

internal fun shouldUseNativePlayer(type: String, url: String): Boolean {
    if (type.equals("PHOTO", ignoreCase = true)) return false
    return normalizePlaybackUrl(url).isNotBlank()
}


/** Returns the adjacent item in the active media list: -1 previous, +1 next. */
internal fun adjacentMedia(
    items: List<com.example.data.model.MediaItem>,
    currentId: String,
    direction: Int
): com.example.data.model.MediaItem? {
    if (direction == 0) return null
    val currentIndex = items.indexOfFirst { it.id == currentId }
    if (currentIndex < 0) return null
    val targetIndex = currentIndex + if (direction > 0) 1 else -1
    return items.getOrNull(targetIndex)
}
