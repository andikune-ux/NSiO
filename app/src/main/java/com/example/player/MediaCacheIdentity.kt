package com.example.player

import androidx.media3.common.util.UnstableApi
import java.net.URI
import java.net.URLDecoder

/**
 * Cache identity prefers source-native IDs, then a stable catalog item ID.
 * It deliberately excludes profile IDs so the same media can share cached bytes.
 */
@UnstableApi
internal object MediaCacheIdentity {
    private val driveHosts = setOf(
        "drive.google.com",
        "docs.google.com",
        "drive.usercontent.google.com",
        "drive.googleusercontent.com",
        "lh3.googleusercontent.com"
    )

    /**
     * Prefer source-native identities (Drive file ID, Telegram message/file ID),
     * then a stable catalog media ID, and only then the resolved URL. This lets
     * CloudStream/CDN URL refreshes reuse the same cached byte ranges.
     */
    fun forPlayback(rawUrl: String, stableMediaKey: String?): String? {
        val scheme = runCatching { URI(rawUrl.trim()).scheme?.lowercase() }.getOrNull()
        // Local downloads and user-selected content URIs are permanent files, not
        // streaming cache candidates; never duplicate them into the Media3 cache.
        if (scheme == "file" || scheme == "content") return null
        val urlIdentity = forUrl(rawUrl)
        urlIdentity?.takeIf { it.startsWith("drive:") }?.let { return it }
        TelegramTdlibDataSource.cacheIdentity(rawUrl)?.let { return it }
        stableMediaKey?.trim()?.takeIf { it.isNotEmpty() }?.let { return "media:" + it }
        return urlIdentity
    }

    fun forCacheRequest(activeCacheKey: String?, explicitCacheKey: String?, encodedPath: String): String? {
        val activeKey = activeCacheKey ?: return explicitCacheKey
        val path = encodedPath.ifBlank { "/" }
        return when {
            activeKey.startsWith("media:") && explicitCacheKey != null -> explicitCacheKey
            activeKey.startsWith("media:") || activeKey.startsWith("url:") -> activeKey + ":" + path
            explicitCacheKey != null -> explicitCacheKey
            else -> activeKey
        }
    }

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
            if (driveId != null) return "drive:${driveId.substringBefore("=")}"
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
