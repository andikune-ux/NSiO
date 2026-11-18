package com.example.embedded

import com.example.embedded.api.ModuleMediaAdapter
import com.example.embedded.api.ModuleMediaBridgeRegistry
import com.example.embedded.api.ModuleMediaRequest
import com.example.embedded.api.ModuleMediaResult
import com.example.embedded.api.ModuleMediaSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleMediaBridgeTest {
    @Test
    fun missingHostAdapterReturnsUnavailableWithoutTouchingPlayer() = runBlocking {
        val bridge = ModuleMediaBridgeRegistry()
        val result = bridge.open(
            ModuleMediaRequest(ModuleMediaSource.DRIVE, "https://drive.example/video", "Sample")
        )

        assertTrue(result is ModuleMediaResult.Unavailable)
        assertTrue(bridge.availableSources().isEmpty())
    }

    @Test
    fun requestIsRoutedOnlyToMatchingSourceAdapter() = runBlocking {
        var openedUri: String? = null
        val adapter = object : ModuleMediaAdapter {
            override val source = ModuleMediaSource.CLOUD_STREAM
            override suspend fun open(request: ModuleMediaRequest): ModuleMediaResult {
                openedUri = request.uri
                return ModuleMediaResult.Opened("host-handle-1")
            }
        }
        val bridge = ModuleMediaBridgeRegistry(listOf(adapter))
        val request = ModuleMediaRequest(
            ModuleMediaSource.CLOUD_STREAM,
            "cloudstream://sample",
            "Sample"
        )

        val result = bridge.open(request)

        assertEquals(setOf(ModuleMediaSource.CLOUD_STREAM), bridge.availableSources())
        assertEquals("cloudstream://sample", openedUri)
        assertEquals(ModuleMediaResult.Opened("host-handle-1"), result)
    }

    @Test
    fun blankUriIsRejectedBeforeAdapterInvocation() = runBlocking {
        var invoked = false
        val adapter = object : ModuleMediaAdapter {
            override val source = ModuleMediaSource.FILE_MANAGER
            override suspend fun open(request: ModuleMediaRequest): ModuleMediaResult {
                invoked = true
                return ModuleMediaResult.Opened("should-not-run")
            }
        }
        val result = ModuleMediaBridgeRegistry(listOf(adapter)).open(
            ModuleMediaRequest(ModuleMediaSource.FILE_MANAGER, " ", "Empty")
        )

        assertTrue(result is ModuleMediaResult.Rejected)
        assertTrue(!invoked)
    }
}
