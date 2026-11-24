package com.example.tiktok

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** TikTod engagement and preferences are deliberately device-local. */
internal class TikTodLocalStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nsio_tiktod_local", Context.MODE_PRIVATE)

    fun likeCount(mediaId: String): Int = prefs.getInt("likes:$mediaId", 0)
    fun addLike(mediaId: String): Int = (likeCount(mediaId) + 1).also {
        prefs.edit().putInt("likes:$mediaId", it).apply()
    }
    fun viewCount(mediaId: String): Int = prefs.getInt("views:$mediaId", 0)
    fun addView(mediaId: String): Int = (viewCount(mediaId) + 1).also {
        prefs.edit().putInt("views:$mediaId", it).apply()
    }
    fun speed(): Float = prefs.getFloat("speed", 1f).coerceIn(0.5f, 8f)
    fun setSpeed(value: Float) { prefs.edit().putFloat("speed", value.coerceIn(0.5f, 8f)).apply() }
    fun quality(): String = prefs.getString("quality", "Original") ?: "Original"
    fun setQuality(value: String) { prefs.edit().putString("quality", value).apply() }

    fun comments(mediaId: String): List<String> = runCatching {
        val array = JSONArray(prefs.getString("comments:$mediaId", "[]"))
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }.getOrDefault(emptyList())

    fun addComment(mediaId: String, comment: String): List<String> {
        val next = comments(mediaId) + comment.trim().take(1000)
        val array = JSONArray().apply { next.forEach(::put) }
        prefs.edit().putString("comments:$mediaId", array.toString()).apply()
        return next
    }

    fun deleteComment(mediaId: String, index: Int): List<String> {
        val next = comments(mediaId).toMutableList().also { if (index in it.indices) it.removeAt(index) }
        val array = JSONArray().apply { next.forEach(::put) }
        prefs.edit().putString("comments:$mediaId", array.toString()).apply()
        return next
    }
}
