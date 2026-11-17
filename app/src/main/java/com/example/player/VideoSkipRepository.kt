package com.example.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class VideoSkipStamp(
    val startMs: Long,
    val endMs: Long,
    val label: String,
    val source: String
)

/**
 * CloudStream-compatible timestamp lookup order:
 * AniSkip -> TheIntroDB -> IntroDB. A source is used only when it returns
 * usable timestamps; network failure never blocks playback.
 */
object VideoSkipRepository {
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(7, TimeUnit.SECONDS)
            .readTimeout(7, TimeUnit.SECONDS)
            .callTimeout(9, TimeUnit.SECONDS)
            .build()
    }

    suspend fun findStamps(extraInfo: String, durationMs: Long): List<VideoSkipStamp> =
        withContext(Dispatchers.IO) {
            if (durationMs <= 0L || extraInfo.isBlank()) return@withContext emptyList()
            val root = runCatching { JSONObject(extraInfo) }.getOrNull() ?: return@withContext emptyList()
            val ids = root.optJSONObject("skipMetadata") ?: return@withContext emptyList()
            val malId = value(ids, "malId", "myAnimeListId", "myanimelist", "mal")
            val imdbId = value(ids, "imdbId", "imdb")
            val tmdbId = value(ids, "tmdbId", "tmdb")
            val season = value(ids, "season")?.toIntOrNull()
            val episode = value(ids, "episode")?.toIntOrNull()

            val sources = buildList<suspend () -> List<VideoSkipStamp>> {
                if (!malId.isNullOrBlank() && episode != null && episode > 0) {
                    add { aniSkip(malId, episode, durationMs) }
                }
                if (!tmdbId.isNullOrBlank() || !imdbId.isNullOrBlank()) {
                    add { theIntroDb(tmdbId, imdbId, season, episode, durationMs) }
                }
                if (!imdbId.isNullOrBlank() && season != null && episode != null && season >= 0 && episode > 0) {
                    add { introDb(imdbId, season, episode, durationMs) }
                }
            }
            for (source in sources) {
                val result = runCatching { source() }.getOrDefault(emptyList())
                    .filter { it.startMs >= 0L && it.endMs > it.startMs && it.startMs < durationMs }
                    .map { it.copy(endMs = it.endMs.coerceAtMost(durationMs)) }
                    .filter { it.endMs > it.startMs }
                    .sortedBy { it.startMs }
                if (result.isNotEmpty()) return@withContext result
            }
            emptyList()
        }

    private fun value(json: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            val candidate = json.opt(key)?.toString()?.trim()
            if (!candidate.isNullOrBlank() && candidate != "null") return candidate
        }
        return null
    }

    private fun getJson(url: String): JSONObject? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "NSiO/1.0 (CloudStream-compatible video skip)")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            response.body?.string()?.let(::JSONObject)
        }
    }

    private fun aniSkip(malId: String, episode: Int, durationMs: Long): List<VideoSkipStamp> {
        if (malId.toLongOrNull() == null) return emptyList()
        val url = "https://api.aniskip.com/v2/skip-times/$malId/$episode".toHttpUrl()
            .newBuilder()
            .addQueryParameter("types[]", "op")
            .addQueryParameter("types[]", "ed")
            .addQueryParameter("types[]", "mixed-op")
            .addQueryParameter("types[]", "mixed-ed")
            .addQueryParameter("types[]", "recap")
            .addQueryParameter("episodeLength", (durationMs / 1000.0).toString())
            .build()
        val results = getJson(url.toString())?.optJSONArray("results") ?: return emptyList()
        return buildList {
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val interval = item.optJSONObject("interval") ?: continue
                val start = (interval.optDouble("startTime", Double.NaN) * 1000.0).toLong()
                val end = (interval.optDouble("endTime", Double.NaN) * 1000.0).toLong()
                val label = when (item.optString("skipType")) {
                    "op" -> "Opening"
                    "ed" -> "Ending"
                    "recap" -> "Recap"
                    "mixed-op" -> "Opening"
                    "mixed-ed" -> "Ending"
                    else -> continue
                }
                if (start >= 0L && end > start) add(VideoSkipStamp(start, end, label, "AniSkip"))
            }
        }
    }

    private fun theIntroDb(
        tmdbId: String?,
        imdbId: String?,
        season: Int?,
        episode: Int?,
        durationMs: Long
    ): List<VideoSkipStamp> {
        val query = when {
            !tmdbId.isNullOrBlank() -> "tmdb_id=$tmdbId"
            !imdbId.isNullOrBlank() -> "imdb_id=$imdbId"
            else -> return emptyList()
        }
        val url = buildString {
            append("https://api.theintrodb.org/v4/media?")
            append(query)
            if (season != null && episode != null) append("&season=$season&episode=$episode")
        }
        val root = getJson(url) ?: return emptyList()
        val keys = listOf("intro" to "Intro", "recap" to "Recap", "credits" to "Credits", "preview" to "Preview")
        return buildList {
            keys.forEach { (key, label) ->
                val items = root.optJSONArray(key) ?: return@forEach
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val start = item.optLong("start_ms", -1L)
                    val end = item.optLong("end_ms", durationMs)
                    if (start >= 0L && end > start) add(VideoSkipStamp(start, end, label, "TheIntroDB"))
                }
            }
        }
    }

    private fun introDb(
        imdbId: String,
        season: Int,
        episode: Int,
        durationMs: Long
    ): List<VideoSkipStamp> {
        val root = getJson("https://api.introdb.app/segments?imdb_id=$imdbId&season=$season&episode=$episode")
            ?: return emptyList()
        return buildList {
            listOf("intro" to "Opening", "recap" to "Recap", "outro" to "Ending").forEach { (key, label) ->
                val segment = root.optJSONObject(key) ?: return@forEach
                val start = segment.optLong("start_ms", (segment.optDouble("start_sec", -1.0) * 1000.0).toLong())
                val end = segment.optLong("end_ms", (segment.optDouble("end_sec", -1.0) * 1000.0).toLong())
                if (start >= 0L && end > start && start < durationMs) {
                    add(VideoSkipStamp(start, end.coerceAtMost(durationMs), label, "IntroDB"))
                }
            }
        }
    }
}
