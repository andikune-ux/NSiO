package com.example.player

import android.content.Context
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import java.io.File

/**
 * Device-local cache shared by every NSiO player surface.
 * Only Google Drive media is routed through this cache.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object DriveMediaCache {
    private const val MAX_CACHE_BYTES = 1024L * 1024L * 1024L // 1 GiB
    private const val CACHE_DIRECTORY = "nsio_drive_media_cache"

    @Volatile
    private var instance: SimpleCache? = null

    fun get(context: Context): Cache {
        return instance ?: synchronized(this) {
            instance ?: SimpleCache(
                File(context.applicationContext.cacheDir, CACHE_DIRECTORY),
                LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
                StandaloneDatabaseProvider(context.applicationContext)
            ).also { instance = it }
        }
    }
}
