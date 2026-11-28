package com.example.data.telegram

import android.content.Context
import java.io.File

/**
 * Bounded on-demand Telegram media cache. Telegram files are copied here only
 * after the user starts playback; catalog synchronization never fills it.
 */
internal object TelegramDeferredMediaCache {
    private const val MAX_CACHE_BYTES = 512L * 1024L * 1024L
    private const val DIRECTORY = "telegram_media_cache"

    @Synchronized
    fun get(context: Context, key: String, extension: String): File? {
        val file = fileFor(context, key, extension)
        return file.takeIf { it.isFile && it.length() > 0L }?.also { it.setLastModified(System.currentTimeMillis()) }
    }

    @Synchronized
    fun store(context: Context, key: String, extension: String, source: File): File? {
        if (!source.isFile || source.length() <= 0L) return null
        val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        val target = fileFor(context, key, extension)
        if (!target.isFile || target.length() != source.length()) {
            val temporary = File(directory, "${target.name}.tmp")
            runCatching {
                source.copyTo(temporary, overwrite = true)
                if (target.exists()) target.delete()
                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = true)
                    temporary.delete()
                }
            }.onFailure {
                temporary.delete()
                return null
            }
        }
        target.setLastModified(System.currentTimeMillis())
        evict(directory, target)
        return target.takeIf { it.isFile && it.length() > 0L }
    }

    private fun fileFor(context: Context, key: String, extension: String): File {
        val safeKey = key.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }
            .joinToString("").take(180)
        val safeExtension = extension.lowercase().filter(Char::isLetterOrDigit).take(8).ifBlank { "bin" }
        return File(File(context.cacheDir, DIRECTORY), "$safeKey.$safeExtension")
    }

    private fun evict(directory: File, keep: File) {
        val files = directory.listFiles()?.filter { it.isFile && it != keep }.orEmpty()
        var total = directory.listFiles()?.filter(File::isFile)?.sumOf { it.length() } ?: 0L
        if (total <= MAX_CACHE_BYTES) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= MAX_CACHE_BYTES) break
            val length = file.length()
            if (file.delete()) total -= length
        }
    }
}
