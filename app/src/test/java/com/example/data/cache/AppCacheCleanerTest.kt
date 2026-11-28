package com.example.data.cache

import android.content.Context
import android.net.Uri
import com.example.data.local.AppDatabase
import com.example.data.model.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.example.player.DriveMediaCache
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
    fun clearMigratesKnownTelegramLegacyRowsAndKeepsTdlibDatabase() = runBlocking {
        val imported = File(context.filesDir, "telegram/imported").apply { mkdirs() }
        val legacyVideo = File(imported, "legacy.mp4").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val database = AppDatabase.getInstance(context)
        database.mediaDao().insertMedia(
            MediaItem(
                id = "cache-cleaner-telegram-row",
                profileId = "cache-cleaner-profile",
                title = "Legacy Telegram",
                url = Uri.fromFile(legacyVideo).toString(),
                sourceType = "TELEGRAM",
                extraInfo = "chatId=-100123&messageId=456&fileId=789&mimeType=video/mp4"
            )
        )
        val tdlibDatabase = File(context.filesDir, "telegram/tdlib/database/sentinel.db").apply {
            parentFile?.mkdirs()
            writeText("keep")
        }
        val oldTdlibMedia = File(context.filesDir, "telegram/tdlib/files/old-video.bin").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(9, 8, 7))
        }

        assertTrue(AppCacheCleaner.clear(context))

        val migrated = database.mediaDao().getAllMediaList().first { it.id == "cache-cleaner-telegram-row" }
        assertEquals("telegram://media/-100123/456/789.mp4", migrated.url)
        assertFalse(legacyVideo.exists())
        assertTrue(tdlibDatabase.exists())
        assertFalse(oldTdlibMedia.exists())
    }

    @Test
    fun clearRefusesToRunWhilePlaybackDataSourceIsOpen() = runBlocking {
        val mediaCache = File(context.cacheDir, "nsio_drive_media_cache").apply { mkdirs() }
        val sentinel = File(mediaCache, "segment.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val tracked = DriveMediaCache.track(NoOpDataSource())
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
