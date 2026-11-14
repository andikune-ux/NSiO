package com.example.data.plugin

import java.util.zip.ZipFile

/**
 * Bounds ZIP metadata and actual decompressed bytes before a CloudStream archive
 * can be retained or handed to a future plugin runtime.
 */
internal object CloudStreamArchivePolicy {
    const val MAX_ENTRIES = 512
    const val MAX_ENTRY_EXPANDED_BYTES = 128L * 1024L * 1024L
    const val MAX_TOTAL_EXPANDED_BYTES = 192L * 1024L * 1024L

    fun validate(
        zip: ZipFile,
        maxEntries: Int = MAX_ENTRIES,
        maxEntryExpandedBytes: Long = MAX_ENTRY_EXPANDED_BYTES,
        maxTotalExpandedBytes: Long = MAX_TOTAL_EXPANDED_BYTES
    ) {
        require(maxEntries > 0 && maxEntryExpandedBytes > 0L && maxTotalExpandedBytes > 0L) {
            "Batas validasi arsip tidak valid."
        }

        val names = HashSet<String>()
        val entries = zip.entries()
        var entryCount = 0
        var totalExpandedBytes = 0L

        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            entryCount += 1
            require(entryCount <= maxEntries) {
                "Arsip .cs3 memiliki terlalu banyak entri (maksimum $maxEntries)."
            }

            val name = entry.name
            require(
                name.isNotBlank() &&
                    !name.startsWith("/") &&
                    !name.contains(92.toChar()) &&
                    !name.contains(0.toChar()) &&
                    name.split('/').none { it == ".." || it.isBlank() && it != name.substringAfterLast('/') }
            ) {
                "Arsip .cs3 memiliki nama/path entri yang tidak aman."
            }
            require(names.add(name)) {
                "Arsip .cs3 memiliki entri ZIP duplikat: $name"
            }

            if (entry.isDirectory) continue

            var entryExpandedBytes = 0L
            zip.getInputStream(entry).buffered().use { input ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    entryExpandedBytes += count
                    totalExpandedBytes += count
                    require(entryExpandedBytes <= maxEntryExpandedBytes) {
                        "Entri $name melebihi batas hasil ekstraksi."
                    }
                    require(totalExpandedBytes <= maxTotalExpandedBytes) {
                        "Total data hasil ekstraksi arsip .cs3 melebihi batas."
                    }
                }
            }
            require(entry.size < 0L || entryExpandedBytes == entry.size) {
                "Ukuran aktual entri $name tidak cocok dengan metadata ZIP."
            }
        }
    }
}
