package com.example.embedded.modules.ddtv

import com.example.embedded.api.EmbeddedModule
import com.example.embedded.api.ModuleContext
import com.example.embedded.api.ModuleMetadata
import java.io.File

class DdtvModule:EmbeddedModule {
    override val metadata=ModuleMetadata("dev.andikune.nsio.ddtv","DDTV Experimental","0.1.0",1,"Modul uji lifecycle, host UI, dan adapter media NSiO.",true)
    private var storage:File?=null
    private var started=false
    override fun onCreate(context:ModuleContext) { storage=context.storageDirectory; check(storage?.isDirectory==true) }
    override fun onStart() { started=true }
    override fun onStop() { started=false }
    override fun onDestroy() { started=false; storage=null }
    fun isStarted()=started
}
