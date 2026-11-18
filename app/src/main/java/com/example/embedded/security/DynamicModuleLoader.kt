package com.example.embedded.security

import android.content.Context
import dalvik.system.DexClassLoader
import java.io.File
import java.security.MessageDigest

fun interface ModuleArtifactTrust {
    fun isTrusted(artifact: File): Boolean
}

/** Dynamic code is deny-by-default until its reviewed SHA-256 is explicitly pinned. */
class PinnedDigestTrust(
    private val trustedSha256: Set<String> = emptySet()
) : ModuleArtifactTrust {
    override fun isTrusted(artifact: File): Boolean {
        if (!artifact.isFile || trustedSha256.isEmpty()) return false
        val digest = MessageDigest.getInstance("SHA-256").let { md ->
            artifact.inputStream().buffered().use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    md.update(buffer, 0, count)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        }
        return digest in trustedSha256
    }
}

class DynamicModuleLoader(
    context: Context,
    private val trust: ModuleArtifactTrust = PinnedDigestTrust()
) {
    private val appContext = context.applicationContext

    fun load(artifact: File, optimizedDirectory: File): ClassLoader {
        require(artifact.isFile && artifact.extension.equals("nmod", true)) {
            "Paket modul .nmod tidak ditemukan."
        }
        java.util.zip.ZipFile(artifact).use { ModuleArchivePolicy.validate(it) }
        require(trust.isTrusted(artifact)) {
            "Modul ditolak: hash belum masuk daftar kepercayaan NSiO."
        }

        // DexClassLoader must only write optimized code into the app-private code cache.
        val cacheRoot = File(appContext.codeCacheDir, "embedded-modules").canonicalFile
        val optimized = optimizedDirectory.canonicalFile
        require(optimized.path.startsWith(cacheRoot.path + File.separator)) {
            "Direktori optimasi modul harus berada di cache privat NSiO."
        }
        if (!optimized.exists()) {
            require(optimized.mkdirs()) { "Direktori optimasi tidak dapat dibuat." }
        }
        return DexClassLoader(
            artifact.canonicalPath,
            optimized.absolutePath,
            null,
            appContext.classLoader
        )
    }
}
