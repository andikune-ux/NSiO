package com.example.data.plugin

import android.content.Context
import dalvik.system.DexClassLoader
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.extractorApis
import com.lagradost.cloudstream3.utils.loadExtractor
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

data class CloudStreamProviderInfo(
    val pluginName: String,
    val providerName: String,
    val mainUrl: String
)

data class CloudStreamSearchItem(
    val pluginName: String,
    val providerName: String,
    val title: String,
    val url: String,
    val posterUrl: String?,
    val contentType: String?,
    val year: Int?
)

data class CloudStreamEpisodeItem(
    val name: String,
    val data: String,
    val season: Int?,
    val episode: Int?,
    val posterUrl: String?
)

data class CloudStreamContent(
    val pluginName: String,
    val providerName: String,
    val title: String,
    val url: String,
    val posterUrl: String?,
    val backgroundPosterUrl: String?,
    val plot: String?,
    val year: Int?,
    val durationMinutes: Int?,
    val contentType: String,
    val dataUrl: String?,
    val episodes: List<CloudStreamEpisodeItem>
)

data class CloudStreamStream(
    val pluginName: String,
    val providerName: String,
    val name: String,
    val source: String,
    val url: String,
    val referer: String,
    val headers: Map<String, String>,
    val quality: Int,
    val streamType: String
)

class CloudStreamPluginRuntime(private val context: Context) {
    private data class LoadedPlugin(
        val instance: Any,
        val classLoader: DexClassLoader,
        val artifactPath: String,
        val artifactLength: Long,
        val artifactLastModified: Long,
        val providers: List<MainAPI>
    )

    private val loadMutex = Mutex()
    private val pluginCache = mutableMapOf<String, LoadedPlugin>()

    companion object {
        private const val SEARCH_TIMEOUT_MS = 30_000L
        private const val LOAD_TIMEOUT_MS = 30_000L
        private const val LINKS_TIMEOUT_MS = 45_000L
    }

    suspend fun load(artifact: File): List<CloudStreamProviderInfo> = withContext(Dispatchers.IO) {
        val loaded = loadPlugin(artifact)
        loaded.providers.map { api ->
            CloudStreamProviderInfo(
                pluginName = artifact.nameWithoutExtension,
                providerName = api.name,
                mainUrl = api.mainUrl
            )
        }
    }

    suspend fun search(artifact: File, query: String): List<CloudStreamSearchItem> = withContext(Dispatchers.IO) {
        require(query.isNotBlank()) { "Masukkan kata pencarian terlebih dahulu." }
        val loaded = loadPlugin(artifact)
        loaded.providers.flatMap { api ->
            val results = withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                val paginated = runCatching { api.search(query, 1)?.list }.getOrNull()
                paginated ?: runCatching { api.search(query).orEmpty() }.getOrDefault(emptyList())
            }.orEmpty()

            results.map { result ->
                CloudStreamSearchItem(
                    pluginName = artifact.nameWithoutExtension,
                    providerName = api.name,
                    title = result.name,
                    url = result.url,
                    posterUrl = result.posterUrl,
                    contentType = result.type?.toString(),
                    year = readProperty(result, "year") as? Int
                )
            }
        }
    }

    suspend fun loadContent(
        artifact: File,
        providerName: String,
        url: String
    ): CloudStreamContent = withContext(Dispatchers.IO) {
        val api = loadPlugin(artifact).providers.firstOrNull { it.name == providerName }
            ?: throw IllegalArgumentException("Provider CloudStream tidak ditemukan: $providerName")
        val response = withTimeoutOrNull(LOAD_TIMEOUT_MS) { api.load(url) }
            ?: throw IllegalStateException("Provider tidak mengembalikan detail konten atau waktu pemuatan habis.")

        return response.toCloudStreamContent(artifact.nameWithoutExtension, api.name)
    }

    suspend fun loadLinks(
        artifact: File,
        providerName: String,
        data: String
    ): List<CloudStreamStream> = withContext(Dispatchers.IO) {
        require(data.isNotBlank()) { "Data URL konten kosong." }
        val api = loadPlugin(artifact).providers.firstOrNull { it.name == providerName }
            ?: throw IllegalArgumentException("Provider CloudStream tidak ditemukan: $providerName")

        val rawLinks = mutableListOf<ExtractorLink>()
        val completed = withTimeoutOrNull(LINKS_TIMEOUT_MS) {
            api.loadLinks(
                data = data,
                isCasting = false,
                subtitleCallback = { },
                callback = { link -> synchronized(rawLinks) { rawLinks.add(link) } }
            )
        } ?: false

        if (!completed && rawLinks.isEmpty()) return emptyList()

        val resolved = mutableListOf<ExtractorLink>()
        rawLinks.forEach { raw ->
            val before = synchronized(resolved) { resolved.size }
            val handled = withTimeoutOrNull(LINKS_TIMEOUT_MS) {
                runCatching {
                    loadExtractor(
                        url = raw.url,
                        referer = raw.referer,
                        subtitleCallback = { },
                        callback = { link -> synchronized(resolved) { resolved.add(link) } }
                    )
                }.getOrDefault(false)
            } ?: false
            val emitted = synchronized(resolved) { resolved.size > before }
            if (!handled || !emitted) synchronized(resolved) { resolved.add(raw) }
        }

        return resolved.distinctBy { it.url to it.referer }.map { link ->
            CloudStreamStream(
                pluginName = artifact.nameWithoutExtension,
                providerName = api.name,
                name = link.name,
                source = link.source,
                url = link.url,
                referer = link.referer,
                headers = link.headers,
                quality = link.quality,
                streamType = link.type.toString()
            )
        }
    }

    suspend fun unload(artifact: File) = withContext(Dispatchers.IO) {
        val key = artifact.canonicalPath
        loadMutex.withLock {
            val loaded = pluginCache.remove(key) ?: return@withLock
            runCatching {
                when (val instance = loaded.instance) {
                    is Plugin -> instance.beforeUnload()
                    is BasePlugin -> instance.beforeUnload()
                }
            }
            loaded.providers.forEach { api ->
                runCatching { APIHolder.removePluginMapping(api) }
                synchronized(APIHolder.allProviders) { APIHolder.allProviders.remove(api) }
            }
            extractorApis.removeAll { it.sourcePlugin == loaded.artifactPath }
            runCatching { APIHolder.initAll() }
        }
    }

    private suspend fun loadPlugin(artifact: File): LoadedPlugin = loadMutex.withLock {
        val canonical = artifact.canonicalFile
        val root = File(context.filesDir, "cloudstream_plugins").canonicalFile
        require(canonical.isFile && canonical.parentFile == root) {
            "Artifact plugin tidak berada di folder penyimpanan privat NSiO."
        }
        val key = canonical.path
        pluginCache[key]?.let { cached ->
            if (canonical.length() == cached.artifactLength &&
                canonical.lastModified() == cached.artifactLastModified
            ) {
                return@withLock cached
            }
            unloadCached(key, cached)
            pluginCache.remove(key)
        }

        val pluginClassName = ZipFile(canonical).use { zip ->
            CloudStreamArchivePolicy.validate(zip)
            val manifestEntry = zip.getEntry("manifest.json")
                ?: throw IllegalArgumentException("Arsip tidak memiliki manifest.json.")
            val dexEntry = zip.getEntry("classes.dex")
                ?: throw IllegalArgumentException("Arsip tidak memiliki classes.dex.")
            require(!dexEntry.isDirectory && dexEntry.size in 1..CloudStreamArchivePolicy.MAX_ENTRY_EXPANDED_BYTES) {
                "Ukuran classes.dex tidak valid."
            }
            require(!manifestEntry.isDirectory && manifestEntry.size in 1..(64L * 1024L)) {
                "Ukuran manifest plugin tidak valid."
            }
            val manifestText = zip.getInputStream(manifestEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            JSONObject(manifestText).optString("pluginClassName").trim()
        }
        require(pluginClassName.matches(Regex("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+"))) {
            "pluginClassName pada manifest tidak valid."
        }

        val dexDirectory = File(
            context.codeCacheDir,
            "cloudstream_plugins/${canonical.nameWithoutExtension}"
        ).apply { mkdirs() }
        val loader = DexClassLoader(
            canonical.absolutePath,
            dexDirectory.absolutePath,
            null,
            BasePlugin::class.java.classLoader
        )
        val pluginClass = Class.forName(pluginClassName, true, loader)
        val instance = pluginClass.getDeclaredConstructor().newInstance()
        require(instance is Plugin || instance is BasePlugin) {
            "Class plugin harus mewarisi CloudStream Plugin atau BasePlugin."
        }

        val before = synchronized(APIHolder.allProviders) { APIHolder.allProviders.toList() }
        try {
            when (instance) {
                is Plugin -> {
                    instance.__filename = canonical.absolutePath
                    instance.load(context)
                }
                is BasePlugin -> {
                    instance.filename = canonical.absolutePath
                    instance.load()
                }
            }
            val after = synchronized(APIHolder.allProviders) { APIHolder.allProviders.toList() }
            val providers = (after - before.toSet()).distinctBy { "${it.name}|${it.mainUrl}|${it.javaClass.name}" }
            runCatching { APIHolder.initAll() }
            val loaded = LoadedPlugin(
                instance = instance,
                classLoader = loader,
                artifactPath = canonical.absolutePath,
                artifactLength = canonical.length(),
                artifactLastModified = canonical.lastModified(),
                providers = providers
            )
            pluginCache[key] = loaded
            loaded
        } catch (error: Throwable) {
            val after = synchronized(APIHolder.allProviders) { APIHolder.allProviders.toList() }
            (after - before.toSet()).forEach { api ->
                runCatching { APIHolder.removePluginMapping(api) }
                synchronized(APIHolder.allProviders) { APIHolder.allProviders.remove(api) }
            }
            extractorApis.removeAll { it.sourcePlugin == canonical.absolutePath }
            runCatching { APIHolder.initAll() }
            runCatching {
                when (instance) {
                    is Plugin -> instance.beforeUnload()
                    is BasePlugin -> instance.beforeUnload()
                }
            }
            throw IllegalStateException(
                "Plugin $pluginClassName gagal dimuat: ${error.message ?: error.javaClass.simpleName}",
                error
            )
        }
    }

    private fun unloadCached(key: String, loaded: LoadedPlugin) {
        runCatching {
            when (val instance = loaded.instance) {
                is Plugin -> instance.beforeUnload()
                is BasePlugin -> instance.beforeUnload()
            }
        }
        loaded.providers.forEach { api ->
            runCatching { APIHolder.removePluginMapping(api) }
            synchronized(APIHolder.allProviders) { APIHolder.allProviders.remove(api) }
        }
        extractorApis.removeAll { it.sourcePlugin == loaded.artifactPath }
        runCatching { APIHolder.initAll() }
    }

    private fun LoadResponse.toCloudStreamContent(
        pluginName: String,
        providerName: String
    ): CloudStreamContent {
        val rawEpisodes = readProperty(this, "episodes")
        val episodeObjects = when (rawEpisodes) {
            is Iterable<*> -> rawEpisodes.toList()
            is Map<*, *> -> rawEpisodes.values.flatMap { value ->
                (value as? Iterable<*>)?.toList().orEmpty()
            }
            else -> emptyList()
        }
        val episodes = episodeObjects.mapNotNull { item ->
            item ?: return@mapNotNull null
            val episodeData = readProperty(item, "data") as? String ?: return@mapNotNull null
            CloudStreamEpisodeItem(
                name = (readProperty(item, "name") as? String).orEmpty(),
                data = episodeData,
                season = readProperty(item, "season") as? Int,
                episode = readProperty(item, "episode") as? Int,
                posterUrl = readProperty(item, "posterUrl") as? String
            )
        }

        return CloudStreamContent(
            pluginName = pluginName,
            providerName = providerName,
            title = name,
            url = url,
            posterUrl = posterUrl,
            backgroundPosterUrl = backgroundPosterUrl,
            plot = plot,
            year = year,
            durationMinutes = duration,
            contentType = type.toString(),
            dataUrl = readProperty(this, "dataUrl") as? String,
            episodes = episodes
        )
    }

    private fun readProperty(instance: Any, property: String): Any? {
        val getter = "get" + property.replaceFirstChar { it.uppercase() }
        return runCatching {
            instance.javaClass.methods.firstOrNull { it.name == getter && it.parameterCount == 0 }
                ?.invoke(instance)
        }.getOrNull()
    }
}
