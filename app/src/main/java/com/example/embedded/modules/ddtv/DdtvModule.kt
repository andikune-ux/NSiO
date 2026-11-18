package com.example.embedded.modules.ddtv

import com.example.embedded.api.EmbeddedModule
import com.example.embedded.api.ModuleContext
import com.example.embedded.api.ModuleMediaBridge
import com.example.embedded.api.ModuleMediaRequest
import com.example.embedded.api.ModuleMediaResult
import com.example.embedded.api.ModuleMediaSource
import com.example.embedded.api.ModuleMetadata
import java.io.File

class DdtvModule : EmbeddedModule {
    override val metadata = ModuleMetadata(
        "dev.andikune.nsio.ddtv",
        "DDTV Experimental",
        "0.1.0",
        1,
        "Modul uji lifecycle, host UI, dan adapter media NSiO.",
        true
    )

    private var storage: File? = null
    private var mediaBridge: ModuleMediaBridge? = null
    private var started = false

    override fun onCreate(context: ModuleContext) {
        storage = context.storageDirectory
        mediaBridge = context.mediaBridge
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
    }

    fun isStarted() = started

    fun availableMediaSources(): Set<ModuleMediaSource> =
        mediaBridge?.availableSources().orEmpty()

    suspend fun openMedia(request: ModuleMediaRequest): ModuleMediaResult =
        mediaBridge?.open(request)
            ?: ModuleMediaResult.Unavailable("DDTV module host belum diinisialisasi.")
}
