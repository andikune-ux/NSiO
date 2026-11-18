package com.example.embedded

import com.example.data.model.MediaItem
import com.example.embedded.api.ModuleMediaRequest
import com.example.embedded.api.ModuleMediaResult
import com.example.embedded.api.ModuleMediaSource
import com.example.embedded.host.CatalogMediaAdapter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogMediaAdapterTest {
    private fun item(source: String, url: String) = MediaItem(
        id = "media-1",
        profileId = "profile-1",
        title = "Sample",
        url = url,
        sourceType = source
    )

    @Test
    fun cloudStreamRequestDelegatesToExistingHostPlayback() = runBlocking {
        val media = item("CLOUDSTREAM", "https://stream.example/video.m3u8")
        var playedId: String? = null
        val adapter = CatalogMediaAdapter(
            ModuleMediaSource.CLOUD_STREAM,
            { listOf(media) },
            { playedId = it.id }
        )

        val result = adapter.open(
            ModuleMediaRequest(ModuleMediaSource.CLOUD_STREAM, media.url, media.title)
        )

        assertEquals(ModuleMediaResult.Opened(media.id), result)
        assertEquals(media.id, playedId)
    }

    @Test
    fun driveAdapterRejectsNonDriveCatalogItem() = runBlocking {
        val media = item("LINK", "https://example.com/video.mp4")
        val adapter = CatalogMediaAdapter(ModuleMediaSource.DRIVE, { listOf(media) }) {}

        val result = adapter.open(
            ModuleMediaRequest(ModuleMediaSource.DRIVE, media.url, media.title)
        )

        assertTrue(result is ModuleMediaResult.Rejected)
    }

    @Test
    fun missingCatalogItemReturnsUnavailable() = runBlocking {
        val adapter = CatalogMediaAdapter(ModuleMediaSource.SERVER, { emptyList() }) {}

        val result = adapter.open(
            ModuleMediaRequest(ModuleMediaSource.SERVER, "https://server.example/a.mp4", "Missing")
        )

        assertTrue(result is ModuleMediaResult.Unavailable)
    }
}
