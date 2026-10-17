package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_items")
data class MediaItem(
    @PrimaryKey val id: String,
    val profileId: String,
    val title: String,
    val url: String,
    val thumbnailUrl: String = "",
    val type: String = "VIDEO", // "VIDEO", "PHOTO", "STREAM"
    val sourceType: String = "LINK", // "LINK", "FILE", "SERVER"
    val category: String = "Umum",
    val playlistId: String = "",
    val sourceUrl: String = "",
    val durationMs: Long = 0L,
    val progressMs: Long = 0L,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val extraInfo: String = ""
)

@Entity(tableName = "source_playlists")
data class SourcePlaylist(
    @PrimaryKey val id: String,
    val profileId: String,
    val name: String,
    val url: String,
    val sourceType: String = "LINK",
    val itemCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey val id: String,
    val name: String,
    val avatarEmoji: String = "🎬",
    val pin: String = "",
    val isPinEnabled: Boolean = false,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "profile_settings")
data class ProfileSettings(
    @PrimaryKey val profileId: String,
    val language: String = "ID", // "ID", "EN"
    val themeMode: String = "DARK", // "AUTO", "DARK", "LIGHT"
    val displayMode: String = "TV", // "AUTO", "TV", "HP"
    val gridColumns: Int = 4, // 3..8
    val serverPin: String = "1234"
)

@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey val id: String,
    val mediaId: String,
    val title: String,
    val url: String,
    val localPath: String,
    val status: String = "PENDING", // PENDING, DOWNLOADING, PAUSED, COMPLETED, FAILED
    val progressPercent: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
