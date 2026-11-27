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
    fun localAndInvalidUrlsAreNotCached() {
        assertNull(MediaCacheIdentity.forUrl("content://media/external/video/1"))
        assertNull(MediaCacheIdentity.forUrl("not a url"))
    }
}
