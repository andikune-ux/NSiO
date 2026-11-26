package com.example.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.data.backup.BackupResult
import com.example.data.backup.BackupUtils
import com.example.data.download.VideoDownloadManager
import com.example.data.extractor.LinkExtractor
import com.example.data.extractor.ParseResult
import com.example.data.firebase.AuthState
import com.example.data.firebase.CloudMediaRepository
import com.example.data.firebase.FirebaseAuthManager
import com.example.data.firebase.FirebaseStorageRepository
import com.example.data.local.AppDatabase
import com.example.data.security.PinVault
import com.example.tiktok.TikTodLocalStore
import com.example.data.model.MediaItem
import com.example.data.model.MediaRefreshPolicy
import com.example.data.model.MediaSourceClassifier
import com.example.data.model.Profile
import com.example.data.model.ProfileSettings
import com.example.data.model.SourcePlaylist
import com.example.data.plugin.PluginManager
import com.example.player.PlayerRuntime
import com.example.player.normalizePlaybackUrl
import com.example.player.shouldUseNativePlayer
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.UUID

data class CloudStreamHealthEvent(
    val timestamp: Long,
    val succeeded: Boolean,
    val responseTimeMs: Long,
    val message: String? = null
)

data class CloudStreamSourceHealth(
    val lastSuccessAt: Long = 0L,
    val lastError: String? = null,
    val hasNext: Boolean? = null,
    val consecutiveFailures: Int = 0,
    val lastAttemptAt: Long = 0L,
    val nextAutoRetryAt: Long = 0L,
    val lastResponseTimeMs: Long = 0L,
    val history: List<CloudStreamHealthEvent> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val mediaDao = database.mediaDao()
    private val profileDao = database.profileDao()
    private val sourcePlaylistDao = database.sourcePlaylistDao()

    val pluginManager = PluginManager(application)
    val downloadManager = VideoDownloadManager.getInstance(application)
    val downloads = downloadManager.downloads
    val player = PlayerRuntime.get(application)
    private var cloudStreamVerifierJob: Job? = null

    init {
        // Only CloudStream items may enter the plugin URL refresh path.
        player.isCloudStreamSourceActive = {
            _currentlyPlaying.value?.sourceType.equals("CLOUDSTREAM", ignoreCase = true)
        }

        // Expired CDN URLs are re-resolved through the original provider, not retried
        // from the stale in-memory stream list.
        player.onCloudStreamRefreshRequested = { resumePositionMs ->
            val item = _currentlyPlaying.value
            if (item == null || item.sourceType != "CLOUDSTREAM" || item.pluginId.isBlank()) {
                false
            } else {
                runCatching {
                    val metadata = org.json.JSONObject(item.extraInfo)
                    val providerName = metadata.optString("providerName").takeIf { it.isNotBlank() }
                        ?: return@runCatching false
                    val playbackData = metadata.optString("playbackData").takeIf { it.isNotBlank() }
                        ?: return@runCatching false
                    val entry = pluginManager.findCloudStreamPlugin(item.pluginId) ?: return@runCatching false
                    val freshStreams = pluginManager.loadCloudStreamLinks(entry, providerName, playbackData)
                        .filter { it.url.isNotBlank() }
                        .distinctBy { it.url to it.referer }
                    val fresh = freshStreams.maxByOrNull { it.quality } ?: return@runCatching false
                    val headers = fresh.headers.toMutableMap().apply {
                        if (fresh.referer.isNotBlank() && keys.none { it.equals("Referer", true) }) {
                            put("Referer", fresh.referer)
                        }
                    }
                    val type = fresh.streamType.substringAfterLast('.').uppercase()
                    val extension = runCatching {
                        java.net.URI(fresh.url).path.substringAfterLast('.', "").uppercase()
                    }.getOrDefault("")
                    val mimeType = when {
                        type.contains("M3U8") || type.contains("HLS") || extension == "M3U8" -> "application/x-mpegURL"
                        type.contains("DASH") || type == "MPD" || extension == "MPD" -> "application/dash+xml"
                        type.contains("WEBM") || extension == "WEBM" -> "video/webm"
                        type.contains("MKV") || type.contains("MATROSKA") || extension == "MKV" -> "video/x-matroska"
                        type.contains("MP4") || type.contains("VIDEO") || extension == "MP4" -> "video/mp4"
                        else -> null
                    }
                    player.playWithMimeType(
                        url = fresh.url,
                        title = item.title,
                        startPositionMs = resumePositionMs,
                        requestHeaders = headers,
                        mimeType = mimeType,
                        externalSubtitles = fresh.subtitles,
                        videoInterceptor = fresh.videoInterceptor,
                        pluginStreams = freshStreams
                    )
                    true
                }.getOrDefault(false)
            }
        }
    }

    // Firebase Auth & Cloud Firestore Integration
    val authManager = FirebaseAuthManager(application)
    val authState: StateFlow<AuthState> = authManager.authState
    val cloudRepository = CloudMediaRepository(application)
    private val storageRepository = FirebaseStorageRepository()

    private val _isCloudSyncing = MutableStateFlow(false)
    val isCloudSyncing: StateFlow<Boolean> = _isCloudSyncing.asStateFlow()
    private val _isUploadingServerMedia = MutableStateFlow(false)
    val isUploadingServerMedia: StateFlow<Boolean> = _isUploadingServerMedia.asStateFlow()
    private val _isImportingLocalFile = MutableStateFlow(false)
    val isImportingLocalFile: StateFlow<Boolean> = _isImportingLocalFile.asStateFlow()

    // Profiles state
    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _activeProfile = MutableStateFlow<Profile?>(null)
    val activeProfile: StateFlow<Profile?> = _activeProfile.asStateFlow()

    private val _settings = MutableStateFlow(ProfileSettings(profileId = "default"))
    val settings: StateFlow<ProfileSettings> = _settings.asStateFlow()

    private val devModePreferences = application.getSharedPreferences("nsio_developer_preferences", Application.MODE_PRIVATE)
    // Keep Home catalog selection independently for each local profile.
    private val catalogPreferences = application.getSharedPreferences("nsio_catalog_preferences", Application.MODE_PRIVATE)
    private val cloudStreamHealthPreferences = application.getSharedPreferences("nsio_cloudstream_health", Application.MODE_PRIVATE)
    private val cloudStreamOrderPreferences = application.getSharedPreferences("nsio_cloudstream_plugin_order", Application.MODE_PRIVATE)
    private val _devMode = MutableStateFlow(devModePreferences.getBoolean("dev_mode_enabled", false))
    val devMode: StateFlow<Boolean> = _devMode.asStateFlow()

    fun setDevModeEnabled(enabled: Boolean) {
        devModePreferences.edit().putBoolean("dev_mode_enabled", enabled).apply()
        _devMode.value = enabled
        showStatus(if (enabled) "Mode Dev aktif. Teks layar dapat diseleksi dan disalin." else "Mode Dev nonaktif.")
    }

    // Content state
    private val _mediaList = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaList: StateFlow<List<MediaItem>> = _mediaList.asStateFlow()

    private val _sourcePlaylists = MutableStateFlow<List<SourcePlaylist>>(emptyList())
    val sourcePlaylists: StateFlow<List<SourcePlaylist>> = _sourcePlaylists.asStateFlow()

    private val _isServerUnlocked = MutableStateFlow(false)
    val isServerUnlocked: StateFlow<Boolean> = _isServerUnlocked.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow("ALL") // "ALL", "LINK", "FILE", "SERVER"
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

    private val _selectedSort = MutableStateFlow("RECENT")
    val selectedSort: StateFlow<String> = _selectedSort.asStateFlow()

    // Currently playing media
    private val _playbackPreparationMessage = MutableStateFlow<String?>(null)
    val playbackPreparationMessage: StateFlow<String?> = _playbackPreparationMessage.asStateFlow()

    /**
     * Opens the player shell immediately while a CloudStream provider resolves a real stream URL.
     * The temporary item is replaced by the stable history item once a playable URL is available.
     */
    fun beginCloudStreamPlaybackPreparation(
        contentUrl: String,
        title: String,
        posterUrl: String?,
        category: String = "CloudStream • Film"
    ) {
        player.clearError()
        player.stop()
        _playbackPreparationMessage.value = "Menghubungkan ke sumber video..."
        _currentlyPlaying.value = MediaItem(
            id = "cloudstream-preparing:${UUID.randomUUID()}",
            profileId = _activeProfile.value?.id ?: "cloudstream-preparing",
            title = title.ifBlank { "CloudStream" },
            url = contentUrl,
            thumbnailUrl = posterUrl.orEmpty(),
            type = "VIDEO",
            sourceType = "CLOUDSTREAM",
            category = category
        )
    }

    fun cancelCloudStreamPlaybackPreparation(message: String) {
        if (_currentlyPlaying.value?.id?.startsWith("cloudstream-preparing:") == true) {
            _currentlyPlaying.value = null
        }
        _playbackPreparationMessage.value = null
        showStatus(message)
    }
    private val _currentlyPlaying = MutableStateFlow<MediaItem?>(null)
    val currentlyPlaying: StateFlow<MediaItem?> = _currentlyPlaying.asStateFlow()

    // A CloudStream catalogue card opens its provider detail page before playback.
    private val _pendingCloudStreamDetail = MutableStateFlow<MediaItem?>(null)
    val pendingCloudStreamDetail: StateFlow<MediaItem?> = _pendingCloudStreamDetail.asStateFlow()
    // A monotonically increasing token makes repeated taps on the same card observable.
    private val _cloudStreamDetailRequestId = MutableStateFlow(0L)
    val cloudStreamDetailRequestId: StateFlow<Long> = _cloudStreamDetailRequestId.asStateFlow()

    fun requestCloudStreamDetail(item: MediaItem) {
        if (!item.sourceType.equals("CLOUDSTREAM", ignoreCase = true)) {
            Log.w("NSiO.CloudStreamDetail", "Rejected detail request: item=" + item.id + ", source=" + item.sourceType)
            showStatus("Item ini bukan konten CloudStream.")
            return
        }
        Log.i("NSiO.CloudStreamDetail", "Poster tapped: item=" + item.id + ", plugin=" + item.pluginId + ", provider=" + item.extraInfo + ", url=" + item.url)
        _pendingCloudStreamDetail.value = item
        _cloudStreamDetailRequestId.value = _cloudStreamDetailRequestId.value + 1L
    }

    fun clearPendingCloudStreamDetail(itemId: String) {
        if (_pendingCloudStreamDetail.value?.id == itemId) {
            _pendingCloudStreamDetail.value = null
        }
    }

    // Toast / Feedback message
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Set when device-bound encrypted PIN data cannot be recovered and must be reset locally.
    private var didRecoverLocalPin = false

    // Keep only one database observer per active profile.
    private var mediaObserverJob: Job? = null
    private var playbackPersistenceJob: Job? = null
    private var playlistObserverJob: Job? = null
    private var sourceRefreshJob: Job? = null
    private var cloudStreamHomeRefreshJob: Job? = null
    private var cloudStreamHomeLoadMoreJob: Job? = null
    private val cloudStreamHomePages = mutableMapOf<String, Int>()
    private val cloudStreamHomeHasNext = mutableMapOf<String, Boolean>()
    // Pagination state is tracked per plugin, provider, and declared main-page request.
    private val cloudStreamHomeSectionPages = mutableMapOf<String, Int>()
    private val cloudStreamHomeSectionHasNext = mutableMapOf<String, Boolean>()

    private fun cloudStreamSectionStateKey(
        pluginId: String,
        section: com.example.data.plugin.CloudStreamHomeSection
    ): String = "$pluginId|${section.providerName}|${section.requestName}"

    private fun cloudStreamSectionPageRequests(pluginId: String): Map<String, Int> {
        val prefix = "$pluginId|"
        return cloudStreamHomeSectionPages.keys
            .filter { it.startsWith(prefix) }
            .associate { stateKey ->
                val requestKey = stateKey.removePrefix(prefix)
                val currentPage = cloudStreamHomeSectionPages[stateKey] ?: 1
                requestKey to if (cloudStreamHomeSectionHasNext[stateKey] == true) currentPage + 1 else 0
            }
    }

    private fun updateCloudStreamSectionPagination(
        pluginId: String,
        sections: List<com.example.data.plugin.CloudStreamHomeSection>,
        requestedPages: Map<String, Int>? = null,
        reset: Boolean = false
    ) {
        val prefix = "$pluginId|"
        if (reset) {
            cloudStreamHomeSectionPages.keys.filter { it.startsWith(prefix) }.forEach { cloudStreamHomeSectionPages.remove(it) }
            cloudStreamHomeSectionHasNext.keys.filter { it.startsWith(prefix) }.forEach { cloudStreamHomeSectionHasNext.remove(it) }
        }
        sections.forEach { section ->
            val key = cloudStreamSectionStateKey(pluginId, section)
            val requestKey = "${section.providerName}|${section.requestName}"
            val page = requestedPages?.get(requestKey) ?: 1
            if (page > 0) cloudStreamHomeSectionPages[key] = page
            cloudStreamHomeSectionHasNext[key] = section.hasNext
        }
        val hasNext = cloudStreamHomeSectionHasNext
            .filterKeys { it.startsWith(prefix) }
            .values.any { it }
        cloudStreamHomeHasNext[pluginId] = hasNext
        cloudStreamHomePages[pluginId] = cloudStreamHomeSectionPages
            .filterKeys { it.startsWith(prefix) }
            .values.maxOrNull() ?: 1
    }
    private val cloudStreamAutoRetryJobs = mutableMapOf<String, Job>()
    private val _isLoadingMoreCloudStreamHome = MutableStateFlow(false)
    val isLoadingMoreCloudStreamHome: StateFlow<Boolean> = _isLoadingMoreCloudStreamHome.asStateFlow()
    private val _cloudStreamSourceHealth = MutableStateFlow<Map<String, CloudStreamSourceHealth>>(emptyMap())
    val cloudStreamSourceHealth: StateFlow<Map<String, CloudStreamSourceHealth>> = _cloudStreamSourceHealth.asStateFlow()
    private val _cloudStreamPluginOrder = MutableStateFlow<List<String>>(emptyList())
    val cloudStreamPluginOrder: StateFlow<List<String>> = _cloudStreamPluginOrder.asStateFlow()
    private val _refreshingCloudStreamPluginId = MutableStateFlow<String?>(null)
    val refreshingCloudStreamPluginId: StateFlow<String?> = _refreshingCloudStreamPluginId.asStateFlow()

    private fun cloudStreamOrderKey(profileId: String) = "plugin_order_$profileId"

    private fun loadCloudStreamPluginOrder(profileId: String): List<String> {
        val raw = cloudStreamOrderPreferences.getString(cloudStreamOrderKey(profileId), null) ?: return emptyList()
        return runCatching {
            val json = org.json.JSONArray(raw)
            (0 until json.length()).mapNotNull { index -> json.optString(index).takeIf { it.isNotBlank() } }.distinct()
        }.getOrDefault(emptyList())
    }

    private fun prioritizeCloudStreamEntries(entries: List<com.example.data.plugin.CloudStreamPluginEntry>): List<com.example.data.plugin.CloudStreamPluginEntry> {
        val order = _cloudStreamPluginOrder.value.withIndex().associate { it.value to it.index }
        return entries.sortedWith(compareBy<com.example.data.plugin.CloudStreamPluginEntry> { entry ->
            order[pluginManager.cloudStreamPluginId(entry)] ?: Int.MAX_VALUE
        }.thenBy { it.name.lowercase() })
    }

    fun moveCloudStreamPluginOrder(pluginId: String, direction: Int, visiblePluginIds: List<String>) {
        val profileId = _activeProfile.value?.id ?: return
        if (direction == 0 || pluginId !in visiblePluginIds) return
        val savedOrder = _cloudStreamPluginOrder.value
        val current = (savedOrder.filter { it in visiblePluginIds } +
            visiblePluginIds.filter { it !in savedOrder } +
            savedOrder.filter { it !in visiblePluginIds }).distinct().toMutableList()
        val from = current.indexOf(pluginId)
        val to = (from + direction.coerceIn(-1, 1)).coerceIn(0, visiblePluginIds.size - 1)
        if (from == to) return
        val targetId = visiblePluginIds.getOrNull(to) ?: return
        val targetIndex = current.indexOf(targetId)
        if (targetIndex < 0) return
        val moved = current.removeAt(from)
        current.add(targetIndex.coerceIn(0, current.size), moved)
        _cloudStreamPluginOrder.value = current
        val json = org.json.JSONArray().apply { current.forEach { put(it) } }
        cloudStreamOrderPreferences.edit().putString(cloudStreamOrderKey(profileId), json.toString()).apply()
    }

    private fun loadCloudStreamSourceHealth(profileId: String): Map<String, CloudStreamSourceHealth> {
        val prefix = "$profileId|"
        return cloudStreamHealthPreferences.all.entries
            .filter { it.key.startsWith(prefix) && it.value is String }
            .mapNotNull { (key, raw) ->
                runCatching {
                    val pluginId = key.removePrefix(prefix)
                    val values = org.json.JSONObject(raw as String)
                    pluginId to CloudStreamSourceHealth(
                        lastSuccessAt = values.optLong("lastSuccessAt", 0L),
                        lastError = values.optString("lastError").takeIf { it.isNotBlank() && it != "null" },
                        hasNext = if (values.has("hasNext") && !values.isNull("hasNext")) values.optBoolean("hasNext") else null,
                        consecutiveFailures = values.optInt("consecutiveFailures", 0).coerceAtLeast(0),
                        lastAttemptAt = values.optLong("lastAttemptAt", 0L),
                        nextAutoRetryAt = values.optLong("nextAutoRetryAt", 0L),
                        lastResponseTimeMs = values.optLong("lastResponseTimeMs", 0L).coerceAtLeast(0L),
                        history = runCatching {
                            val events = values.optJSONArray("history") ?: org.json.JSONArray()
                            (0 until events.length()).mapNotNull { index ->
                                val event = events.optJSONObject(index) ?: return@mapNotNull null
                                CloudStreamHealthEvent(
                                    timestamp = event.optLong("timestamp", 0L),
                                    succeeded = event.optBoolean("succeeded", false),
                                    responseTimeMs = event.optLong("responseTimeMs", 0L).coerceAtLeast(0L),
                                    message = event.optString("message").takeIf { it.isNotBlank() && it != "null" }
                                )
                            }.takeLast(10)
                        }.getOrDefault(emptyList())
                    )
                }.getOrNull()
            }.toMap()
    }

    private fun updateCloudStreamSourceHealth(
        profileId: String,
        pluginId: String,
        hasNext: Boolean? = null,
        error: String? = null,
        succeeded: Boolean = false,
        responseTimeMs: Long? = null
    ) {
        if (_activeProfile.value?.id != profileId) return
        val previous = _cloudStreamSourceHealth.value[pluginId] ?: CloudStreamSourceHealth()
        val now = System.currentTimeMillis()
        val failureCount = if (succeeded) 0 else previous.consecutiveFailures + 1
        val retryDelay = if (succeeded || failureCount < 2) 0L else
            (60_000L * (1L shl (failureCount - 2).coerceIn(0, 5))).coerceAtMost(30 * 60_000L)
        val event = CloudStreamHealthEvent(
            timestamp = now,
            succeeded = succeeded,
            responseTimeMs = responseTimeMs?.coerceAtLeast(0L) ?: 0L,
            message = if (succeeded) "Berhasil" else (error ?: previous.lastError ?: "Gagal memuat sumber")
        )
        val updated = previous.copy(
            history = (previous.history + event).takeLast(10),
            lastSuccessAt = if (succeeded) now else previous.lastSuccessAt,
            lastError = if (succeeded) null else error ?: previous.lastError,
            hasNext = hasNext ?: previous.hasNext,
            consecutiveFailures = failureCount,
            lastAttemptAt = now,
            nextAutoRetryAt = if (succeeded) 0L else if (retryDelay > 0L) now + retryDelay else 0L,
            lastResponseTimeMs = responseTimeMs?.coerceAtLeast(0L) ?: previous.lastResponseTimeMs
        )
        _cloudStreamSourceHealth.value = _cloudStreamSourceHealth.value + (pluginId to updated)
        val values = org.json.JSONObject()
            .put("lastSuccessAt", updated.lastSuccessAt)
            .put("lastError", updated.lastError ?: org.json.JSONObject.NULL)
            .put("hasNext", updated.hasNext ?: org.json.JSONObject.NULL)
            .put("consecutiveFailures", updated.consecutiveFailures)
            .put("lastAttemptAt", updated.lastAttemptAt)
            .put("nextAutoRetryAt", updated.nextAutoRetryAt)
            .put("lastResponseTimeMs", updated.lastResponseTimeMs)
            .put("history", org.json.JSONArray().apply {
                updated.history.forEach { event ->
                    put(org.json.JSONObject()
                        .put("timestamp", event.timestamp)
                        .put("succeeded", event.succeeded)
                        .put("responseTimeMs", event.responseTimeMs)
                        .put("message", event.message ?: org.json.JSONObject.NULL))
                }
            })
        cloudStreamHealthPreferences.edit()
            .putString("$profileId|$pluginId", values.toString())
            .apply()
        val retryKey = "$profileId|$pluginId"
        if (succeeded) {
            cloudStreamAutoRetryJobs.remove(retryKey)?.cancel()
        } else if (updated.consecutiveFailures >= 2) {
            scheduleCloudStreamAutoRetry(profileId, pluginId, updated.nextAutoRetryAt)
        }
    }


    /**
     * Retry a repeatedly failing source with exponential backoff.
     * Retries are profile-scoped and never run concurrently for the same provider.
     */
    private fun scheduleCloudStreamAutoRetry(profileId: String, pluginId: String, retryAt: Long) {
        val retryKey = "$profileId|$pluginId"
        if (_selectedFilter.value != "CLOUDSTREAM_PLUGIN:$pluginId") return
        if (retryAt <= 0L || cloudStreamAutoRetryJobs[retryKey]?.isActive == true) return
        cloudStreamAutoRetryJobs[retryKey] = viewModelScope.launch {
            try {
                val waitMs = (retryAt - System.currentTimeMillis()).coerceAtLeast(0L)
                if (waitMs > 0L) kotlinx.coroutines.delay(waitMs)
                while (_activeProfile.value?.id == profileId &&
                    (cloudStreamHomeRefreshJob?.isActive == true ||
                        cloudStreamHomeLoadMoreJob?.isActive == true ||
                        _refreshingCloudStreamPluginId.value != null)) {
                    kotlinx.coroutines.delay(30_000L)
                }
                if (_activeProfile.value?.id != profileId || _selectedFilter.value != "CLOUDSTREAM_PLUGIN:$pluginId") return@launch
                val health = _cloudStreamSourceHealth.value[pluginId] ?: return@launch
                if (health.consecutiveFailures < 2 || health.nextAutoRetryAt > System.currentTimeMillis()) return@launch
                refreshCloudStreamPluginOnHome(pluginId)
            } finally {
                cloudStreamAutoRetryJobs.remove(retryKey)
            }
        }
    }

    private val _playbackQueue = MutableStateFlow<List<MediaItem>>(emptyList())
    val playbackQueue: StateFlow<List<MediaItem>> = _playbackQueue.asStateFlow()
    private var playbackQueueBase: List<MediaItem> = emptyList()
    private var playbackQueueOrder: List<MediaItem> = emptyList()
    private var playbackQueueIndex: Int = -1
    private var handledPlaybackEnd = false
    @Volatile private var tikTodModeActive = false

    /** TikTod owns feed advancement and must not inherit the regular player's Repeat mode. */
    fun setTikTodModeActive(active: Boolean) {
        tikTodModeActive = active
    }

    private fun syncPlaybackQueue(current: MediaItem, forceRebuild: Boolean = false) {
        val drivePlaylistIds = _sourcePlaylists.value
            .filter { it.sourceType.equals("DRIVE", ignoreCase = true) }
            .mapTo(mutableSetOf()) { it.id }
        val base = com.example.player.PlaybackQueuePolicy
            .filterForCurrentSource(_mediaList.value, current, drivePlaylistIds)
        val baseChanged = base.map { it.id } != playbackQueueBase.map { it.id }
        if (forceRebuild || baseChanged || playbackQueueOrder.none { it.id == current.id }) {
            playbackQueueBase = base
            playbackQueueOrder = com.example.player.PlaybackQueuePolicy.build(
                base, current.id, player.playerState.value.shuffleEnabled
            )
        }
        playbackQueueIndex = playbackQueueOrder.indexOfFirst { it.id == current.id }
        _playbackQueue.value = playbackQueueOrder
    }

    fun togglePlaybackShuffle() {
        player.toggleShuffle()
        val current = _currentlyPlaying.value ?: return
        syncPlaybackQueue(current, forceRebuild = true)
    }

    fun canSkipPlayback(direction: Int): Boolean {
        val current = _currentlyPlaying.value ?: return false
        syncPlaybackQueue(current)
        return com.example.player.PlaybackQueuePolicy.adjacentIndex(
            playbackQueueOrder.size,
            playbackQueueIndex,
            direction,
            player.playerState.value.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ALL
        ) != null
    }

    fun skipPlayback(direction: Int): Boolean {
        val current = _currentlyPlaying.value ?: return false
        syncPlaybackQueue(current)
        val targetIndex = com.example.player.PlaybackQueuePolicy.adjacentIndex(
            playbackQueueOrder.size,
            playbackQueueIndex,
            direction,
            player.playerState.value.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ALL
        ) ?: return false
        val target = playbackQueueOrder.getOrNull(targetIndex) ?: return false
        playMedia(target)
        return true
    }

    private fun handlePlaybackEnded() {
        val current = _currentlyPlaying.value ?: return
        when (player.playerState.value.repeatMode) {
            androidx.media3.common.Player.REPEAT_MODE_ONE -> playMedia(current)
            else -> skipPlayback(1)
        }
    }

    init {
        loadInitialData()
        observeAuthForCloudSync()
        viewModelScope.launch {
            player.playerState.collect { state ->
                if (state.playbackEnded && !handledPlaybackEnd) {
                    handledPlaybackEnd = true
                    if (!tikTodModeActive) handlePlaybackEnded()
                } else if (!state.playbackEnded) {
                    handledPlaybackEnd = false
                }
            }
        }
    }

    private fun observeAuthForCloudSync() {
        viewModelScope.launch {
            authState.collect { state ->
                if (state is AuthState.Authenticated) {
                    val user = state.user
                    try {
                        cloudRepository.saveUserProfile(
                            userId = user.uid,
                            email = user.email,
                            displayName = user.displayName,
                            photoUrl = user.photoUrl?.toString()
                        )
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val existingProfiles = profileDao.getAllProfilesList()
            if (existingProfiles.isEmpty()) {
                val defaultProfile = Profile(
                    id = "profile_utama",
                    name = "Utama",
                    avatarEmoji = "👑",
                    isPinEnabled = false,
                    isDefault = true
                )
                profileDao.insertProfile(defaultProfile)
                val defaultSettings = ProfileSettings(profileId = defaultProfile.id)
                profileDao.insertSettings(defaultSettings.copy(serverPin = PinVault.encrypt(getApplication(), defaultSettings.serverPin)))

                _profiles.value = listOf(defaultProfile)
                _activeProfile.value = defaultProfile
                _settings.value = defaultSettings
            } else {
                val decodedProfiles = existingProfiles.map { stored ->
                    val wasEncrypted = PinVault.isEncrypted(stored.pin)
                    val pin = PinVault.decrypt(getApplication(), stored.pin)
                    val lostProtectedPin = stored.isPinEnabled && wasEncrypted && pin.isBlank()
                    when {
                        lostProtectedPin -> {
                            // A device-bound Keystore key cannot be restored from backup.
                            // Disable only the unrecoverable local PIN instead of locking the profile.
                            profileDao.updateProfile(stored.copy(pin = "", isPinEnabled = false))
                            didRecoverLocalPin = true
                        }
                        stored.pin.isNotBlank() && !wasEncrypted -> {
                            profileDao.updateProfile(stored.copy(pin = PinVault.encrypt(getApplication(), pin)))
                        }
                    }
                    stored.copy(pin = if (lostProtectedPin) "" else pin, isPinEnabled = if (lostProtectedPin) false else stored.isPinEnabled)
                }
                _profiles.value = decodedProfiles
                val active = decodedProfiles.firstOrNull { it.isDefault } ?: decodedProfiles.first()
                _activeProfile.value = active
                _settings.value = loadSettingsForProfile(active.id)
                _cloudStreamSourceHealth.value = loadCloudStreamSourceHealth(active.id)
                _cloudStreamPluginOrder.value = loadCloudStreamPluginOrder(active.id)
                _cloudStreamSourceHealth.value.forEach { (pluginId, health) ->
                    if (health.consecutiveFailures >= 2) scheduleCloudStreamAutoRetry(active.id, pluginId, health.nextAutoRetryAt)
                }
                _selectedFilter.value = loadSelectedFilter(active.id)
                _selectedSort.value = loadSelectedSort(active.id)
            }

            if (didRecoverLocalPin) {
                showStatus("Kunci keamanan perangkat berubah. PIN lokal yang tidak dapat dipulihkan telah direset; silakan atur PIN baru di Pengaturan.")
            }

            observeActiveProfileMedia()
            observeSourcePlaylists()
            viewModelScope.launch {
                kotlinx.coroutines.delay(1_000)
                refreshAllSourcePlaylists()
            }
        }
    }

    private fun observeActiveProfileMedia() {
        val current = _activeProfile.value ?: return
        mediaObserverJob?.cancel()
        _mediaList.value = emptyList()
        mediaObserverJob = viewModelScope.launch {
            mediaDao.getMediaForProfile(current.id).collect { list ->
                // Ignore late emissions from a profile that is no longer active.
                if (_activeProfile.value?.id == current.id) {
                    _mediaList.value = list
                    _currentlyPlaying.value?.let { syncPlaybackQueue(it) }
                }
            }
        }
    }

    private fun observeSourcePlaylists() {
        val current = _activeProfile.value ?: return
        playlistObserverJob?.cancel()
        _sourcePlaylists.value = emptyList()
        playlistObserverJob = viewModelScope.launch {
            sourcePlaylistDao.observeForProfile(current.id).collect { playlists ->
                if (_activeProfile.value?.id == current.id) {
                    _sourcePlaylists.value = playlists
                    _currentlyPlaying.value?.let { syncPlaybackQueue(it) }
                }
            }
        }
    }

    fun switchProfile(profile: Profile) {
        viewModelScope.launch {
            if (_activeProfile.value?.id == profile.id) return@launch
            _activeProfile.value = profile
            _isServerUnlocked.value = false
            _settings.value = loadSettingsForProfile(profile.id)
            _cloudStreamSourceHealth.value = loadCloudStreamSourceHealth(profile.id)
            _cloudStreamPluginOrder.value = loadCloudStreamPluginOrder(profile.id)
            cloudStreamAutoRetryJobs.values.forEach { it.cancel() }
            cloudStreamAutoRetryJobs.clear()
            _cloudStreamSourceHealth.value.forEach { (pluginId, health) ->
                if (health.consecutiveFailures >= 2) scheduleCloudStreamAutoRetry(profile.id, pluginId, health.nextAutoRetryAt)
            }
            _selectedFilter.value = loadSelectedFilter(profile.id)
            _selectedSort.value = loadSelectedSort(profile.id)
            observeActiveProfileMedia()
            observeSourcePlaylists()
            showStatus("Beralih ke profil '${profile.name}'")
        }
    }

    fun addProfile(name: String, avatar: String, pin: String, isPinEnabled: Boolean) {
        if (name.isBlank() || (isPinEnabled && !pin.matches(Regex("\\d{4}")))) {
            showStatus(if (name.isBlank()) "Nama profil wajib diisi" else "PIN profil harus tepat 4 digit")
            return
        }
        viewModelScope.launch {
            val newProfile = Profile(
                id = UUID.randomUUID().toString(),
                name = name,
                avatarEmoji = avatar.ifEmpty { "👤" },
                pin = pin,
                isPinEnabled = isPinEnabled,
                isDefault = false
            )
            profileDao.insertProfile(newProfile.copy(pin = PinVault.encrypt(getApplication(), newProfile.pin)))
            val newSettings = ProfileSettings(profileId = newProfile.id)
            profileDao.insertSettings(newSettings.copy(serverPin = PinVault.encrypt(getApplication(), newSettings.serverPin)))
            _profiles.value = profileDao.getAllProfilesList().map { it.copy(pin = PinVault.decrypt(getApplication(), it.pin)) }
            switchProfile(newProfile)
        }
    }

    fun updateProfilePin(profile: Profile, newPin: String) {
        if (!newPin.matches(Regex("\\d{4}"))) {
            showStatus("PIN harus tepat 4 digit angka")
            return
        }
        viewModelScope.launch {
            val latest = profileDao.getProfileById(profile.id) ?: run {
                showStatus("Profil tidak ditemukan")
                return@launch
            }
            val updated = latest.copy(pin = PinVault.encrypt(getApplication(), newPin), isPinEnabled = true)
            profileDao.updateProfile(updated)
            val decodedUpdated = updated.copy(pin = newPin)
            _profiles.value = profileDao.getAllProfilesList().map { it.copy(pin = PinVault.decrypt(getApplication(), it.pin)) }
            if (_activeProfile.value?.id == decodedUpdated.id) _activeProfile.value = decodedUpdated
            showStatus("PIN profil berhasil diperbarui")
        }
    }

    fun disableProfilePin(profile: Profile) {
        viewModelScope.launch {
            val latest = profileDao.getProfileById(profile.id) ?: run {
                showStatus("Profil tidak ditemukan")
                return@launch
            }
            profileDao.updateProfile(latest.copy(pin = "", isPinEnabled = false))
            _profiles.value = profileDao.getAllProfilesList().map {
                it.copy(pin = PinVault.decrypt(getApplication(), it.pin))
            }
            if (_activeProfile.value?.id == latest.id) {
                _activeProfile.value = latest.copy(pin = "", isPinEnabled = false)
            }
            showStatus("PIN profil '${latest.name}' berhasil dinonaktifkan")
        }
    }

    private suspend fun loadSettingsForProfile(profileId: String): ProfileSettings {
        val stored = profileDao.getSettings(profileId) ?: ProfileSettings(profileId = profileId)
        val wasEncrypted = PinVault.isEncrypted(stored.serverPin)
        val plainPin = PinVault.decrypt(getApplication(), stored.serverPin)
        if (stored.serverPin.isNotBlank() && wasEncrypted && plainPin.isBlank()) {
            // The previous Keystore key is gone: generate a fresh device-local server PIN.
            val replacementPin = ProfileSettings(profileId = profileId).serverPin
            profileDao.insertSettings(stored.copy(serverPin = PinVault.encrypt(getApplication(), replacementPin)))
            didRecoverLocalPin = true
            return stored.copy(serverPin = replacementPin)
        }
        val effectivePin = plainPin.ifBlank { "123456" }
        if (stored.serverPin.isBlank() || !wasEncrypted) {
            profileDao.insertSettings(stored.copy(serverPin = PinVault.encrypt(getApplication(), effectivePin)))
        }
        return stored.copy(serverPin = effectivePin)
    }

    fun updateSettings(newSettings: ProfileSettings) {
        viewModelScope.launch {
            profileDao.insertSettings(newSettings.copy(serverPin = PinVault.encrypt(getApplication(), newSettings.serverPin)))
            _settings.value = newSettings
            showStatus("Pengaturan berhasil disimpan")
        }
    }

    fun toggleDisplayMode() {
        val nextMode = when (_settings.value.displayMode) {
            "TV" -> "HP"
            "HP" -> "AUTO"
            else -> "TV"
        }
        val updated = _settings.value.copy(displayMode = nextMode)
        updateSettings(updated)
        showStatus("Mode Tampilan: $nextMode")
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private fun filterPreferenceKey(profileId: String) = "home_filter_$profileId"

    private fun loadSelectedFilter(profileId: String): String =
        catalogPreferences.getString(filterPreferenceKey(profileId), "ALL") ?: "ALL"

    private fun sortPreferenceKey(profileId: String) = "home_sort_$profileId"

    private fun loadSelectedSort(profileId: String): String =
        catalogPreferences.getString(sortPreferenceKey(profileId), "RECENT") ?: "RECENT"

    fun selectSort(sort: String) {
        val supported = setOf("RECENT", "TITLE", "CONTINUE", "HISTORY", "FAVORITES")
        if (sort !in supported) return
        _selectedSort.value = sort
        _activeProfile.value?.id?.let { profileId ->
            catalogPreferences.edit().putString(sortPreferenceKey(profileId), sort).apply()
        }
    }

    fun selectFilter(filter: String) {
        _selectedFilter.value = filter
        _activeProfile.value?.id?.let { profileId ->
            catalogPreferences.edit().putString(filterPreferenceKey(profileId), filter).apply()
        }
    }

    fun unlockServerAccess() {
        _isServerUnlocked.value = true
    }

    // --- Firebase Auth & Cloud Firestore Operations ---
    fun signInWithGoogle() {
        viewModelScope.launch {
            showStatus("Menghubungkan ke Google...")
            val result = authManager.signInWithGoogle()
            result.onSuccess { user ->
                showStatus("Selamat datang, ${user.displayName ?: user.email ?: "Pengguna"}")
                // Auto sync
                syncToCloud()
            }.onFailure { e ->
                showStatus("Gagal login: ${e.localizedMessage ?: "Coba lagi"}")
            }
        }
    }

    fun signOut() {
        authManager.signOut()
        showStatus("Berhasil keluar dari akun")
    }

    fun syncToCloud() {
        val user = authManager.currentUser ?: run {
            showStatus("Silakan login Google terlebih dahulu untuk sinkronisasi cloud")
            return
        }
        viewModelScope.launch {
            _isCloudSyncing.value = true
            try {
                cloudRepository.saveUserProfile(
                    userId = user.uid,
                    email = user.email,
                    displayName = user.displayName,
                    photoUrl = user.photoUrl?.toString()
                )
                val allProfiles = profileDao.getAllProfilesList()
                val allSettings = profileDao.getAllSettingsList()
                cloudRepository.syncProfileMetadata(user.uid, allProfiles, allSettings)
                var totalSynced = 0
                allProfiles.forEach { profile ->
                    val localPlaylists = sourcePlaylistDao.getForProfile(profile.id)
                    cloudRepository.syncSourcePlaylists(user.uid, profile.id, localPlaylists)
                    val localItems = mediaDao.getMediaListForProfile(profile.id)
                    totalSynced += cloudRepository.syncLocalListToCloud(user.uid, profile.id, localItems)
                }
                showStatus("$totalSynced item dan metadata ${allProfiles.size} profil berhasil disinkronkan ke Cloud Firestore!")
            } catch (e: Exception) {
                showStatus("Gagal sinkronisasi: ${e.localizedMessage ?: "periksa koneksi"}")
            } finally {
                _isCloudSyncing.value = false
            }
        }
    }

    fun restoreFromCloud() {
        val user = authManager.currentUser ?: run {
            showStatus("Silakan login Google terlebih dahulu untuk memulihkan dari cloud")
            return
        }
        viewModelScope.launch {
            _isCloudSyncing.value = true
            try {
                // Restore portable profile metadata first so media never points
                // to profiles that do not exist on this device.
                val (cloudProfiles, cloudSettings) = cloudRepository.fetchProfileMetadata(user.uid)
                val cloudItems = cloudRepository.fetchMediaItems(user.uid)
                val cloudPlaylists = cloudRepository.fetchSourcePlaylists(user.uid)
                val profileIdsInCloud = cloudProfiles.mapTo(mutableSetOf()) { it.id }
                val legacyProfileIds = (
                    cloudItems.map { it.profileId } +
                        cloudPlaylists.map { it.profileId } +
                        cloudSettings.map { it.profileId }
                    ).distinct()
                val legacyProfiles = legacyProfileIds
                    .filter { it !in profileIdsInCloud }
                    .map { profileId ->
                        Profile(
                            id = profileId,
                            name = if (profileId == "profile_utama") "Utama" else "Profil dipulihkan",
                            isDefault = profileId == "profile_utama",
                            isPinEnabled = false
                        )
                    }
                val profilesToRestore = (cloudProfiles + legacyProfiles).distinctBy { it.id }
                val restoredItems = if (cloudItems.isEmpty() && profilesToRestore.isEmpty() && cloudPlaylists.isEmpty()) {
                    emptyList()
                } else {
                    database.withTransaction {
                        // Preserve the device's current default profile. A cloud
                        // restore must never introduce a second default profile.
                        var hasDefaultProfile = profileDao.getAllProfilesList().any { it.isDefault }
                        profilesToRestore.forEach { cloudProfile ->
                            if (profileDao.getProfileById(cloudProfile.id) == null) {
                                val makeDefault = cloudProfile.isDefault && !hasDefaultProfile
                                profileDao.insertProfile(
                                    cloudProfile.copy(
                                        pin = "",
                                        isPinEnabled = false,
                                        isDefault = makeDefault
                                    )
                                )
                                if (makeDefault) hasDefaultProfile = true
                            }
                        }
                        cloudSettings.forEach { cloudSetting ->
                            val localSettings = profileDao.getSettings(cloudSetting.profileId)
                            // Keep the encrypted device-local server PIN; cloud only carries preferences.
                            val serverPin = localSettings?.serverPin
                                ?: PinVault.encrypt(getApplication(), cloudSetting.serverPin)
                            profileDao.insertSettings(cloudSetting.copy(serverPin = serverPin))
                        }
                        cloudPlaylists.forEach { cloudPlaylist ->
                            val existing = sourcePlaylistDao.findByUrl(cloudPlaylist.profileId, cloudPlaylist.url)
                            if (existing == null) {
                                sourcePlaylistDao.insert(cloudPlaylist)
                            } else {
                                // Keep the local user-edited title; only reconcile metadata.
                                sourcePlaylistDao.insert(
                                    existing.copy(
                                        itemCount = cloudPlaylist.itemCount,
                                        updatedAt = maxOf(existing.updatedAt, cloudPlaylist.updatedAt)
                                    )
                                )
                            }
                        }
                        val normalized = restoreSourcePlaylistsForItems(cloudItems)
                        if (normalized.isNotEmpty()) mediaDao.insertAll(normalized)
                        normalized
                    }
                }

                if (cloudItems.isEmpty() && profilesToRestore.isEmpty() && cloudPlaylists.isEmpty()) {
                    showStatus("Belum ada data profil atau media di Cloud")
                } else {
                    _profiles.value = profileDao.getAllProfilesList().map { stored ->
                        stored.copy(pin = PinVault.decrypt(getApplication(), stored.pin))
                    }
                    _activeProfile.value?.let { active ->
                        _settings.value = loadSettingsForProfile(active.id)
                    }
                    showStatus(
                        "${profilesToRestore.size} profil, ${cloudPlaylists.size} playlist, dan ${restoredItems.size} item berhasil dipulihkan. " +
                            "PIN profil dan PIN server tetap aman dan tidak dipindahkan antarperangkat."
                    )
                }
            } catch (e: Exception) {
                showStatus("Gagal memulihkan: ${e.localizedMessage ?: "periksa koneksi dan coba lagi"}")
            } finally {
                _isCloudSyncing.value = false
            }
        }
    }

    private suspend fun restoreSourcePlaylistsForItems(items: List<MediaItem>): List<MediaItem> {
        val normalized = items.toMutableList()
        items.filter { it.sourceUrl.isNotBlank() }
            .groupBy { Triple(it.profileId, it.playlistId.ifBlank { it.sourceUrl }, it.sourceUrl) }
            .values.forEach { group ->
                val first = group.first()
                val existing = sourcePlaylistDao.findByUrl(first.profileId, first.sourceUrl)
                val playlistId = existing?.id ?: first.playlistId.ifBlank { UUID.randomUUID().toString() }
                val playlist = existing ?: SourcePlaylist(
                    id = playlistId,
                    profileId = first.profileId,
                    name = first.sourceUrl.substringAfter("//").substringBefore("/").ifBlank { first.title },
                    url = first.sourceUrl,
                    sourceType = first.sourceType,
                    itemCount = group.size,
                    createdAt = group.minOfOrNull { it.createdAt } ?: System.currentTimeMillis()
                )
                sourcePlaylistDao.insert(playlist.copy(itemCount = group.size, updatedAt = System.currentTimeMillis()))
                val ids = group.mapTo(hashSetOf()) { it.id }
                normalized.indices.forEach { index ->
                    val item = normalized[index]
                    if (item.id in ids) normalized[index] = item.copy(playlistId = playlistId)
                }
            }
        return normalized
    }

    // --- Link Parsing (B1) ---
    fun parseAndAddLink(url: String, playlistName: String = "", onFinished: (String) -> Unit) {
        val profileId = _activeProfile.value?.id ?: return
        viewModelScope.launch {
            val message = runCatching {
                parseAndAddLinkInternal(url.trim(), profileId, playlistName.trim())
            }.getOrElse { "Error: ${it.localizedMessage ?: "gagal memproses sumber"}" }
            onFinished(message)
        }
    }

    /**
     * Parses and persists one source in the caller's coroutine.
     * Keeping this suspendable lets bulk refresh wait for real completion instead
     * of reporting success while child jobs are still running.
     */
    private fun googleDriveResourceKey(rawUrl: String): String? = runCatching {
        val uri = java.net.URI(rawUrl.trim())
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return@runCatching null
        if (host != "drive.google.com" && host != "docs.google.com") return@runCatching null
        val path = uri.path.orEmpty()
        val query = uri.rawQuery.orEmpty().split('&').mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) null else
                java.net.URLDecoder.decode(part.substring(0, separator), "UTF-8") to
                    java.net.URLDecoder.decode(part.substring(separator + 1), "UTF-8")
        }.toMap()
        val folderId = Regex("/folders/([^/]+)").find(path)?.groupValues?.get(1)
        val fileId = Regex("/file/d/([^/]+)|/document/d/([^/]+)|/spreadsheets/d/([^/]+)|/presentation/d/([^/]+)")
            .find(path)?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }
            ?: query["id"]
        when {
            !folderId.isNullOrBlank() -> "folder:$folderId"
            !fileId.isNullOrBlank() -> "file:$fileId"
            else -> null
        }
    }.getOrNull()

    private fun googleDriveFileId(rawUrl: String): String? =
        googleDriveResourceKey(rawUrl)?.takeIf { it.startsWith("file:") }?.removePrefix("file:")

    private suspend fun parseAndAddLinkInternal(url: String, profileId: String, playlistName: String = ""): String {
        val driveKey = googleDriveResourceKey(url)
        if (driveKey != null) {
            val existingPlaylist = sourcePlaylistDao.getForProfile(profileId)
                .firstOrNull { googleDriveResourceKey(it.url) == driveKey }
            if (existingPlaylist != null) {
                return "Link Google Drive sudah ada di playlist '${existingPlaylist.name}'. Link yang sama tidak ditambahkan ulang."
            }
            if (driveKey.startsWith("file:")) {
                val fileId = driveKey.removePrefix("file:")
                val alreadyImported = mediaDao.getMediaListForProfile(profileId).any { item ->
                    googleDriveFileId(item.url) == fileId ||
                        googleDriveFileId(item.thumbnailUrl) == fileId ||
                        googleDriveFileId(item.sourceUrl) == fileId
                }
                if (alreadyImported) {
                    return "File Google Drive ini sudah ada di daftar media profil ini. Tidak ditambahkan ulang."
                }
            }
        }

        return when (val result = LinkExtractor.parseUrl(url, profileId)) {
            is ParseResult.Single -> {
                val sourceKind = when {
                    result.item.type == "STREAM" -> "STREAM"
                    url.substringBefore('?').endsWith(".json", ignoreCase = true) -> "REPO"
                    MediaSourceClassifier.isGoogleDriveUrl(url) -> "DRIVE"
                    else -> "LINK"
                }
                val playlist = ensureSourcePlaylist(url, playlistName.ifBlank { result.item.title }, sourceKind, profileId, playlistName)
                val item = result.item.copy(
                    sourceType = sourceKind,
                    playlistId = playlist.id,
                    sourceUrl = url
                )
                mediaDao.replacePlaylist(playlist.id, listOf(item))
                sourcePlaylistDao.updateCount(playlist.id, mediaDao.countForPlaylist(playlist.id))
                val cloudWarning = authManager.currentUser?.let { user ->
                    runCatching {
                        sourcePlaylistDao.getForProfile(profileId).firstOrNull { it.id == playlist.id }?.let {
                            cloudRepository.syncSourcePlaylists(user.uid, profileId, listOf(it))
                        }
                        cloudRepository.syncPlaylistToCloud(user.uid, profileId, playlist.id, listOf(item))
                    }.exceptionOrNull()?.let { " Cloud gagal disinkronkan: ${it.localizedMessage ?: "koneksi bermasalah"}" }
                }.orEmpty()
                "Berhasil menambahkan: ${result.item.title}.$cloudWarning"
            }
            is ParseResult.Multiple -> {
                // A successful HTTP response with an empty/invalid parse must not
                // replace the existing playlist with an empty list.
                if (result.items.isEmpty()) {
                    return "Error: sumber berhasil dibuka tetapi tidak berisi media yang valid; data playlist lama dipertahankan."
                }
                val sourceKind = when {
                    result.title.startsWith("M3U", ignoreCase = true) -> "STREAM"
                    MediaSourceClassifier.isGoogleDriveUrl(url) -> "DRIVE"
                    else -> "REPO"
                }
                val existingMedia = if (sourceKind == "DRIVE") mediaDao.getMediaListForProfile(profileId) else emptyList()
                val existingDriveIds = existingMedia.flatMap { item ->
                    listOfNotNull(googleDriveFileId(item.url), googleDriveFileId(item.thumbnailUrl), googleDriveFileId(item.sourceUrl))
                }.toSet()
                val uniqueParsedItems = if (sourceKind == "DRIVE") {
                    result.items.distinctBy { googleDriveFileId(it.url) ?: it.url }
                        .filter { item ->
                            val fileId = googleDriveFileId(item.url)
                            fileId == null || fileId !in existingDriveIds
                        }
                } else result.items
                val skippedDuplicates = result.items.size - uniqueParsedItems.size
                if (uniqueParsedItems.isEmpty()) {
                    return "Semua ${result.items.size} media dari folder Google Drive sudah ada. Tidak ada duplikat yang ditambahkan."
                }
                val playlist = ensureSourcePlaylist(
                    url,
                    playlistNameFromUrl(url, result.title),
                    sourceKind,
                    profileId,
                    playlistName
                )
                val items = uniqueParsedItems.map {
                    it.copy(sourceType = sourceKind, playlistId = playlist.id, sourceUrl = url)
                }
                mediaDao.replacePlaylist(playlist.id, items)
                sourcePlaylistDao.updateCount(playlist.id, mediaDao.countForPlaylist(playlist.id))
                val cloudWarning = authManager.currentUser?.let { user ->
                    runCatching {
                        sourcePlaylistDao.getForProfile(profileId).firstOrNull { it.id == playlist.id }?.let {
                            cloudRepository.syncSourcePlaylists(user.uid, profileId, listOf(it))
                        }
                        cloudRepository.syncPlaylistToCloud(user.uid, profileId, playlist.id, items)
                    }.exceptionOrNull()?.let { " Cloud gagal disinkronkan: ${it.localizedMessage ?: "koneksi bermasalah"}" }
                }.orEmpty()
                "${items.size} item berhasil ditambahkan (${result.title})" +
                    (if (skippedDuplicates > 0) "; $skippedDuplicates media duplikat dilewati." else ".") +
                    cloudWarning
            }
            is ParseResult.Error -> "Error: ${result.message}"
        }
    }

    private suspend fun ensureSourcePlaylist(
        url: String,
        name: String,
        sourceType: String,
        profileId: String,
        preferredName: String = ""
    ): SourcePlaylist {
        val existing = sourcePlaylistDao.findByUrl(profileId, url)
        if (existing != null) {
            val cleanName = preferredName.trim().take(48)
            if (cleanName.isNotEmpty() && cleanName != existing.name) {
                return existing.copy(name = cleanName, updatedAt = System.currentTimeMillis())
                    .also { sourcePlaylistDao.update(it) }
            }
            return existing
        }
        return SourcePlaylist(
            id = UUID.randomUUID().toString(), profileId = profileId,
            name = preferredName.trim().take(48).ifBlank { name.ifBlank { playlistNameFromUrl(url, "Playlist") } }, url = url,
            sourceType = sourceType
        ).also { sourcePlaylistDao.insert(it) }
    }

    private fun playlistNameFromUrl(url: String, fallback: String): String = runCatching {
        java.net.URI(url).host?.removePrefix("www.")?.ifBlank { null } ?: fallback
    }.getOrDefault(fallback)

    fun renameSourcePlaylist(playlist: SourcePlaylist, newName: String) {
        viewModelScope.launch {
            val cleanName = newName.trim().take(48)
            if (cleanName.isNotEmpty()) {
                val updatedPlaylist = playlist.copy(name = cleanName, updatedAt = System.currentTimeMillis())
                sourcePlaylistDao.update(updatedPlaylist)
                val cloudSyncFailed = authManager.currentUser?.let { user ->
                    runCatching {
                        cloudRepository.syncSourcePlaylists(user.uid, updatedPlaylist.profileId, listOf(updatedPlaylist))
                    }.isFailure
                } ?: false
                showStatus(
                    if (cloudSyncFailed) "Nama playlist diubah di perangkat, tetapi sinkronisasi Cloud gagal"
                    else "Nama playlist diubah menjadi '$cleanName'"
                )
            } else {
                showStatus("Nama playlist tidak boleh kosong")
            }
        }
    }

    fun deleteSourcePlaylist(playlist: SourcePlaylist) {
        viewModelScope.launch {
            val profileItems = mediaDao.getMediaListForProfile(playlist.profileId)
                .filter { it.playlistId == playlist.id }
            val user = authManager.currentUser
            val warnings = mutableListOf<String>()
            var cloudMetadataDeleted = false

            if (user != null) {
                cloudMetadataDeleted = runCatching {
                    cloudRepository.deletePlaylistItems(user.uid, playlist.profileId, playlist.id)
                    cloudRepository.deleteSourcePlaylist(user.uid, playlist.id)
                    true
                }.getOrElse {
                    warnings += "metadata cloud gagal dihapus"
                    false
                }
            } else if (profileItems.isNotEmpty()) {
                warnings += "metadata cloud belum dibersihkan karena akun tidak aktif"
            }

            profileItems.asSequence()
                .filter { it.sourceType == "SERVER" && storageRepository.isFirebaseStorageUrl(it.url) }
                .distinctBy { it.url }
                .forEach { item ->
                    val usedElsewhere = mediaDao.countReferencesOutsidePlaylist(item.url, playlist.id) > 0
                    if (usedElsewhere) return@forEach
                    if (!cloudMetadataDeleted) {
                        warnings += "file Storage '${item.title}' dipertahankan agar metadata cloud tidak rusak"
                    } else {
                        runCatching { storageRepository.deleteOwnedMedia(user!!.uid, item.url) }
                            .onFailure { warnings += "file Storage '${item.title}' gagal dihapus" }
                    }
                }

            mediaDao.clearPlaylist(playlist.id)
            sourcePlaylistDao.delete(playlist)
            if (_selectedFilter.value == "PLAYLIST:${playlist.id}") selectFilter("ALL")

            showStatus(
                if (warnings.isEmpty()) "Playlist '${playlist.name}' dan data terkait berhasil dihapus"
                else "Playlist '${playlist.name}' dihapus dari perangkat. Perlu perhatian: ${warnings.distinct().joinToString("; ")}"
            )
        }
    }

    suspend fun persistCloudStreamCatalog(
        entry: com.example.data.plugin.CloudStreamPluginEntry,
        sections: List<com.example.data.plugin.CloudStreamHomeSection>
    ): Int {
        val profileId = _activeProfile.value?.id
            ?: throw IllegalStateException("Profil aktif tidak ditemukan.")
        val pluginId = pluginManager.cloudStreamPluginId(entry)
        val repositoryId = pluginManager.cloudStreamRepositoryId(entry)
        // Keep one media record per provider URL, but retain every home-page section
        // that contains it. This prevents duplicate progress/favorite records while allowing
        // the same title to appear in multiple category rows on Home.
        val fetchedRecords = sections
            .flatMap { section ->
                section.items.map { item -> section to item }
            }
            .groupBy { (section, item) -> "${profileId}|${pluginId}|${item.providerName}|${item.url}" }
            .map { (_, memberships) ->
                val (section, item) = memberships.first()
                val sectionNames = memberships
                    .map { (membershipSection, _) -> membershipSection.sectionName.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                MediaItem(
                    id = MediaRefreshPolicy.cloudStreamId(profileId, pluginId, item.providerName, item.url),
                    profileId = profileId,
                    title = item.title,
                    url = item.url,
                    thumbnailUrl = item.posterUrl.orEmpty(),
                    type = "CLOUDSTREAM",
                    sourceType = "CLOUDSTREAM",
                    category = (sectionNames + listOfNotNull(item.contentType?.takeIf { it.isNotBlank() }) + listOf("language:${entry.language}")).distinct().joinToString(" • ").ifBlank { "CloudStream" },
                    sourceUrl = entry.url,
                    pluginId = pluginId,
                    repositoryId = repositoryId,
                    extraInfo = item.providerName
                )
            }
        if (fetchedRecords.isEmpty()) {
            throw IllegalStateException("Katalog kosong; data terakhir tidak dihapus.")
        }
        val previousRecords = mediaDao.getCloudStreamCatalog(profileId, pluginId)
        val refreshedRecords = MediaRefreshPolicy.mergeCloudStream(
            profileId = profileId,
            pluginId = pluginId,
            incoming = fetchedRecords,
            existing = previousRecords
        )
        // Home catalog can be paginated. Keep cached pages not fetched in this pass,
        // but do not retain legacy-ID rows that were just migrated to profile-scoped IDs.
        val refreshedSourceKeys = refreshedRecords.mapTo(hashSetOf()) {
            "${it.pluginId}|${it.extraInfo}|${it.url}"
        }
        val records = (refreshedRecords + previousRecords.filterNot {
            "${it.pluginId}|${it.extraInfo}|${it.url}" in refreshedSourceKeys
        }).distinctBy { it.id }
        mediaDao.replaceCloudStreamCatalog(profileId, pluginId, records)
        showStatus("Katalog ${entry.name} disimpan: ${records.size} item.")
        return records.size
    }

    /**
     * Loads the next page only for the plugin currently selected in the Home filter.
     * Other installed providers are never queried by this action.
     */
    fun loadMoreCloudStreamCatalogOnHome(pluginId: String) {
        val selectedPluginId = pluginId.trim()
        if (selectedPluginId.isBlank()) return
        if (cloudStreamHomeLoadMoreJob?.isActive == true) return
        if (cloudStreamHomeRefreshJob?.isActive == true) {
            showStatus("Katalog CloudStream sedang diperbarui. Coba lagi sebentar.")
            return
        }
        val profileId = _activeProfile.value?.id ?: return
        val entry = pluginManager.repositories.value
            .asSequence()
            .flatMap { it.plugins.asSequence() }
            .firstOrNull {
                it.status != 0 &&
                    pluginManager.isCloudStreamPluginInstalled(it) &&
                    pluginManager.cloudStreamPluginId(it) == selectedPluginId
            }
        if (entry == null) {
            showStatus("Plugin yang dipilih tidak ditemukan atau belum terpasang.")
            return
        }
        if (cloudStreamHomeHasNext[selectedPluginId] == false) {
            showStatus("Katalog ${entry.name} sudah mencapai halaman terakhir.")
            return
        }
        cloudStreamHomeLoadMoreJob = viewModelScope.launch {
            _isLoadingMoreCloudStreamHome.value = true
            try {
                val nextPage = (cloudStreamHomePages[selectedPluginId] ?: 1) + 1
                val sectionPages = cloudStreamSectionPageRequests(selectedPluginId)
                val requestStartedAt = System.currentTimeMillis()
                runCatching {
                    pluginManager.loadCloudStreamHomePage(
                        entry,
                        nextPage,
                        sectionPages.takeIf { it.isNotEmpty() }
                    )
                }.onSuccess { sections ->
                    if (_activeProfile.value?.id != profileId) return@onSuccess
                    updateCloudStreamSectionPagination(
                        selectedPluginId,
                        sections,
                        requestedPages = sectionPages.takeIf { it.isNotEmpty() }
                    )
                    updateCloudStreamSourceHealth(
                        profileId,
                        selectedPluginId,
                        hasNext = cloudStreamHomeHasNext[selectedPluginId] == true,
                        succeeded = true,
                        responseTimeMs = System.currentTimeMillis() - requestStartedAt
                    )
                    if (sections.isNotEmpty()) {
                        runCatching { persistCloudStreamCatalog(entry, sections) }
                            .onSuccess { showStatus("Katalog ${entry.name} bertambah: $it item.") }
                            .onFailure { showStatus("Halaman ${entry.name} dimuat tetapi gagal disimpan: ${it.message ?: "kesalahan penyimpanan"}") }
                    } else {
                        showStatus("Tidak ada item baru dari katalog ${entry.name}.")
                    }
                }.onFailure { error ->
                    updateCloudStreamSourceHealth(
                        profileId,
                        selectedPluginId,
                        error = error.localizedMessage ?: "Gagal memuat halaman berikutnya",
                        responseTimeMs = System.currentTimeMillis() - requestStartedAt
                    )
                    showStatus("Gagal memuat halaman ${entry.name}; cache lama tetap digunakan.")
                }
            } finally {
                _isLoadingMoreCloudStreamHome.value = false
                cloudStreamHomeLoadMoreJob = null
            }
        }
    }

    /**
     * Retry one failed provider without restarting healthy providers or clearing its cached items.
     */
    fun refreshCloudStreamPluginOnHome(pluginId: String) {
        val profileId = _activeProfile.value?.id ?: return
        if (cloudStreamHomeRefreshJob?.isActive == true || cloudStreamHomeLoadMoreJob?.isActive == true ||
            _refreshingCloudStreamPluginId.value != null) {
            showStatus("Katalog CloudStream sedang diproses. Coba lagi sebentar.")
            return
        }
        val entry = pluginManager.repositories.value
            .flatMap { it.plugins }
            .firstOrNull {
                it.status != 0 &&
                    pluginManager.isCloudStreamPluginInstalled(it) &&
                    pluginManager.cloudStreamPluginId(it) == pluginId
            }
        if (entry == null) {
            showStatus("Plugin tidak ditemukan atau belum terpasang.")
            return
        }
        viewModelScope.launch {
            _refreshingCloudStreamPluginId.value = pluginId
            try {
                val requestStartedAt = System.currentTimeMillis()
                runCatching { pluginManager.loadCloudStreamHomePage(entry, 1) }
                    .onSuccess { sections ->
                        if (_activeProfile.value?.id != profileId) return@onSuccess
                        updateCloudStreamSectionPagination(pluginId, sections, reset = true)
                        updateCloudStreamSourceHealth(profileId, pluginId, hasNext = cloudStreamHomeHasNext[pluginId] == true, succeeded = true, responseTimeMs = System.currentTimeMillis() - requestStartedAt)
                        if (sections.isNotEmpty()) {
                            runCatching { persistCloudStreamCatalog(entry, sections) }
                                .onSuccess { showStatus("Sumber ${entry.name} pulih: $it item diperbarui.") }
                                .onFailure { showStatus("Sumber ${entry.name} berhasil dimuat, tetapi cache gagal disimpan: ${it.localizedMessage ?: "kesalahan penyimpanan"}") }
                        } else {
                            showStatus("Sumber ${entry.name} merespons, tetapi belum memiliki konten.")
                        }
                    }
                    .onFailure { error ->
                        updateCloudStreamSourceHealth(profileId, pluginId, error = error.localizedMessage ?: "Gagal memuat katalog", responseTimeMs = System.currentTimeMillis() - requestStartedAt)
                        showStatus("Sumber ${entry.name} masih bermasalah. Cache lama tetap dipertahankan.")
                    }
            } finally {
                _refreshingCloudStreamPluginId.value = null
            }
        }
    }
    fun refreshRepository(repo: com.example.data.plugin.RepoManifest) {
        viewModelScope.launch {
            runCatching { pluginManager.refreshRepositoryCatalog(repo) }
                .onSuccess { updated ->
                    showStatus("Katalog ${updated.name} diperbarui: ${updated.plugins.size} plugin ditemukan. Pasang dan aktifkan .cs3 dari katalog untuk menggunakan providernya.")
                }
                .onFailure { error ->
                    showStatus("Gagal memperbarui katalog: ${error.localizedMessage ?: "periksa URL dan koneksi"}")
                }
        }
    }

    /**
     * Refreshes the first page of each enabled, installed CloudStream provider when Home opens.
     * Each plugin is isolated; failed or empty responses leave its last saved catalog untouched.
     */
    fun refreshCloudStreamCatalogOnHome(selectedPluginId: String? = null) {
        if (selectedPluginId.isNullOrBlank()) return
        val profileId = _activeProfile.value?.id ?: return
        if (cloudStreamHomeRefreshJob?.isActive == true) {
            if (selectedPluginId.isNullOrBlank()) return
            cloudStreamHomeRefreshJob?.cancel()
        }
        cloudStreamHomeRefreshJob = viewModelScope.launch {
            _cloudStreamSourceHealth.value = loadCloudStreamSourceHealth(profileId)
            var refreshed = 0
            val failures = mutableListOf<String>()
            try {
                val entries = pluginManager.repositories.value
                    .flatMap { it.plugins }
                    .filter { entry ->
                        entry.status != 0 &&
                            pluginManager.isCloudStreamPluginInstalled(entry) &&
                            (selectedPluginId.isNullOrBlank() ||
                                pluginManager.cloudStreamPluginId(entry) == selectedPluginId)
                    }
                    .distinctBy { pluginManager.cloudStreamPluginId(it) }
                entries.forEach { entry ->
                    if (_activeProfile.value?.id != profileId) return@forEach
                    val pluginId = pluginManager.cloudStreamPluginId(entry)
                    val requestStartedAt = System.currentTimeMillis()
                    runCatching {
                        pluginManager.loadCloudStreamHomePage(entry, 1)
                    }.onSuccess { sections ->
                        updateCloudStreamSectionPagination(pluginId, sections, reset = true)
                        updateCloudStreamSourceHealth(profileId, pluginId, hasNext = cloudStreamHomeHasNext[pluginId] == true, succeeded = true, responseTimeMs = System.currentTimeMillis() - requestStartedAt)
                        if (_activeProfile.value?.id == profileId && sections.isNotEmpty()) {
                            runCatching { persistCloudStreamCatalog(entry, sections) }
                                .onSuccess { refreshed += it }
                                .onFailure { failures += "${entry.name}: ${it.message ?: "gagal menyimpan"}" }
                        }
                    }.onFailure { error ->
                        updateCloudStreamSourceHealth(profileId, pluginId, error = error.localizedMessage ?: "Gagal memuat katalog", responseTimeMs = System.currentTimeMillis() - requestStartedAt)
                        failures += "${entry.name}: ${error.message ?: "gagal memuat"}"
                    }
                }
                if (refreshed > 0 || failures.isNotEmpty()) {
                    showStatus(
                        when {
                            refreshed > 0 && failures.isEmpty() -> "Katalog CloudStream diperbarui: $refreshed item."
                            refreshed > 0 -> "$refreshed item diperbarui; ${failures.size} plugin gagal, cache lama dipertahankan."
                            else -> "Pembaruan CloudStream gagal; katalog tersimpan tetap digunakan."
                        }
                    )
                }
            } finally {
                cloudStreamHomeRefreshJob = null
            }
        }
    }

    fun refreshAllSourcePlaylists() {
        val profileId = _activeProfile.value?.id ?: return
        if (sourceRefreshJob?.isActive == true) {
            showStatus("Pembaruan sumber sedang berjalan. Tunggu sampai selesai.")
            return
        }
        sourceRefreshJob = viewModelScope.launch {
            try {
                val playlists = sourcePlaylistDao.getForProfile(profileId)
                    .filter { it.sourceType == "LINK" || it.sourceType == "STREAM" || it.sourceType == "REPO" }
                if (playlists.isEmpty()) {
                    showStatus("Belum ada playlist sumber untuk diperbarui")
                    return@launch
                }

                var successCount = 0
                val failures = mutableListOf<String>()
                playlists.forEach { playlist ->
                    // A profile switch must not refresh the previous profile into the active UI.
                    if (_activeProfile.value?.id != profileId) return@forEach
                    val result = runCatching { parseAndAddLinkInternal(playlist.url, profileId) }
                        .getOrElse { "Error: ${it.localizedMessage ?: "gagal memuat sumber"}" }
                    if (result.startsWith("Error:")) failures += "${playlist.name}: $result"
                    else successCount++
                }

                showStatus(
                    if (failures.isEmpty()) "Selesai memperbarui $successCount playlist sumber"
                    else "$successCount playlist berhasil diperbarui, ${failures.size} gagal"
                )
            } finally {
                sourceRefreshJob = null
            }
        }
    }

    // --- File Source (B2) ---
    fun addLocalFileMedia(
        title: String,
        fileUri: String,
        isPhoto: Boolean,
        onFinished: (Boolean) -> Unit = {}
    ) {
        val currentProfileId = _activeProfile.value?.id ?: run {
            showStatus("Profil aktif tidak ditemukan")
            onFinished(false)
            return
        }
        val rawUri = fileUri.trim()
        if (rawUri.isBlank()) {
            showStatus("Pilih file terlebih dahulu")
            onFinished(false)
            return
        }
        val parsedUri = Uri.parse(rawUri)
        val normalizedUri = when (parsedUri.scheme?.lowercase()) {
            "content", "file" -> parsedUri.toString()
            null -> if (rawUri.startsWith("/")) Uri.fromFile(java.io.File(rawUri)).toString() else null
            else -> null
        }
        if (normalizedUri == null) {
            showStatus("Sumber file harus berupa URI lokal atau path perangkat, bukan URL internet")
            onFinished(false)
            return
        }
        if (_isImportingLocalFile.value) {
            showStatus("Impor file sedang berjalan. Tunggu sampai selesai.")
            onFinished(false)
            return
        }

        viewModelScope.launch {
            _isImportingLocalFile.value = true
            var success = false
            try {
                val playlist = ensureSourcePlaylist(
                    normalizedUri,
                    title.ifBlank { "File Lokal" },
                    "FILE",
                    currentProfileId
                )
                val newItem = MediaItem(
                    id = UUID.randomUUID().toString(),
                    profileId = currentProfileId,
                    title = title.ifBlank { "File Lokal" },
                    url = normalizedUri,
                    thumbnailUrl = if (isPhoto) normalizedUri else "",
                    type = if (isPhoto) "PHOTO" else "VIDEO",
                    sourceType = "FILE",
                    category = "Penyimpanan Lokal",
                    playlistId = playlist.id,
                    sourceUrl = normalizedUri
                )
                mediaDao.insertMedia(newItem)
                sourcePlaylistDao.updateCount(playlist.id, mediaDao.countForPlaylist(playlist.id))
                success = true
                val cloudFailed = authManager.currentUser?.let { user ->
                    runCatching { cloudRepository.saveMediaItem(user.uid, newItem) }.isFailure
                } ?: false
                showStatus(
                    if (cloudFailed) "File ditambahkan ke perangkat, tetapi metadata Cloud gagal disinkronkan"
                    else "File lokal berhasil ditambahkan ke beranda"
                )
            } catch (e: Exception) {
                showStatus("Gagal menambahkan file: ${e.localizedMessage ?: "URI tidak dapat dibaca"}")
            } finally {
                _isImportingLocalFile.value = false
                onFinished(success)
            }
        }
    }

    // --- Server Source (B3) ---
    fun uploadMediaToServer(title: String, url: String, isPhoto: Boolean) {
        val currentProfileId = _activeProfile.value?.id ?: return
        viewModelScope.launch {
            val playlist = ensureSourcePlaylist(url, title.ifEmpty { "Media Server" }, "SERVER", currentProfileId)
            val newItem = MediaItem(
                id = UUID.randomUUID().toString(),
                profileId = currentProfileId,
                title = title.ifEmpty { "Server Upload" },
                url = url,
                thumbnailUrl = if (isPhoto) url else "https://images.unsplash.com/photo-1574375927938-d5a98e8ffe85?w=500",
                type = if (isPhoto) "PHOTO" else "VIDEO",
                sourceType = "SERVER",
                category = "Media Server", playlistId = playlist.id, sourceUrl = url
            )
            mediaDao.insertMedia(newItem)
            sourcePlaylistDao.updateCount(playlist.id, mediaDao.countForPlaylist(playlist.id))
            authManager.currentUser?.let { user ->
                try { cloudRepository.saveMediaItem(user.uid, newItem) } catch (_: Exception) {}
            }
            showStatus("Media berhasil ditambahkan ke server & beranda!")
        }
    }

    /** Rename the catalog title only; the original Drive file and URL remain untouched. */
    fun renameMedia(item: MediaItem, newName: String) {
        viewModelScope.launch {
            val cleanName = newName.trim().take(180)
            if (cleanName.isBlank()) {
                showStatus("Nama media tidak boleh kosong")
                return@launch
            }
            if (cleanName == item.title) return@launch
            val updated = item.copy(title = cleanName)
            mediaDao.updateMedia(updated)
            val cloudSyncFailed = authManager.currentUser?.let { user ->
                runCatching { cloudRepository.saveMediaItem(user.uid, updated) }.isFailure
            } ?: false
            showStatus(
                if (cloudSyncFailed) "Nama media diubah di perangkat, tetapi sinkronisasi Cloud gagal"
                else "Nama media diubah menjadi '$cleanName'"
            )
        }
    }

    fun toggleFavorite(item: MediaItem) {
        viewModelScope.launch {
            val updated = item.copy(isFavorite = !item.isFavorite)
            mediaDao.updateMedia(updated)
            authManager.currentUser?.let { user ->
                try { cloudRepository.saveMediaItem(user.uid, updated) } catch (_: Exception) {}
            }
            showStatus(if (updated.isFavorite) "Disimpan ke Favorit" else "Dihapus dari Favorit")
        }
    }

    fun deleteMedia(item: MediaItem) {
        viewModelScope.launch {
            val user = authManager.currentUser
            var storageMessage: String? = null
            var cloudMessage: String? = null
            var storageDeleted = false
            val isFirebaseFile = item.sourceType == "SERVER" &&
                storageRepository.isFirebaseStorageUrl(item.url)

            var cloudMetadataDeleted = false
            if (user != null) {
                try {
                    cloudRepository.deleteMediaItem(user.uid, item.id)
                    cloudMetadataDeleted = true
                } catch (e: Exception) {
                    cloudMessage = e.localizedMessage ?: "metadata cloud gagal dihapus"
                }
            } else {
                cloudMessage = "metadata cloud belum dibersihkan karena akun tidak aktif"
            }

            if (isFirebaseFile) {
                val usedElsewhere = mediaDao.countOtherReferencesToUrl(item.url, item.id) > 0
                if (usedElsewhere) {
                    storageMessage = "file Firebase dipertahankan karena masih dipakai media lain"
                } else if (!cloudMetadataDeleted) {
                    storageMessage = "file Firebase dipertahankan agar metadata cloud tidak menunjuk file yang hilang"
                } else {
                    try {
                        storageRepository.deleteOwnedMedia(user!!.uid, item.url)
                        storageDeleted = true
                    } catch (e: Exception) {
                        storageMessage = e.localizedMessage ?: "file Firebase tidak dapat dihapus"
                    }
                }
            }

            // Local removal is always allowed; remote failures are reported so the user
            // knows that a Storage object or cloud record may still need attention.
            mediaDao.deleteMedia(item)
            if (item.playlistId.isNotBlank()) {
                val remainingCount = mediaDao.countForPlaylist(item.playlistId)
                sourcePlaylistDao.updateCount(item.playlistId, remainingCount)
                if (user != null) {
                    val updatedPlaylist = sourcePlaylistDao.getForProfile(item.profileId)
                        .firstOrNull { it.id == item.playlistId }
                    if (updatedPlaylist != null) {
                        runCatching {
                            cloudRepository.syncSourcePlaylists(user.uid, item.profileId, listOf(updatedPlaylist))
                        }.onFailure {
                            cloudMessage = listOfNotNull(cloudMessage, "metadata playlist gagal diperbarui").joinToString("; ")
                        }
                    }
                }
            }
            val details = listOfNotNull(
                storageMessage?.let { "Storage: $it" },
                cloudMessage?.let { "Cloud: $it" }
            )
            if (details.isEmpty()) {
                val remoteNote = when {
                    storageDeleted -> " dan Firebase Storage"
                    item.sourceType == "SERVER" && !isFirebaseFile -> ". File eksternal tetap berada di server asal"
                    else -> ""
                }
                showStatus("Media '${item.title}' dihapus dari perangkat$remoteNote")
            } else {
                showStatus("Media '${item.title}' dihapus dari perangkat. " + details.joinToString("; "))
            }
        }
    }

    /**
     * Resolve a saved or freshly searched CloudStream item for an independent Multiview slot.
     * Uses the installed plugin runtime already owned by NSiO.
     */
    suspend fun resolveCloudStreamForMultiview(item: MediaItem): List<com.example.data.plugin.CloudStreamStream> {
        require(item.sourceType == "CLOUDSTREAM") { "Item bukan sumber CloudStream." }
        require(item.pluginId.isNotBlank()) { "ID plugin asal tidak tersedia." }
        val entry = pluginManager.findCloudStreamPlugin(item.pluginId)
            ?: throw IllegalStateException("Plugin asal tidak ditemukan atau belum terpasang.")
        val metadata = runCatching { org.json.JSONObject(item.extraInfo) }.getOrNull()
        val providerName: String = metadata?.optString("providerName")
            ?.takeIf { it.isNotBlank() }
            ?: item.extraInfo.takeIf { metadata == null && it.isNotBlank() }
            ?: throw IllegalStateException("Nama provider CloudStream tidak tersedia.")
        val playbackData = metadata?.optString("playbackData")
            ?.takeIf { it.isNotBlank() }
            ?: run {
                val content = pluginManager.loadCloudStreamContent(entry, providerName, item.url)
                content.dataUrl?.takeIf { it.isNotBlank() }
                    ?: content.episodes.firstOrNull()?.data?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Provider tidak menyediakan data pemutaran.")
            }
        return pluginManager.loadCloudStreamLinks(entry, providerName, playbackData)
            .filter { it.url.isNotBlank() }
            .distinctBy { it.url to it.referer }
            .sortedWith(compareByDescending<com.example.data.plugin.CloudStreamStream> { it.quality }.thenBy { it.name })
            .also { streams ->
                if (streams.isEmpty()) throw IllegalStateException("Plugin tidak menemukan stream yang dapat diputar.")
            }
    }

    // --- Player Management ---
    /**
     * Opens NSiO's normal PlayerScreen for a resolved external/CloudStream stream.
     * CloudStream items are transient and are not persisted as Room media records.
     */
    fun playExternalStream(
        url: String,
        title: String,
        requestHeaders: Map<String, String> = emptyMap(),
        mimeType: String? = null,
        sourceUrl: String = ""
    ) {
        require(url.isNotBlank()) { "URL stream kosong." }
        val displayTitle = title.ifBlank { "Streaming" }
        _currentlyPlaying.value = MediaItem(
            id = "external-stream:${java.util.UUID.randomUUID()}",
            profileId = _activeProfile.value?.id ?: "external-stream",
            title = displayTitle,
            url = url,
            type = "STREAM",
            sourceType = "CLOUDSTREAM",
            sourceUrl = sourceUrl
        )
        player.playWithMimeType(
            url = url,
            title = displayTitle,
            requestHeaders = requestHeaders,
            mimeType = mimeType
        )
    }

    /**
     * Resolves the best CloudStream film source in ViewModel scope so navigation to
     * PlayerScreen cannot cancel provider work when the detail composable is disposed.
     */
    fun playCloudStreamFilmImmediately(
        entry: com.example.data.plugin.CloudStreamPluginEntry,
        contentUrl: String,
        providerName: String,
        playbackData: String,
        title: String,
        posterUrl: String?,
        durationMs: Long = 0L,
        skipMetadata: Map<String, String> = emptyMap()
    ) {
        beginCloudStreamPlaybackPreparation(contentUrl, title, posterUrl, "CloudStream • Film")
        viewModelScope.launch {
            runCatching {
                val playable = pluginManager.loadCloudStreamLinks(entry, providerName, playbackData)
                    .filter { it.url.isNotBlank() }
                val stream = playable.maxByOrNull { it.quality }
                    ?: throw IllegalStateException("Provider belum menemukan sumber film yang dapat diputar.")
                val headers = stream.headers.toMutableMap().apply {
                    if (stream.referer.isNotBlank() && keys.none { it.equals("Referer", true) }) {
                        put("Referer", stream.referer)
                    }
                }
                val streamType = stream.streamType.substringAfterLast('.').uppercase()
                val extension = runCatching {
                    java.net.URI(stream.url).path.substringAfterLast('.', "").uppercase()
                }.getOrDefault("")
                val mimeType = when {
                    streamType.contains("M3U8") || streamType.contains("HLS") || extension == "M3U8" -> "application/x-mpegURL"
                    streamType.contains("DASH") || streamType == "MPD" || extension == "MPD" -> "application/dash+xml"
                    streamType.contains("WEBM") || extension == "WEBM" -> "video/webm"
                    streamType.contains("MKV") || streamType.contains("MATROSKA") || extension == "MKV" -> "video/x-matroska"
                    streamType.contains("MP4") || streamType.contains("VIDEO") || extension == "MP4" -> "video/mp4"
                    else -> null
                }
                playCloudStreamEpisode(
                    entry = entry,
                    contentUrl = contentUrl,
                    providerName = providerName,
                    playbackData = playbackData,
                    title = title,
                    posterUrl = posterUrl,
                    durationMs = durationMs,
                    streamUrl = stream.url,
                    requestHeaders = headers,
                    mimeType = mimeType,
                    externalSubtitles = stream.subtitles,
                    extractorData = stream.extractorData,
                    videoInterceptor = stream.videoInterceptor,
                    availablePluginStreams = playable,
                    category = "CloudStream • Film",
                    skipMetadata = skipMetadata
                )
            }.onFailure { error ->
                cancelCloudStreamPlaybackPreparation(
                    error.localizedMessage ?: "Gagal mengambil sumber film dari plugin."
                )
            }
        }
    }

    fun playCloudStreamTrailerFromPlugin(
        entry: com.example.data.plugin.CloudStreamPluginEntry,
        providerName: String,
        trailer: com.example.data.plugin.CloudStreamTrailer,
        title: String,
        posterUrl: String?
    ) {
        beginCloudStreamPlaybackPreparation(trailer.url, "Trailer • $title", posterUrl, "CloudStream • Trailer")
        viewModelScope.launch {
            runCatching {
                pluginManager.resolveCloudStreamTrailer(entry, providerName, trailer)
                    .sortedWith(compareByDescending<com.example.data.plugin.CloudStreamStream> { it.quality })
                    .firstOrNull()
                    ?: throw IllegalStateException("Extractor tidak menghasilkan stream trailer.")
            }.onSuccess { stream ->
                playCloudStreamTrailer(title, posterUrl, stream)
            }.onFailure { error ->
                cancelCloudStreamPlaybackPreparation(
                    "Trailer gagal disiapkan: ${error.localizedMessage ?: "stream tidak tersedia"}"
                )
            }
        }
    }

    /**
     * Saves a stable, episode-specific CloudStream history item before playback.
     * The original provider URL and playback data are retained so resume can
     * re-resolve a fresh stream URL after temporary CDN links expire.
     */
    fun playCloudStreamEpisode(
        entry: com.example.data.plugin.CloudStreamPluginEntry,
        contentUrl: String,
        providerName: String,
        playbackData: String,
        title: String,
        posterUrl: String?,
        durationMs: Long = 0L,
        streamUrl: String,
        requestHeaders: Map<String, String> = emptyMap(),
        mimeType: String? = null,
        externalSubtitles: List<com.example.data.plugin.CloudStreamSubtitle> = emptyList(),
        extractorData: String? = null,
        videoInterceptor: okhttp3.Interceptor? = null,
        availablePluginStreams: List<com.example.data.plugin.CloudStreamStream> = emptyList(),
        category: String = "CloudStream • Episode",
        skipMetadata: Map<String, String> = emptyMap()
    ) {
        val profileId = _activeProfile.value?.id ?: run {
            _currentlyPlaying.value = null
            _playbackPreparationMessage.value = null
            showStatus("Profil aktif tidak ditemukan.")
            return
        }
        if (contentUrl.isBlank() || providerName.isBlank() || playbackData.isBlank() || streamUrl.isBlank()) {
            _currentlyPlaying.value = null
            _playbackPreparationMessage.value = null
            showStatus("Informasi episode CloudStream belum lengkap.")
            return
        }
        if (_playbackPreparationMessage.value == null) {
            beginCloudStreamPlaybackPreparation(contentUrl, title, posterUrl, category)
        }
        viewModelScope.launch {
            runCatching {
                val pluginId = pluginManager.cloudStreamPluginId(entry)
                val repositoryId = pluginManager.cloudStreamRepositoryId(entry)
                val stableKey = "$profileId|$pluginId|$providerName|$contentUrl|$playbackData"
                val mediaId = UUID.nameUUIDFromBytes(stableKey.toByteArray(Charsets.UTF_8)).toString()
                // History storage must never block playback when the local/cloud store is unavailable.
                val previous = runCatching {
                    mediaDao.getMediaListForProfile(profileId).firstOrNull { it.id == mediaId }
                }.getOrNull()
                val metadata = org.json.JSONObject()
                    .put("kind", "cloudstream_episode")
                    .put("providerName", providerName)
                    .put("playbackData", playbackData)
                    .put("skipMetadata", org.json.JSONObject(skipMetadata.filterValues { it.isNotBlank() }))
                    .toString()
                val item = MediaItem(
                    id = mediaId,
                    profileId = profileId,
                    title = title.ifBlank { "CloudStream Episode" },
                    url = contentUrl,
                    thumbnailUrl = posterUrl.orEmpty(),
                    type = "CLOUDSTREAM",
                    sourceType = "CLOUDSTREAM",
                    category = category,
                    sourceUrl = entry.url,
                    pluginId = pluginId,
                    repositoryId = repositoryId,
                    // A completed episode starts from zero when the user chooses to watch it again.
                    progressMs = previous?.let { saved ->
                        if (saved.durationMs > 0L && saved.progressMs >= (saved.durationMs * 0.95).toLong()) 0L
                        else saved.progressMs.coerceAtLeast(0L)
                    } ?: 0L,
                    durationMs = maxOf(previous?.durationMs ?: 0L, durationMs.coerceAtLeast(0L)),
                    lastWatchedAt = previous?.lastWatchedAt ?: 0L,
                    isFavorite = previous?.isFavorite ?: false,
                    createdAt = previous?.createdAt ?: System.currentTimeMillis(),
                    extraInfo = metadata
                )
                _currentlyPlaying.value = item
                _playbackPreparationMessage.value = null
                syncPlaybackQueue(item)
                player.playWithMimeType(
                    url = streamUrl,
                    title = item.title,
                    startPositionMs = item.progressMs.coerceAtLeast(0L),
                    requestHeaders = requestHeaders,
                    mimeType = mimeType,
                    externalSubtitles = externalSubtitles,
                    videoInterceptor = videoInterceptor,
                    pluginStreams = availablePluginStreams
                )
                cloudStreamVerifierJob?.cancel()
                cloudStreamVerifierJob = if (!extractorData.isNullOrBlank()) {
                    viewModelScope.launch {
                        runCatching {
                            pluginManager.runCloudStreamExtractorVerifier(entry, providerName, extractorData)
                        }
                    }
                } else null
                startPlaybackProgressPersistence(item)
                val historySaved = runCatching { mediaDao.insertMedia(item) }.isSuccess
                authManager.currentUser?.let { user ->
                    runCatching { cloudRepository.saveMediaItem(user.uid, item) }
                }
                showStatus(
                    if (historySaved) "Permintaan putar ${item.title} dikirim. Riwayat episode tersimpan."
                    else "Permintaan putar ${item.title} dikirim. Riwayat lokal gagal disimpan."
                )
            }.onFailure { error ->
                if (_currentlyPlaying.value?.id?.startsWith("cloudstream-preparing:") == true) {
                    _currentlyPlaying.value = null
                    _playbackPreparationMessage.value = null
                }
                showStatus("Gagal menyiapkan pemutaran episode: ${error.localizedMessage ?: "kesalahan database"}")
            }
        }
    }

    private fun playCloudStreamMedia(item: MediaItem) {
        viewModelScope.launch {
            runCatching {
                val entry = pluginManager.findCloudStreamPlugin(item.pluginId)
                    ?: throw IllegalStateException("Plugin asal tidak ditemukan atau belum terpasang.")
                val metadata = runCatching { org.json.JSONObject(item.extraInfo) }.getOrNull()
                val providerName = metadata?.optString("providerName")
                    ?.takeIf { it.isNotBlank() } ?: item.extraInfo
                val content = pluginManager.loadCloudStreamContent(entry, providerName, item.url)
                val playbackData = metadata?.optString("playbackData")
                    ?.takeIf { it.isNotBlank() }
                    ?: content.dataUrl?.takeIf { it.isNotBlank() }
                    ?: content.episodes.firstOrNull()?.data?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Provider tidak menyediakan data film atau episode untuk diputar.")
                val streams = pluginManager.loadCloudStreamLinks(entry, content.providerName, playbackData)
                    .filter { it.url.isNotBlank() }
                val stream = streams.maxByOrNull { it.quality }
                    ?: throw IllegalStateException("Provider tidak menemukan link streaming yang dapat diputar.")
                val headers = stream.headers.toMutableMap()
                if (stream.referer.isNotBlank() && headers.keys.none { it.equals("Referer", ignoreCase = true) }) {
                    headers["Referer"] = stream.referer
                }
                // CloudStream providers expose different type labels; normalize the
                // declared type first, then fall back to the resolved URL extension.
                val streamType = stream.streamType.substringAfterLast('.').uppercase()
                val streamExtension = runCatching {
                    java.net.URI(stream.url).path.substringAfterLast('.', "").uppercase()
                }.getOrDefault("")
                val mimeType = when {
                    streamType in setOf("M3U8", "HLS") || streamType.contains("M3U8") || streamType.contains("HLS") || streamExtension == "M3U8" ->
                        "application/x-mpegURL"
                    streamType in setOf("DASH", "MPD") || streamType.contains("DASH") || streamExtension == "MPD" ->
                        "application/dash+xml"
                    streamType.contains("WEBM") || streamExtension == "WEBM" -> "video/webm"
                    streamType.contains("MKV") || streamType.contains("MATROSKA") || streamExtension == "MKV" ->
                        "video/x-matroska"
                    streamType.contains("MP4") || streamType.contains("VIDEO") || streamExtension == "MP4" -> "video/mp4"
                    else -> null
                }
                _playbackPreparationMessage.value = null
                player.playWithMimeType(
                    url = stream.url,
                    title = item.title,
                    startPositionMs = item.progressMs.coerceAtLeast(0L),
                    requestHeaders = headers,
                    mimeType = mimeType,
                    externalSubtitles = stream.subtitles,
                    videoInterceptor = stream.videoInterceptor,
                    pluginStreams = streams
                )
                startPlaybackProgressPersistence(item)
                "Memutar ${item.title} melalui ${entry.name}."
            }.onSuccess { message ->
                showStatus(message)
            }.onFailure { error ->
                if (_currentlyPlaying.value?.id == item.id) _currentlyPlaying.value = null
                _playbackPreparationMessage.value = null
                showStatus("CloudStream gagal memutar '${item.title}': ${error.localizedMessage ?: "sumber tidak tersedia"}")
            }
        }
    }

    fun playCloudStreamTrailer(
        title: String,
        posterUrl: String?,
        stream: com.example.data.plugin.CloudStreamStream
    ) {
        val profileId = _activeProfile.value?.id ?: run {
            showStatus("Profil aktif tidak ditemukan.")
            return
        }
        if (stream.url.isBlank()) {
            showStatus("Link trailer tidak tersedia.")
            return
        }
        val headers = stream.headers.toMutableMap().apply {
            if (stream.referer.isNotBlank() && keys.none { it.equals("Referer", true) }) {
                put("Referer", stream.referer)
            }
        }
        val extension = runCatching {
            java.net.URI(stream.url).path.substringAfterLast('.', "").uppercase()
        }.getOrDefault("")
        val streamType = stream.streamType.substringAfterLast('.').uppercase()
        val mimeType = when {
            extension == "M3U8" || streamType.contains("HLS") -> "application/x-mpegURL"
            extension == "MPD" || streamType.contains("DASH") -> "application/dash+xml"
            extension == "WEBM" || streamType.contains("WEBM") -> "video/webm"
            extension == "MKV" || streamType.contains("MKV") -> "video/x-matroska"
            else -> "video/mp4"
        }
        val item = MediaItem(
            id = "external-stream:trailer:${UUID.randomUUID()}",
            profileId = profileId,
            title = "Trailer • ${title.ifBlank { "CloudStream" }}",
            url = stream.url,
            thumbnailUrl = posterUrl.orEmpty(),
            type = "VIDEO",
            sourceType = "CLOUDSTREAM_TRAILER",
            category = "CloudStream • Trailer",
            sourceUrl = stream.referer,
            createdAt = System.currentTimeMillis()
        )
        _currentlyPlaying.value = item
        _playbackPreparationMessage.value = null
        syncPlaybackQueue(item)
        player.playWithMimeType(
            url = stream.url,
            title = item.title,
            requestHeaders = headers,
            mimeType = mimeType,
            externalSubtitles = stream.subtitles,
            videoInterceptor = stream.videoInterceptor,
            pluginStreams = listOf(stream)
        )
    }

    fun playDdtvMedia(item: MediaItem, headers: Map<String, String>, streamType: String) {
        // DDTV streams carry provider-specific authorization and may have no
        // recognizable file extension, so preserve headers and MIME explicitly.
        syncPlaybackQueue(item)
        _playbackPreparationMessage.value = null
        _currentlyPlaying.value = item
        val streamExtension = runCatching {
            java.net.URI(item.url).path.orEmpty().substringAfterLast('.', "").lowercase()
        }.getOrDefault("")
        val mimeType = when (streamType.lowercase()) {
            "hls", "m3u8" -> "application/x-mpegURL"
            "dash", "mpd" -> "application/dash+xml"
            "progressive", "mp4" -> when (streamExtension) {
                "m4v", "mp4" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                "ts" -> "video/mp2t"
                "flv" -> "video/x-flv"
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                else -> null
            }
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            else -> null
        }
        player.playWithMimeType(
            url = normalizePlaybackUrl(item.url),
            title = item.title,
            startPositionMs = item.progressMs,
            requestHeaders = headers,
            mimeType = mimeType
        )
        startPlaybackProgressPersistence(item)
    }

    fun playMedia(item: MediaItem) {
        syncPlaybackQueue(item)
        if (item.sourceType == "CLOUDSTREAM") {
            player.clearError()
            player.stop()
            _playbackPreparationMessage.value = "Mencari sumber video dari plugin..."
            _currentlyPlaying.value = item
            playCloudStreamMedia(item)
            return
        }
        val useNativePlayer = shouldUseNativePlayer(item.type, item.url)
        if (!useNativePlayer) player.stop()
        _playbackPreparationMessage.value = null
        _currentlyPlaying.value = item
        if (useNativePlayer) {
            val headers = item.extraInfo.split('&')
                .mapNotNull { entry ->
                    val separator = entry.indexOf('=')
                    if (separator <= 0) null else entry.substring(0, separator).trim() to entry.substring(separator + 1).trim()
                }
                .filter { it.first.isNotBlank() && it.second.isNotBlank() }
                .toMap()
                .toMutableMap()
            if (item.sourceUrl.startsWith("http", ignoreCase = true)) {
                headers.putIfAbsent("Referer", item.sourceUrl)
                runCatching {
                    val source = java.net.URI(item.sourceUrl)
                    if (!headers.containsKey("Origin") && !source.host.isNullOrBlank()) {
                        headers["Origin"] = "${source.scheme}://${source.host}${if (source.port > 0) ":${source.port}" else ""}"
                    }
                }
            }
            player.play(normalizePlaybackUrl(item.url), item.title, item.progressMs, headers, "${item.profileId}:${item.id}")
            startPlaybackProgressPersistence(item)
        } else {
            player.clearError()
        }
    }

    /**
     * Pause native playback and persist its last position when the host activity
     * loses the foreground. Playback intentionally does not auto-resume on return.
     */
    fun pausePlaybackForBackground() {
        val current = _currentlyPlaying.value ?: return
        val isNativeVideo = shouldUseNativePlayer(current.type, current.url)
        if (!isNativeVideo) return

        player.pause()
        val positionMs = player.playerState.value.currentPositionMs.coerceAtLeast(0L)
        persistPlaybackProgress(current, positionMs, player.playerState.value.durationMs)
        playbackPersistenceJob?.cancel()
    }

    private fun startPlaybackProgressPersistence(item: MediaItem) {
        playbackPersistenceJob?.cancel()
        if (item.id.startsWith("external-stream:")) return
        playbackPersistenceJob = viewModelScope.launch {
            while (isActive && _currentlyPlaying.value?.id == item.id) {
                delay(5_000)
                if (_currentlyPlaying.value?.id != item.id) break
                val state = player.playerState.value
                if (state.isPlaying && state.currentPositionMs > 0L) {
                    val duration = state.durationMs.coerceAtLeast(item.durationMs)
                    runCatching {
                        mediaDao.updatePlaybackState(
                            item.id,
                            state.currentPositionMs.coerceAtLeast(0L),
                            duration,
                            System.currentTimeMillis()
                        )
                    }
                }
            }
        }
    }

    /** Stores duration learned by a silent TikTod preview without changing watch progress/history. */
    fun savePreviewMediaDuration(mediaId: String, durationMs: Long) {
        if (mediaId.isBlank() || durationMs <= 0L || mediaId.startsWith("external-stream:")) return
        viewModelScope.launch {
            runCatching { mediaDao.updateDurationIfMissing(mediaId, durationMs) }
        }
    }

    private fun persistPlaybackProgress(item: MediaItem, positionMs: Long, durationMs: Long) {
        // External streams have no catalog row; CloudStream catalog items do.
        if (item.id.startsWith("external-stream:")) return
        val safePosition = positionMs.coerceAtLeast(0L)
        viewModelScope.launch {
            runCatching { mediaDao.updatePlaybackState(item.id, safePosition, durationMs.coerceAtLeast(item.durationMs)) }
            authManager.currentUser?.let { user ->
                runCatching {
                    cloudRepository.saveMediaItem(user.uid, item.copy(
                        progressMs = safePosition,
                        durationMs = durationMs.coerceAtLeast(item.durationMs),
                        lastWatchedAt = if (safePosition > 0L) System.currentTimeMillis() else item.lastWatchedAt
                    ))
                }
            }
        }
    }

    fun closePlayer() {
        val current = _currentlyPlaying.value
        val isPlayableVideo = current?.let { shouldUseNativePlayer(it.type, it.url) } == true
        if (current != null && isPlayableVideo) {
            val pos = player.playerState.value.currentPositionMs.coerceAtLeast(0L)
            persistPlaybackProgress(current, pos, player.playerState.value.durationMs)
        }
        playbackPersistenceJob?.cancel()
        player.stop()
        _currentlyPlaying.value = null
    }

    // --- Download ---
    fun downloadMedia(item: MediaItem) {
        viewModelScope.launch {
            try {
                val headers = item.extraInfo.split('&')
                    .mapNotNull { entry ->
                        val separator = entry.indexOf('=')
                        if (separator <= 0) null else entry.substring(0, separator).trim() to entry.substring(separator + 1).trim()
                    }
                    .filter { it.first.isNotBlank() && it.second.isNotBlank() }
                    .toMap()
                    .toMutableMap()
                if (item.sourceUrl.startsWith("http", ignoreCase = true)) {
                    headers.putIfAbsent("Referer", item.sourceUrl)
                    runCatching {
                        val source = java.net.URI(item.sourceUrl)
                        if (!headers.containsKey("Origin") && !source.host.isNullOrBlank()) {
                            headers["Origin"] = "${source.scheme}://${source.host}${if (source.port > 0) ":${source.port}" else ""}"
                        }
                    }
                }
                downloadManager.enqueueDownload(item.id, item.title, item.url, headers)
                showStatus("Unduhan '${item.title}' dimulai")
            } catch (e: Exception) {
                showStatus("Gagal memulai unduhan: ${e.localizedMessage ?: "URL tidak didukung"}")
            }
        }
    }

    fun pauseDownload(id: String) {
        viewModelScope.launch { runCatching { downloadManager.pauseDownload(id) }.onFailure { showStatus("Gagal menjeda unduhan: ${it.localizedMessage}") } }
    }

    fun resumeDownload(id: String) {
        viewModelScope.launch { runCatching { downloadManager.resumeDownload(id) }.onFailure { showStatus("Gagal melanjutkan unduhan: ${it.localizedMessage}") } }
    }

    fun retryDownload(id: String) {
        viewModelScope.launch { runCatching { downloadManager.retryDownload(id) }.onFailure { showStatus("Gagal mengulang unduhan: ${it.localizedMessage}") } }
    }

    fun cancelDownload(id: String) {
        viewModelScope.launch { runCatching { downloadManager.cancelDownload(id) }.onFailure { showStatus("Gagal menghapus unduhan: ${it.localizedMessage}") } }
    }

    fun uploadMediaFileToServer(
        title: String,
        localUri: String,
        isPhoto: Boolean,
        onFinished: (Boolean) -> Unit = {}
    ) {
        val currentProfileId = _activeProfile.value?.id ?: run {
            showStatus("Profil aktif tidak ditemukan")
            onFinished(false)
            return
        }
        if (_isUploadingServerMedia.value) {
            showStatus("Upload sedang berjalan. Tunggu sampai selesai.")
            onFinished(false)
            return
        }
        if (localUri.isBlank()) {
            showStatus("Pilih file terlebih dahulu")
            onFinished(false)
            return
        }

        viewModelScope.launch {
            _isUploadingServerMedia.value = true
            var uploadCompleted = false
            try {
                showStatus("Mengunggah file ke server...")
                val user = authManager.ensureStorageUser()
                val remoteUri = storageRepository.uploadMedia(user.uid, Uri.parse(localUri), title.ifBlank { "Media Server" })
                try {
                    val playlist = ensureSourcePlaylist(
                        remoteUri.toString(),
                        title.ifBlank { "Media Server" },
                        "SERVER",
                        currentProfileId
                    )
                    val item = MediaItem(
                        id = UUID.randomUUID().toString(),
                        profileId = currentProfileId,
                        title = title.ifBlank { "Media Server" },
                        url = remoteUri.toString(),
                        thumbnailUrl = if (isPhoto) remoteUri.toString() else "",
                        type = if (isPhoto) "PHOTO" else "VIDEO",
                        sourceType = "SERVER",
                        category = "Media Server",
                        playlistId = playlist.id,
                        sourceUrl = remoteUri.toString()
                    )
                    mediaDao.insertMedia(item)
                    sourcePlaylistDao.updateCount(playlist.id, mediaDao.countForPlaylist(playlist.id))
                    uploadCompleted = true
                    onFinished(true)
                    if (!user.isAnonymous) {
                        runCatching { cloudRepository.saveMediaItem(user.uid, item) }
                            .onFailure { error ->
                                showStatus("Upload berhasil, tetapi metadata cloud gagal disinkronkan: ${error.localizedMessage ?: "izin Firestore"}")
                            }
                    }
                    if (_statusMessage.value == "Mengunggah file ke server...") {
                        showStatus("File berhasil di-upload ke Firebase Storage dan ditambahkan ke beranda")
                    }
                } catch (e: Exception) {
                    val cleanup = runCatching { storageRepository.deleteOwnedMedia(user.uid, remoteUri.toString()) }
                    val suffix = if (cleanup.isFailure) " File Storage belum terhapus otomatis." else " File sementara di Storage sudah dibersihkan."
                    showStatus("Pencatatan media gagal: ${e.localizedMessage ?: "database lokal"}.$suffix")
                }
            } catch (e: Exception) {
                showStatus("Upload Storage gagal: ${e.localizedMessage ?: "periksa koneksi dan izin Firebase"}")
            } finally {
                _isUploadingServerMedia.value = false
                if (!uploadCompleted) onFinished(false)
            }
        }
    }

    // --- Backup & Restore ---
    fun runProfileBackup(onResult: (BackupResult) -> Unit) {
        val p = _activeProfile.value ?: return
        val s = _settings.value
        val items = _mediaList.value
        val playlists = _sourcePlaylists.value
        val pin = if (p.isPinEnabled) PinVault.decrypt(getApplication(), p.pin) else ""
        val avatarAssignments = TikTodLocalStore(getApplication()).avatarAssignments(p.id)
        viewModelScope.launch {
            val res = BackupUtils.createProfileBackup(
                context = getApplication(),
                profile = p,
                settings = s,
                mediaList = items,
                sourcePlaylists = playlists,
                profilePin = pin,
                avatarAssignments = avatarAssignments
            )
            showStatus(res.message)
            onResult(res)
        }
    }

    fun runFullBackupAman(onResult: (BackupResult) -> Unit) {
        viewModelScope.launch {
            val res = BackupUtils.createFullBackup(getApplication(), database)
            showStatus(res.message)
            onResult(res)
        }
    }

    fun restoreBackup(jsonContent: String, backupPin: String = "", onResult: (BackupResult) -> Unit) {
        val recipientProfileId = _activeProfile.value?.id.orEmpty()
        viewModelScope.launch {
            val res = BackupUtils.restoreFromFile(
                getApplication(), database, jsonContent, recipientProfileId, backupPin
            )
            // Profile backups are additive imports into the active profile. Do not reload
            // startup state here because that would reset the active profile to the default.
            if (runCatching {
                    org.json.JSONObject(jsonContent).optString("backupType") == "FULL_BACKUP_AMAN"
                }.getOrDefault(false)) {
                loadInitialData()
            }
            showStatus(res.message)
            onResult(res)
        }
    }

    fun showStatus(msg: String) {
        _statusMessage.value = msg
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        // Playback engine is application-scoped and is released by process teardown,
        // not by an Activity/ViewModel leaving the foreground.
    }
}
