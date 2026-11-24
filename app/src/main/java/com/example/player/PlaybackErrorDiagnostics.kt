package com.example.player

import androidx.media3.datasource.HttpDataSource
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Media3 wraps failures from custom OkHttp interceptors in HttpDataSourceException.
 * Preserve the useful root cause instead of reporting every wrapped IOException
 * as a broken internet connection.
 */
internal fun describeHttpPlaybackFailure(causes: List<Throwable>): String {
    val driveFailure = causes.firstOrNull {
        it.message?.contains("Google Drive", ignoreCase = true) == true
    }?.message
    if (!driveFailure.isNullOrBlank()) return driveFailure

    if (causes.any { it is UnknownHostException }) {
        return "Alamat server video tidak dapat ditemukan (DNS). Periksa alamat sumber atau jaringan DNS."
    }
    if (causes.any { it is SocketTimeoutException }) {
        return "Server video tidak merespons sampai batas waktu. Ini belum tentu berarti koneksi internet perangkat terputus."
    }
    if (causes.any { it is SocketException }) {
        return "Sambungan ke server video terputus saat transfer data. Coba ulangi atau gunakan sumber lain."
    }

    val underlyingIo = causes.firstOrNull {
        it is IOException && it !is HttpDataSource.HttpDataSourceException
    }?.message?.takeIf { it.isNotBlank() }
    if (underlyingIo != null) {
        return "Permintaan data video gagal: ${underlyingIo.take(240)}"
    }

    return "Permintaan data video gagal. Periksa respons server video atau coba sumber lain; koneksi internet perangkat belum tentu terputus."
}
