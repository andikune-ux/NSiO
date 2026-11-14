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
import java.net.HttpURLConnection
import java.net.URL
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

data class CloudStreamPluginEntry(
    val name: String,
    val internalName: String,
    val version: Int,
    val apiVersion: Int,
    val status: Int,
    val url: String,
    val description: String,
    val authors: List<String>,
    val tvTypes: List<String>,
    val language: String,
    val iconUrl: String,
    val fileHash: String,
    val fileSize: Long
)

data class RepoManifest(
    val name: String,
    val description: String,
    val manifestVersion: Int = 1,
    val pluginLists: List<String> = emptyList(),
    val sourceUrl: String = "",
    val plugins: List<CloudStreamPluginEntry> = emptyList()
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
    private val cloudStreamRuntime = CloudStreamPluginRuntime(context)
    companion object {
        private const val MAX_CLOUDSTREAM_ARTIFACT_BYTES = 128L * 1024L * 1024L
        const val PLUGINS_KEY = "installed_online_plugins"
        const val PLUGINS_KEY_LOCAL = "installed_local_plugins"
        const val REPOSITORIES_KEY = "extension_repositories"
    }
    private val _installedPlugins = MutableStateFlow<List<Plugin>>(emptyList())
    val installedPlugins: StateFlow<List<Plugin>> = _installedPlugins.asStateFlow()
    private val _repositories = MutableStateFlow<List<RepoManifest>>(emptyList())
    val repositories: StateFlow<List<RepoManifest>> = _repositories.asStateFlow()

    init {
        loadRepositories()
        loadInstalledPlugins()
    }

    fun getLocalPluginsDirectory(): File = File(context.filesDir, "Cloudstream3/plugins").also { it.mkdirs() }
    fun getOnlinePluginsDirectory(repoHash: String): File = File(context.filesDir, "Extensions/$repoHash").also { it.mkdirs() }

    private fun fetchJson(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "NSiO/1.0")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            require(code in 200..299) { "HTTP $code saat membaca $url" }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parsePluginEntries(json: String): List<CloudStreamPluginEntry> {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val url = item.optString("url").trim()
            val internalName = item.optString("internalName").trim()
            if (url.isBlank() || internalName.isBlank()) return@mapNotNull null
            val authorsJson = item.optJSONArray("authors")
            val tvTypesJson = item.optJSONArray("tvTypes")
            CloudStreamPluginEntry(
                name = item.optString("name").ifBlank { internalName },
                internalName = internalName,
                version = item.optInt("version", 1),
                apiVersion = item.optInt("apiVersion", 1),
                status = item.optInt("status", 1),
                url = url,
                description = item.optString("description").ifBlank {
                    (0 until (tvTypesJson?.length() ?: 0)).joinToString(", ") { tvTypesJson!!.optString(it) }
                },
                authors = (0 until (authorsJson?.length() ?: 0)).map { authorsJson!!.optString(it) },
                tvTypes = (0 until (tvTypesJson?.length() ?: 0)).map { tvTypesJson!!.optString(it) },
                language = item.optString("language"),
                iconUrl = item.optString("iconUrl"),
                fileHash = item.optString("fileHash"),
                fileSize = item.optLong("fileSize", 0L)
            )
        }
    }

    private fun loadCloudStreamCatalog(sourceUrl: String): RepoManifest {
        val payload = fetchJson(sourceUrl).trim()
        if (payload.startsWith("[")) {
            val entries = parsePluginEntries(payload)
            return RepoManifest(
                name = "CloudStream Plugin List",
                description = "${entries.size} plugin ditemukan",
                pluginLists = listOf(sourceUrl),
                sourceUrl = sourceUrl,
                plugins = entries
            )
        }
        val root = JSONObject(payload)
        val pluginListsArray = root.optJSONArray("pluginLists")
            ?: throw IllegalArgumentException("Manifest tidak memiliki field pluginLists.")
        val pluginListUrls = (0 until pluginListsArray.length())
            .map { pluginListsArray.optString(it).trim() }
            .filter { it.startsWith("https://") || it.startsWith("http://") }
        require(pluginListUrls.isNotEmpty()) { "Manifest tidak memiliki URL plugins.json yang valid." }
        val entries = pluginListUrls.flatMap { listUrl ->
            val listJson = fetchJson(listUrl).trim()
            require(listJson.startsWith("[")) { "Daftar plugin dari $listUrl bukan JSON array." }
            parsePluginEntries(listJson)
        }.distinctBy { it.internalName to it.url }
        return RepoManifest(
            name = root.optString("name").ifBlank { "CloudStream Repository" },
            description = root.optString("description").ifBlank { "${entries.size} plugin ditemukan" },
            manifestVersion = root.optInt("manifestVersion", 1),
            pluginLists = pluginListUrls,
            sourceUrl = sourceUrl,
            plugins = entries
        )
    }

    suspend fun addRepository(name: String, url: String) = withContext(Dispatchers.IO) {
        val normalized = url.trim()
        val uri = try { URI(normalized) } catch (_: Exception) { null }
        require(uri?.scheme in listOf("http", "https") && !uri?.host.isNullOrBlank()) { "URL repository tidak valid" }
        val canonicalUrl = uri!!.normalize().let {
            URI(it.scheme.lowercase(), it.userInfo, it.host.lowercase(), it.port, it.path, it.query, it.fragment).toASCIIString()
        }
        require(_repositories.value.none { repo ->
            val existing = repo.sourceUrl.ifBlank { repo.pluginLists.firstOrNull().orEmpty() }
            runCatching {
                val saved = URI(existing).normalize()
                URI(saved.scheme?.lowercase(), saved.userInfo, saved.host?.lowercase(), saved.port, saved.path, saved.query, saved.fragment).toASCIIString() == canonicalUrl
            }.getOrDefault(existing == normalized)
        }) { "Repository ini sudah tersimpan." }
        val catalog = loadCloudStreamCatalog(normalized)
        val repo = catalog.copy(name = catalog.name.ifBlank { name.trim().ifBlank { "Repositori Eksternal" } })
        _repositories.value = _repositories.value + repo
        saveRepositories()
    }

    suspend fun refreshRepositoryCatalog(repo: RepoManifest): RepoManifest = withContext(Dispatchers.IO) {
        val source = repo.sourceUrl.ifBlank { repo.pluginLists.firstOrNull().orEmpty() }
        require(source.isNotBlank()) { "URL repository tidak ditemukan." }
        val updated = loadCloudStreamCatalog(source)
        _repositories.value = _repositories.value.map { current ->
            if (current.sourceUrl == source || current == repo) updated else current
        }
        saveRepositories()
        updated
    }


    /**
     * Downloads and validates a CloudStream .cs3 archive into app-private storage.
     * Artifact installation is separate from execution: no DEX code is run here.
     */
    suspend fun installCloudStreamPlugin(entry: CloudStreamPluginEntry): File = withContext(Dispatchers.IO) {
        require(entry.status != 0) { "Plugin ini dinonaktifkan oleh repository." }
        val uri = URI(entry.url)
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) {
            "Artifact plugin wajib menggunakan URL HTTPS."
        }
        val safeName = entry.internalName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(80).ifBlank { throw IllegalArgumentException("Nama internal plugin tidak valid.") }
        val root = File(context.filesDir, "cloudstream_plugins").canonicalFile
        require(root.exists() || root.mkdirs()) { "Folder plugin tidak dapat dibuat." }
        val destination = File(root, "${safeName}-${entry.version}.cs3").canonicalFile
        require(destination.parentFile == root) { "Lokasi artifact tidak valid." }
        val temporary = File(root, ".${safeName}-${UUID.randomUUID()}.download")
        val connection = (URL(entry.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "NSiO/1.0")
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            val code = connection.responseCode
            require(code in 200..299) { "Unduhan plugin gagal: HTTP $code" }
            require(connection.url.protocol.equals("https", ignoreCase = true)) {
                "Unduhan dialihkan ke koneksi non-HTTPS."
            }
            require(connection.contentLengthLong <= MAX_CLOUDSTREAM_ARTIFACT_BYTES) {
                "Ukuran plugin melebihi batas 128 MB."
            }
            var total = 0L
            temporary.outputStream().buffered().use { output ->
                connection.inputStream.buffered().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_CLOUDSTREAM_ARTIFACT_BYTES) {
                            "Ukuran plugin melebihi batas 128 MB."
                        }
                        output.write(buffer, 0, count)
                    }
                }
            }
            require(total > 0L) { "File plugin kosong." }
            if (entry.fileSize > 0L) require(total == entry.fileSize) {
                "Ukuran file tidak cocok dengan metadata repository."
            }
            val rawHash = entry.fileHash.trim()
            val expectedHash = rawHash
                .removePrefix("sha256:")
                .removePrefix("sha256-")
                .trim()
            if (rawHash.isNotBlank()) {
                require(expectedHash.matches(Regex("(?i)[a-f0-9]{64}"))) {
                    "Format fileHash repository tidak didukung; hanya SHA-256 yang diterima."
                }
                val sha256 = java.security.MessageDigest.getInstance("SHA-256")
                temporary.inputStream().buffered().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        sha256.update(buffer, 0, count)
                    }
                }
                val digest = sha256.digest().joinToString("") { "%02x".format(it) }
                require(digest.equals(expectedHash, ignoreCase = true)) {
                    "Verifikasi SHA-256 gagal. File plugin ditolak."
                }
            }
            java.util.zip.ZipFile(temporary).use { zip ->
                CloudStreamArchivePolicy.validate(zip)
                val manifestEntry = zip.getEntry("manifest.json")
                    ?: throw IllegalArgumentException("Arsip .cs3 tidak memiliki manifest.json.")
                val dexEntry = zip.getEntry("classes.dex")
                    ?: throw IllegalArgumentException("Arsip .cs3 tidak memiliki classes.dex.")
                require(!manifestEntry.isDirectory && manifestEntry.size in 1..(64L * 1024L)) {
                    "Ukuran manifest.json tidak valid."
                }
                require(!dexEntry.isDirectory && dexEntry.size in 1..MAX_CLOUDSTREAM_ARTIFACT_BYTES) {
                    "Ukuran classes.dex tidak valid."
                }
                val manifestBytes = zip.getInputStream(manifestEntry).use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8 * 1024)
                    var manifestTotal = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        manifestTotal += count
                        require(manifestTotal <= 64 * 1024) {
                            "manifest.json melebihi batas 64 KB."
                        }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                val manifestText = String(manifestBytes, Charsets.UTF_8)
                val pluginClassName = JSONObject(manifestText).optString("pluginClassName").trim()
                require(pluginClassName.matches(Regex("[A-Za-z_$][A-Za-z0-9_$]*(\\\\.[A-Za-z_$][A-Za-z0-9_$]*)+"))) {
                    "manifest.json tidak memiliki pluginClassName yang valid."
                }

                // ZIP central-directory sizes are attacker-controlled metadata. Count the
                // actual decompressed DEX bytes as well, so a forged size cannot bypass
                // the expansion limit or trigger an unbounded allocation during install.
                var dexTotal = 0L
                zip.getInputStream(dexEntry).buffered().use { input ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        dexTotal += count
                        require(dexTotal <= MAX_CLOUDSTREAM_ARTIFACT_BYTES) {
                            "Data classes.dex hasil ekstraksi melebihi batas 128 MB."
                        }
                    }
                }
                require(dexTotal == dexEntry.size) {
                    "Ukuran classes.dex aktual tidak cocok dengan metadata ZIP."
                }
            }
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
            destination
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    fun isCloudStreamPluginInstalled(entry: CloudStreamPluginEntry): Boolean {
        val safeName = entry.internalName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
        val root = File(context.filesDir, "cloudstream_plugins").canonicalFile
        val artifact = File(root, "${safeName}-${entry.version}.cs3").canonicalFile
        return artifact.parentFile == root && artifact.isFile && artifact.length() > 0L
    }

    private fun getCloudStreamArtifact(entry: CloudStreamPluginEntry): File? {
        val safeName = entry.internalName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
        val root = File(context.filesDir, "cloudstream_plugins").canonicalFile
        val artifact = File(root, "${safeName}-${entry.version}.cs3").canonicalFile
        return artifact.takeIf { it.parentFile == root && it.isFile && it.length() > 0L }
    }

    suspend fun loadCloudStreamPlugin(entry: CloudStreamPluginEntry): List<CloudStreamProviderInfo> {
        val artifact = getCloudStreamArtifact(entry)
            ?: throw IllegalStateException("File .cs3 belum terpasang.")
        return cloudStreamRuntime.load(artifact)
    }

    suspend fun searchCloudStreamPlugin(
        entry: CloudStreamPluginEntry,
        query: String
    ): List<CloudStreamSearchItem> {
        val artifact = getCloudStreamArtifact(entry)
            ?: throw IllegalStateException("File .cs3 belum terpasang.")
        return cloudStreamRuntime.search(artifact, query)
    }

    suspend fun loadCloudStreamContent(
        entry: CloudStreamPluginEntry,
        providerName: String,
        url: String
    ): CloudStreamContent {
        val artifact = getCloudStreamArtifact(entry)
            ?: throw IllegalStateException("File .cs3 belum terpasang.")
        return cloudStreamRuntime.loadContent(artifact, providerName, url)
    }

    suspend fun loadCloudStreamLinks(
        entry: CloudStreamPluginEntry,
        providerName: String,
        data: String
    ): List<CloudStreamStream> {
        val artifact = getCloudStreamArtifact(entry)
            ?: throw IllegalStateException("File .cs3 belum terpasang.")
        return cloudStreamRuntime.loadLinks(artifact, providerName, data)
    }

    suspend fun unloadCloudStreamPlugin(entry: CloudStreamPluginEntry) {
        getCloudStreamArtifact(entry)?.let { cloudStreamRuntime.unload(it) }
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
                RepoManifest(
                    name = obj.optString("name", "Repositori"),
                    description = obj.optString("description"),
                    manifestVersion = obj.optInt("manifestVersion", 1),
                    pluginLists = (0 until urls.length()).map { urls.optString(it) }.filter { it.isNotBlank() },
                    sourceUrl = obj.optString("sourceUrl"),
                    plugins = obj.optJSONArray("plugins")?.let { entries ->
                        (0 until entries.length()).mapNotNull { index ->
                            entries.optJSONObject(index)?.let { item ->
                                val authors = item.optJSONArray("authors")
                                val types = item.optJSONArray("tvTypes")
                                CloudStreamPluginEntry(
                                    item.optString("name"), item.optString("internalName"), item.optInt("version", 1),
                                    item.optInt("apiVersion", 1), item.optInt("status", 1), item.optString("url"),
                                    item.optString("description"), (0 until (authors?.length() ?: 0)).map { authors!!.optString(it) },
                                    (0 until (types?.length() ?: 0)).map { types!!.optString(it) }, item.optString("language"),
                                    item.optString("iconUrl"), item.optString("fileHash"), item.optLong("fileSize", 0L)
                                )
                            }
                        }
                    }.orEmpty()
                )
            }
        }
    }

    private fun saveRepositories() {
        val array = JSONArray()
        _repositories.value.forEach { repo ->
            array.put(JSONObject().apply {
                put("name", repo.name); put("description", repo.description); put("manifestVersion", repo.manifestVersion)
                put("pluginLists", JSONArray(repo.pluginLists))
                put("sourceUrl", repo.sourceUrl)
                put("plugins", JSONArray().apply {
                    repo.plugins.forEach { plugin ->
                        put(JSONObject().apply {
                            put("name", plugin.name); put("internalName", plugin.internalName)
                            put("version", plugin.version); put("apiVersion", plugin.apiVersion)
                            put("status", plugin.status); put("url", plugin.url)
                            put("description", plugin.description); put("authors", JSONArray(plugin.authors))
                            put("tvTypes", JSONArray(plugin.tvTypes)); put("language", plugin.language)
                            put("iconUrl", plugin.iconUrl); put("fileHash", plugin.fileHash); put("fileSize", plugin.fileSize)
                        })
                    }
                })
            })
        }
        prefs.edit().putString(REPOSITORIES_KEY, array.toString()).apply()
    }

    /**
     * Restores NSiO-native plugin metadata placeholders. CloudStream .cs3
     * artifacts are managed and executed by CloudStreamPluginRuntime.
     */
    private fun loadInstalledPlugins() {
        val raw = prefs.getString(PLUGINS_KEY, null) ?: return
        runCatching {
            val array = JSONArray(raw)
            _installedPlugins.value = (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val manifest = PluginManifest(
                    name = obj.optString("name").trim(),
                    pluginClassName = obj.optString("pluginClassName"),
                    version = obj.optInt("version", 1),
                    description = obj.optString("description"),
                    url = obj.optString("url"),
                    author = obj.optString("author", "NSiO Community"),
                    iconUrl = obj.optString("iconUrl")
                )
                if (manifest.name.isBlank()) return@mapNotNull null
                object : Plugin() {
                    init {
                        this.manifest = manifest
                        this.filename = manifest.url
                        load(context)
                    }
                }
            }
        }.onFailure {
            // Keep the app usable if a previous plugin metadata file is malformed.
            _installedPlugins.value = emptyList()
        }
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
