package com.example.data.cache

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.example.player.DriveMediaCache
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppCacheCleanerTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun clearRemovesOnlyDisposableCacheAndPreservesAppData() = runBlocking {
        val mediaCache = File(context.cacheDir, "nsio_drive_media_cache").apply { mkdirs() }
        File(mediaCache, "segment.bin").writeBytes(byteArrayOf(1, 2, 3))
        val legacyCache = File(context.cacheDir, "telegram_media_cache").apply { mkdirs() }
        File(legacyCache, "segment.bin").writeBytes(byteArrayOf(4, 5, 6))

        val database = File(context.filesDir, "nsio_database_sentinel.db").apply { writeText("keep") }
        val telegramSession = File(context.filesDir, "telegram/session/sentinel").apply {
            parentFile?.mkdirs()
            writeText("keep")
        }

        assertTrue(AppCacheCleaner.clear(context))
        // SimpleCache may keep its database directory open after removing all spans.
        assertFalse(legacyCache.exists())
        assertTrue(database.exists())
        assertTrue(telegramSession.exists())
    }

    @Test
    fun clearRefusesToRunWhilePlaybackDataSourceIsOpen() = runBlocking {
        val mediaCache = File(context.cacheDir, "nsio_drive_media_cache").apply { mkdirs() }
        val sentinel = File(mediaCache, "segment.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val tracked = DriveMediaCache.track(NoOpDataSource(), context)
        tracked.open(DataSpec(Uri.parse("https://example.test/video.mp4")))

        assertFalse(AppCacheCleaner.clear(context))
        assertTrue(sentinel.exists())

        tracked.close()
        assertTrue(AppCacheCleaner.clear(context))
    }

    private class NoOpDataSource : DataSource {
        private var uri: Uri? = null
        override fun addTransferListener(transferListener: TransferListener) = Unit
        override fun open(dataSpec: DataSpec): Long {
            uri = dataSpec.uri
            return C.LENGTH_UNSET.toLong()
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = C.RESULT_END_OF_INPUT
        override fun getUri(): Uri? = uri
        override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()
        override fun close() { uri = null }
    }
}
