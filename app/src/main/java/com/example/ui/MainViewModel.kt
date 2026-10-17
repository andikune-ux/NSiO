package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.backup.BackupResult
import com.example.data.backup.BackupUtils
import com.example.data.download.VideoDownloadManager
import com.example.data.extractor.LinkExtractor
import com.example.data.extractor.ParseResult
import com.example.data.firebase.AuthState
import com.example.data.firebase.CloudMediaRepository
import com.example.data.firebase.FirebaseAuthManager
import com.example.data.local.AppDatabase
import com.example.data.model.MediaItem
import com.example.data.model.Profile
import com.example.data.model.ProfileSettings
import com.example.data.model.SourcePlaylist
import com.example.data.plugin.PluginManager
import com.example.player.CS3IPlayer
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val mediaDao = database.mediaDao()
    private val profileDao = database.profileDao()
    private val sourcePlaylistDao = database.sourcePlaylistDao()

    val pluginManager = PluginManager(application)
    val downloadManager = VideoDownloadManager.getInstance(application)
    val player = CS3IPlayer(application, viewModelScope)

    // Firebase Auth & Cloud Firestore Integration
    val authManager = FirebaseAuthManager(application)
    val authState: StateFlow<AuthState> = authManager.authState
    val cloudRepository = CloudMediaRepository(application)

    private val _isCloudSyncing = MutableStateFlow(false)
    val isCloudSyncing: StateFlow<Boolean> = _isCloudSyncing.asStateFlow()

    // Profiles state
    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _activeProfile = MutableStateFlow<Profile?>(null)
    val activeProfile: StateFlow<Profile?> = _activeProfile.asStateFlow()

    private val _settings = MutableStateFlow(ProfileSettings(profileId = "default"))
    val settings: StateFlow<ProfileSettings> = _settings.asStateFlow()

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

    // Currently playing media
    private val _currentlyPlaying = MutableStateFlow<MediaItem?>(null)
    val currentlyPlaying: StateFlow<MediaItem?> = _currentlyPlaying.asStateFlow()

    // Toast / Feedback message
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        loadInitialData()
        observeAuthForCloudSync()
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
                profileDao.insertSettings(defaultSettings)

                _profiles.value = listOf(defaultProfile)
                _activeProfile.value = defaultProfile
                _settings.value = defaultSettings
            } else {
                _profiles.value = existingProfiles
                val active = existingProfiles.first()
                _activeProfile.value = active
                _settings.value = profileDao.getSettings(active.id) ?: ProfileSettings(profileId = active.id)
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
        viewModelScope.launch {
            mediaDao.getMediaForProfile(current.id).collect { list ->
                _mediaList.value = list
            }
        }
    }

    private fun observeSourcePlaylists() {
        val current = _activeProfile.value ?: return
        viewModelScope.launch {
            sourcePlaylistDao.observeForProfile(current.id).collect { _sourcePlaylists.value = it }
        }
    }

    fun switchProfile(profile: Profile) {
        viewModelScope.launch {
            _activeProfile.value = profile
            _settings.value = profileDao.getSettings(profile.id) ?: ProfileSettings(profileId = profile.id)
            observeActiveProfileMedia()
            observeSourcePlaylists()
            showStatus("Beralih ke profil '${profile.name}'")
        }
    }

    fun addProfile(name: String, avatar: String, pin: String, isPinEnabled: Boolean) {
        viewModelScope.launch {
            val newProfile = Profile(
                id = UUID.randomUUID().toString(),
                name = name,
                avatarEmoji = avatar.ifEmpty { "👤" },
                pin = pin,
                isPinEnabled = isPinEnabled,
                isDefault = false
            )
            profileDao.insertProfile(newProfile)
            profileDao.insertSettings(ProfileSettings(profileId = newProfile.id))
            _profiles.value = profileDao.getAllProfilesList()
            switchProfile(newProfile)
        }
    }

    fun updateSettings(newSettings: ProfileSettings) {
        viewModelScope.launch {
            profileDao.updateSettings(newSettings)
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

    fun selectFilter(filter: String) {
        _selectedFilter.value = filter
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
                val localItems = _mediaList.value
                val count = cloudRepository.syncLocalListToCloud(user.uid, localItems)
                showStatus("$count item berhasil disinkronkan ke Cloud Firestore!")
            } catch (e: Exception) {
                showStatus("Gagal sinkronisasi: ${e.localizedMessage}")
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
                val cloudItems = cloudRepository.fetchMediaItems(user.uid)
                if (cloudItems.isEmpty()) {
                    showStatus("Tidak ada data tersimpan di Cloud Firestore")
                } else {
                    val currentProfileId = _activeProfile.value?.id ?: "profile_utama"
                    val itemsForProfile = cloudItems.map { it.copy(profileId = currentProfileId) }
                    mediaDao.insertAll(itemsForProfile)
                    showStatus("${itemsForProfile.size} item berhasil dipulihkan dari Cloud!")
                }
            } catch (e: Exception) {
                showStatus("Gagal memulihkan: ${e.localizedMessage}")
            } finally {
                _isCloudSyncing.value = false
            }
        }
    }

    // --- Link Parsing (B1) ---
    fun parseAndAddLink(url: String, onFinished: (String) -> Unit) {
        val currentProfileId = _activeProfile.value?.id ?: return
        viewModelScope.launch {
            when (val result = LinkExtractor.parseUrl(url, currentProfileId)) {
                is ParseResult.Single -> {
                    val sourceKind = if (result.item.type == "STREAM") "STREAM" else if (url.lowercase().contains(".json")) "REPO" else "LINK"
                    val playlist = ensureSourcePlaylist(url, result.item.title, sourceKind, currentProfileId)
                    val item = result.item.copy(sourceType = sourceKind, playlistId = playlist.id, sourceUrl = url)
                    mediaDao.clearPlaylist(playlist.id)
                    mediaDao.insertMedia(item)
                    sourcePlaylistDao.updateCount(playlist.id, _mediaList.value.count { it.playlistId == playlist.id } + 1)
                    // If user is authenticated, sync to cloud
                    authManager.currentUser?.let { user ->
                        try { cloudRepository.saveMediaItem(user.uid, item) } catch (_: Exception) {}
                    }
                    showStatus("1 Media berhasil ditambahkan: ${result.item.title}")
                    onFinished("Berhasil menambahkan: ${result.item.title}")
                }
                is ParseResult.Multiple -> {
                    val sourceKind = if (result.title.startsWith("M3U", ignoreCase = true)) "STREAM" else "REPO"
                    val playlist = ensureSourcePlaylist(url, playlistNameFromUrl(url, result.title), sourceKind, currentProfileId)
                    val items = result.items.map { it.copy(sourceType = sourceKind, playlistId = playlist.id, sourceUrl = url) }
                    mediaDao.clearPlaylist(playlist.id)
                    mediaDao.insertAll(items)
                    sourcePlaylistDao.updateCount(playlist.id, _mediaList.value.count { it.playlistId == playlist.id } + items.size)
                    authManager.currentUser?.let { user ->
                        try { cloudRepository.syncLocalListToCloud(user.uid, items) } catch (_: Exception) {}
                    }
                    showStatus("${items.size} item berhasil ditambahkan ke beranda!")
                    onFinished("${items.size} item berhasil ditambahkan (${result.title})")
                }
                is ParseResult.Error -> {
                    showStatus("Error: ${result.message}")
                    onFinished("Error: ${result.message}")
                }
            }
        }
    }

    private suspend fun ensureSourcePlaylist(url: String, name: String, sourceType: String, profileId: String): SourcePlaylist {
        val existing = sourcePlaylistDao.findByUrl(profileId, url)
        if (existing != null) return existing
        return SourcePlaylist(
            id = UUID.randomUUID().toString(), profileId = profileId,
            name = name.ifBlank { playlistNameFromUrl(url, "Playlist") }, url = url,
            sourceType = sourceType
        ).also { sourcePlaylistDao.insert(it) }
    }

    private fun playlistNameFromUrl(url: String, fallback: String): String = runCatching {
        java.net.URI(url).host?.removePrefix("www.")?.ifBlank { null } ?: fallback
    }.getOrDefault(fallback)

    fun renameSourcePlaylist(playlist: SourcePlaylist, newName: String) {
        viewModelScope.launch {
            val cleanName = newName.trim()
            if (cleanName.isNotEmpty()) sourcePlaylistDao.update(playlist.copy(name = cleanName, updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteSourcePlaylist(playlist: SourcePlaylist) {
        viewModelScope.launch {
            mediaDao.clearPlaylist(playlist.id)
            sourcePlaylistDao.delete(playlist)
            if (_selectedFilter.value == "PLAYLIST:${playlist.id}") _selectedFilter.value = "ALL"
            showStatus("Playlist '${playlist.name}' dihapus")
        }
    }

    fun refreshRepository(repo: com.example.data.plugin.RepoManifest) {
        repo.pluginLists.forEach { url ->
            parseAndAddLink(url) { message -> showStatus("Refresh repo: $message") }
        }
    }

    fun refreshAllSourcePlaylists() {
        val profileId = _activeProfile.value?.id ?: return
        viewModelScope.launch {
            sourcePlaylistDao.getForProfile(profileId)
                .filter { it.sourceType == "LINK" || it.sourceType == "STREAM" || it.sourceType == "REPO" }
                .forEach { playlist -> parseAndAddLink(playlist.url) { } }
            showStatus("Semua playlist sumber diperbarui")
        }
    }

    // --- File Source (B2) ---
    fun addLocalFileMedia(title: String, fileUri: String, isPhoto: Boolean) {
        val currentProfileId = _activeProfile.value?.id ?: return
        viewModelScope.launch {
            val playlist = ensureSourcePlaylist(fileUri, title.ifEmpty { "File Lokal" }, "FILE", currentProfileId)
            val newItem = MediaItem(
                id = UUID.randomUUID().toString(),
                profileId = currentProfileId,
                title = title.ifEmpty { "File Lokal" },
                url = fileUri,
                thumbnailUrl = if (isPhoto) fileUri else "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=500",
                type = if (isPhoto) "PHOTO" else "VIDEO",
                sourceType = "FILE",
                category = "Penyimpanan Lokal", playlistId = playlist.id, sourceUrl = fileUri
            )
            mediaDao.insertMedia(newItem)
            authManager.currentUser?.let { user ->
                try { cloudRepository.saveMediaItem(user.uid, newItem) } catch (_: Exception) {}
            }
            showStatus("File lokal berhasil ditambahkan ke beranda!")
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
            authManager.currentUser?.let { user ->
                try { cloudRepository.saveMediaItem(user.uid, newItem) } catch (_: Exception) {}
            }
            showStatus("Media berhasil di-upload ke server & beranda!")
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
            mediaDao.deleteMedia(item)
            authManager.currentUser?.let { user ->
                try { cloudRepository.deleteMediaItem(user.uid, item.id) } catch (_: Exception) {}
            }
            showStatus("Media '${item.title}' dihapus")
        }
    }

    // --- Player Management ---
    fun playMedia(item: MediaItem) {
        _currentlyPlaying.value = item
        if (item.type != "PHOTO") player.play(item.url, item.title, item.progressMs)
    }

    fun closePlayer() {
        val current = _currentlyPlaying.value
        if (current != null) {
            val pos = player.playerState.value.currentPositionMs
            viewModelScope.launch {
                mediaDao.updateProgress(current.id, pos)
                authManager.currentUser?.let { user ->
                    try { cloudRepository.saveMediaItem(user.uid, current.copy(progressMs = pos)) } catch (_: Exception) {}
                }
            }
        }
        player.pause()
        _currentlyPlaying.value = null
    }

    // --- Download ---
    fun downloadMedia(item: MediaItem) {
        viewModelScope.launch {
            downloadManager.enqueueDownload(item.id, item.title, item.url)
            showStatus("Unduhan '${item.title}' dimulai")
        }
    }

    // --- Backup & Restore ---
    fun runProfileBackup(onResult: (BackupResult) -> Unit) {
        val p = _activeProfile.value ?: return
        val s = _settings.value
        val items = _mediaList.value
        viewModelScope.launch {
            val res = BackupUtils.createProfileBackup(getApplication(), p, s, items)
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

    fun restoreBackup(jsonContent: String, onResult: (BackupResult) -> Unit) {
        viewModelScope.launch {
            val res = BackupUtils.restoreFromFile(getApplication(), database, jsonContent)
            loadInitialData()
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
        player.release()
    }
}
