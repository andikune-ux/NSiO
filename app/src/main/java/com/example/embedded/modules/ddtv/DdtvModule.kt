package com.example.embedded.modules.ddtv

import com.example.embedded.api.EmbeddedModule
import com.example.embedded.api.ModuleContext
import com.example.embedded.api.ModuleMediaBridge
import com.example.embedded.api.ModuleMediaRequest
import com.example.embedded.api.ModuleMediaResult
import com.example.embedded.api.ModuleMediaSource
import com.example.embedded.api.ModuleMetadata
import java.io.File
import com.example.embedded.modules.ddtv.server.DdtvCategory
import com.example.embedded.modules.ddtv.server.DdtvServerClient
import com.example.embedded.modules.ddtv.server.DdtvStream
import org.json.JSONObject

class DdtvModule : EmbeddedModule {
    override val metadata = ModuleMetadata(
        "dev.andikune.nsio.ddtv",
        "DDTV Experimental",
        "0.2.0",
        1,
        "Client server DDTV protokol v1 dengan AES-CBC dan verifikasi RSA-SHA256.",
        true
    )

    private var storage: File? = null
    private var mediaBridge: ModuleMediaBridge? = null
    private var started = false
    private var serverClient: DdtvServerClient? = null

    override fun onCreate(context: ModuleContext) {
        storage = context.storageDirectory
        mediaBridge = context.mediaBridge
        serverClient = DdtvServerClient(context.appContext)
        check(storage?.isDirectory == true)
    }

    override fun onStart() {
        started = true
    }

    override fun onStop() {
        started = false
    }

    override fun onDestroy() {
        started = false
        storage = null
        mediaBridge = null
        serverClient = null
    }

    fun isStarted() = started

    suspend fun connectServer(): List<DdtvCategory> =
        serverClient?.fetchCategories() ?: throw IllegalStateException("DDTV module belum diinisialisasi.")

    suspend fun fetchPage(category: DdtvCategory, page: Int = 1): List<JSONObject> =
        serverClient?.fetchPage(category, page) ?: throw IllegalStateException("DDTV module belum diinisialisasi.")

    suspend fun fetchDetail(videoId: Long, videoType: Int): JSONObject =
        serverClient?.fetchDetail(videoId, videoType) ?: throw IllegalStateException("DDTV module belum diinisialisasi.")

    fun resolveStreams(detail: JSONObject): List<DdtvStream> =
        serverClient?.resolveStreams(detail) ?: throw IllegalStateException("DDTV module belum diinisialisasi.")

    fun availableMediaSources(): Set<ModuleMediaSource> =
        mediaBridge?.availableSources().orEmpty()

    suspend fun openMedia(request: ModuleMediaRequest): ModuleMediaResult =
        mediaBridge?.open(request)
            ?: ModuleMediaResult.Unavailable("DDTV module host belum diinisialisasi.")
}
