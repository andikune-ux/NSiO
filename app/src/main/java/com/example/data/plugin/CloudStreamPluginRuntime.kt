package com.example.data.plugin

import android.content.Context
import dalvik.system.DexClassLoader
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.extractorApis
import com.lagradost.cloudstream3.utils.loadExtractor
import java.io.File
import java.lang.ref.WeakReference
import java.util.zip.ZipFile
import com.lagradost.api.setContext
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

data class CloudStreamResolvedId(
    val pluginName: String,
    val providerName: String,
    val syncIdName: String,
    val contentUrl: String
)

data class CloudStreamSearchPage(
    val items: List<CloudStreamSearchItem>,
    val hasNext: Boolean,
    val page: Int
)

data class CloudStreamHomeSection(
    val pluginName: String,
    val providerName: String,
    val sectionName: String,
    val items: List<CloudStreamSearchItem>,
    val hasNext: Boolean,
    val requestName: String = sectionName
)

data class CloudStreamEpisodeItem(
    val name: String,
    val data: String,
    val season: Int?,
    val episode: Int?,
    val posterUrl: String?,
    val description: String? = null,
    val rating: Int? = null,
    val date: Long? = null,
    val runtimeSeconds: Int? = null,
    val dubStatus: String? = null
)

data class CloudStreamCastMember(val name: String, val role: String?, val imageUrl: String?, val voiceActorName: String? = null, val voiceActorImageUrl: String? = null)
data class CloudStreamRecommendation(val title: String, val url: String, val posterUrl: String?, val contentType: String?, val year: Int?)
data class CloudStreamTrailer(val url: String, val referer: String?, val raw: Boolean)

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
    val episodes: List<CloudStreamEpisodeItem>,
    val rating: Int? = null,
    val tags: List<String> = emptyList(),
    val contentRating: String? = null,
    val showStatus: String? = null,
    val comingSoon: Boolean = false,
    val cast: List<CloudStreamCastMember> = emptyList(),
    val trailers: List<CloudStreamTrailer> = emptyList(),
    val recommendations: List<CloudStreamRecommendation> = emptyList(),
    val seasonNames: Map<Int, String> = emptyMap(),
    val externalIds: Map<String, String> = emptyMap(),
    val englishTitle: String? = null,
    val japaneseTitle: String? = null,
    val synonyms: List<String> = emptyList(),
    val nextAiringEpisode: Int? = null,
    val nextAiringUnixTime: Long? = null,
    val nextAiringSeason: Int? = null,
    val posterHeaders: Map<String, String> = emptyMap(),
    val magnetUrl: String? = null,
    val torrentUrl: String? = null
)

data class CloudStreamSubtitle(
    val language: String,
    val url: String,
    val headers: Map<String, String> = emptyMap()
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
    val streamType: String,
    val subtitles: List<CloudStreamSubtitle> = emptyList(),
    val extractorData: String? = null,
    val videoInterceptor: okhttp3.Interceptor? = null
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

    /**
     * Reads the provider's declared CloudStream home-page categories. Providers
     * without a main page are reported as unsupported rather than silently
     * substituting search results.
     */
    suspend fun loadHomePage(
        artifact: File,
        page: Int = 1,
        sectionPages: Map<String, Int>? = null
    ): List<CloudStreamHomeSection> =
        withContext(Dispatchers.IO) {
            require(page >= 1) { "Nomor halaman katalog tidak valid." }
            val loaded = loadPlugin(artifact)
            loaded.providers.filter { it.hasMainPage }.flatMap { api ->
                api.mainPage.mapNotNull requestLoop@ { request ->
                    val sectionKey = "${api.name}|${request.name}"
                    val sectionPage = sectionPages?.get(sectionKey) ?: page
                    if (sectionPage <= 0) return@requestLoop null
                    val response = withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                        runCatching {
                            api.getMainPage(sectionPage, MainPageRequest(request.name, request.data, request.horizontalImages))
                        }.getOrNull()
                    } ?: return@requestLoop null
                    val items = response.items.flatMap { section ->
                        section.list.mapNotNull itemLoop@ { item ->
                            val title = (readProperty(item, "name") as? String).orEmpty()
                            val url = (readProperty(item, "url") as? String).orEmpty()
                            if (title.isBlank() || url.isBlank()) return@itemLoop null
                            CloudStreamSearchItem(
                                pluginName = artifact.nameWithoutExtension,
                                providerName = api.name,
                                title = title,
                                url = url,
                                posterUrl = readProperty(item, "posterUrl") as? String,
                                contentType = readProperty(item, "type")?.toString(),
                                year = readProperty(item, "year") as? Int
                            )
                        }
                    }
                    CloudStreamHomeSection(
                        pluginName = artifact.nameWithoutExtension,
                        providerName = api.name,
                        sectionName = response.items.firstOrNull()?.name?.ifBlank { request.name } ?: request.name,
                        items = items,
                        hasNext = response.hasNext,
                        requestName = request.name
                    )
                }
            }
        }

    suspend fun search(artifact: File, query: String, page: Int = 1): CloudStreamSearchPage = withContext(Dispatchers.IO) {
        require(query.isNotBlank()) { "Masukkan kata pencarian terlebih dahulu." }
        val requestedPage = page.coerceAtLeast(1)
        val loaded = loadPlugin(artifact)
        val pages = loaded.providers.mapNotNull { api ->
            withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                val response = if (requestedPage == 1) {
                    val quick = runCatching { api.quickSearch(query) }.getOrNull()
                    if (quick != null) {
                        com.lagradost.cloudstream3.SearchResponseList(quick, hasNext = false)
                    } else {
                        runCatching { api.search(query, requestedPage) }.getOrNull()
                            ?: runCatching { api.search(query)?.let { com.lagradost.cloudstream3.SearchResponseList(it, hasNext = false) } }.getOrNull()
                    }
                } else {
                    runCatching { api.search(query, requestedPage) }.getOrNull()
                }
                response?.let { api to it }
            }
        }
        val items = pages.flatMap { (api, response) ->
            response.list.map { result ->
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
        CloudStreamSearchPage(
            items = items,
            hasNext = pages.any { (_, response) -> response.hasNext },
            page = requestedPage
        )
    }

    suspend fun resolveLoadUrl(
        artifact: File,
        syncIdName: String,
        id: String
    ): List<CloudStreamResolvedId> = withContext(Dispatchers.IO) {
        require(id.isNotBlank()) { "ID eksternal kosong." }
        val syncName = when (syncIdName.trim().lowercase().replace("_", "").replace("-", "")) {
            "imdb" -> SyncIdName.Imdb
            "anilist", "ani" -> SyncIdName.Anilist
            "mal", "myanimelist" -> SyncIdName.MyAnimeList
            "trakt" -> SyncIdName.Trakt
            "simkl" -> SyncIdName.Simkl
            else -> throw IllegalArgumentException("Jenis ID eksternal belum didukung: $syncIdName")
        }
        val loaded = loadPlugin(artifact)
        loaded.providers.mapNotNull { api ->
            if (syncName !in api.supportedSyncNames) return@mapNotNull null
            val url = withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                runCatching { api.getLoadUrl(syncName, id.trim()) }.getOrNull()
            }?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CloudStreamResolvedId(
                pluginName = artifact.nameWithoutExtension,
                providerName = api.name,
                syncIdName = syncName.name,
                contentUrl = url
            )
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

        response.toCloudStreamContent(artifact.nameWithoutExtension, api.name)
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
        val subtitles = java.util.Collections.synchronizedList(mutableListOf<CloudStreamSubtitle>())
        val subtitleCallback: (SubtitleFile) -> Unit = { subtitle ->
            if (subtitle.url.isNotBlank()) {
                subtitles.add(CloudStreamSubtitle(
                    language = subtitle.lang.ifBlank { "Unknown" },
                    url = subtitle.url,
                    headers = subtitle.headers.orEmpty()
                ))
            }
        }
        val completed = withTimeoutOrNull(LINKS_TIMEOUT_MS) {
            api.loadLinks(
                data = data,
                isCasting = false,
                subtitleCallback = subtitleCallback,
                callback = { link -> synchronized(rawLinks) { rawLinks.add(link) } }
            )
        } ?: false

        if (!completed && rawLinks.isEmpty()) return@withContext emptyList()

        val resolved = mutableListOf<ExtractorLink>()
        rawLinks.forEach { raw ->
            val before = synchronized(resolved) { resolved.size }
            val handled = withTimeoutOrNull(LINKS_TIMEOUT_MS) {
                runCatching {
                    loadExtractor(
                        url = raw.url,
                        referer = raw.referer,
                        subtitleCallback = subtitleCallback,
                        callback = { link -> synchronized(resolved) { resolved.add(link) } }
                    )
                }.getOrDefault(false)
            } ?: false
            val emitted = synchronized(resolved) { resolved.size > before }
            if (!handled || !emitted) synchronized(resolved) { resolved.add(raw) }
        }

        resolved.distinctBy { it.url to it.referer }.map { link ->
            CloudStreamStream(
                pluginName = artifact.nameWithoutExtension,
                providerName = api.name,
                name = link.name,
                source = link.source,
                url = link.url,
                referer = link.referer,
                headers = link.headers,
                quality = link.quality,
                streamType = link.type.toString(),
                subtitles = synchronized(subtitles) { subtitles.distinctBy { it.language to it.url } },
                extractorData = link.extractorData,
                videoInterceptor = runCatching { api.getVideoInterceptor(link) }.getOrNull()
            )
        }
    }

    suspend fun resolveTrailer(
        artifact: File,
        providerName: String,
        trailer: CloudStreamTrailer
    ): List<CloudStreamStream> = withContext(Dispatchers.IO) {
        require(trailer.url.startsWith("http://", true) || trailer.url.startsWith("https://", true)) {
            "URL trailer tidak valid."
        }
        val loaded = loadPlugin(artifact)
        val api = loaded.providers.firstOrNull { it.name == providerName }
        if (trailer.raw) {
            return@withContext listOf(
                CloudStreamStream(
                    pluginName = artifact.nameWithoutExtension,
                    providerName = providerName,
                    name = "Trailer",
                    source = "CloudStream trailer",
                    url = trailer.url,
                    referer = trailer.referer.orEmpty(),
                    headers = emptyMap(),
                    quality = 0,
                    streamType = trailer.url.substringBefore('?').substringAfterLast('.', "MP4").uppercase()
                )
            )
        }

        val resolved = java.util.Collections.synchronizedList(mutableListOf<ExtractorLink>())
        val subtitles = java.util.Collections.synchronizedList(mutableListOf<CloudStreamSubtitle>())
        val subtitleCallback: (SubtitleFile) -> Unit = { subtitle ->
            if (subtitle.url.isNotBlank()) subtitles.add(
                CloudStreamSubtitle(subtitle.lang.ifBlank { "Unknown" }, subtitle.url, subtitle.headers.orEmpty())
            )
        }
        withTimeoutOrNull(LINKS_TIMEOUT_MS) {
            runCatching {
                loadExtractor(
                    url = trailer.url,
                    referer = trailer.referer.orEmpty(),
                    subtitleCallback = subtitleCallback,
                    callback = { link -> synchronized(resolved) { resolved.add(link) } }
                )
            }
        }
        val links = synchronized(resolved) { resolved.distinctBy { it.url to it.referer } }
        if (links.isEmpty()) {
            val extension = trailer.url.substringBefore('?').substringAfterLast('.', "").lowercase()
            if (extension !in setOf("mp4", "m3u8", "mpd", "webm")) {
                throw IllegalStateException("Extractor trailer tidak menghasilkan stream yang dapat diputar.")
            }
            return@withContext listOf(
                CloudStreamStream(
                    pluginName = artifact.nameWithoutExtension,
                    providerName = providerName,
                    name = "Trailer",
                    source = "Direct trailer",
                    url = trailer.url,
                    referer = trailer.referer.orEmpty(),
                    headers = emptyMap(),
                    quality = 0,
                    streamType = extension.uppercase()
                )
            )
        }
        links.map { link ->
            CloudStreamStream(
                pluginName = artifact.nameWithoutExtension,
                providerName = providerName,
                name = link.name.ifBlank { "Trailer" },
                source = link.source,
                url = link.url,
                referer = link.referer,
                headers = link.headers,
                quality = link.quality,
                streamType = link.type.toString(),
                subtitles = synchronized(subtitles) { subtitles.distinctBy { it.language to it.url } },
                extractorData = link.extractorData,
                videoInterceptor = runCatching { api?.getVideoInterceptor(link) }.getOrNull()
            )
        }
    }

    suspend fun runExtractorVerifier(artifact: File, providerName: String, extractorData: String?) = withContext(Dispatchers.IO) {
        if (extractorData.isNullOrBlank()) return@withContext
        val api = loadPlugin(artifact).providers.firstOrNull { it.name == providerName } ?: return@withContext
        runCatching { api.extractorVerifierJob(extractorData) }
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

        setContext(WeakReference(context as Any))
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
        // AnimeLoadResponse stores separate episode lists keyed by DubStatus.
        // Preserve that key instead of flattening away the provider's dub/sub variants.
        val episodeEntries: List<Pair<Any?, String?>> = when (rawEpisodes) {
            is Iterable<*> -> rawEpisodes.map { it to null }
            is Map<*, *> -> rawEpisodes.entries.flatMap { entry ->
                (entry.value as? Iterable<*>)?.map { it to entry.key?.toString() }.orEmpty()
            }
            else -> emptyList()
        }
        val episodes = episodeEntries.mapNotNull { (item, dubStatus) ->
            item ?: return@mapNotNull null
            val episodeData = readProperty(item, "data") as? String ?: return@mapNotNull null
            CloudStreamEpisodeItem(
                name = (readProperty(item, "name") as? String).orEmpty(),
                data = episodeData,
                season = readProperty(item, "season") as? Int,
                episode = readProperty(item, "episode") as? Int,
                posterUrl = readProperty(item, "posterUrl") as? String,
                description = readProperty(item, "description") as? String,
                rating = readProperty(item, "rating") as? Int,
                date = readProperty(item, "date") as? Long,
                runtimeSeconds = readProperty(item, "runTime") as? Int,
                dubStatus = dubStatus?.takeIf { it != "None" }
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
            episodes = episodes,
            rating = (readProperty(this, "rating") as? Int) ?: readProperty(this, "score")?.let { score ->
                runCatching {
                    val toFloat = score.javaClass.methods.firstOrNull { it.name == "toFloat" && it.parameterCount == 1 }
                        ?: return@runCatching null
                    ((toFloat.invoke(score, 10) as? Number)?.toFloat()?.times(1000f))?.toInt()
                }.getOrNull()
            },
            tags = (readProperty(this, "tags") as? Iterable<*>)?.mapNotNull { it?.toString() }.orEmpty(),
            contentRating = readProperty(this, "contentRating") as? String,
            showStatus = readProperty(this, "showStatus")?.toString(),
            comingSoon = readProperty(this, "comingSoon") as? Boolean ?: false,
            cast = (readProperty(this, "actors") as? Iterable<*>)?.mapNotNull { actorData ->
                actorData ?: return@mapNotNull null
                val actor = readProperty(actorData, "actor") ?: return@mapNotNull null
                val actorName = readProperty(actor, "name") as? String ?: return@mapNotNull null
                val voiceActor = readProperty(actorData, "voiceActor")
                CloudStreamCastMember(
                    name = actorName,
                    role = (readProperty(actorData, "roleString") as? String) ?: readProperty(actorData, "role")?.toString(),
                    imageUrl = readProperty(actor, "image") as? String,
                    voiceActorName = voiceActor?.let { readProperty(it, "name") as? String },
                    voiceActorImageUrl = voiceActor?.let { readProperty(it, "image") as? String }
                )
            }.orEmpty(),
            trailers = (readProperty(this, "trailers") as? Iterable<*>)?.mapNotNull { trailer ->
                trailer ?: return@mapNotNull null
                val trailerUrl = readProperty(trailer, "extractorUrl") as? String ?: return@mapNotNull null
                CloudStreamTrailer(trailerUrl, readProperty(trailer, "referer") as? String, readProperty(trailer, "raw") as? Boolean ?: false)
            }.orEmpty(),
            recommendations = (readProperty(this, "recommendations") as? Iterable<*>)?.mapNotNull { item ->
                item ?: return@mapNotNull null
                val itemTitle = readProperty(item, "name") as? String ?: return@mapNotNull null
                val itemUrl = readProperty(item, "url") as? String ?: return@mapNotNull null
                CloudStreamRecommendation(itemTitle, itemUrl, readProperty(item, "posterUrl") as? String, readProperty(item, "type")?.toString(), readProperty(item, "year") as? Int)
            }.orEmpty(),
            seasonNames = (readProperty(this, "seasonNames") as? Iterable<*>)?.mapNotNull { season ->
                season ?: return@mapNotNull null
                val number = readProperty(season, "season") as? Int ?: return@mapNotNull null
                val display = readProperty(season, "name") as? String
                    ?: (readProperty(season, "displaySeason") as? Int)?.let { "Season $it" }
                    ?: return@mapNotNull null
                number to display
            }?.toMap().orEmpty(),
            externalIds = buildMap {
                (readProperty(this@toCloudStreamContent, "syncData") as? Map<*, *>)
                    ?.forEach { (key, value) ->
                        if (key is String && value != null && value.toString().isNotBlank()) put(key, value.toString())
                    }
                listOf("imdbId", "tmdbId", "aniListId", "malId", "traktId", "simklId")
                    .forEach { key ->
                        readProperty(this@toCloudStreamContent, key)?.toString()
                            ?.takeIf { it.isNotBlank() }?.let { put(key, it) }
                    }
            },
            englishTitle = readProperty(this@toCloudStreamContent, "engName") as? String,
            japaneseTitle = readProperty(this@toCloudStreamContent, "japName") as? String,
            synonyms = (readProperty(this@toCloudStreamContent, "synonyms") as? Iterable<*>)
                ?.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }.orEmpty(),
            nextAiringEpisode = readProperty(readProperty(this@toCloudStreamContent, "nextAiring") ?: this@toCloudStreamContent, "episode") as? Int,
            nextAiringUnixTime = readProperty(readProperty(this@toCloudStreamContent, "nextAiring") ?: this@toCloudStreamContent, "unixTime") as? Long,
            nextAiringSeason = readProperty(readProperty(this@toCloudStreamContent, "nextAiring") ?: this@toCloudStreamContent, "season") as? Int,
            posterHeaders = (readProperty(this@toCloudStreamContent, "posterHeaders") as? Map<*, *>)
                ?.mapNotNull { (key, value) ->
                    if (key is String && value != null) key to value.toString() else null
                }?.toMap().orEmpty(),
            magnetUrl = readProperty(this@toCloudStreamContent, "magnet") as? String,
            torrentUrl = readProperty(this@toCloudStreamContent, "torrent") as? String
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
