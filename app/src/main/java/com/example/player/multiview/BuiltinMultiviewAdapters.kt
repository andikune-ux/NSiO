package com.example.player.multiview

import android.net.Uri
import com.example.data.model.MediaItem
import com.example.player.normalizePlaybackUrl

/** Public Google Drive links reuse NSiO's existing URL normalization policy. */
class GoogleDriveMultiviewAdapter : MultiviewSourceAdapter {
    override val kind = MultiviewSourceKind.GOOGLE_DRIVE

    override suspend fun resolve(request: MultiviewSourceRequest): MultiviewResolveResult {
        val raw = request.mediaItem?.url ?: request.uri?.toString().orEmpty()
        if (raw.isBlank() || !isGoogleDriveUri(raw)) {
            return MultiviewResolveResult.Failure("Pilih tautan atau item Google Drive yang valid.")
        }
        val normalized = normalizePlaybackUrl(raw)
        return MultiviewResolveResult.Success(
            MultiviewResolvedSource(
                uri = Uri.parse(normalized),
                title = request.displayName.ifBlank { request.mediaItem?.title ?: "Google Drive" },
                sourceKind = kind
            )
        )
    }

    private fun isGoogleDriveUri(value: String): Boolean {
        val host = runCatching { Uri.parse(value).host?.lowercase() }.getOrNull()
        return host == "drive.google.com" ||
            host == "docs.google.com" ||
            host == "drive.usercontent.google.com"
    }
}

/** SAF/content URIs keep their granted URI permission; no filesystem path conversion. */
class LocalFileMultiviewAdapter : MultiviewSourceAdapter {
    override val kind = MultiviewSourceKind.LOCAL_FILE

    override suspend fun resolve(request: MultiviewSourceRequest): MultiviewResolveResult {
        val raw = request.mediaItem?.url.orEmpty()
        val uri = request.uri ?: runCatching { Uri.parse(raw) }.getOrNull()
            ?: return MultiviewResolveResult.Failure("Pilih file lokal terlebih dahulu.")
        val resolvedUri = when {
            uri.scheme == "content" || uri.scheme == "file" -> uri
            raw.startsWith("/") -> Uri.fromFile(java.io.File(raw))
            else -> null
        } ?: return MultiviewResolveResult.Failure("File lokal harus memakai URI content://, file://, atau path lokal.")
        return MultiviewResolveResult.Success(
            MultiviewResolvedSource(
                uri = resolvedUri,
                title = request.displayName.ifBlank { request.mediaItem?.title ?: "File lokal" },
                sourceKind = kind
            )
        )
    }
}

/**
 * CloudStream is intentionally injected from NSiO's current PluginManager flow.
 * The callback must return the selected resolved stream with its real headers,
 * MIME type and any DRM metadata supported by the player integration.
 */
class CloudStreamMultiviewAdapter(
    private val resolvePluginMedia: suspend (MediaItem) -> MultiviewResolveResult
) : MultiviewSourceAdapter {
    override val kind = MultiviewSourceKind.CLOUDSTREAM

    override suspend fun resolve(request: MultiviewSourceRequest): MultiviewResolveResult {
        val item = request.mediaItem
            ?: return MultiviewResolveResult.Failure("Pilih konten dari katalog plugin CloudStream.")
        if (!item.sourceType.equals("CLOUDSTREAM", ignoreCase = true)) {
            return MultiviewResolveResult.Failure("Item yang dipilih bukan konten CloudStream.")
        }
        return resolvePluginMedia(item)
    }
}

/**
 * HTTP/HTTPS server adapter is a forward-compatible entry point. SMB and WebDAV
 * require dedicated protocol clients and are deliberately not treated as HTTP URLs.
 */
class HttpFileServerMultiviewAdapter : MultiviewSourceAdapter {
    override val kind = MultiviewSourceKind.FILE_SERVER

    override suspend fun resolve(request: MultiviewSourceRequest): MultiviewResolveResult {
        val raw = request.uri?.toString() ?: request.mediaItem?.url.orEmpty()
        val uri = runCatching { Uri.parse(raw) }.getOrNull()
        if (uri == null || (uri.scheme != "http" && uri.scheme != "https") || uri.host.isNullOrBlank()) {
            return MultiviewResolveResult.Failure(
                "File Server belum dikonfigurasi. Adapter HTTP/HTTPS disiapkan; SMB dan WebDAV memerlukan konektor tersendiri."
            )
        }
        return MultiviewResolveResult.Success(
            MultiviewResolvedSource(
                uri = uri,
                title = request.displayName.ifBlank { request.mediaItem?.title ?: uri.lastPathSegment ?: "File Server" },
                sourceKind = kind
            )
        )
    }
}
