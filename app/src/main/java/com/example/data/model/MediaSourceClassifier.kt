package com.example.data.model

import java.net.URI

/**
 * Centralized source identity rules for Google Drive catalogs.
 * Host matching is exact so unrelated domains cannot be classified as Drive.
 */
object MediaSourceClassifier {
    private val googleDriveHosts = setOf(
        "drive.google.com",
        "docs.google.com",
        "drive.usercontent.google.com",
        "drive.googleusercontent.com"
    )

    fun isGoogleDriveUrl(rawUrl: String): Boolean {
        if (rawUrl.isBlank()) return false
        val host = runCatching { URI(rawUrl.trim()).host?.lowercase().orEmpty() }
            .getOrDefault("")
        return host in googleDriveHosts
    }

    fun isDriveMedia(item: MediaItem, drivePlaylistIds: Set<String> = emptySet()): Boolean {
        return item.sourceType.equals("DRIVE", ignoreCase = true) ||
            item.playlistId in drivePlaylistIds ||
            item.category.contains("Google Drive", ignoreCase = true) ||
            isGoogleDriveUrl(item.url) ||
            isGoogleDriveUrl(item.sourceUrl)
    }
}
