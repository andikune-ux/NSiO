package com.example.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleMimeResolverTest {
    @Test
    fun resolvesAssAndSsaExtensionsIncludingQueryStrings() {
        assertEquals("text/x-ssa", resolveSubtitleMimeType("https://cdn.example/subtitle.ASS?token=abc", null))
        assertEquals("text/x-ssa", resolveSubtitleMimeType("https://cdn.example/subtitle.ssa#track", null))
    }

    @Test
    fun normalizesAssAliasesFromPluginMimeTypes() {
        listOf("text/x-ass", "application/x-ass", "application/x-ssa", "text/x-ssa").forEach { mime ->
            assertEquals("text/x-ssa", resolveSubtitleMimeType("https://cdn.example/file.bin", mime))
        }
    }

    @Test
    fun normalizesSubripAliasesAndMimeParameters() {
        assertEquals("application/x-subrip", resolveSubtitleMimeType("https://cdn.example/captions", "text/srt; charset=utf-8"))
        assertEquals("application/x-subrip", resolveSubtitleMimeType("https://cdn.example/captions", "application/srt"))
        assertEquals("application/x-subrip", resolveSubtitleMimeType("https://cdn.example/captions.srt", null))
    }

    @Test
    fun recognizesVttAndTtml() {
        assertEquals("text/vtt", resolveSubtitleMimeType("https://cdn.example/captions.vtt", null))
        assertEquals("application/ttml+xml", resolveSubtitleMimeType("https://cdn.example/captions.xml", null))
        assertEquals("application/ttml+xml", resolveSubtitleMimeType("https://cdn.example/captions", "application/ttml+xml"))
    }

    @Test
    fun prefersRecognizedDeclaredMimeAndLeavesUnknownFormatsUntouched() {
        assertEquals("text/vtt", resolveSubtitleMimeType("https://cdn.example/captions.ass", "text/vtt"))
        assertNull(resolveSubtitleMimeType("https://cdn.example/captions.bin", "application/octet-stream"))
        assertNull(resolveSubtitleMimeType("https://cdn.example/captions", null))
    }
}
