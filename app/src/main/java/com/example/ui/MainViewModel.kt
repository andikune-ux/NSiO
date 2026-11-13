package com.example.ui

import android.app.Application
import android.net.Uri
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
import com.example.data.firebase.FirebaseStorageRepository
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
import kotlinx.coroutines.Job
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

    // Keep only one database observer per active profile.
    private var mediaObserverJob: Job? = null
    private var playlistObserverJob: Job? = null

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
                val active = existingProfiles.firstOrNull { it.isDefault } ?: existingProfiles.first()
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
        mediaObserverJob?.cancel()
        _mediaList.value = emptyList()
        mediaObserverJob = viewModelScope.launch {
            mediaDao.getMediaForProfile(current.id).collect { list ->
                // Ignore late emissions from a profile that is no longer active.
                if (_activeProfile.value?.id == current.id) _mediaList.value = list
            }
        }
    }

    private fun observeSourcePlaylists() {
        val current = _activeProfile.value ?: return
        playlistObserverJob?.cancel()
        _sourcePlaylists.value = emptyList()
        playlistObserverJob = viewModelScope.launch {
            sourcePlaylistDao.observeForProfile(current.id).collect { playlists ->
                if (_activeProfile.value?.id == current.id) _sourcePlaylists.value = playlists
            }
        }
    }

    fun switchProfile(profile: Profile) {
        viewModelScope.launch {
            if (_activeProfile.value?.id == profile.id) return@launch
            _activeProfile.value = profile
            _isServerUnlocked.value = false
            _selectedFilter.value = "ALL"
            _settings.value = profileDao.getSettings(profile.id) ?: ProfileSettings(profileId = profile.id)
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
                val allProfiles = profileDao.getAllProfilesList()
                var totalSynced = 0
                allProfiles.forEach { profile ->
                    val localItems = mediaDao.getMediaListForProfile(profile.id)
                    totalSynced += cloudRepository.syncLocalListToCloud(user.uid, profile.id, localItems)
                }
                showStatus("$totalSynced item dari ${allProfiles.size} profil berhasil disinkronkan ke Cloud Firestore!")
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
                val currentProfileId = _activeProfile.value?.id ?: "profile_utama"
                val cloudItems = cloudRepository.fetchMediaItems(user.uid, currentProfileId)
                if (cloudItems.isEmpty()) {
                    showStatus("Tidak ada data cloud untuk profil ini")
                } else {
                    val restoredItems = restoreSourcePlaylistsForItems(cloudItems)
                    mediaDao.insertAll(restoredItems)
                    showStatus("${restoredItems.size} item berhasil dipulihkan beserta sumber playlist!")
                }
            } catch (e: Exception) {
                showStatus("Gagal memulihkan: ${e.localizedMessage}")
            } finally {
                _isCloudSyncing.value = false
            }
        }
    }

    private suspend fun restoreSourcePlaylistsForItems(items: List<MediaItem>): List<MediaItem> {
        val normalized = items.toMutableList()
        items.filter { it.sourceUrl.isNotBlank() }
            .groupBy { it.playlistId.ifBlank { it.sourceUrl } }
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
    fun parseAndAddLink(url: String, onFinished: (String) -> Unit) {
        val profileId = _activeProfile.value?.id ?: return
        viewModelScope.launch {
            val message = parseAndAddLinkInternal(url, profileId)
            onFinished(message)
        }
    }

    /**
     * Parses and persists one source in the caller's coroutine.
     * Keeping this suspendable lets bulk refresh wait for real completion instead
     * of reporting success while child jobs are still running.
     */
    private suspend fun parseAndAddLinkInternal(url: String, profileId: String): String {
        return when (val result = LinkExtractor.parseUrl(url, profileId)) {
            is ParseResult.Single -> {
                val sourceKind = if (result.item.type == "STREAM") {
                    "STREAM"
                } else if (url.lowercase().contains(".json")) {
                    "REPO"
                } else {
                    "LINK"
                }
                val playlist = ensureSourcePlaylist(url, result.item.title, sourceKind, profileId)
                val item = result.item.copy(
                    sourceType = sourceKind,
                    playlistId = playlist.id,
                    sourceUrl = url
                )
                mediaDao.replacePlaylist(playlist.id, listOf(item))
                sourcePlaylistDao.updateCount(playlist.id, mediaDao.countForPlaylist(playlist.id))
                val cloudWarning = authManager.currentUser?.let { user ->
                    runCatching {
                        cloudRepository.syncPlaylistToCloud(user.uid, profileId, playlist.id, listOf(item))
                    }.exceptionOrNull()?.let { " Cloud gagal disinkronkan: ${it.localizedMessage ?: "koneksi bermasalah"}" }
                }.orEmpty()
                "Berhasil menambahkan: ${result.item.title}.$cloudWarning"
            }
            is ParseResult.Multiple -> {
                val sourceKind = if (result.title.startsWith("M3U", ignoreCase = true)) "STREAM" else "REPO"
                val playlist = ensureSourcePlaylist(
                    url,
                    playlistNameFromUrl(url, result.title),
                    sourceKind,
                    profileId
                )
                val items = result.items.map {
                    it.copy(sourceType = sourceKind, playlistId = playlist.id, sourceUrl = url)
                }
                mediaDao.replacePlaylist(playlist.id, items)
                sourcePlaylistDao.updateCount(playlist.id, mediaDao.countForPlaylist(playlist.id))
                val cloudWarning = authManager.currentUser?.let { user ->
                    runCatching {
                        cloudRepository.syncPlaylistToCloud(user.uid, profileId, playlist.id, items)
                    }.exceptionOrNull()?.let { " Cloud gagal disinkronkan: ${it.localizedMessage ?: "koneksi bermasalah"}" }
                }.orEmpty()
                "${items.size} item berhasil ditambahkan (${result.title}).$cloudWarning"
            }
            is ParseResult.Error -> "Error: ${result.message}"
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
            val profileItems = mediaDao.getMediaListForProfile(playlist.profileId)
                .filter { it.playlistId == playlist.id }
            val user = authManager.currentUser
            val warnings = mutableListOf<String>()
            var cloudMetadataDeleted = false

            if (user != null) {
                cloudMetadataDeleted = runCatching {
                    cloudRepository.deletePlaylistItems(user.uid, playlist.profileId, playlist.id)
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
            if (_selectedFilter.value == "PLAYLIST:${playlist.id}") _selectedFilter.value = "ALL"

            showStatus(
                if (warnings.isEmpty()) "Playlist '${playlist.name}' dan data terkait berhasil dihapus"
                else "Playlist '${playlist.name}' dihapus dari perangkat. Perlu perhatian: ${warnings.distinct().joinToString("; ")}"
            )
        }
    }

    fun refreshRepository(repo: com.example.data.plugin.RepoManifest) {
        val profileId = _activeProfile.value?.id ?: return
        viewModelScope.launch {
            var successCount = 0
            val failures = mutableListOf<String>()
            repo.pluginLists.distinct().forEach { url ->
                val result = runCatching { parseAndAddLinkInternal(url, profileId) }
                    .getOrElse { "Error: ${it.localizedMessage ?: "gagal memuat sumber"}" }
                if (result.startsWith("Error:")) failures += result else successCount++
            }
            showStatus(
                if (failures.isEmpty()) "Repository diperbarui: $successCount sumber"
                else "Repository: $successCount berhasil, ${failures.size} gagal"
            )
        }
    }

    fun refreshAllSourcePlaylists() {
        val profileId = _activeProfile.value?.id ?: return
        viewModelScope.launch {
            val playlists = sourcePlaylistDao.getForProfile(profileId)
                .filter { it.sourceType == "LINK" || it.sourceType == "STREAM" || it.sourceType == "REPO" }
            if (playlists.isEmpty()) {
                showStatus("Belum ada playlist sumber untuk diperbarui")
                return@launch
            }

            var successCount = 0
            val failures = mutableListOf<String>()
            playlists.forEach { playlist ->
                val result = runCatching { parseAndAddLinkInternal(playlist.url, profileId) }
                    .getOrElse { "Error: ${it.localizedMessage ?: "gagal memuat sumber"}" }
                if (result.startsWith("Error:")) failures += "${playlist.name}: $result"
                else successCount++
            }

            showStatus(
                if (failures.isEmpty()) "Selesai memperbarui $successCount playlist sumber"
                else "$successCount playlist berhasil diperbarui, ${failures.size} gagal"
            )
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

    // --- Player Management ---
    fun playMedia(item: MediaItem) {
        val isGoogleDrivePreview = item.url.startsWith("https://drive.google.com/file/d/") &&
            item.url.endsWith("/preview")
        if (item.type == "PHOTO" || isGoogleDrivePreview) player.stop()
        _currentlyPlaying.value = item
        if (item.type != "PHOTO" && !isGoogleDrivePreview) {
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
            player.play(item.url, item.title, item.progressMs, headers)
        } else {
            player.clearError()
        }
    }

    fun closePlayer() {
        val current = _currentlyPlaying.value
        val isPlayableVideo = current != null && current.type != "PHOTO" &&
            !(current.url.startsWith("https://drive.google.com/file/d/") && current.url.endsWith("/preview"))
        if (current != null && isPlayableVideo) {
            val pos = player.playerState.value.currentPositionMs.coerceAtLeast(0L)
            viewModelScope.launch {
                mediaDao.updateProgress(current.id, pos)
                authManager.currentUser?.let { user ->
                    try { cloudRepository.saveMediaItem(user.uid, current.copy(progressMs = pos)) } catch (_: Exception) {}
                }
            }
        }
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
