package com.example.data.telegram

/**
 * Explicit Telegram container mapping shared by catalog URLs and Media3 playback.
 * Unknown MIME values deliberately keep extractor sniffing enabled instead of
 * falsely labelling arbitrary video bytes as MP4.
 */
internal data class TelegramPlaybackFormat(
    val extension: String,
    val mimeType: String?
)

internal fun resolveTelegramPlaybackFormat(rawMimeType: String?): TelegramPlaybackFormat {
    val mime = rawMimeType.orEmpty().substringBefore(';').trim().lowercase()
    return when (mime) {
        "video/mp4", "application/mp4" -> TelegramPlaybackFormat("mp4", "video/mp4")
        "video/webm" -> TelegramPlaybackFormat("webm", "video/webm")
        "video/x-matroska", "video/matroska" -> TelegramPlaybackFormat("mkv", "video/x-matroska")
        "video/quicktime" -> TelegramPlaybackFormat("mov", "video/quicktime")
        "video/3gpp", "video/3gpp2" -> TelegramPlaybackFormat("3gp", mime)
        "video/mp2t" -> TelegramPlaybackFormat("ts", "video/mp2t")
        "video/x-flv" -> TelegramPlaybackFormat("flv", "video/x-flv")
        else -> TelegramPlaybackFormat("bin", null)
    }
}

internal fun telegramPlaybackMimeType(rawMimeType: String?, rawUrl: String): String? {
    resolveTelegramPlaybackFormat(rawMimeType).mimeType?.let { return it }
    val extension = runCatching {
        java.net.URI(rawUrl).path.substringAfterLast('.', "").lowercase()
    }.getOrDefault("")
    return when (extension) {
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"
        "3gp", "3g2" -> "video/3gpp"
        "ts", "m2ts" -> "video/mp2t"
        "flv" -> "video/x-flv"
        else -> null
    }
}
