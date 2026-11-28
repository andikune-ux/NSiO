package com.example.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaCacheIdentityTest {
    @Test
    fun driveUrlFormsShareTheSameFileIdentity() {
        val fileLink = MediaCacheIdentity.forUrl("https://drive.google.com/file/d/abc123/view")
        val downloadLink = MediaCacheIdentity.forUrl("https://drive.usercontent.google.com/download?id=abc123&export=download")
        val docsLink = MediaCacheIdentity.forUrl("https://docs.google.com/document/d/abc123/edit")
        val photoUrl = MediaCacheIdentity.forUrl("https://lh3.googleusercontent.com/d/abc123=w2000")
        assertEquals("drive:abc123", fileLink)
        assertEquals(fileLink, downloadLink)
        assertEquals(fileLink, docsLink)
        assertEquals(fileLink, photoUrl)
    }

    @Test
    fun differentDriveFilesNeverShareAnIdentity() {
        assertNotEquals(
            MediaCacheIdentity.forUrl("https://drive.google.com/file/d/first/view"),
            MediaCacheIdentity.forUrl("https://drive.google.com/file/d/second/view")
        )
    }

    @Test
    fun stableMediaIdentitySurvivesResolvedUrlRefresh() {
        val first = MediaCacheIdentity.forPlayback(
            "https://cdn-one.example/video/temporary-token-a.mp4",
            "CLOUDSTREAM:movie-42"
        )
        val refreshed = MediaCacheIdentity.forPlayback(
            "https://cdn-two.example/video/temporary-token-b.mp4",
            "CLOUDSTREAM:movie-42"
        )
        assertEquals("media:CLOUDSTREAM:movie-42", first)
        assertEquals(first, refreshed)
    }

    @Test
    fun permanentLocalFilesNeverEnterStreamingCache() {
        assertNull(MediaCacheIdentity.forPlayback("file:///data/user/0/nsio/files/download.mp4", "FILE:download-1"))
        assertNull(MediaCacheIdentity.forPlayback("content://media/external/video/1", "FILE:download-1"))
    }

    @Test
    fun sourceNativeDriveAndTelegramIdentitiesTakePriorityOverCatalogIds() {
        assertEquals(
            "drive:file-123",
            MediaCacheIdentity.forPlayback("https://drive.google.com/file/d/file-123/view", "DRIVE:item-1")
        )
        assertEquals(
            "telegram:-100123:456:789",
            MediaCacheIdentity.forPlayback("telegram://media/-100123/456/789.mp4", "TELEGRAM:item-2")
        )
    }

    @Test
    fun adaptiveSegmentsUseStableMediaPrefixAndDistinctPaths() {
        val first = MediaCacheIdentity.forCacheRequest("media:CLOUDSTREAM:movie-42", null, "/segment-001.ts")
        val second = MediaCacheIdentity.forCacheRequest("media:CLOUDSTREAM:movie-42", null, "/segment-002.ts")
        assertEquals("media:CLOUDSTREAM:movie-42:/segment-001.ts?token=one", first)
        assertEquals("media:CLOUDSTREAM:movie-42:/segment-002.ts?token=two", second)
        assertNotEquals(first, second)
    }

    @Test
    fun progressiveMediaUsesOneStableKeyAcrossResolvedPathChanges() {
        assertEquals(
            "media:CLOUDSTREAM:movie-42",
            MediaCacheIdentity.forCacheRequest(
                "media:CLOUDSTREAM:movie-42",
                "media:CLOUDSTREAM:movie-42",
                "/new-cdn/path/changed.mp4"
            )
        )
    }

    @Test
    fun sourceNativeCacheKeysRemainUnmodified() {
        assertEquals(
            "drive:file-123",
            MediaCacheIdentity.forCacheRequest("drive:file-123", "drive:file-123", "/video.mp4")
        )
        assertEquals(
            "telegram:-100123:456:789",
            MediaCacheIdentity.forCacheRequest("telegram:-100123:456:789", "telegram:-100123:456:789", "/789.mp4")
        )
    }

    @Test
    fun genericUrlsNormalizeHostAndDefaultPortButKeepMeaningfulQuery() {
        assertEquals(
            MediaCacheIdentity.forUrl("HTTPS://CDN.EXAMPLE:443/video.mp4#player"),
            MediaCacheIdentity.forUrl("https://cdn.example/video.mp4")
        )
        assertNotEquals(
            MediaCacheIdentity.forUrl("https://cdn.example/video.mp4?token=one"),
            MediaCacheIdentity.forUrl("https://cdn.example/video.mp4?token=two")
        )
    }

    @Test
    fun telegramMessageUsesStableChatMessageAndFileIdentity() {
        assertEquals(
            "telegram:-100123:456:789",
            TelegramTdlibDataSource.cacheIdentity("telegram://media/-100123/456/789.mp4")
        )
    }

    @Test
    fun telegramDifferentFilesNeverShareCacheIdentity() {
        val first = TelegramTdlibDataSource.cacheIdentity("telegram://media/-100123/456/789.mp4")
        val second = TelegramTdlibDataSource.cacheIdentity("telegram://media/-100123/456/790.mp4")
        assertNotEquals(first, second)
    }

    @Test
    fun localAndInvalidUrlsAreNotCached() {
        assertNull(MediaCacheIdentity.forUrl("content://media/external/video/1"))
        assertNull(MediaCacheIdentity.forUrl("not a url"))
    }
}
