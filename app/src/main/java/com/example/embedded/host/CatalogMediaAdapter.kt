package com.example.embedded.host

import com.example.data.model.MediaItem
import com.example.data.model.MediaSourceClassifier
import com.example.embedded.api.ModuleMediaAdapter
import com.example.embedded.api.ModuleMediaRequest
import com.example.embedded.api.ModuleMediaResult
import com.example.embedded.api.ModuleMediaSource

/**
 * Connects a DDTV source request to an item already known by NSiO.
 * The adapter delegates playback to the existing host ViewModel; it never owns a player.
 */
class CatalogMediaAdapter(
    override val source: ModuleMediaSource,
    private val mediaProvider: () -> List<MediaItem>,
    private val onPlay: (MediaItem) -> Unit
) : ModuleMediaAdapter {
    override suspend fun open(request: ModuleMediaRequest): ModuleMediaResult {
        if (request.uri.isBlank()) return ModuleMediaResult.Rejected("Alamat media kosong.")
        val item = mediaProvider().firstOrNull { candidate ->
            candidate.url == request.uri || candidate.id == request.uri
        } ?: return ModuleMediaResult.Unavailable("Media tidak ditemukan lagi di katalog NSiO.")

        val matchesSource = when (source) {
            ModuleMediaSource.DRIVE -> MediaSourceClassifier.isDriveMedia(item)
            ModuleMediaSource.CLOUD_STREAM -> item.sourceType.equals("CLOUDSTREAM", true)
            ModuleMediaSource.FILE_MANAGER -> item.sourceType.contains("FILE", true) ||
                item.sourceType.contains("LOCAL", true) || item.url.startsWith("content://", true)
            ModuleMediaSource.SERVER -> item.sourceType.contains("SERVER", true)
        }
        if (!matchesSource) {
            return ModuleMediaResult.Rejected("Media tidak cocok dengan adapter ${source.name}.")
        }

        return try {
            onPlay(item)
            ModuleMediaResult.Opened(item.id)
        } catch (failure: Exception) {
            ModuleMediaResult.Rejected(failure.message ?: "Player NSiO menolak media.")
        }
    }
}
