package com.example.data.cache

import android.content.Context
import com.example.player.DriveMediaCache
import java.io.File

/**
 * Clears only disposable media caches. Profile/database/preferences, Drive
 * playlist URLs, Firebase authentication, Telegram credentials and TDLib data
 * are deliberately outside these paths. The shared Media3 cache includes Drive,
 * Telegram, CloudStream/plugin streams, and other HTTP(S) video sources.
 */
object AppCacheCleaner {
    fun clear(context: Context): Boolean {
        val app = context.applicationContext
        val driveCleared = DriveMediaCache.clear(app) {
            com.example.data.telegram.TelegramLegacyCacheMigration.clearPreviouslyVerifiedCopies(app)
        }
        // If any Media3 reader is active, leave every media-cache directory untouched.
        if (!driveCleared) return false
        val telegramDirectory = File(app.cacheDir, "telegram_media_cache")
        val telegramCleared = !telegramDirectory.exists() || runCatching {
            telegramDirectory.deleteRecursively()
        }.getOrDefault(false)
        val allMediaCachesCleared = driveCleared && telegramCleared
        if (allMediaCachesCleared) {
            // Let TDLib evict its own disposable downloaded media through its supported API.
            // Its database, account state, and user Downloads are not touched.
            com.example.data.telegram.TelegramClientManager.optimizeDisposableStorage()
        }
        return allMediaCachesCleared
    }
}
