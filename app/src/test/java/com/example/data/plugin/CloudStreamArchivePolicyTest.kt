package com.example.data.plugin

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipFile
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudStreamArchivePolicyTest {
    private fun archive(vararg entries: Pair<String, ByteArray>): File {
        val file = File.createTempFile("nsio-cloudstream-", ".cs3")
        file.deleteOnExit()
        ZipOutputStream(file.outputStream()).use { output ->
            entries.forEach { (name, bytes) ->
                output.putNextEntry(ZipEntry(name))
                output.write(bytes)
                output.closeEntry()
            }
        }
        return file
    }

    @Test
    fun acceptsSmallRelativeEntries() {
        val file = archive(
            "manifest.json" to "{}".toByteArray(),
            "classes.dex" to byteArrayOf(1, 2, 3)
        )
        ZipFile(file).use { zip -> CloudStreamArchivePolicy.validate(zip) }
        assertTrue(file.length() > 0L)
    }

    @Test
    fun rejectsPathTraversal() {
        val file = archive("../outside.txt" to byteArrayOf(1))
        ZipFile(file).use { zip ->
            assertThrows(IllegalArgumentException::class.java) {
                CloudStreamArchivePolicy.validate(zip)
            }
        }
    }

    @Test
    fun rejectsTooManyEntries() {
        val file = archive("one" to byteArrayOf(1), "two" to byteArrayOf(2))
        ZipFile(file).use { zip ->
            assertThrows(IllegalArgumentException::class.java) {
                CloudStreamArchivePolicy.validate(zip, maxEntries = 1)
            }
        }
    }

    @Test
    fun rejectsExpandedSizeBeyondLimit() {
        val file = archive("payload.bin" to ByteArray(1024) { 7 })
        ZipFile(file).use { zip ->
            assertThrows(IllegalArgumentException::class.java) {
                CloudStreamArchivePolicy.validate(
                    zip,
                    maxEntryExpandedBytes = 512L,
                    maxTotalExpandedBytes = 2048L
                )
            }
        }
    }
}
