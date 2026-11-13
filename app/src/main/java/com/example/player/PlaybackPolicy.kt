package com.example.player

/**
 * Converts a public Google Drive preview URL into a direct download endpoint
 * so supported video files can be handled by Media3 instead of an embedded browser.
 * Private files may still require authentication and fail with a native playback error.
 */
internal fun normalizePlaybackUrl(url: String): String {
    val normalized = url.trim()
    val match = Regex(
        "^https?://drive\\.google\\.com/file/d/([A-Za-z0-9_-]+)/preview/?(?:[?#].*)?$",
        RegexOption.IGNORE_CASE
    ).matchEntire(normalized)
    val fileId = match?.groupValues?.getOrNull(1)
    return if (!fileId.isNullOrBlank()) {
        "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t"
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
