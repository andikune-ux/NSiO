package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.backup.BackupResult
import com.example.data.backup.BackupUtils
import com.example.data.download.VideoDownloadManager
import com.example.data.extractor.LinkExtractor
import com.example.data.extractor.ParseResult
import com.example.data.local.AppDatabase
import com.example.data.model.MediaItem
import com.example.data.model.Profile
import com.example.data.model.ProfileSettings
import com.example.data.plugin.PluginManager
import com.example.player.CS3IPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val mediaDao = database.mediaDao()
    private val profileDao = database.profileDao()

    val pluginManager = PluginManager(application)
    val downloadManager = VideoDownloadManager.getInstance(application)
    val player = CS3IPlayer(application, viewModelScope)

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

                // Populate high quality sample streams for first launch
                val sampleMedia = listOf(
                    MediaItem(
                        id = UUID.randomUUID().toString(),
                        profileId = defaultProfile.id,
                        title = "Big Buck Bunny 4K (Open Source Movie)",
                        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                        thumbnailUrl = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=500",
                        type = "VIDEO",
                        sourceType = "LINK",
                        category = "Film Pendek",
                        durationMs = 596000L
                    ),
                    MediaItem(
                        id = UUID.randomUUID().toString(),
                        profileId = defaultProfile.id,
                        title = "Elephants Dream (Sci-Fi Animation)",
                        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                        thumbnailUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=500",
                        type = "VIDEO",
                        sourceType = "LINK",
                        category = "Animasi Sci-Fi",
                        durationMs = 653000L
                    ),
                    MediaItem(
                        id = UUID.randomUUID().toString(),
                        profileId = defaultProfile.id,
                        title = "Sintel Cinema trailer HD",
                        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                        thumbnailUrl = "https://images.unsplash.com/photo-1518791841217-8f162f1e1131?w=500",
                        type = "VIDEO",
                        sourceType = "FILE",
                        category = "Trailer",
                        durationMs = 52000L
                    ),
                    MediaItem(
                        id = UUID.randomUUID().toString(),
                        profileId = defaultProfile.id,
                        title = "Tears of Steel (VFX Studio)",
                        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                        thumbnailUrl = "https://images.unsplash.com/photo-1574375927938-d5a98e8ffe85?w=500",
                        type = "VIDEO",
                        sourceType = "SERVER",
                        category = "Cyberpunk VFX",
                        durationMs = 734000L
                    ),
                    MediaItem(
                        id = UUID.randomUUID().toString(),
                        profileId = defaultProfile.id,
                        title = "Poster Cyberpunk Wallpaper 4K",
                        url = "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=1200",
                        thumbnailUrl = "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=500",
                        type = "PHOTO",
                        sourceType = "LINK",
                        category = "Galeri Foto"
                    )
                )
                mediaDao.insertAll(sampleMedia)

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

    fun switchProfile(profile: Profile) {
        viewModelScope.launch {
            _activeProfile.value = profile
            _settings.value = profileDao.getSettings(profile.id) ?: ProfileSettings(profileId = profile.id)
            observeActiveProfileMedia()
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

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectFilter(filter: String) {
        _selectedFilter.value = filter
    }

    // --- Link Parsing (B1) ---
    fun parseAndAddLink(url: String, onFinished: (String) -> Unit) {
        val currentProfileId = _activeProfile.value?.id ?: return
        viewModelScope.launch {
            when (val result = LinkExtractor.parseUrl(url, currentProfileId)) {
                is ParseResult.Single -> {
                    mediaDao.insertMedia(result.item)
                    showStatus("1 Media berhasil ditambahkan: ${result.item.title}")
                    onFinished("Berhasil menambahkan: ${result.item.title}")
                }
                is ParseResult.Multiple -> {
                    mediaDao.insertAll(result.items)
                    showStatus("${result.items.size} item berhasil ditambahkan ke beranda!")
                    onFinished("${result.items.size} item berhasil ditambahkan (${result.title})")
                }
                is ParseResult.Error -> {
                    showStatus("Error: ${result.message}")
                    onFinished("Error: ${result.message}")
                }
            }
        }
    }

    // --- File Source (B2) ---
    fun addLocalFileMedia(title: String, fileUri: String, isPhoto: Boolean) {
        val currentProfileId = _activeProfile.value?.id ?: return
        viewModelScope.launch {
            val newItem = MediaItem(
                id = UUID.randomUUID().toString(),
                profileId = currentProfileId,
                title = title.ifEmpty { "File Lokal" },
                url = fileUri,
                thumbnailUrl = if (isPhoto) fileUri else "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=500",
                type = if (isPhoto) "PHOTO" else "VIDEO",
                sourceType = "FILE",
                category = "Penyimpanan Lokal"
            )
            mediaDao.insertMedia(newItem)
            showStatus("File lokal berhasil ditambahkan ke beranda!")
        }
    }

    // --- Server Source (B3) ---
    fun uploadMediaToServer(title: String, url: String, isPhoto: Boolean) {
        val currentProfileId = _activeProfile.value?.id ?: return
        viewModelScope.launch {
            val newItem = MediaItem(
                id = UUID.randomUUID().toString(),
                profileId = currentProfileId,
                title = title.ifEmpty { "Server Upload" },
                url = url,
                thumbnailUrl = if (isPhoto) url else "https://images.unsplash.com/photo-1574375927938-d5a98e8ffe85?w=500",
                type = if (isPhoto) "PHOTO" else "VIDEO",
                sourceType = "SERVER",
                category = "Media Server"
            )
            mediaDao.insertMedia(newItem)
            showStatus("Media berhasil di-upload ke server & beranda!")
        }
    }

    fun deleteMedia(item: MediaItem) {
        viewModelScope.launch {
            mediaDao.deleteMedia(item)
            showStatus("Media '${item.title}' dihapus")
        }
    }

    // --- Player Management ---
    fun playMedia(item: MediaItem) {
        _currentlyPlaying.value = item
        player.play(item.url, item.title, item.progressMs)
    }

    fun closePlayer() {
        val current = _currentlyPlaying.value
        if (current != null) {
            val pos = player.playerState.value.currentPositionMs
            viewModelScope.launch {
                mediaDao.updateProgress(current.id, pos)
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
