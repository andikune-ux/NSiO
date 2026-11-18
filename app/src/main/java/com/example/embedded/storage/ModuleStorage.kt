package com.example.embedded.storage

import android.content.Context
import java.io.File

class ModuleStorage(context:Context) {
    private val root=File(context.applicationContext.filesDir,"embedded/modules").canonicalFile
    fun directory(moduleId:String):File {
        require(moduleId.matches(Regex("[a-zA-Z0-9._-]{1,96}"))) { "ID modul tidak valid." }
        val target=File(root,moduleId).canonicalFile
        require(target.path.startsWith(root.path+File.separator)) { "Lokasi penyimpanan modul tidak valid." }
        if(!target.exists() && !target.mkdirs()) error("Tidak dapat membuat penyimpanan modul.")
        return target
    }
    fun clear(moduleId:String):Boolean = directory(moduleId).deleteRecursively()
}
