package com.example.embedded.security

import org.json.JSONObject
import java.util.zip.ZipFile

object ModuleArchivePolicy {
    const val MAX_ENTRIES=256
    const val MAX_ENTRY_BYTES=32L*1024L*1024L
    const val MAX_TOTAL_BYTES=96L*1024L*1024L
    fun validate(zip:ZipFile):JSONObject {
        val entries=zip.entries()
        var count=0
        var total=0L
        var hasDex=false
        var manifest:JSONObject?=null
        val normalizedNames=hashSetOf<String>()
        while(entries.hasMoreElements()) {
            val entry=entries.nextElement()
            count++
            require(count<=MAX_ENTRIES) { "Paket modul memiliki terlalu banyak file." }
            val name=entry.name.replace('\\','/')
            val normalizedName=name.trimEnd('/')
            val segments=normalizedName.split('/')
            require(!name.startsWith("/") && segments.none { it==".." || it=="." || it.isBlank() }) { "Paket modul berisi jalur file tidak aman." }
            require(normalizedNames.add(normalizedName)) { "Paket modul berisi nama file duplikat." }
            if(entry.isDirectory) continue
            require(entry.size>=0L && entry.size<=MAX_ENTRY_BYTES) { "Ukuran file modul tidak valid atau terlalu besar." }
            total+=entry.size
            require(total<=MAX_TOTAL_BYTES) { "Ukuran ekstraksi modul melewati batas." }
            if(name=="classes.dex") hasDex=true
            if(name=="module.json") manifest=JSONObject(zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() })
        }
        val parsed=requireNotNull(manifest) { "module.json tidak ditemukan." }
        require(parsed.optString("id").matches(Regex("[a-zA-Z0-9._-]{1,96}"))) { "ID modul tidak valid." }
        require(parsed.optInt("apiVersion",-1)==1) { "Versi API modul tidak didukung." }
        require(hasDex) { "classes.dex tidak ditemukan." }
        return parsed
    }
}
