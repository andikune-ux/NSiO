package com.example.data.telegram

import android.content.Context
import android.os.Build
import com.example.data.security.TelegramApiCredentialStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.concurrent.ConcurrentHashMap

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
        val createdAt: Long = 0L
    )

    data class State(
        val stage: AuthStage = AuthStage.NOT_CONFIGURED,
        val message: String = "Telegram belum dihubungkan.",
        val chats: List<TelegramChat> = emptyList(),
        val media: List<TelegramMedia> = emptyList()
    )

    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()
    @Volatile private var client: Client? = null
    @Volatile private var appContext: Context? = null
    private val downloadCallbacks = ConcurrentHashMap<Int, (String?) -> Unit>()
    private val photoDownloadsInFlight = ConcurrentHashMap.newKeySet<Long>()

    @Synchronized
    fun initialize(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (client != null) return
        val credentials = TelegramApiCredentialStore(app).load()
        if (credentials == null) {
            mutableState.value = State(message = "Masukkan API ID dan API Hash di Pengaturan Telegram.")
            return
        }
        val apiId = credentials.apiId.toIntOrNull()
        if (apiId == null) {
            mutableState.value = State(stage = AuthStage.ERROR, message = "API ID Telegram tidak valid.")
            return
        }
        mutableState.value = State(stage = AuthStage.INITIALIZING, message = "Menginisialisasi koneksi Telegram…")
        // The stable 256-bit database key is wrapped by Android Keystore.
        val root = File(app.filesDir, "telegram/tdlib").apply { mkdirs() }
        val databaseKey = TelegramApiCredentialStore(app).loadOrCreateDatabaseEncryptionKey()
        // TDLib sends AuthorizationStateWaitTdlibParameters after Client.create.
        pendingApiId = apiId
        pendingApiHash = credentials.apiHash
        pendingDatabaseDirectory = File(root, "database").apply { mkdirs() }.absolutePath
        pendingFilesDirectory = File(root, "files").apply { mkdirs() }.absolutePath
        pendingDatabaseKey = databaseKey
        client = Client.create({ update -> handleUpdate(update) }, { error ->
            mutableState.value = mutableState.value.copy(stage = AuthStage.ERROR, message = error.message ?: "Kesalahan TDLib.")
        }, { error ->
            mutableState.value = mutableState.value.copy(message = error.message ?: "Kesalahan TDLib.")
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
            is TdApi.UpdateChatLastMessage -> refreshChat(update.chatId)
            is TdApi.UpdateFile -> {
                val file = update.file
                val local = file.local
                if (local.isDownloadingCompleted && local.path.isNotBlank()) {
                    downloadCallbacks.remove(file.id)?.invoke(local.path)
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
                    useMessageDatabase = true
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
                mutableState.value = State(message = "Sesi Telegram ditutup.")
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

    fun loadChats() {
        val activeClient = client ?: return
        val lists = listOf(
            TdApi.ChatListMain() to "utama",
            TdApi.ChatListArchive() to "arsip"
        )
        lists.forEach { (chatList, label) ->
            activeClient.send(TdApi.LoadChats(chatList, 100), { loadResult ->
                if (loadResult is TdApi.Error && loadResult.code != 404) {
                    mutableState.value = mutableState.value.copy(
                        message = "Gagal memuat chat $label Telegram: ${loadResult.message}"
                    )
                }
                activeClient.send(TdApi.GetChats(chatList, 100), { chatsResult ->
                    if (chatsResult is TdApi.Chats) {
                        chatsResult.chatIds.forEach { refreshChat(it) }
                    } else if (chatsResult is TdApi.Error) {
                        mutableState.value = mutableState.value.copy(
                            message = "Gagal mengambil chat $label Telegram: ${chatsResult.message}"
                        )
                    }
                })
            })
        }
    }

    private fun refreshChat(chatId: Long) {
        client?.send(TdApi.GetChat(chatId), { result ->
            if (result is TdApi.Chat) upsertChat(result)
        })
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
                TelegramMedia(chatId, message.id, content.video.fileName.orEmpty().ifBlank { "Video Telegram" }, it.id, false, content.video.mimeType.orEmpty().ifBlank { "video/mp4" }, content.caption?.text.orEmpty(), message.date * 1000L)
            }
            is TdApi.MessagePhoto -> content.photo?.sizes?.maxByOrNull { it.width.toLong() * it.height }?.photo?.let {
                TelegramMedia(chatId, message.id, "Foto Telegram", it.id, true, "image/jpeg", content.caption?.text.orEmpty(), message.date * 1000L)
            }
            is TdApi.MessageDocument -> content.document?.let { document ->
                val mime = document.mimeType.orEmpty()
                if (mime.startsWith("video/") || mime.startsWith("image/")) {
                    TelegramMedia(chatId, message.id, document.fileName.orEmpty().ifBlank { if (mime.startsWith("image/")) "Foto Telegram" else "Video Telegram" }, document.document.id, mime.startsWith("image/"), mime, content.caption?.text.orEmpty(), message.date * 1000L)
                } else null
            }
            is TdApi.MessageAnimation -> content.animation?.let { animation ->
                TelegramMedia(chatId, message.id, animation.fileName.orEmpty().ifBlank { "Animasi Telegram" }, animation.animation.id, false, animation.mimeType.orEmpty().ifBlank { "video/mp4" }, content.caption?.text.orEmpty(), message.date * 1000L)
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
                        val reachedEnd = oldestId <= 0L || oldestId == cursorMessageId || messages.size < 100
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
        downloadCallbacks[fileId] = onFinished
        client?.send(TdApi.DownloadFile(fileId, 16, 0L, 0L, true)) { result ->
            when (result) {
                is TdApi.File -> {
                    val local = result.local
                    if (local.isDownloadingCompleted && local.path.isNotBlank()) {
                        downloadCallbacks.remove(fileId)?.invoke(local.path)
                    }
                }
                is TdApi.Error -> downloadCallbacks.remove(fileId)?.invoke(null)
            }
        } ?: run {
            downloadCallbacks.remove(fileId)?.invoke(null)
        }
    }

    fun downloadMedia(media: TelegramMedia, onFinished: (String?) -> Unit) {
        val activeClient = client ?: run { onFinished(null); return }
        downloadCallbacks[media.fileId] = onFinished
        activeClient.send(TdApi.DownloadFile(media.fileId, 32, 0L, 0L, true), { result ->
            when (result) {
                is TdApi.File -> {
                    val local = result.local
                    if (local.isDownloadingCompleted && local.path.isNotBlank()) {
                        downloadCallbacks.remove(media.fileId)?.invoke(local.path)
                    }
                    // Otherwise UpdateFile completes the callback when TDLib finishes downloading.
                }
                is TdApi.Error -> {
                    downloadCallbacks.remove(media.fileId)?.invoke(null)
                    mutableState.value = mutableState.value.copy(message = "Unduhan Telegram gagal: ${result.message}")
                }
            }
        })
    }

    private fun upsertChat(chat: TdApi.Chat) {
        val kind = when (val type = chat.type) {
            is TdApi.ChatTypeSupergroup -> if (type.isChannel) "Channel" else "Grup"
            is TdApi.ChatTypeBasicGroup -> "Grup"
            else -> return
        }
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

        // Load profile photos for the integration list even before a playlist is created.
        if (photoFileId > 0 && item.photoPath.isBlank() && photoDownloadsInFlight.add(chat.id)) {
            downloadChatPhoto(chat.id) { path ->
                photoDownloadsInFlight.remove(chat.id)
                if (!path.isNullOrBlank()) {
                    mutableState.value = mutableState.value.copy(
                        chats = mutableState.value.chats.map { current ->
                            if (current.id == chat.id && current.photoFileId == photoFileId) current.copy(photoPath = path)
                            else current
                        }
                    )
                }
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
        mutableState.value = State(message = "Sesi Telegram sedang diakhiri.")
    }
}
