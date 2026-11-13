package com.example.player

/**
 * Returns whether a media item can be handled by NSiO's native Media3 player.
 * Google Drive preview URLs are HTML pages and must remain in their WebView.
 */
internal fun shouldUseNativePlayer(type: String, url: String): Boolean {
    if (type.equals("PHOTO", ignoreCase = true)) return false
    val normalizedUrl = url.trim()
    val isGoogleDrivePreview = normalizedUrl.startsWith(
        "https://drive.google.com/file/",
        ignoreCase = true
    ) && normalizedUrl.endsWith("/preview", ignoreCase = true)
    return normalizedUrl.isNotBlank() && !isGoogleDrivePreview
}
