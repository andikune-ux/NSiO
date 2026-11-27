package com.example.data.model

import java.net.URI

/**
 * Validates and canonicalizes Telegram share links before TDLib resolves them.
 */
data class TelegramSourceLink(
    val url: String,
    val sourceType: String,
    val fallbackName: String
)

object TelegramSourceLinkPolicy {
    private val allowedHosts = setOf("t.me", "telegram.me")
    private val reservedPaths = setOf(
        "share", "addstickers", "addemoji", "proxy", "socks", "login",
        "confirmphone", "setlanguage", "iv", "boost", "giftcode"
    )

    fun parse(rawUrl: String): TelegramSourceLink {
        val input = rawUrl.trim()
        require(input.isNotEmpty()) { "Tautan Telegram tidak boleh kosong." }

        val uri = runCatching { URI(input) }
            .getOrElse { throw IllegalArgumentException("Format tautan Telegram tidak valid.") }
        val host = uri.host?.lowercase()?.removePrefix("www.")
        require(uri.scheme.equals("https", ignoreCase = true) && host in allowedHosts) {
            "Gunakan tautan Telegram HTTPS dari t.me atau telegram.me."
        }
        require(uri.userInfo == null && (uri.port == -1 || uri.port == 443)) {
            "Tautan Telegram tidak boleh berisi kredensial atau port khusus."
        }

        val rawSegments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        require(rawSegments.isNotEmpty()) {
            "Tautan Telegram tidak memuat nama channel, grup, atau postingan."
        }
        require(rawSegments.none { it == "." || it == ".." }) {
            "Format jalur tautan Telegram tidak valid."
        }

        // t.me/s/<public-channel>/<message-id> is Telegram's public preview URL.
        val segments = if (rawSegments.first().equals("s", ignoreCase = true)) {
            rawSegments.drop(1)
        } else {
            rawSegments
        }
        require(segments.isNotEmpty()) { "Nama channel Telegram tidak ditemukan." }
        require(segments.first().lowercase() !in reservedPaths) {
            "Tautan tersebut bukan tautan channel, grup, atau postingan Telegram."
        }

        val isInvite = segments.first().startsWith("+") || segments.first().equals("joinchat", ignoreCase = true)
        val isPost = segments.size >= 2 && segments.last().toLongOrNull() != null
        val normalizedPath = segments.joinToString("/") { segment ->
            if (segment.startsWith("+")) segment else segment
        }
        val normalizedUrl = "https://t.me/$normalizedPath"
        val first = segments.first()
        val fallbackName = when {
            first.startsWith("+") || first.equals("joinchat", ignoreCase = true) -> "Undangan Telegram"
            isPost -> "Postingan Telegram ${segments.last()}"
            else -> "Telegram $first"
        }

        return TelegramSourceLink(
            url = normalizedUrl,
            sourceType = when {
                isInvite -> "TELEGRAM_INVITE"
                isPost -> "TELEGRAM_POST"
                else -> "TELEGRAM_CHAT"
            },
            fallbackName = fallbackName
        )
    }
}
