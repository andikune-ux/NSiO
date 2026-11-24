package com.example.player

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Converts common public Google Drive sharing links to Drive's download endpoint.
 * Do not hard-code a confirmation token: Drive issues per-file/per-session tokens
 * and may require a confirmation form or warning cookie on the first response.
 */
internal fun normalizePlaybackUrl(url: String): String {
    val normalized = url.trim()
    val uri = runCatching { URI(normalized) }.getOrNull()
    if (uri == null || uri.scheme?.lowercase() !in setOf("http", "https") ||
        uri.host?.lowercase() != "drive.google.com"
    ) return normalized

    val path = uri.path.orEmpty()
    val pathFileId = Regex(
        "^/file/d/([A-Za-z0-9_-]+)(?:/(?:preview|view))?/?$",
        RegexOption.IGNORE_CASE
    ).matchEntire(path)?.groupValues?.getOrNull(1)
    val query = uri.rawQuery.orEmpty().split('&').mapNotNull { part ->
        val separator = part.indexOf('=')
        if (separator <= 0) null else runCatching {
            URLDecoder.decode(part.substring(0, separator), StandardCharsets.UTF_8.name()) to
                URLDecoder.decode(part.substring(separator + 1), StandardCharsets.UTF_8.name())
        }.getOrNull()
    }.toMap()
    val queryFileId = query["id"]
    val fileId = pathFileId ?: if (path.equals("/open", true) || path.equals("/uc", true)) {
        queryFileId
    } else null

    return if (!fileId.isNullOrBlank()) {
        val encodedId = URLEncoder.encode(fileId, StandardCharsets.UTF_8.name())
        "https://drive.google.com/uc?export=download&id=$encodedId"
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
