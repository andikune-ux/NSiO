package com.example.player

import java.net.URI
import java.net.URLDecoder

/**
 * Cache identity is independent from database item IDs, profile IDs, playlist
 * IDs and Avatar assignments. Drive links share by underlying file ID.
 */
internal object MediaCacheIdentity {
    private val driveHosts = setOf(
        "drive.google.com",
        "docs.google.com",
        "drive.usercontent.google.com",
        "drive.googleusercontent.com"
    )

    fun forUrl(rawUrl: String): String? {
        if (rawUrl.isBlank()) return null
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase() ?: return null

        if (host in driveHosts) {
            val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)
            val driveId = sequenceOf("d", "document", "spreadsheets", "presentation")
                .mapNotNull { marker ->
                    val index = segments.indexOf(marker)
                    if (index >= 0) segments.getOrNull(index + 1)?.takeIf(String::isNotBlank) else null
                }
                .firstOrNull()
                ?: uri.rawQuery.orEmpty().split('&')
                    .firstOrNull { it.substringBefore('=') == "id" }
                    ?.substringAfter('=', "")
                    ?.takeIf(String::isNotBlank)
                    ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
            if (driveId != null) return "drive:$driveId"
        }

        val port = when {
            uri.port == -1 -> ""
            scheme == "https" && uri.port == 443 -> ""
            scheme == "http" && uri.port == 80 -> ""
            else -> ":${uri.port}"
        }
        val path = uri.normalize().rawPath.orEmpty().ifBlank { "/" }
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        return "url:$scheme://$host$port$path$query"
    }
}
