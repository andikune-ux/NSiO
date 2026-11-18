package com.example.embedded.api

/**
 * Stable boundary between an embedded module and NSiO's media host.
 * Adapters are supplied by the host; modules never create or control the player directly.
 */
enum class ModuleMediaSource {
    DRIVE,
    CLOUD_STREAM,
    FILE_MANAGER,
    SERVER
}

data class ModuleMediaRequest(
    val source: ModuleMediaSource,
    val uri: String,
    val title: String,
    val mimeType: String? = null
)

sealed class ModuleMediaResult {
    data class Opened(val handle: String) : ModuleMediaResult()
    data class Unavailable(val reason: String) : ModuleMediaResult()
    data class Rejected(val reason: String) : ModuleMediaResult()
}

interface ModuleMediaAdapter {
    val source: ModuleMediaSource
    suspend fun open(request: ModuleMediaRequest): ModuleMediaResult
}

interface ModuleMediaBridge {
    fun availableSources(): Set<ModuleMediaSource>
    suspend fun open(request: ModuleMediaRequest): ModuleMediaResult
}

/** Safe default until NSiO injects a reviewed host adapter for a source type. */
class ModuleMediaBridgeRegistry(
    adapters: Iterable<ModuleMediaAdapter> = emptyList()
) : ModuleMediaBridge {
    private val adaptersBySource = adapters.associateBy { it.source }.also {
        require(it.size == adapters.count()) { "Adapter sumber media duplikat." }
    }

    override fun availableSources(): Set<ModuleMediaSource> = adaptersBySource.keys.toSet()

    override suspend fun open(request: ModuleMediaRequest): ModuleMediaResult {
        if (request.uri.isBlank()) {
            return ModuleMediaResult.Rejected("Alamat media kosong.")
        }
        val adapter = adaptersBySource[request.source]
            ?: return ModuleMediaResult.Unavailable(
                "Adapter ${request.source.name} belum disediakan oleh host NSiO."
            )
        return try {
            adapter.open(request)
        } catch (failure: Exception) {
            ModuleMediaResult.Rejected(
                failure.message ?: "Host gagal memproses permintaan media."
            )
        }
    }
}
