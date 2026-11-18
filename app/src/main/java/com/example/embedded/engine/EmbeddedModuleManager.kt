package com.example.embedded.engine

import android.content.Context
import com.example.embedded.api.EmbeddedModule
import com.example.embedded.api.ModuleContext
import com.example.embedded.api.ModuleMetadata
import com.example.embedded.api.ModuleMediaBridge
import com.example.embedded.api.ModuleMediaBridgeRegistry
import com.example.embedded.modules.ddtv.DdtvModule
import com.example.embedded.storage.ModuleStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EmbeddedModuleState(
    val metadata: ModuleMetadata,
    val enabled: Boolean,
    val running: Boolean = false,
    val lastError: String? = null
)

/**
 * Owns module lifecycle on the host side. A failed module callback is contained here and
 * reported to the UI; it must never take down the host/player process.
 */
class EmbeddedModuleManager(
    context: Context,
    private val mediaBridge: ModuleMediaBridge = ModuleMediaBridgeRegistry()
) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("embedded_modules", Context.MODE_PRIVATE)
    private val storage = ModuleStorage(app)
    private val modules: Map<String, EmbeddedModule> =
        listOf(DdtvModule()).associateBy { it.metadata.id }
    private val created = mutableSetOf<String>()
    private val running = mutableSetOf<String>()
    private val errors = mutableMapOf<String, String>()

    private fun snapshot() = modules.values.map { module ->
        val id = module.metadata.id
        val enabled = prefs.getBoolean("enabled:$id", module.metadata.builtIn)
        EmbeddedModuleState(module.metadata, enabled, id in running && enabled, errors[id])
    }

    private val _states = MutableStateFlow(snapshot())
    val states: StateFlow<List<EmbeddedModuleState>> = _states.asStateFlow()

    fun availableMediaSources() = mediaBridge.availableSources()

    private fun publish() {
        _states.value = snapshot()
    }

    @Synchronized
    fun setEnabled(id: String, enabled: Boolean) {
        require(id in modules) { "Modul tidak dikenal." }
        if (!enabled) stop(id)
        prefs.edit().putBoolean("enabled:$id", enabled).apply()
        publish()
    }

    @Synchronized
    fun start(id: String) {
        val module = modules[id] ?: error("Modul tidak dikenal.")
        require(prefs.getBoolean("enabled:$id", module.metadata.builtIn)) {
            "Aktifkan modul sebelum membukanya."
        }
        if (id in running) return

        var createdNow = false
        try {
            if (id !in created) {
                module.onCreate(ModuleContext(app, storage.directory(id), mediaBridge))
                created += id
                createdNow = true
            }
            module.onStart()
            running += id
            errors.remove(id)
            publish()
        } catch (failure: Throwable) {
            running -= id
            if (createdNow) {
                runCatching { module.onDestroy() }
                created -= id
            } else if (id in created) {
                runCatching { module.onStop() }
            }
            errors[id] = failure.message ?: failure.javaClass.simpleName
            publish()
            throw IllegalStateException("Modul ${module.metadata.name} gagal dijalankan: ${errors[id]}", failure)
        }
    }

    @Synchronized
    fun stop(id: String) {
        val module = modules[id] ?: return
        if (id !in running) return
        try {
            module.onStop()
            errors.remove(id)
        } catch (failure: Throwable) {
            errors[id] = failure.message ?: failure.javaClass.simpleName
        } finally {
            running -= id
            publish()
        }
    }

    @Synchronized
    fun destroy(id: String) {
        val module = modules[id] ?: return
        stop(id)
        if (id !in created) return
        try {
            module.onDestroy()
            created -= id
            errors.remove(id)
        } catch (failure: Throwable) {
            errors[id] = failure.message ?: failure.javaClass.simpleName
        } finally {
            publish()
        }
    }
}
