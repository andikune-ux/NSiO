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
import java.net.URI
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
    override fun load(context: Context) { isLoaded = true }
    override fun beforeUnload() { isLoaded = false }
    open fun registerVideoClickAction(action: (MediaItem) -> Unit) { onVideoClick = action }
    open suspend fun searchMedia(query: String): List<MediaItem> = emptyList()
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

    init { loadRepositories() }

    fun getLocalPluginsDirectory(): File = File(context.filesDir, "Cloudstream3/plugins").also { it.mkdirs() }
    fun getOnlinePluginsDirectory(repoHash: String): File = File(context.filesDir, "Extensions/$repoHash").also { it.mkdirs() }

    suspend fun addRepository(name: String, url: String) = withContext(Dispatchers.IO) {
        val normalized = url.trim()
        val uri = try { URI(normalized) } catch (_: Exception) { null }
        require(uri?.scheme in listOf("http", "https") && !uri?.host.isNullOrBlank()) { "URL repository tidak valid" }
        val newRepo = RepoManifest(name.trim().ifEmpty { "Repositori Eksternal" }, "Repositori pengguna: $normalized", 1, listOf(normalized))
        if (_repositories.value.none { it.pluginLists == newRepo.pluginLists }) {
            _repositories.value = _repositories.value + newRepo
            saveRepositories()
        }
    }

    suspend fun installPluginFromManifest(manifest: PluginManifest) = withContext(Dispatchers.IO) {
        require(manifest.name.isNotBlank()) { "Manifest plugin tidak memiliki nama" }
        if (_installedPlugins.value.none { it.manifest?.name == manifest.name && it.manifest?.url == manifest.url }) {
            val plugin = object : Plugin() { init { this.manifest = manifest } }.apply { load(context) }
            _installedPlugins.value = _installedPlugins.value + plugin
            savePlugins()
        }
    }

    fun unloadPlugin(plugin: Plugin) {
        plugin.beforeUnload()
        _installedPlugins.value = _installedPlugins.value.filter { it !== plugin }
        savePlugins()
    }

    private fun loadRepositories() {
        val raw = prefs.getString(REPOSITORIES_KEY, null) ?: return
        runCatching {
            val arr = JSONArray(raw)
            _repositories.value = (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val urls = obj.optJSONArray("pluginLists") ?: JSONArray()
                RepoManifest(obj.optString("name", "Repositori"), obj.optString("description"), obj.optInt("manifestVersion", 1), (0 until urls.length()).map { urls.optString(it) }.filter { it.isNotBlank() })
            }
        }
    }

    private fun saveRepositories() {
        val array = JSONArray()
        _repositories.value.forEach { repo ->
            array.put(JSONObject().apply {
                put("name", repo.name); put("description", repo.description); put("manifestVersion", repo.manifestVersion)
                put("pluginLists", JSONArray(repo.pluginLists))
            })
        }
        prefs.edit().putString(REPOSITORIES_KEY, array.toString()).apply()
    }

    private fun savePlugins() {
        val array = JSONArray()
        _installedPlugins.value.mapNotNull { it.manifest }.forEach { manifest ->
            array.put(JSONObject().apply {
                put("name", manifest.name); put("pluginClassName", manifest.pluginClassName); put("version", manifest.version)
                put("description", manifest.description); put("url", manifest.url); put("author", manifest.author); put("iconUrl", manifest.iconUrl)
            })
        }
        prefs.edit().putString(PLUGINS_KEY, array.toString()).apply()
    }
}
