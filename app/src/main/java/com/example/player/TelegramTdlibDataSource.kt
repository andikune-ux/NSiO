package com.example.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.example.data.telegram.TelegramClientManager
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.math.min

/**
 * Media3 upstream for Telegram. Each read asks TDLib for only the next byte range;
 * CacheDataSource stores the returned bytes in the same bounded LRU cache as Drive.
 */
@UnstableApi
internal class TelegramTdlibDataSource : DataSource {
    private var currentUri: Uri? = null
    private var fileId: Int = 0
    private var position: Long = 0L
    private var bytesRemaining: Long = 0L
    private var totalLength: Long = C.LENGTH_UNSET.toLong()
    private var chunk = ByteArray(0)
    private var chunkPosition = 0
    private var chunkLength = 0

    override fun addTransferListener(transferListener: TransferListener) = Unit

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        currentUri = dataSpec.uri
        fileId = dataSpec.uri.lastPathSegment.orEmpty()
            .substringBefore('.')
            .toIntOrNull()
            ?: throw IOException("ID file Telegram tidak valid.")
        position = dataSpec.position
        val metadata = try {
            TelegramClientManager.getFileBlocking(fileId)
        } catch (error: Exception) {
            throw IOException("Tidak dapat membaca metadata file Telegram.", error)
        }
        totalLength = metadata.size.takeIf { it > 0L } ?: C.LENGTH_UNSET.toLong()
        if (totalLength != C.LENGTH_UNSET.toLong() && position > totalLength) {
            throw IOException("Posisi baca melewati ukuran file Telegram.")
        }
        bytesRemaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            totalLength != C.LENGTH_UNSET.toLong() -> (totalLength - position).coerceAtLeast(0L)
            else -> Long.MAX_VALUE
        }
        return if (bytesRemaining == Long.MAX_VALUE) C.LENGTH_UNSET.toLong() else bytesRemaining
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L || (totalLength != C.LENGTH_UNSET.toLong() && position >= totalLength)) {
            return C.RESULT_END_OF_INPUT
        }
        if (chunkPosition >= chunkLength) loadNextChunk()
        if (chunkLength <= 0) return C.RESULT_END_OF_INPUT

        val count = min(length, chunkLength - chunkPosition)
            .let { if (bytesRemaining == Long.MAX_VALUE) it else min(it.toLong(), bytesRemaining).toInt() }
        System.arraycopy(chunk, chunkPosition, buffer, offset, count)
        chunkPosition += count
        position += count
        if (bytesRemaining != Long.MAX_VALUE) bytesRemaining -= count
        return count
    }

    private fun loadNextChunk() {
        val wanted = when {
            bytesRemaining == Long.MAX_VALUE -> CHUNK_BYTES.toLong()
            else -> min(CHUNK_BYTES.toLong(), bytesRemaining)
        }
        if (wanted <= 0L) {
            chunkLength = 0
            return
        }
        val file = try {
            TelegramClientManager.downloadFileRangeBlocking(fileId, position, wanted)
        } catch (error: Exception) {
            throw IOException("Gagal mengambil bagian video Telegram pada byte $position.", error)
        }
        val local = file.local
        if (local.path.isBlank()) throw IOException("TDLib belum menyediakan bagian file Telegram.")
        val localStart = local.downloadOffset
        val relativeOffset = position - localStart
        val path = java.io.File(local.path)
        val available = if (local.isDownloadingCompleted) {
            path.length()
        } else {
            min(local.downloadedPrefixSize, path.length())
        }
        if (relativeOffset < 0L || relativeOffset >= available) {
            if (totalLength != C.LENGTH_UNSET.toLong() && position >= totalLength) {
                chunkLength = 0
                return
            }
            throw IOException("Bagian file Telegram belum lengkap (offset $localStart, tersedia $available).")
        }
        val count = min(wanted, available - relativeOffset).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        if (count <= 0) {
            chunkLength = 0
            return
        }
        chunk = ByteArray(count)
        // TDLib writes sparse range data at its absolute file offsets (pwrite).
        // downloadedPrefixSize is relative to downloadOffset, but the file seek is absolute.
        RandomAccessFile(path, "r").use { input ->
            input.seek(position)
            input.readFully(chunk)
        }
        chunkPosition = 0
        chunkLength = count
    }

    override fun getUri(): Uri? = currentUri

    override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()

    override fun close() {
        if (fileId > 0) TelegramClientManager.deleteDownloadedFile(fileId)
        currentUri = null
        fileId = 0
        position = 0L
        bytesRemaining = 0L
        totalLength = C.LENGTH_UNSET.toLong()
        chunk = ByteArray(0)
        chunkPosition = 0
        chunkLength = 0
    }

    internal class Factory : DataSource.Factory {
        override fun createDataSource(): DataSource = TelegramTdlibDataSource()
    }

    companion object {
        private const val CHUNK_BYTES = 512 * 1024
        fun cacheIdentity(rawUrl: String): String? {
            val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return null
            if (uri.scheme != "telegram" || uri.host != "media") return null
            val parts = uri.pathSegments
            if (parts.size != 3 || parts[2].substringBefore('.').toIntOrNull() == null) return null
            return "telegram:${parts[0]}:${parts[1]}:${parts[2].substringBefore('.')}"
        }
    }
}
