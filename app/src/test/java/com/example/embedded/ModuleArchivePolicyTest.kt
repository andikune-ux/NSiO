package com.example.embedded

import com.example.embedded.security.ModuleArchivePolicy
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ModuleArchivePolicyTest {
    private fun archive(vararg entries:Pair<String,String>):File {
        val file=File.createTempFile("nsio-module-",".nmod")
        file.deleteOnExit()
        ZipOutputStream(file.outputStream()).use { out ->
            entries.forEach { (name,value) ->
                out.putNextEntry(ZipEntry(name));out.write(value.toByteArray());out.closeEntry()
            }
        }
        return file
    }
    @Test fun acceptsValidPackage() {
        val file=archive("module.json" to """{"id":"test.module","apiVersion":1}""","classes.dex" to "dex")
        ZipFile(file).use { assertEquals("test.module",ModuleArchivePolicy.validate(it).getString("id")) }
    }
    @Test fun rejectsTraversal() {
        val file=archive("module.json" to """{"id":"test.module","apiVersion":1}""","classes.dex" to "dex","../outside.txt" to "bad")
        ZipFile(file).use { assertThrows(IllegalArgumentException::class.java) { ModuleArchivePolicy.validate(it) } }
    }
    @Test fun rejectsUnsupportedApi() {
        val file=archive("module.json" to """{"id":"test.module","apiVersion":2}""","classes.dex" to "dex")
        ZipFile(file).use { assertThrows(IllegalArgumentException::class.java) { ModuleArchivePolicy.validate(it) } }
    }
    @Test fun rejectsDirectoryFileNameCollision() {
        val file=archive(
            "module.json" to """{"id":"test.module","apiVersion":1}""",
            "classes.dex" to "dex",
            "payload/" to "",
            "payload" to "collision"
        )
        ZipFile(file).use {
            assertThrows(IllegalArgumentException::class.java) { ModuleArchivePolicy.validate(it) }
        }
    }

    @Test fun rejectsDotSegmentPaths() {
        val file=archive(
            "module.json" to """{"id":"test.module","apiVersion":1}""",
            "classes.dex" to "dex",
            "./payload.txt" to "bad"
        )
        ZipFile(file).use {
            assertThrows(IllegalArgumentException::class.java) { ModuleArchivePolicy.validate(it) }
        }
    }

    @Test fun rejectsPackageWithoutDex() {
        val file=archive("module.json" to """{"id":"test.module","apiVersion":1}""")
        ZipFile(file).use {
            assertThrows(IllegalArgumentException::class.java) { ModuleArchivePolicy.validate(it) }
        }
    }

}
