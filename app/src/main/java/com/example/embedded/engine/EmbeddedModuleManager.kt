package com.example.embedded.engine

import android.content.Context
import com.example.embedded.api.EmbeddedModule
import com.example.embedded.api.ModuleContext
import com.example.embedded.api.ModuleMetadata
import com.example.embedded.modules.ddtv.DdtvModule
import com.example.embedded.storage.ModuleStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EmbeddedModuleState(val metadata:ModuleMetadata,val enabled:Boolean,val running:Boolean=false)

class EmbeddedModuleManager(context:Context) {
    private val app=context.applicationContext
    private val prefs=app.getSharedPreferences("embedded_modules",Context.MODE_PRIVATE)
    private val storage=ModuleStorage(app)
    private val modules:Map<String,EmbeddedModule> = listOf(DdtvModule()).associateBy { it.metadata.id }
    private val created=mutableSetOf<String>()
    private val running=mutableSetOf<String>()
    private fun snapshot()=modules.values.map { m ->
        val enabled=prefs.getBoolean("enabled:${m.metadata.id}",false)
        EmbeddedModuleState(m.metadata,enabled,m.metadata.id in running && enabled)
    }
    private val _states=MutableStateFlow(snapshot())
    val states:StateFlow<List<EmbeddedModuleState>> = _states.asStateFlow()
    @Synchronized fun setEnabled(id:String,enabled:Boolean) {
        require(id in modules) { "Modul tidak dikenal." }
        prefs.edit().putBoolean("enabled:$id",enabled).apply()
        if(!enabled) stop(id)
        _states.value=snapshot()
    }
    @Synchronized fun start(id:String) {
        val module=modules[id] ?: error("Modul tidak dikenal.")
        require(prefs.getBoolean("enabled:$id",false)) { "Aktifkan modul sebelum membukanya." }
        if(id !in created) { module.onCreate(ModuleContext(app,storage.directory(id))); created+=id }
        module.onStart()
        running+=id
        _states.value=snapshot()
    }
    @Synchronized fun stop(id:String) {
        modules[id]?.let { if(id in running) { it.onStop(); running-=id } }
        _states.value=snapshot()
    }
    @Synchronized fun destroy(id:String) {
        modules[id]?.let { if(id in created) { it.onStop(); it.onDestroy(); running-=id; created-=id } }
        _states.value=snapshot()
    }
}
