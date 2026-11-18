package com.example.embedded.api

import android.content.Context
import java.io.File

data class ModuleMetadata(val id:String,val name:String,val version:String,val apiVersion:Int,val description:String,val builtIn:Boolean=false)
data class ModuleContext(val appContext:Context,val storageDirectory:File)
interface EmbeddedModule {
    val metadata:ModuleMetadata
    fun onCreate(context:ModuleContext)
    fun onStart()
    fun onStop()
    fun onDestroy()
}
