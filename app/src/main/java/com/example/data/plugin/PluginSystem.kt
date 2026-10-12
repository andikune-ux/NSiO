package com.example.data.plugin

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class PluginManifest(
    val name: String,
    val pluginClassName: String,
    val version: Int,
    val description: String,
    val url: String = "",
    val author: String = "NSiO Community",
    val iconUrl: String = ""
)

data class RepoManifest(
    val name: String,
    val description: String,
    val manifestVersion: Int = 1,
    val pluginLists: List<String> = emptyList()
)

abstract class BasePlugin {
    open var filename: String = ""
    open var manifest: PluginManifest? = null
    open var isLoaded: Boolean = false

    abstract fun load(context: Context)
    abstract fun beforeUnload()
}

open class Plugin : BasePlugin() {
    var openSettings: (() -> Unit)? = null
    var onVideoClick: ((MediaItem) -> Unit)? = null

    override fun load(context: Context) {
        isLoaded = true
    }

    override fun beforeUnload() {
        isLoaded = false
    }

    open fun registerVideoClickAction(action: (MediaItem) -> Unit) {
        this.onVideoClick = action
    }

    open suspend fun searchMedia(query: String): List<MediaItem> {
        return emptyList()
    }
}

class SampleStreamPlugin : Plugin() {
    init {
        manifest = PluginManifest(
            name = "Indo Stream & Film HD",
            pluginClassName = "com.example.plugin.IndoStreamPlugin",
            version = 1,
            description = "Menyediakan katalog film Indonesia dan live streaming lokal",
            author = "andikune"
        )
    }

    override suspend fun searchMedia(query: String): List<MediaItem> {
        return listOf(
            MediaItem(
                id = UUID.randomUUID().toString(),
                profileId = "",
                title = "Big Buck Bunny 4K Cinema",
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                thumbnailUrl = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=500",
                type = "VIDEO",
                sourceType = "LINK",
                category = "Film HD"
            ),
            MediaItem(
                id = UUID.randomUUID().toString(),
                profileId = "",
                title = "Elephants Dream Cyber Animation",
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                thumbnailUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=500",
                type = "VIDEO",
                sourceType = "LINK",
                category = "Animasi"
            )
        )
    }
}

class PluginManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("nsio_plugins_prefs", Context.MODE_PRIVATE)

    companion object {
        const val PLUGINS_KEY = "installed_online_plugins"
        const val PLUGINS_KEY_LOCAL = "installed_local_plugins"
        const val REPOSITORIES_KEY = "extension_repositories"
    }

    private val _installedPlugins = MutableStateFlow<List<Plugin>>(emptyList())
    val installedPlugins: StateFlow<List<Plugin>> = _installedPlugins.asStateFlow()

    private val _repositories = MutableStateFlow<List<RepoManifest>>(emptyList())
    val repositories: StateFlow<List<RepoManifest>> = _repositories.asStateFlow()

    init {
        // Bootstrap sample built-in plugin and repo
        val sample = SampleStreamPlugin().apply { load(context) }
        _installedPlugins.value = listOf(sample)
        _repositories.value = listOf(
            RepoManifest(
                name = "CloudStream English / Indo Repo",
                description = "Repositori resmi penyedia media streaming CloudStream & NSiO",
                manifestVersion = 1,
                pluginLists = listOf("https://raw.githubusercontent.com/cloudstream/repo/main/plugins.json")
            )
        )
    }

    fun getLocalPluginsDirectory(): File {
        val dir = File(context.filesDir, "Cloudstream3/plugins")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getOnlinePluginsDirectory(repoHash: String): File {
        val dir = File(context.filesDir, "Extensions/$repoHash")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun addRepository(name: String, url: String) = withContext(Dispatchers.IO) {
        val newRepo = RepoManifest(
            name = name,
            description = "Repositori pengguna: $url",
            manifestVersion = 1,
            pluginLists = listOf(url)
        )
        _repositories.value = _repositories.value + newRepo
        saveRepositories()
    }

    suspend fun installPluginFromManifest(manifest: PluginManifest) = withContext(Dispatchers.IO) {
        val dynamicPlugin = object : Plugin() {
            init {
                this.manifest = manifest
            }
        }.apply { load(context) }

        _installedPlugins.value = _installedPlugins.value + dynamicPlugin
    }

    fun unloadPlugin(plugin: Plugin) {
        plugin.beforeUnload()
        _installedPlugins.value = _installedPlugins.value.filter { it != plugin }
    }

    private fun saveRepositories() {
        val array = JSONArray()
        _repositories.value.forEach { repo ->
            val obj = JSONObject()
            obj.put("name", repo.name)
            obj.put("description", repo.description)
            val listArr = JSONArray()
            repo.pluginLists.forEach { listArr.put(it) }
            obj.put("pluginLists", listArr)
            array.put(obj)
        }
        prefs.edit().putString(REPOSITORIES_KEY, array.toString()).apply()
    }
}
