package com.example.data.telegram

import android.content.Context
import android.os.Build
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import com.example.data.security.TelegramApiCredentialStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Process-wide TDLib bridge. TDLib owns its own persistent encrypted database in app-private storage.
 * No Telegram phone number, OTP, or 2FA password is persisted by NSiO.
 */
object TelegramClientManager {
    enum class AuthStage { NOT_CONFIGURED, INITIALIZING, WAITING_PHONE, WAITING_CODE, WAITING_PASSWORD, READY, ERROR }

    data class TelegramChat(val id: Long, val title: String, val kind: String, val unreadCount: Int, val photoFileId: Int = 0, val isArchived: Boolean = false, val photoPath: String = "")

    data class TelegramMedia(
        val chatId: Long,
        val messageId: Long,
        val title: String,
        val fileId: Int,
        val isPhoto: Boolean,
        val mimeType: String,
        val caption: String = "",
        val createdAt: Long = 0L,
        val thumbnailFileId: Int = 0
    )

    data class State(
        val stage: AuthStage = AuthStage.NOT_CONFIGURED,
        val message: String = "Telegram belum dihubungkan.",
        val chats: List<TelegramChat> = emptyList(),
        val media: List<TelegramMedia> = emptyList()
    )

    private const val MAX_TELEGRAM_BACKUP_ZIP_BYTES = 28 * 1024 * 1024
    private const val MAX_TELEGRAM_BACKUP_EXPANDED_BYTES = 128L * 1024L * 1024L
    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()
    @Volatile private var client: Client? = null
    @Volatile private var appContext: Context? = null
    @Volatile private var activeProfileId: String = ""
    @Volatile private var clientGeneration: Long = 0L
    private val downloadCallbacks = ConcurrentHashMap<Int, CopyOnWriteArrayList<(String?) -> Unit>>()
    private val photoDownloadsInFlight = ConcurrentHashMap.newKeySet<Long>()
    private val cacheCleanupLock = Any()
    private val activeDownloadCallbacks = java.util.concurrent.atomic.AtomicInteger(0)
    private val activeMediaStreams = java.util.concurrent.atomic.AtomicInteger(0)
    @Volatile private var cacheCleanupInProgress = false

    /**
     * A lease covers the complete lifetime of a Telegram Media3 upstream.
     * Cache maintenance must not evict TDLib files while a stream is reading them.
     */
    fun beginMediaStream(): Boolean = synchronized(cacheCleanupLock) {
        if (cacheCleanupInProgress) false
        else {
            activeMediaStreams.incrementAndGet()
            true
        }
    }

    fun endMediaStream() {
        synchronized(cacheCleanupLock) {
            if (activeMediaStreams.get() > 0) activeMediaStreams.decrementAndGet()
        }
    }

    fun beginCacheCleanup(): Boolean = synchronized(cacheCleanupLock) {
        if (cacheCleanupInProgress || downloadCallbacks.isNotEmpty() ||
            activeDownloadCallbacks.get() > 0 || activeMediaStreams.get() > 0) {
            false
        } else {
            cacheCleanupInProgress = true
            true
        }
    }

    fun endCacheCleanup() {
        synchronized(cacheCleanupLock) { cacheCleanupInProgress = false }
    }

    private fun profileBindingPreferences(context: Context) =
        context.applicationContext.getSharedPreferences("nsio_telegram_profile_binding", Context.MODE_PRIVATE)

    fun ownerProfileId(context: Context): String? =
        profileBindingPreferences(context).getString("owner_profile_id", null)?.takeIf(String::isNotBlank)

    fun isProfileOwner(context: Context, profileId: String): Boolean =
        profileId.isNotBlank() && ownerProfileId(context) == profileId

    fun claimProfile(context: Context, profileId: String): Boolean {
        if (profileId.isBlank()) return false
        val preferences = profileBindingPreferences(context)
        val owner = preferences.getString("owner_profile_id", null)
        if (owner.isNullOrBlank()) {
            // Claim the existing legacy Telegram session for the profile that first configures it.
            return preferences.edit().putString("owner_profile_id", profileId).commit()
        }
        return owner == profileId
    }

    @Synchronized
    fun selectProfile(context: Context, profileId: String) {
        if (profileId.isBlank()) return
        if (ownerProfileId(context) == profileId) {
            initialize(context, profileId)
            return
        }
        if (activeProfileId != profileId || client != null) {
            clientGeneration++
            client?.let { runCatching { it.send(TdApi.Close()) { _ -> } } }
            client = null
            activeProfileId = profileId
        }
        mutableState.value = State(message = "Telegram tidak terhubung pada profil ini.")
    }

    @Synchronized
    fun releaseProfile(context: Context, profileId: String) {
        if (!isProfileOwner(context, profileId)) return
        clientGeneration++
        client?.let { runCatching { it.send(TdApi.Close()) { _ -> } } }
        client = null
        activeProfileId = ""
        TelegramApiCredentialStore(context).clearAll()
        File(context.filesDir, "telegram/tdlib/database").deleteRecursively()
        File(context.cacheDir, "telegram/tdlib/files").deleteRecursively()
        profileBindingPreferences(context).edit().remove("owner_profile_id").commit()
        catalogPreferences(context).edit().clear().commit()
        mutableState.value = State(message = "Integrasi Telegram dilepas dari profil.")
    }

    @Synchronized
    fun initialize(context: Context, profileId: String) {
        val app = context.applicationContext
        appContext = app
        if (!claimProfile(app, profileId)) {
            if (activeProfileId != profileId || client != null) {
                clientGeneration++
                client?.let { runCatching { it.send(TdApi.Close()) { _ -> } } }
                client = null
                activeProfileId = profileId
            }
            mutableState.value = State(message = "Integrasi Telegram terikat pada profil lain.")
            return
        }
        if (activeProfileId != profileId) {
            clientGeneration++
            client?.let { runCatching { it.send(TdApi.Close()) { _ -> } } }
            client = null
            activeProfileId = profileId
        }
        restoreCatalog(app)
        if (client != null) return
        val credentials = TelegramApiCredentialStore(app).load()
        if (credentials == null) {
            mutableState.value = mutableState.value.copy(stage = AuthStage.NOT_CONFIGURED, message = "Masukkan API ID dan API Hash di Pengaturan Telegram.")
            return
        }
        val apiId = credentials.apiId.toIntOrNull()
        if (apiId == null) {
            mutableState.value = mutableState.value.copy(stage = AuthStage.ERROR, message = "API ID Telegram tidak valid.")
            return
        }
        mutableState.value = mutableState.value.copy(stage = AuthStage.INITIALIZING, message = "Menginisialisasi koneksi Telegram…")
        // The stable 256-bit database key is wrapped by Android Keystore.
        val databaseRoot = File(app.filesDir, "telegram/tdlib/database").apply { mkdirs() }
        val mediaCacheRoot = File(app.cacheDir, "telegram/tdlib/files").apply { mkdirs() }
        val databaseKey = TelegramApiCredentialStore(app).loadOrCreateDatabaseEncryptionKey()
        // TDLib sends AuthorizationStateWaitTdlibParameters after Client.create.
        pendingApiId = apiId
        pendingApiHash = credentials.apiHash
        pendingDatabaseDirectory = databaseRoot.absolutePath
        pendingFilesDirectory = mediaCacheRoot.absolutePath
        pendingDatabaseKey = databaseKey
        val generation = clientGeneration
        client = Client.create({ update ->
            if (generation == clientGeneration) handleUpdate(update)
        }, { error ->
            if (generation == clientGeneration) {
                mutableState.value = mutableState.value.copy(stage = AuthStage.ERROR, message = error.message ?: "Kesalahan TDLib.")
            }
        }, { error ->
            if (generation == clientGeneration) {
                mutableState.value = mutableState.value.copy(message = error.message ?: "Kesalahan TDLib.")
            }
        })
    }

    private var pendingApiId: Int = 0
    private var pendingApiHash: String = ""
    private var pendingDatabaseDirectory: String = ""
    private var pendingFilesDirectory: String = ""
    private var pendingDatabaseKey: ByteArray = byteArrayOf()

    private fun handleUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> handleAuthorizationState(update.authorizationState)
            is TdApi.UpdateNewChat -> upsertChat(update.chat)
            is TdApi.UpdateChatTitle -> refreshChat(update.chatId)
            is TdApi.UpdateChatLastMessage -> {
                // Metadata refreshes may arrive before TDLib has populated dialog positions.
                refreshChat(update.chatId)
            }
            // Only a position update is allowed to confirm removal from both account dialog lists.
            is TdApi.UpdateChatPosition -> refreshChat(update.chatId, allowRemoval = true)
            is TdApi.UpdateFile -> {
                val file = update.file
                val local = file.local
                if (local.isDownloadingCompleted && local.path.isNotBlank()) {
                    completeDownload(file.id, local.path)
                }
            }
        }
    }

    private fun handleAuthorizationState(auth: TdApi.AuthorizationState) {
        when (auth) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                val parameters = TdApi.SetTdlibParameters().apply {
                    useTestDc = false
                    databaseDirectory = pendingDatabaseDirectory
                    filesDirectory = pendingFilesDirectory
                    databaseEncryptionKey = pendingDatabaseKey
                    useFileDatabase = true
                    useChatInfoDatabase = true
                    // Message history is fetched from Telegram on demand instead of
                    // accumulating a persistent local copy in app-private data.
                    useMessageDatabase = false
                    useSecretChats = false
                    apiId = pendingApiId
                    apiHash = pendingApiHash
                    systemLanguageCode = "id"
                    deviceModel = listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Android" }
                    systemVersion = Build.VERSION.RELEASE.orEmpty()
                    applicationVersion = "NSiO"
                }
                send(parameters)
            }
            is TdApi.AuthorizationStateWaitPhoneNumber ->
                mutableState.value = mutableState.value.copy(stage = AuthStage.WAITING_PHONE, message = "Masukkan nomor telepon Telegram dengan kode negara.")
            is TdApi.AuthorizationStateWaitCode ->
                mutableState.value = mutableState.value.copy(stage = AuthStage.WAITING_CODE, message = "Masukkan kode verifikasi yang dikirim Telegram.")
            is TdApi.AuthorizationStateWaitPassword ->
                mutableState.value = mutableState.value.copy(stage = AuthStage.WAITING_PASSWORD, message = "Masukkan kata sandi verifikasi dua langkah Telegram.")
            is TdApi.AuthorizationStateReady -> {
                mutableState.value = mutableState.value.copy(stage = AuthStage.READY, message = "Telegram terhubung.")
                loadChats()
            }
            is TdApi.AuthorizationStateClosed -> {
                client = null
                mutableState.value = mutableState.value.copy(stage = AuthStage.NOT_CONFIGURED, message = "Sesi Telegram ditutup.")
            }
            else -> mutableState.value = mutableState.value.copy(stage = AuthStage.INITIALIZING, message = auth.javaClass.simpleName)
        }
    }

    fun submitPhoneNumber(phone: String) {
        val normalized = phone.trim().replace(" ", "").replace("-", "")
        require(Regex("^\\+[1-9][0-9]{7,14}$").matches(normalized)) {
            "Gunakan format internasional, contoh +628123456789."
        }
        send(TdApi.SetAuthenticationPhoneNumber(normalized, null))
    }

    fun submitCode(code: String) {
        val value = code.trim()
        require(value.length in 3..10 && value.all(Char::isDigit)) { "Kode verifikasi tidak valid." }
        send(TdApi.CheckAuthenticationCode(value))
    }

    fun submitPassword(password: String) {
        require(password.isNotBlank()) { "Kata sandi tidak boleh kosong." }
        send(TdApi.CheckAuthenticationPassword(password))
    }

    @Volatile private var chatsLoading = false

    /**
     * Load every available dialog from Main and Archive.
     * 100 is only the TDLib page size; loading continues until TDLib returns 404
     * to indicate that the selected list has been fully loaded.
     */
    @Synchronized
    fun loadChats() {
        val activeClient = client ?: return
        if (chatsLoading) return
        chatsLoading = true
        // Keep the last known catalog visible while TDLib loads Main and Archive.
        // A temporary empty/incomplete response must never erase the user's catalog.
        photoDownloadsInFlight.clear()
        val lists = listOf(
            TdApi.ChatListMain() to "utama",
            TdApi.ChatListArchive() to "arsip"
        )

        fun loadList(index: Int) {
            if (index >= lists.size) {
                chatsLoading = false
                mutableState.value = mutableState.value.copy(
                    message = "Daftar grup dan channel Telegram selesai dimuat: ${mutableState.value.chats.size} dialog."
                )
                return
            }

            val (chatList, label) = lists[index]
            fun loadNextBatch() {
                activeClient.send(TdApi.LoadChats(chatList, 100)) { result ->
                    when (result) {
                        is TdApi.Ok -> loadNextBatch()
                        is TdApi.Error -> {
                            if (result.code == 404) {
                                // TDLib has exhausted this list; continue with the next one.
                                loadList(index + 1)
                            } else {
                                chatsLoading = false
                                mutableState.value = mutableState.value.copy(
                                    message = "Gagal memuat seluruh dialog $label Telegram: ${result.message}"
                                )
                            }
                        }
                        else -> {
                            chatsLoading = false
                            mutableState.value = mutableState.value.copy(
                                message = "Respons tidak dikenal saat memuat dialog $label Telegram."
                            )
                        }
                    }
                }
            }
            loadNextBatch()
        }

        loadList(0)
    }

    private fun refreshChat(chatId: Long, allowRemoval: Boolean = false) {
        client?.send(TdApi.GetChat(chatId), { result ->
            if (result is TdApi.Chat) {
                val stillInAccountDialogs = result.positions.any { position ->
                    position.list is TdApi.ChatListMain || position.list is TdApi.ChatListArchive
                }
                if (stillInAccountDialogs) {
                    upsertChat(result)
                } else if (allowRemoval) {
                    // A missing position is authoritative only after a dialog-position update,
                    // never after title/last-message metadata refreshes.
                    removeChatFromCatalog(chatId)
                }
            }
        })
    }

    private fun removeChatFromCatalog(chatId: Long) {
        val remaining = mutableState.value.chats.filterNot { it.id == chatId }
        if (remaining.size != mutableState.value.chats.size) {
            mutableState.value = mutableState.value.copy(chats = remaining)
            persistCatalog()
        }
    }

    /** Resolve public Telegram links through TDLib; never auto-join private invites. */
    fun resolveSourceLink(rawUrl: String, onResolved: (Long?, String) -> Unit) {
        val activeClient = client
        if (activeClient == null || mutableState.value.stage != AuthStage.READY) {
            onResolved(null, "Hubungkan akun Telegram terlebih dahulu melalui Pengaturan.")
            return
        }
        val parsed = runCatching { com.example.data.model.TelegramSourceLinkPolicy.parse(rawUrl) }
            .getOrElse { onResolved(null, it.localizedMessage ?: "Link Telegram tidak valid."); return }
        val path = runCatching { java.net.URI(parsed.url).path.orEmpty().split('/').filter(String::isNotBlank) }.getOrDefault(emptyList())
        if (parsed.sourceType == "TELEGRAM_INVITE") {
            onResolved(null, "Ini link undangan private. Buka melalui Telegram resmi dan gabung secara manual dengan persetujuan Anda, lalu coba link kembali.")
            return
        }
        if (path.firstOrNull() == "c" && path.size >= 2) {
            val internalId = path.getOrNull(1)?.toLongOrNull()
            val chatId = internalId?.let { "-100$it".toLongOrNull() }
            if (chatId == null) { onResolved(null, "ID channel private pada link tidak valid."); return }
            activeClient.send(TdApi.GetChat(chatId), { result ->
                if (result is TdApi.Chat) {
                    upsertChat(result)
                    val postId = path.getOrNull(2)?.toLongOrNull()
                    if (postId != null) loadLinkedMessage(result.id, postId) else loadRecentMedia(result.id)
                    onResolved(result.id, "Sumber private ditemukan. Akun Anda memiliki akses.")
                } else if (result is TdApi.Error) {
                    onResolved(null, "Channel private belum dapat diakses (${result.code}). Pastikan akun sudah bergabung dan memiliki izin melihat pesan.")
                } else onResolved(null, "Telegram tidak mengembalikan informasi channel tersebut.")
            })
            return
        }
        val username = path.firstOrNull()
        if (username.isNullOrBlank() || username.startsWith("+") || username.equals("joinchat", true)) {
            onResolved(null, "Link undangan private harus dibuka melalui Telegram resmi terlebih dahulu.")
            return
        }
        activeClient.send(TdApi.SearchPublicChat(username), { result ->
            if (result is TdApi.Chat) {
                upsertChat(result)
                val postId = path.lastOrNull()?.toLongOrNull()
                if (postId != null) loadLinkedMessage(result.id, postId) else loadRecentMedia(result.id)
                onResolved(result.id, if (postId != null) "Postingan Telegram ditemukan pada ${result.title}." else "Sumber Telegram ditemukan: ${result.title}.")
            } else if (result is TdApi.Error) {
                onResolved(null, if (result.code == 400 || result.code == 404)
                    "Sumber tidak ditemukan atau tidak dapat diakses. Periksa link dan izin akun."
                else "Telegram gagal memeriksa sumber (${result.code}): ${result.message}")
            } else onResolved(null, "Telegram tidak dapat mengenali link tersebut.")
        })
    }

    private fun extractMedia(chatId: Long, message: TdApi.Message): TelegramMedia? {
        return when (val content = message.content) {
            is TdApi.MessageVideo -> content.video?.video?.let {
                TelegramMedia(chatId, message.id, content.video.fileName.orEmpty().ifBlank { "Video Telegram" }, it.id, false, content.video.mimeType.orEmpty().ifBlank { "video/mp4" }, content.caption?.text.orEmpty(), message.date * 1000L, content.video.thumbnail?.file?.id ?: 0)
            }
            is TdApi.MessagePhoto -> content.photo?.let { photo ->
                photo.sizes.maxByOrNull { it.width.toLong() * it.height }?.let { largest ->
                    val smallest = photo.sizes.minByOrNull { it.width.toLong() * it.height }
                    TelegramMedia(chatId, message.id, "Foto Telegram", largest.photo.id, true, "image/jpeg",
                        content.caption?.text.orEmpty(), message.date * 1000L, smallest?.photo?.id ?: 0)
                }
            }
            is TdApi.MessageDocument -> content.document?.let { document ->
                val mime = document.mimeType.orEmpty()
                if (mime.startsWith("video/") || mime.startsWith("image/")) {
                    TelegramMedia(chatId, message.id, document.fileName.orEmpty().ifBlank { if (mime.startsWith("image/")) "Foto Telegram" else "Video Telegram" }, document.document.id, mime.startsWith("image/"), mime, content.caption?.text.orEmpty(), message.date * 1000L, document.thumbnail?.file?.id ?: 0)
                } else null
            }
            else -> null
        }
    }

    fun loadLinkedMessage(chatId: Long, messageId: Long) {
        val activeClient = client ?: return
        mutableState.value = mutableState.value.copy(media = emptyList(), message = "Memeriksa postingan Telegram…")
        activeClient.send(TdApi.GetMessage(chatId, messageId), { result ->
            if (result is TdApi.Message) {
                val media = extractMedia(chatId, result)
                mutableState.value = mutableState.value.copy(
                    media = listOfNotNull(media),
                    message = if (media == null) "Postingan ditemukan, tetapi tidak berisi foto/video yang didukung." else "Media dari postingan yang ditautkan."
                )
            } else if (result is TdApi.Error) {
                mutableState.value = mutableState.value.copy(media = emptyList(), message = "Postingan tidak dapat diakses: ${result.message}")
            } else {
                mutableState.value = mutableState.value.copy(media = emptyList(), message = "Telegram tidak mengembalikan postingan tersebut.")
            }
        })
    }

    fun loadRecentMedia(chatId: Long) {
        loadAllChatMedia(chatId) { items, error ->
            mutableState.value = mutableState.value.copy(
                media = items,
                message = error ?: "Ditemukan ${items.size} media dari seluruh riwayat yang dapat diakses."
            )
        }
    }

    /**
     * Reads the complete accessible chat history in pages. The callback receives media only;
     * text-only messages are ignored. TDLib may stop at the oldest message available to this account.
     */
    fun loadAllChatMedia(chatId: Long, onFinished: (List<TelegramMedia>, String?) -> Unit) {
        val collected = linkedMapOf<Long, TelegramMedia>()
        loadAllChatMedia(chatId, { page, acknowledge ->
            page.forEach { collected[it.messageId] = it }
            acknowledge()
        }) { failure ->
            onFinished(collected.values.sortedByDescending { it.createdAt }, failure)
        }
    }

    /**
     * Streams media one history page at a time. The caller acknowledges a page only after
     * persisting it, so large histories do not have to remain in memory before import starts.
     */
    fun loadAllChatMedia(
        chatId: Long,
        onPage: (List<TelegramMedia>, acknowledge: () -> Unit) -> Unit,
        onFinished: (String?) -> Unit
    ) {
        val activeClient = client
        if (activeClient == null || mutableState.value.stage != AuthStage.READY) {
            onFinished("Telegram belum terhubung.")
            return
        }
        var cursorMessageId = 0L
        var completed = false

        fun finish(failure: String? = null) {
            if (completed) return
            completed = true
            onFinished(failure)
        }

        fun loadPage() {
            if (completed) return
            activeClient.send(TdApi.GetChatHistory(chatId, cursorMessageId, if (cursorMessageId == 0L) 0 else -1, 100, false)) { result ->
                when (result) {
                    is TdApi.Messages -> {
                        val messages = result.messages
                        if (messages.isEmpty()) {
                            finish()
                            return@send
                        }
                        val page = messages.mapNotNull { extractMedia(chatId, it) }
                        val oldestId = messages.minOfOrNull { it.id } ?: 0L
                        val reachedEnd = oldestId <= 0L || oldestId == cursorMessageId
                        val acknowledged = java.util.concurrent.atomic.AtomicBoolean(false)
                        val acknowledge = {
                            if (acknowledged.compareAndSet(false, true) && !completed) {
                                if (reachedEnd) finish() else {
                                    cursorMessageId = oldestId
                                    loadPage()
                                }
                            }
                        }
                        if (page.isEmpty()) acknowledge() else onPage(page, acknowledge)
                    }
                    is TdApi.Error -> finish("Gagal membaca riwayat Telegram: ${result.message}")
                    else -> finish("Telegram tidak mengembalikan riwayat pesan.")
                }
            }
        }
        loadPage()
    }

    fun downloadChatPhoto(chatId: Long, onFinished: (String?) -> Unit) {
        val chat = mutableState.value.chats.firstOrNull { it.id == chatId }
        val fileId = chat?.photoFileId ?: 0
        if (fileId <= 0) {
            onFinished(null)
            return
        }
        if (!registerDownloadCallback(fileId, onFinished)) {
            onFinished(null)
            return
        }
        client?.send(TdApi.DownloadFile(fileId, 16, 0L, 0L, true)) { result ->
            when (result) {
                is TdApi.File -> {
                    val local = result.local
                    if (local.isDownloadingCompleted && local.path.isNotBlank()) {
                        completeDownload(fileId, local.path)
                    }
                }
                is TdApi.Error -> completeDownload(fileId, null)
            }
        } ?: run {
            completeDownload(fileId, null)
        }
    }

    fun downloadMedia(media: TelegramMedia, onFinished: (String?) -> Unit) {
        val activeClient = client ?: run { onFinished(null); return }
        if (!registerDownloadCallback(media.fileId, onFinished)) {
            onFinished(null)
            return
        }
        activeClient.send(TdApi.DownloadFile(media.fileId, 32, 0L, 0L, true), { result ->
            when (result) {
                is TdApi.File -> {
                    val local = result.local
                    if (local.isDownloadingCompleted && local.path.isNotBlank()) {
                        completeDownload(media.fileId, local.path)
                    }
                    // Otherwise UpdateFile completes the callback when TDLib finishes downloading.
                }
                is TdApi.Error -> {
                    completeDownload(media.fileId, null)
                    mutableState.value = mutableState.value.copy(message = "Unduhan Telegram gagal: ${result.message}")
                }
            }
        })
    }

    /**
     * TDLib byte-range requests used by Media3. A request completes only after the
     * requested range is available, so the player never needs to download the full file.
     */
    fun getFileBlocking(fileId: Int): TdApi.File = sendBlocking(TdApi.GetFile(fileId))

    fun downloadFileRangeBlocking(fileId: Int, offset: Long, limit: Long): TdApi.File {
        if (cacheCleanupInProgress) throw java.io.IOException("Cache Telegram sedang dibersihkan.")
        // After moving TDLib's files directory or clearing cache, its persistent file
        // database can still report an old completed path. Remove only that stale local
        // copy so TDLib recreates it under the new cacheDir on this range request.
        val current = getFileBlocking(fileId)
        val localPath = current.local.path
        if (current.local.isDownloadingCompleted) {
            val localFile = localPath.takeIf { it.isNotBlank() }?.let(::File)
            val stale = localFile == null || !localFile.isFile ||
                (current.size > 0L && localFile.length() < current.size)
            if (stale) sendBlockingOk(TdApi.DeleteFile(fileId))
        }
        return sendBlocking(TdApi.DownloadFile(fileId, 32, offset, limit, true))
    }

    private fun sendBlockingOk(request: TdApi.Function<TdApi.Ok>): TdApi.Ok {
        val activeClient = client ?: throw IllegalStateException("Telegram belum terhubung.")
        val latch = CountDownLatch(1)
        val response = AtomicReference<TdApi.Object?>()
        activeClient.send(request) { result ->
            response.set(result)
            latch.countDown()
        }
        if (!latch.await(120, TimeUnit.SECONDS)) {
            throw java.io.IOException("Telegram terlalu lama menghapus salinan file lokal.")
        }
        return when (val result = response.get()) {
            is TdApi.Ok -> result
            is TdApi.Error -> throw java.io.IOException("Telegram: ${result.message}")
            else -> throw java.io.IOException("Respons penghapusan file Telegram tidak valid.")
        }
    }

    private fun sendBlocking(request: TdApi.Function<TdApi.File>): TdApi.File {
        val activeClient = client ?: throw IllegalStateException("Telegram belum terhubung.")
        val latch = CountDownLatch(1)
        val response = AtomicReference<TdApi.Object?>()
        activeClient.send(request) { result ->
            response.set(result)
            latch.countDown()
        }
        if (!latch.await(120, TimeUnit.SECONDS)) {
            throw java.io.IOException("Telegram terlalu lama mengirim bagian video.")
        }
        return when (val result = response.get()) {
            is TdApi.File -> result
            is TdApi.Error -> throw java.io.IOException("Telegram: ${result.message}")
            else -> throw java.io.IOException("Respons file Telegram tidak valid.")
        }
    }

    /** Download only Telegram's small preview image, never the corresponding video. */
    fun downloadThumbnail(fileId: Int, onFinished: (String?) -> Unit) {
        val activeClient = client
        if (activeClient == null || fileId <= 0) {
            onFinished(null)
            return
        }
        if (!registerDownloadCallback(fileId, onFinished)) {
            onFinished(null)
            return
        }
        activeClient.send(TdApi.DownloadFile(fileId, 8, 0L, 0L, true)) { result ->
            when (result) {
                is TdApi.File -> {
                    if (result.local.isDownloadingCompleted && result.local.path.isNotBlank()) {
                        completeDownload(fileId, result.local.path)
                    }
                }
                is TdApi.Error -> completeDownload(fileId, null)
            }
        }
    }

    /** Clear stale UI references after Telegram preview files are evicted. */
    fun invalidateCachedPhotoPaths() {
        mutableState.value = mutableState.value.copy(
            chats = mutableState.value.chats.map { it.copy(photoPath = "") }
        )
    }

    /**
     * Ask TDLib to reclaim disposable local media files using its own storage policy.
     *
     * This deliberately does not touch TDLib's SQLite database, credentials, dialog
     * history, or files copied into NSiO's user Downloads. TDLib retains metadata and
     * can download media again when it is opened later.
     */
    fun clearDisposableStorage(context: Context, timeoutSeconds: Long = 60L): Boolean {
        val activeClient = client
        if (activeClient == null) {
            val directory = File(context.applicationContext.cacheDir, "telegram/tdlib/files")
            return !directory.exists() || runCatching { directory.deleteRecursively() }.getOrDefault(false)
        }
        if (mutableState.value.stage != AuthStage.READY) {
            val directory = File(context.applicationContext.cacheDir, "telegram/tdlib/files")
            return !directory.exists() || directory.list().isNullOrEmpty()
        }

        val request = TdApi.OptimizeStorage().apply {
            // Zero limits and no immunity window ask TDLib to remove all eligible
            // downloaded media. The encrypted database and chat history are untouched.
            size = 0L
            ttl = 0
            count = 0
            immunityDelay = 0
            fileTypes = emptyArray()
            chatIds = longArrayOf()
            excludeChatIds = longArrayOf()
            chatLimit = 0
        }
        val latch = CountDownLatch(1)
        val response = AtomicReference<TdApi.Object?>()
        activeClient.send(request) { result ->
            response.set(result)
            latch.countDown()
        }
        if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) {
            android.util.Log.w("NSiO.Telegram", "TDLib cache cleanup timed out.")
            return false
        }
        return when (val result = response.get()) {
            is TdApi.StorageStatistics -> {
                android.util.Log.i("NSiO.Telegram", "TDLib disposable media cleared: $result")
                true
            }
            is TdApi.Error -> {
                android.util.Log.w("NSiO.Telegram", "TDLib cache cleanup failed: ${result.message}")
                false
            }
            else -> {
                android.util.Log.w("NSiO.Telegram", "Unexpected TDLib cache cleanup response: $result")
                false
            }
        }
    }

    /** Remove TDLib's downloaded local copy after it has been placed in NSiO's bounded cache. */
    fun deleteDownloadedFile(fileId: Int) {
        if (fileId <= 0) return
        client?.send(TdApi.DeleteFile(fileId)) { result ->
            if (result is TdApi.Error) {
                android.util.Log.w("NSiO.Telegram", "TDLib local file cleanup failed: ${result.message}")
            }
        }
    }

    /**
     * Only chats with an actual Main/Archive dialog position may enter the automatic
     * integration catalog. Resolving a public link is not enough to create a catalog row.
     * Bot dialogs are included, but ordinary private conversations are not.
     */
    private fun upsertChat(chat: TdApi.Chat) {
        val isMainOrArchiveDialog = chat.positions.any { position ->
            position.list is TdApi.ChatListMain || position.list is TdApi.ChatListArchive
        }
        if (!isMainOrArchiveDialog) {
            // SearchPublicChat/GetChat can resolve public sources that are not account
            // dialogs. Ignore them, but never delete a known entry on incomplete metadata.
            return
        }
        when (val type = chat.type) {
            is TdApi.ChatTypeSupergroup -> publishEligibleChat(chat, if (type.isChannel) "Channel" else "Grup")
            is TdApi.ChatTypeBasicGroup -> publishEligibleChat(chat, "Grup")
            is TdApi.ChatTypePrivate -> {
                // Only bot accounts are shown as private-source entries, and only when
                // TDLib confirms that the bot is already present in the user's dialogs.
                client?.send(TdApi.GetUser(type.userId)) { result ->
                    if (result is TdApi.User && result.type is TdApi.UserTypeBot) {
                        publishEligibleChat(chat, "Bot")
                    }
                }
            }
            else -> Unit
        }
    }

    /** Persist known source containers independently of media counts. */
    private fun catalogPreferences(context: Context) =
        context.getSharedPreferences("telegram_source_catalog", Context.MODE_PRIVATE)

    /**
     * Export the Telegram session database and its decryption material. The caller must
     * place this object inside the PIN-encrypted profile backup before writing it to disk.
     */
    suspend fun exportProfileBackup(context: Context, profileId: String): JSONObject? {
        if (!isProfileOwner(context, profileId)) return null
        val credentials = TelegramApiCredentialStore(context).exportPortableData() ?: return null
        val wasConnected = activeProfileId == profileId && client != null
        if (wasConnected) {
            val closingClient = client
            mutableState.value = mutableState.value.copy(stage = AuthStage.INITIALIZING, message = "Menutup sesi Telegram untuk backup…")
            val closeResult = CompletableDeferred<Unit>()
            closingClient?.send(TdApi.Close()) { _ -> closeResult.complete(Unit) }
            val acknowledged = withTimeoutOrNull(15_000L) { closeResult.await(); true } ?: false
            if (!acknowledged) throw IllegalStateException("TDLib belum mengonfirmasi penutupan database. Backup Telegram dibatalkan.")
            val closed = withTimeoutOrNull(15_000L) {
                mutableState.first { it.stage == AuthStage.NOT_CONFIGURED }
                true
            } ?: false
            if (!closed) throw IllegalStateException("Database Telegram masih aktif. Backup Telegram dibatalkan.")
        }

        val databaseRoot = File(context.filesDir, "telegram/tdlib/database")
        val zipBytes = try {
            ByteArrayOutputStream().use { output ->
                ZipOutputStream(output).use { zip ->
                    if (databaseRoot.isDirectory) {
                        databaseRoot.walkTopDown().filter { it.isFile }.forEach { file ->
                            val relative = file.relativeTo(databaseRoot).invariantSeparatorsPath
                            zip.putNextEntry(ZipEntry(relative))
                            file.inputStream().buffered().use { it.copyTo(zip) }
                            zip.closeEntry()
                            require(output.size() <= MAX_TELEGRAM_BACKUP_ZIP_BYTES) {
                                "Database Telegram terlalu besar untuk dimasukkan ke backup profil."
                            }
                        }
                    }
                }
                output.toByteArray()
            }
        } catch (error: Exception) {
            if (wasConnected) {
                clientGeneration++
                runCatching { initialize(context, profileId) }
            }
            throw error
        }
        if (wasConnected) {
            clientGeneration++
            initialize(context, profileId)
        }
        val catalog = catalogPreferences(context).getString("items", "[]") ?: "[]"
        return JSONObject()
            .put("credentials", credentials)
            .put("databaseZipBase64", Base64.encodeToString(zipBytes, Base64.NO_WRAP))
            .put("catalog", catalog)
    }

    /** Restore the encrypted Telegram database and re-wrap its key on this installation. */
    suspend fun restoreProfileBackup(context: Context, profileId: String, payload: JSONObject): String =
        withContext(Dispatchers.IO) {
            require(profileId.isNotBlank()) { "Profil tujuan Telegram tidak valid." }
            val credentialPayload = payload.getJSONObject("credentials")
            val apiId = credentialPayload.optString("apiId")
            val apiHash = credentialPayload.optString("apiHash")
            val databaseKey = Base64.decode(credentialPayload.getString("tdlibDatabaseKey"), Base64.NO_WRAP)
            require(apiId.toLongOrNull()?.let { it > 0L } == true) { "API ID Telegram dalam backup tidak valid." }
            require(apiHash.matches(Regex("^[A-Fa-f0-9]{32}$")) && databaseKey.size == 32) {
                "Kredensial atau kunci database Telegram dalam backup tidak valid."
            }
            val catalog = payload.optString("catalog", "[]")
            JSONArray(catalog)

            val baseDirectory = File(context.filesDir, "telegram/tdlib").canonicalFile
            require(baseDirectory.mkdirs() || baseDirectory.isDirectory) {
                "Folder database Telegram tidak dapat dibuat."
            }
            val databaseRoot = File(baseDirectory, "database").canonicalFile
            val staging = File(baseDirectory, "database.restore.tmp").canonicalFile
            val previous = File(baseDirectory, "database.previous.tmp").canonicalFile
            require(staging.parentFile == baseDirectory && previous.parentFile == baseDirectory) {
                "Lokasi pemulihan Telegram tidak valid."
            }
            staging.deleteRecursively()
            require(staging.mkdirs() || staging.isDirectory)

            try {
                val encodedZip = payload.optString("databaseZipBase64")
                if (encodedZip.isNotBlank()) {
                    val zipBytes = Base64.decode(encodedZip, Base64.NO_WRAP)
                    require(zipBytes.size <= MAX_TELEGRAM_BACKUP_ZIP_BYTES) {
                        "Arsip database Telegram melebihi batas yang diizinkan."
                    }
                    var expandedBytes = 0L
                    ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            val target = File(staging, entry.name).canonicalFile
                            require(target == staging || target.path.startsWith(staging.path + File.separator)) {
                                "Arsip Telegram berisi jalur file yang tidak valid."
                            }
                            if (entry.isDirectory) {
                                require(target.mkdirs() || target.isDirectory)
                            } else {
                                require(target.parentFile?.mkdirs() == true || target.parentFile?.isDirectory == true)
                                target.outputStream().buffered().use { output ->
                                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                    while (true) {
                                        val count = zip.read(buffer)
                                        if (count < 0) break
                                        expandedBytes += count
                                        require(expandedBytes <= MAX_TELEGRAM_BACKUP_EXPANDED_BYTES) {
                                            "Database Telegram hasil ekstraksi terlalu besar."
                                        }
                                        output.write(buffer, 0, count)
                                    }
                                }
                            }
                            zip.closeEntry()
                        }
                    }
                }

                val credentialStore = TelegramApiCredentialStore(context)
                val previousCredentials = credentialStore.exportPortableData()
                val previousOwner = ownerProfileId(context)
                val previousCatalog = catalogPreferences(context).getString("items", "[]") ?: "[]"

                val currentClient = client
                if (currentClient != null) {
                    val closeResult = CompletableDeferred<Unit>()
                    currentClient.send(TdApi.Close()) { _ -> closeResult.complete(Unit) }
                    val acknowledged = withTimeoutOrNull(15_000L) { closeResult.await(); true } ?: false
                    require(acknowledged) { "TDLib belum berhenti. Pemulihan Telegram dibatalkan." }
                    val closed = withTimeoutOrNull(15_000L) {
                        mutableState.first { it.stage == AuthStage.NOT_CONFIGURED }
                        true
                    } ?: false
                    require(closed) { "Database Telegram masih aktif. Pemulihan dibatalkan." }
                    clientGeneration++
                }

                previous.deleteRecursively()
                if (databaseRoot.exists()) {
                    require(databaseRoot.renameTo(previous)) {
                        "Database Telegram lama tidak dapat diamankan sebelum pemulihan."
                    }
                }
                if (!staging.renameTo(databaseRoot)) {
                    previous.renameTo(databaseRoot)
                    throw IllegalStateException("Database Telegram baru tidak dapat dipasang.")
                }

                try {
                    credentialStore.importPortableData(credentialPayload)
                    check(profileBindingPreferences(context).edit().putString("owner_profile_id", profileId).commit()) {
                        "Kepemilikan integrasi Telegram gagal dipulihkan."
                    }
                    check(catalogPreferences(context).edit().putString("items", catalog).commit()) {
                        "Katalog Telegram gagal dipulihkan."
                    }
                } catch (error: Exception) {
                    databaseRoot.deleteRecursively()
                    previous.renameTo(databaseRoot)
                    if (previousCredentials != null) {
                        runCatching { credentialStore.importPortableData(previousCredentials) }
                    } else {
                        credentialStore.clearAll()
                    }
                    val bindingEditor = profileBindingPreferences(context).edit()
                    if (previousOwner.isNullOrBlank()) bindingEditor.remove("owner_profile_id")
                    else bindingEditor.putString("owner_profile_id", previousOwner)
                    bindingEditor.commit()
                    catalogPreferences(context).edit().putString("items", previousCatalog).commit()
                    throw error
                }

                previous.deleteRecursively()
                activeProfileId = ""
                client = null
                mutableState.value = State(message = "Data Telegram dipulihkan. Menyambungkan kembali…")
                initialize(context, profileId)
                val restoredState = withTimeoutOrNull(20_000L) {
                    mutableState.first {
                        it.stage == AuthStage.READY ||
                            it.stage == AuthStage.WAITING_PHONE ||
                            it.stage == AuthStage.WAITING_CODE ||
                            it.stage == AuthStage.WAITING_PASSWORD ||
                            it.stage == AuthStage.ERROR
                    }
                }
                when (restoredState?.stage) {
                    AuthStage.READY ->
                        "Sesi Telegram, kredensial, kunci database, dan katalog berhasil dipulihkan tanpa login ulang."
                    AuthStage.WAITING_PHONE, AuthStage.WAITING_CODE, AuthStage.WAITING_PASSWORD ->
                        "Database Telegram dipulihkan, tetapi Telegram meminta verifikasi karena sesi tidak lagi diterima."
                    AuthStage.ERROR ->
                        "Data Telegram dipulihkan, tetapi koneksi gagal: ${restoredState.message}"
                    else ->
                        "Data Telegram dipulihkan. Koneksi dilanjutkan di latar belakang."
                }
            } catch (error: Exception) {
                staging.deleteRecursively()
                ownerProfileId(context)?.let { owner -> runCatching { initialize(context, owner) } }
                throw error
            }
        }

    private fun restoreCatalog(context: Context) {
        val stored = runCatching { catalogPreferences(context).getString("items", "[]") ?: "[]" }.getOrDefault("[]")
        val restored = runCatching {
            val array = JSONArray(stored)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optLong("id", 0L)
                    if (id == 0L) continue
                    add(TelegramChat(id, item.optString("title"), item.optString("kind"), item.optInt("unreadCount"), item.optInt("photoFileId"), item.optBoolean("isArchived"), ""))
                }
            }
        }.getOrDefault(emptyList())
        if (restored.isNotEmpty()) {
            val merged = (mutableState.value.chats + restored).distinctBy { it.id }.sortedBy { it.title.lowercase() }
            mutableState.value = mutableState.value.copy(chats = merged)
        }
    }

    private fun persistCatalog() {
        val context = appContext ?: return
        val array = JSONArray()
        mutableState.value.chats.forEach { chat ->
            array.put(JSONObject().apply {
                put("id", chat.id); put("title", chat.title); put("kind", chat.kind)
                put("unreadCount", chat.unreadCount); put("photoFileId", chat.photoFileId)
                put("isArchived", chat.isArchived)
            })
        }
        catalogPreferences(context).edit().putString("items", array.toString()).apply()
    }
    private fun publishEligibleChat(chat: TdApi.Chat, kind: String) {
        val isArchived = chat.positions.any { it.list is TdApi.ChatListArchive }
        val photoFileId = chat.photo?.small?.id ?: 0
        val previous = mutableState.value.chats.firstOrNull { it.id == chat.id }
        val item = TelegramChat(
            chat.id, chat.title.orEmpty(), kind, chat.unreadCount, photoFileId, isArchived,
            previous?.photoPath.orEmpty().takeIf { previous?.photoFileId == photoFileId }.orEmpty()
        )
        val updated = (mutableState.value.chats.filterNot { it.id == item.id } + item)
            .sortedBy { it.title.lowercase() }
        mutableState.value = mutableState.value.copy(chats = updated)
        persistCatalog()

        // Load profile photos for the integration list even before a playlist is created.
        if (photoFileId > 0 && item.photoPath.isBlank() && photoDownloadsInFlight.add(chat.id)) {
            downloadChatPhoto(chat.id) { path ->
                photoDownloadsInFlight.remove(chat.id)
                if (!path.isNullOrBlank()) {
                    mutableState.value = mutableState.value.copy(
                        chats = mutableState.value.chats.map { current ->
                            if (current.id == chat.id && current.photoFileId == photoFileId) current.copy(photoPath = android.net.Uri.fromFile(File(path)).toString())
                            else current
                        }
                    )
                }
            }
        }
    }

    private fun registerDownloadCallback(fileId: Int, callback: (String?) -> Unit): Boolean =
        synchronized(cacheCleanupLock) {
            if (cacheCleanupInProgress) return@synchronized false
            downloadCallbacks.computeIfAbsent(fileId) { CopyOnWriteArrayList() }.add(callback)
            true
        }

    private fun completeDownload(fileId: Int, path: String?) {
        val callbacks = synchronized(cacheCleanupLock) {
            downloadCallbacks.remove(fileId)?.also { activeDownloadCallbacks.addAndGet(it.size) }
        }
        callbacks?.forEach { callback ->
            try {
                runCatching { callback(path) }
            } finally {
                activeDownloadCallbacks.decrementAndGet()
            }
        }
    }

    private fun send(function: TdApi.Function<*>) {
        val activeClient = client
        if (activeClient == null) {
            mutableState.value = mutableState.value.copy(stage = AuthStage.ERROR, message = "Koneksi Telegram belum diinisialisasi.")
            return
        }
        activeClient.send(function, { result ->
            if (result is TdApi.Error) {
                mutableState.value = mutableState.value.copy(
                    message = "Telegram (${result.code}): ${result.message}"
                )
            }
        })
    }

    @Synchronized
    fun logout() {
        val activeClient = client ?: return
        activeClient.send(TdApi.LogOut(), { result ->
            if (result is TdApi.Error) {
                mutableState.value = mutableState.value.copy(message = "Gagal keluar dari Telegram: ${result.message}")
            }
        })
        client = null
        mutableState.value = mutableState.value.copy(stage = AuthStage.NOT_CONFIGURED, message = "Sesi Telegram sedang diakhiri.")
    }
}
