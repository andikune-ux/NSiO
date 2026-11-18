package com.example.embedded.host

import com.example.data.model.MediaItem
import com.example.embedded.api.ModuleMediaAdapter
import com.example.embedded.api.ModuleMediaRequest
import com.example.embedded.api.ModuleMediaResult
import com.example.embedded.api.ModuleMediaSource
import java.net.URI

/**
 * Dedicated bridge for NSiO server media.
 *
 * It resolves only catalog entries explicitly tagged SERVER and hands their
 * HTTP(S) playback URL to NSiO's existing player. Firebase Storage download
 * URLs and externally hosted media URLs use the same playback path.
 */
class ServerMediaAdapter(
    private val mediaProvider: () -> List<MediaItem>,
    private val onPlay: (MediaItem) -> Unit
) : ModuleMediaAdapter {
    override val source: ModuleMediaSource = ModuleMediaSource.SERVER

    override suspend fun open(request: ModuleMediaRequest): ModuleMediaResult {
        if (request.uri.isBlank()) {
            return ModuleMediaResult.Rejected("Alamat media server kosong.")
        }

        val item = mediaProvider().firstOrNull { candidate ->
            candidate.url == request.uri || candidate.id == request.uri
        } ?: return ModuleMediaResult.Unavailable(
            "Media server tidak ditemukan lagi di katalog NSiO."
        )

        if (!item.sourceType.equals("SERVER", ignoreCase = true)) {
            return ModuleMediaResult.Rejected("Item bukan media dari sumber Server NSiO.")
        }

        val playbackUri = runCatching { URI(item.url.trim()) }.getOrNull()
            ?: return ModuleMediaResult.Rejected("Alamat media server tidak valid.")
        val scheme = playbackUri.scheme?.lowercase()
        if (scheme !in setOf("http", "https") || playbackUri.host.isNullOrBlank() ||
            playbackUri.rawUserInfo != null
        ) {
            return ModuleMediaResult.Rejected(
                "Server hanya menerima URL HTTP(S) yang memiliki host valid."
            )
        }

        return try {
            onPlay(item)
            ModuleMediaResult.Opened(item.id)
        } catch (failure: Exception) {
            ModuleMediaResult.Rejected(
                failure.message ?: "Player NSiO gagal membuka media server."
            )
        }
    }
}
