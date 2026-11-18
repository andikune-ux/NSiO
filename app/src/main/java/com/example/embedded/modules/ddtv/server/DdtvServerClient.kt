package com.example.embedded.modules.ddtv.server

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable
import java.util.concurrent.CompletionService
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import javax.net.ssl.HttpsURLConnection

class DdtvServerClient(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("ddtv_server_protocol", Context.MODE_PRIVATE)
    private val profile: DdtvProtocolProfile by lazy {
        val source = appContext.assets.open("ddtv-ui/protocol.json")
            .bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        DdtvProtocolProfile.fromJson(JSONObject(source))
    }
    private val deviceId: String by lazy {
        val existing = preferences.getString("device_id", null)
        if (!existing.isNullOrBlank() && existing.matches(Regex("[0-9a-f]{16}"))) {
            existing
        } else {
            java.util.UUID.randomUUID().toString().replace("-", "").take(16).also {
                preferences.edit().putString("device_id", it).apply()
            }
        }
    }

    @Volatile
    private var cachedApiBase: String? = null

    suspend fun fetchCategories(): List<DdtvCategory> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val response = invokeMethod("categories", JSONObject()) as? JSONArray
                ?: throw IOException("Respons kategori DDTV tidak valid.")
            (0 until response.length()).mapNotNull { response.optJSONObject(it)?.let(DdtvCategory::fromJson) }
        }

    suspend fun fetchPage(category: DdtvCategory, page: Int = 1): List<JSONObject> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (page !in 1..1_000_000) throw IOException("Halaman DDTV tidak valid.")
            val pageSize = if (category.related && category.videoType == 1) 30 else 10
            val data = JSONObject().put("page", page).put("page_size", pageSize)
            val method = if (category.related) {
                data.put("category_id", category.categoryIds())
                "category_related"
            } else {
                data.put("video_type", category.videoType)
                "jav_list"
            }
            val response = invokeMethod(method, data) as? JSONArray
                ?: throw IOException("Respons daftar video DDTV tidak valid.")
            (0 until response.length()).mapNotNull { response.optJSONObject(it) }
        }

    suspend fun fetchDetail(videoId: Long, videoType: Int): JSONObject =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (videoId <= 0L) throw IOException("Identitas video tidak valid.")
            val data = JSONObject()
                .put("video_id", videoId)
                .put("video_type", videoType)
                .put("lang", "id")
            invokeMethod("jav_detail", data) as? JSONObject
                ?: throw IOException("Detail video DDTV tidak valid.")
        }

    fun resolveStreams(video: JSONObject, detail: JSONObject): List<DdtvStream> = DdtvStreamResolver.resolve(video, detail)

    private fun invokeMethod(method: String, data: JSONObject): Any {
        val request = JSONObject()
            .put("method", method)
            .put("lang", "id")
            .put("device_id", deviceId)
            .put("data", data)
        return post(resolveApiBase(), "/player/methods", request, encryptRequest = true)
    }

    @Synchronized
    private fun resolveApiBase(): String {
        cachedApiBase?.let { return it }
        val bootstrapBase = firstHealthy(profile.bootstrap)
        val bootstrapRequest = JSONObject()
            .put("agent", "DDTV")
            .put("channel", "DDTV")
        val configuration = post(
            bootstrapBase,
            "/player/config",
            bootstrapRequest,
            encryptRequest = false
        ) as? JSONObject ?: throw IOException("Konfigurasi layanan DDTV belum tersedia.")

        val rawUrls = configuration.optJSONArray("api_urls")
            ?: throw IOException("Daftar server API DDTV tidak tersedia.")
        val candidates = (0 until minOf(rawUrls.length(), 8)).mapNotNull { index ->
            val raw = rawUrls.optString(index, "").trim()
            if (raw.isBlank()) return@mapNotNull null
            runCatching {
                DdtvServerUrlPolicy.origin(
                    if (raw.startsWith("https://", ignoreCase = true)) raw else "https://$raw"
                )
            }.getOrNull()
        }.distinct()
        if (candidates.isEmpty()) throw IOException("Konfigurasi layanan DDTV belum tersedia.")

        return firstHealthy(candidates).also { cachedApiBase = it }
    }

    private fun firstHealthy(urls: List<String>): String {
        val candidates = urls.distinct().take(5)
        if (candidates.isEmpty()) throw IOException("Server DDTV belum dapat dihubungi.")
        val executor = Executors.newFixedThreadPool(candidates.size)
        val completion: CompletionService<String> = ExecutorCompletionService(executor)
        val futures = mutableListOf<Future<String>>()
        var lastFailure: Throwable? = null
        try {
            candidates.forEach { base ->
                futures += completion.submit(Callable { probeHealth(base) })
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(9)
            var completedCount = 0
            while (completedCount < futures.size) {
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0L) break
                val completed = completion.poll(remaining, TimeUnit.NANOSECONDS) ?: break
                completedCount++
                try {
                    val healthy = completed.get()
                    futures.forEach { if (it !== completed) it.cancel(true) }
                    return healthy
                } catch (failure: ExecutionException) {
                    lastFailure = failure.cause ?: failure
                } catch (failure: Exception) {
                    lastFailure = failure
                }
            }
        } finally {
            futures.forEach { it.cancel(true) }
            executor.shutdownNow()
        }
        throw IOException("Server DDTV belum dapat dihubungi. Periksa koneksi lalu coba lagi.", lastFailure)
    }

    private fun probeHealth(base: String): String {
        val connection = openConnection("$base/player/health")
        try {
            readResponse(connection, 32 * 1024)
            return base
        } finally {
            connection.disconnect()
        }
    }

    private fun post(
        base: String,
        path: String,
        payload: JSONObject,
        encryptRequest: Boolean
    ): Any {
        val request = if (encryptRequest) {
            DdtvProtocolCodec.encodeRequest(payload, profile.key)
        } else {
            payload
        }
        val body = request.toString().toByteArray(StandardCharsets.UTF_8)
        if (body.size > 4 * 1024 * 1024) throw IOException("Permintaan DDTV terlalu besar.")

        val connection = openConnection("$base$path")
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(body.size)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { output -> output.write(body) }

            val responseBytes = readResponse(connection, 4 * 1024 * 1024)
            val responseJson = runCatching {
                JSONObject(String(responseBytes, StandardCharsets.UTF_8))
            }.getOrElse { throw IOException("Respons server DDTV bukan JSON yang valid.", it) }
            return DdtvProtocolCodec.decodeResponse(responseJson, profile.key, profile.publicKey)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(rawUrl: String): HttpsURLConnection {
        if (!DdtvServerUrlPolicy.isPublicHttps(rawUrl)) {
            throw IOException("Alamat server DDTV tidak valid.")
        }
        val uri = URI(rawUrl)
        val addresses = runCatching { InetAddress.getAllByName(uri.host) }
            .getOrElse { throw IOException("Domain server DDTV tidak dapat ditemukan.", it) }
        if (addresses.isEmpty() || addresses.any { !DdtvServerUrlPolicy.isPublicAddress(it) }) {
            throw IOException("Alamat server DDTV mengarah ke jaringan privat.")
        }

        val connection = URL(rawUrl).openConnection() as? HttpsURLConnection
            ?: throw IOException("Server DDTV harus menggunakan HTTPS.")
        connection.connectTimeout = 3_500
        connection.readTimeout = 5_000
        connection.instanceFollowRedirects = false
        connection.useCaches = false
        connection.setRequestProperty("User-Agent", "okhttp/4.12.0")
        connection.setRequestProperty("Accept-Encoding", "gzip")
        return connection
    }

    private fun readResponse(connection: HttpsURLConnection, maxBytes: Int): ByteArray {
        val status = connection.responseCode
        if (status != 200) {
            throw IOException("Layanan DDTV belum tersedia (HTTP $status).")
        }
        val raw = connection.inputStream
        val input = if (connection.contentEncoding.equals("gzip", ignoreCase = true)) {
            GZIPInputStream(raw)
        } else {
            raw
        }
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                if (total > maxBytes) throw IOException("Respons server DDTV terlalu besar.")
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
    }
}
