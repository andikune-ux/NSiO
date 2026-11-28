package com.example.data.telegram

import android.content.Context
import android.util.Log
import com.example.player.DriveMediaCache
import java.io.File

/**
 * Defers reclaiming an imported Telegram video until its real playback data
 * source has closed and Media3 reports at least one committed cache span.
 */
internal object TelegramLegacyCacheMigration {
    private const val TAG = "NSiO.TelegramMigration"
    private const val PREFS = "telegram_legacy_cache_migration"
    private fun verifiedKey(cacheKey: String): String = cacheKey + "#playback_verified"

    fun remember(context: Context, cacheKey: String, legacyFile: File) {
        if (!cacheKey.startsWith("telegram:")) return
        val canonical = runCatching { legacyFile.canonicalFile }.getOrNull() ?: return
        val imported = runCatching {
            File(context.filesDir, "telegram/imported").canonicalFile
        }.getOrNull() ?: return
        if (!canonical.isFile || canonical.parentFile != imported || canonical.length() <= 0L) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(cacheKey, canonical.absolutePath)
            .apply()
    }

    fun cleanupIfCached(context: Context, cacheKey: String?, actualPlaybackReadSucceeded: Boolean): Boolean {
        if (cacheKey.isNullOrBlank() || !cacheKey.startsWith("telegram:")) return false
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val path = preferences.getString(cacheKey, null) ?: return false
        // Cache spans are checked only after the actual CacheDataSource has closed,
        // so CacheDataSink has had an opportunity to commit its written fragment.
        val cachedBytesVerified = DriveMediaCache.hasCachedData(cacheKey)
        val legacy = runCatching { File(path).canonicalFile }.getOrNull() ?: return false
        val imported = runCatching { File(context.filesDir, "telegram/imported").canonicalFile }.getOrNull()
            ?: return false
        val canonicalLegacyFile = legacy.isFile && legacy.parentFile == imported && legacy.length() > 0L
        if (!TelegramLegacyMigrationPolicy.mayDeleteLegacyCopy(
                actualPlaybackReadSucceeded = actualPlaybackReadSucceeded,
                cachedBytesVerified = cachedBytesVerified,
                legacyPathIsCanonical = canonicalLegacyFile,
                hasOtherDatabaseReferences = false
            )
        ) return false

        if (actualPlaybackReadSucceeded && cachedBytesVerified) {
            preferences.edit().putBoolean(verifiedKey(cacheKey), true).apply()
        }

        return try {
            if (!legacy.isFile || legacy.parentFile != imported || legacy.length() <= 0L) {
                preferences.edit().remove(cacheKey).remove(verifiedKey(cacheKey)).apply()
                return false
            }
            val deleted = legacy.delete()
            if (deleted) {
                preferences.edit().remove(cacheKey).remove(verifiedKey(cacheKey)).apply()
                Log.i(TAG, "Salinan Telegram lama dihapus setelah data streaming terverifikasi di cache: $cacheKey")
            }
            deleted
        } catch (error: Exception) {
            Log.w(TAG, "Salinan Telegram lama tetap dipertahankan; verifikasi cache/penghapusan gagal: $cacheKey", error)
            false
        }
    }
    /**
     * Removes only legacy imports whose playback read and committed cache spans
     * were previously verified. Unplayed imports remain as the only safe copy.
     */
    fun clearPreviouslyVerifiedCopies(context: Context): Int {
        runCatching { DriveMediaCache.get(context.applicationContext) }.onFailure {
            Log.w(TAG, "Cache Media3 tidak dapat dibuka untuk verifikasi migrasi Telegram", it)
            return 0
        }
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val entries = preferences.all.filter { (key, value) -> key.startsWith("telegram:") && value is String }
        var deletedCount = 0
        for ((cacheKey, rawPath) in entries) {
            if (!preferences.getBoolean(verifiedKey(cacheKey), false)) continue
            if (!DriveMediaCache.hasCachedData(cacheKey)) continue
            val legacy = runCatching { File(rawPath as String).canonicalFile }.getOrNull() ?: continue
            val imported = runCatching { File(context.filesDir, "telegram/imported").canonicalFile }.getOrNull() ?: continue
            if (!legacy.isFile || legacy.parentFile != imported || legacy.length() <= 0L) continue
            if (runCatching { legacy.delete() }.getOrDefault(false)) {
                preferences.edit().remove(cacheKey).remove(verifiedKey(cacheKey)).apply()
                deletedCount++
                Log.i(TAG, "Salinan Telegram lama terverifikasi dibersihkan: $cacheKey")
            }
        }
        return deletedCount
    }
}
