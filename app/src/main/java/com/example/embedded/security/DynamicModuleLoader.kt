package com.example.embedded.security

import android.content.Context
import dalvik.system.DexClassLoader
import java.io.File
import java.security.MessageDigest

fun interface ModuleArtifactTrust { fun isTrusted(artifact:File):Boolean }

/** Dynamic code is deny-by-default until its reviewed SHA-256 is explicitly pinned. */
class PinnedDigestTrust(private val trustedSha256:Set<String> = emptySet()):ModuleArtifactTrust {
    override fun isTrusted(artifact:File):Boolean {
        if(!artifact.isFile || trustedSha256.isEmpty()) return false
        val digest=MessageDigest.getInstance("SHA-256").digest(artifact.readBytes()).joinToString("") { "%02x".format(it) }
        return digest in trustedSha256
    }
}

class DynamicModuleLoader(context:Context,private val trust:ModuleArtifactTrust=PinnedDigestTrust()) {
    private val appContext=context.applicationContext
    fun load(artifact:File,optimizedDirectory:File):ClassLoader {
        require(artifact.isFile && artifact.extension.equals("nmod",true)) { "Paket modul .nmod tidak ditemukan." }
        java.util.zip.ZipFile(artifact).use { ModuleArchivePolicy.validate(it) }
        require(trust.isTrusted(artifact)) { "Modul ditolak: hash belum masuk daftar kepercayaan NSiO." }
        if(!optimizedDirectory.exists()) require(optimizedDirectory.mkdirs()) { "Direktori optimasi tidak dapat dibuat." }
        return DexClassLoader(artifact.absolutePath,optimizedDirectory.absolutePath,null,appContext.classLoader)
    }
}
