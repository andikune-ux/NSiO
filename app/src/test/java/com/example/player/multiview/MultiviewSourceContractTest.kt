package com.example.player.multiview

import android.net.Uri
import com.example.data.model.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultiviewSourceContractTest {
    @Test
    fun registryReportsOnlyRegisteredSourceKinds() {
        val registry = MultiviewSourceRegistry(listOf(GoogleDriveMultiviewAdapter(), LocalFileMultiviewAdapter()))
        assertTrue(registry.supports(MultiviewSourceKind.GOOGLE_DRIVE))
        assertTrue(registry.supports(MultiviewSourceKind.LOCAL_FILE))
        assertFalse(registry.supports(MultiviewSourceKind.FILE_SERVER))
    }

    @Test
    fun registryReturnsFailureForAnUnregisteredAdapter() = runBlocking {
        val result = MultiviewSourceRegistry(emptyList()).resolve(
            MultiviewSourceRequest(kind = MultiviewSourceKind.FILE_SERVER)
        )
        assertTrue(result is MultiviewResolveResult.Failure)
        assertTrue((result as MultiviewResolveResult.Failure).reason.contains("belum tersedia"))
    }

    @Test
    fun registryPreservesCoroutineCancellation() = runBlocking {
        val adapter = object : MultiviewSourceAdapter {
            override val kind = MultiviewSourceKind.CLOUDSTREAM
            override suspend fun resolve(request: MultiviewSourceRequest): MultiviewResolveResult {
                throw CancellationException("cancelled by caller")
            }
        }
        try {
            MultiviewSourceRegistry(listOf(adapter)).resolve(
                MultiviewSourceRequest(kind = MultiviewSourceKind.CLOUDSTREAM)
            )
            throw AssertionError("Cancellation must propagate to the caller")
        } catch (expected: CancellationException) {
            assertEquals("cancelled by caller", expected.message)
        }
    }

    @Test
    fun googleDriveAdapterAcceptsDriveLinksAndRejectsUnrelatedHosts() = runBlocking {
        val adapter = GoogleDriveMultiviewAdapter()
        val valid = adapter.resolve(MultiviewSourceRequest(
            kind = MultiviewSourceKind.GOOGLE_DRIVE,
            uri = Uri.parse("https://drive.google.com/file/d/example/view")
        ))
        val invalid = adapter.resolve(MultiviewSourceRequest(
            kind = MultiviewSourceKind.GOOGLE_DRIVE,
            uri = Uri.parse("https://example.com/video.mp4")
        ))
        assertTrue(valid is MultiviewResolveResult.Success)
        assertEquals(MultiviewSourceKind.GOOGLE_DRIVE, (valid as MultiviewResolveResult.Success).source.sourceKind)
        assertTrue(invalid is MultiviewResolveResult.Failure)
    }

    @Test
    fun localAdapterPreservesContentUriInsteadOfConvertingToAFilePath() = runBlocking {
        val uri = Uri.parse("content://com.example.provider/video/123")
        val result = LocalFileMultiviewAdapter().resolve(
            MultiviewSourceRequest(kind = MultiviewSourceKind.LOCAL_FILE, uri = uri)
        )
        assertTrue(result is MultiviewResolveResult.Success)
        assertEquals(uri, (result as MultiviewResolveResult.Success).source.uri)
    }

    @Test
    fun fileServerAdapterAcceptsHttpAndRejectsUnsupportedProtocols() = runBlocking {
        val adapter = HttpFileServerMultiviewAdapter()
        val http = adapter.resolve(MultiviewSourceRequest(
            kind = MultiviewSourceKind.FILE_SERVER,
            uri = Uri.parse("https://media.example.com/movie.mp4")
        ))
        val smb = adapter.resolve(MultiviewSourceRequest(
            kind = MultiviewSourceKind.FILE_SERVER,
            uri = Uri.parse("smb://nas.example.com/movie.mp4")
        ))
        assertTrue(http is MultiviewResolveResult.Success)
        assertTrue(smb is MultiviewResolveResult.Failure)
    }

    @Test
    fun cloudStreamAdapterRejectsNonPluginMedia() = runBlocking {
        val adapter = CloudStreamMultiviewAdapter {
            MultiviewResolveResult.Failure("should not be called")
        }
        val result = adapter.resolve(MultiviewSourceRequest(
            kind = MultiviewSourceKind.CLOUDSTREAM,
            mediaItem = MediaItem(
                id = "local",
                profileId = "profile",
                title = "Local video",
                url = "content://media/video/1",
                sourceType = "FILE"
            )
        ))
        assertTrue(result is MultiviewResolveResult.Failure)
        assertTrue((result as MultiviewResolveResult.Failure).reason.contains("bukan konten CloudStream"))
    }
}
