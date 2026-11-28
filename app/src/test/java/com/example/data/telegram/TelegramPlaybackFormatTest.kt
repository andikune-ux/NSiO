package com.example.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelegramPlaybackFormatTest {
    @Test
    fun mapsKnownVideoContainersWithoutRelabelingThemAsMp4() {
        assertEquals(TelegramPlaybackFormat("mp4", "video/mp4"), resolveTelegramPlaybackFormat("video/mp4"))
        assertEquals(TelegramPlaybackFormat("webm", "video/webm"), resolveTelegramPlaybackFormat("video/webm"))
        assertEquals(TelegramPlaybackFormat("mkv", "video/x-matroska"), resolveTelegramPlaybackFormat("video/x-matroska"))
    }

    @Test
    fun leavesUnknownContainersForExtractorSniffing() {
        assertEquals(TelegramPlaybackFormat("bin", null), resolveTelegramPlaybackFormat("application/octet-stream"))
        assertNull(telegramPlaybackMimeType("application/octet-stream", "telegram://media/1/2/3.bin"))
    }

    @Test
    fun resolvesMimeTypeFromStreamExtensionWhenMetadataIsMissing() {
        assertEquals("video/webm", telegramPlaybackMimeType(null, "telegram://media/1/2/3.webm"))
        assertEquals("video/x-matroska", telegramPlaybackMimeType(null, "telegram://media/1/2/3.mkv"))
    }
}
