package com.example.embedded

import com.example.data.model.MediaItem
import com.example.embedded.api.ModuleMediaRequest
import com.example.embedded.api.ModuleMediaResult
import com.example.embedded.api.ModuleMediaSource
import com.example.embedded.host.ServerMediaAdapter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerMediaAdapterTest {
    private fun item(source: String = "SERVER", url: String) = MediaItem(
        id = "server-media-1",
        profileId = "profile-1",
        title = "Server sample",
        url = url,
        sourceType = source
    )

    @Test
    fun firebaseStorageUrlDelegatesToNsioPlayer() = runBlocking {
        val media = item(
            url = "https://firebasestorage.googleapis.com/v0/b/example/o/users%2Fu1%2Fmedia%2Fmovie.mp4?alt=media&token=test"
        )
        var played: String? = null
        val adapter = ServerMediaAdapter({ listOf(media) }) { played = it.id }

        val result = adapter.open(
            ModuleMediaRequest(ModuleMediaSource.SERVER, media.url, media.title, "video/mp4")
        )

        assertEquals(ModuleMediaResult.Opened(media.id), result)
        assertEquals(media.id, played)
    }

    @Test
    fun externalHttpsServerUrlDelegatesToNsioPlayer() = runBlocking {
        val media = item(url = "https://media.example.test/movie/master.m3u8")
        var played = false
        val adapter = ServerMediaAdapter({ listOf(media) }) { played = true }

        val result = adapter.open(
            ModuleMediaRequest(ModuleMediaSource.SERVER, media.url, media.title)
        )

        assertTrue(result is ModuleMediaResult.Opened)
        assertTrue(played)
    }

    @Test
    fun rejectsNonServerCatalogItem() = runBlocking {
        val media = item(source = "LINK", url = "https://media.example.test/movie.mp4")
        val adapter = ServerMediaAdapter({ listOf(media) }) {}

        val result = adapter.open(
            ModuleMediaRequest(ModuleMediaSource.SERVER, media.url, media.title)
        )

        assertTrue(result is ModuleMediaResult.Rejected)
    }

    @Test
    fun rejectsNonHttpSchemesAndCredentialUrls() = runBlocking {
        val invalidUrls = listOf(
            "file:///sdcard/movie.mp4",
            "javascript:alert(1)",
            "https://user:pass@media.example.test/movie.mp4"
        )

        invalidUrls.forEach { url ->
            val media = item(url = url)
            val adapter = ServerMediaAdapter({ listOf(media) }) {}
            val result = adapter.open(
                ModuleMediaRequest(ModuleMediaSource.SERVER, media.url, media.title)
            )
            assertTrue("$url should be rejected", result is ModuleMediaResult.Rejected)
        }
    }

    @Test
    fun missingServerItemReturnsUnavailable() = runBlocking {
        val adapter = ServerMediaAdapter({ emptyList() }) {}
        val result = adapter.open(
            ModuleMediaRequest(ModuleMediaSource.SERVER, "https://media.example.test/a.mp4", "Missing")
        )
        assertTrue(result is ModuleMediaResult.Unavailable)
    }
}
