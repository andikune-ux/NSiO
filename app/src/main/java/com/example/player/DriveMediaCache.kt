package com.example.player

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import java.io.File

/**
 * Device-local Media3 cache shared by Drive and Telegram playback.
 * Telegram is fetched in byte ranges; both sources share the same 1 GiB LRU budget.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object DriveMediaCache {
    private const val MAX_CACHE_BYTES = 1024L * 1024L * 1024L // 1 GiB
    private const val CACHE_DIRECTORY = "nsio_drive_media_cache"

    @Volatile
    private var instance: SimpleCache? = null
    private var activeReaders: Int = 0

    internal fun track(dataSource: androidx.media3.datasource.DataSource): androidx.media3.datasource.DataSource =
        ActiveReadDataSource(dataSource)

    @Synchronized
    internal fun acquireRead() { activeReaders++ }

    @Synchronized
    internal fun releaseRead() { activeReaders = (activeReaders - 1).coerceAtLeast(0) }

    /**
     * Evict cached media entries without deleting the cache database directory
     * while Media3 may still hold an active SimpleCache instance.
     */
    fun clear(context: Context): Boolean = synchronized(this) {
        // Never mutate/remove cache spans while ExoPlayer is reading them.
        if (activeReaders > 0) return@synchronized false
        val activeCache = instance
        if (activeCache != null) {
            runCatching {
                activeCache.keys.toList().forEach { key -> activeCache.removeResource(key) }
            }.isSuccess
        } else {
            val directory = File(context.applicationContext.cacheDir, CACHE_DIRECTORY)
            !directory.exists() || directory.deleteRecursively()
        }
    }

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


@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class ActiveReadDataSource(
    private val delegate: androidx.media3.datasource.DataSource
) : androidx.media3.datasource.DataSource {
    private var leaseHeld = false

    override fun addTransferListener(transferListener: TransferListener) {
        delegate.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        DriveMediaCacheLease.acquire()
        leaseHeld = true
        return try {
            delegate.open(dataSpec)
        } catch (error: Throwable) {
            releaseLease()
            throw error
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        delegate.read(buffer, offset, length)

    override fun getUri(): Uri? = delegate.uri

    override fun getResponseHeaders(): Map<String, List<String>> = delegate.responseHeaders

    override fun close() {
        try {
            delegate.close()
        } finally {
            releaseLease()
        }
    }

    private fun releaseLease() {
        if (leaseHeld) {
            leaseHeld = false
            DriveMediaCacheLease.release()
        }
    }
}

/** Narrow bridge keeps lease accounting private to the cache owner. */
private object DriveMediaCacheLease {
    fun acquire() = DriveMediaCache.acquireRead()
    fun release() = DriveMediaCache.releaseRead()
}
