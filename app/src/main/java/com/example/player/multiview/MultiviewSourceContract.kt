package com.example.player.multiview

import android.net.Uri
import com.example.data.model.MediaItem
import com.example.data.plugin.CloudStreamStream

/**
 * Source contract for Multiview. Adapters resolve a source without owning a player;
 * the Multiview player layer owns exactly four independent Media3 instances.
 *
 * Keep plugin resolution in the existing CloudStream runtime. In particular, an
 * adapter must not replace plugin headers, cookies, MIME information or DRM data
 * with a bare URL.
 */
enum class MultiviewSourceKind {
    GOOGLE_DRIVE,
    CLOUDSTREAM,
    LOCAL_FILE,
    FILE_SERVER
}

data class MultiviewSourceRequest(
    val kind: MultiviewSourceKind,
    val uri: Uri? = null,
    val mediaItem: MediaItem? = null,
    val displayName: String = "",
    val serverProtocol: String? = null
)

data class MultiviewResolvedSource(
    val uri: Uri,
    val title: String,
    val mimeType: String? = null,
    val requestHeaders: Map<String, String> = emptyMap(),
    val sourceKind: MultiviewSourceKind,
    val isLive: Boolean = false,
    val cloudStream: CloudStreamStream? = null,
    val pluginStreams: List<CloudStreamStream> = emptyList()
)

sealed interface MultiviewResolveResult {
    data class Success(val source: MultiviewResolvedSource) : MultiviewResolveResult
    data class Failure(val reason: String, val recoverable: Boolean = true) : MultiviewResolveResult
}

/**
 * A source adapter translates one source family into a playback descriptor.
 * It deliberately does not create, prepare, pause or release an ExoPlayer.
 */
interface MultiviewSourceAdapter {
    val kind: MultiviewSourceKind
    suspend fun resolve(request: MultiviewSourceRequest): MultiviewResolveResult
}

/**
 * Registry is the only source-selection entry point used by Multiview.
 * Future server protocols can be registered here without changing the player grid.
 */
class MultiviewSourceRegistry(adapters: Collection<MultiviewSourceAdapter>) {
    private val adaptersByKind = adapters.associateBy { it.kind }

    suspend fun resolve(request: MultiviewSourceRequest): MultiviewResolveResult {
        val adapter = adaptersByKind[request.kind]
            ?: return MultiviewResolveResult.Failure(
                reason = "Adapter ${request.kind} belum tersedia."
            )
        return runCatching { adapter.resolve(request) }
            .getOrElse { error ->
                MultiviewResolveResult.Failure(
                    reason = error.localizedMessage ?: "Sumber tidak dapat dibuka."
                )
            }
    }

    fun supports(kind: MultiviewSourceKind): Boolean = kind in adaptersByKind.keys
}

enum class MultiviewSlotStatus {
    EMPTY,
    RESOLVING,
    READY,
    BUFFERING,
    PLAYING,
    PAUSED,
    ENDED,
    ERROR
}

data class MultiviewSlotState(
    val index: Int,
    val status: MultiviewSlotStatus = MultiviewSlotStatus.EMPTY,
    val source: MultiviewResolvedSource? = null,
    val errorMessage: String? = null,
    val isAudioFocused: Boolean = false,
    val isFullscreen: Boolean = false
) {
    init {
        require(index in 0..3) { "Multiview hanya memiliki empat slot." }
    }
}
