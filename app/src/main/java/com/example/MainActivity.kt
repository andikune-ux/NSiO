package com.example

import android.content.pm.ActivityInfo
import android.app.Activity
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.content.pm.PackageManager
import java.io.File
import android.provider.Settings
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.util.Rational
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text as Material3Text
import com.example.ui.components.AppText as Text
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ui.MainViewModel
import com.example.ui.components.AmbientFluidBackground
import com.example.ui.components.LocalAppLanguage
import com.example.ui.components.LocalDevModeTextSelection
import com.example.ui.components.translateAppText
import com.example.ui.components.BottomNavGlassBar
import com.example.ui.components.BottomNavSafeAreaGlass
import com.example.ui.components.NavDestination
import com.example.ui.components.VerticalNavToolbar
import com.example.ui.screens.ExtensionScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MultiviewScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var isPipMode by mutableStateOf(false)
    @Volatile private var coreStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemNotificationBar()

        setContent {
            var unlocked by rememberSaveable { mutableStateOf(false) }
            var secretMessage by remember { mutableStateOf<String?>(null) }
            var showCacheResetConfirm by remember { mutableStateOf(false) }
            val context = LocalContext.current

            if (!unlocked) {
                MyApplicationTheme(themeMode = "system") {
                    CalculatorAccessGate(
                        onUnlock = {
                            // Only this transition composes MainApplicationContent and creates MainViewModel.
                            coreStarted = true
                            unlocked = true
                        },
                        onSecretCode = { code ->
                            val message = when (code) {
                                "000" -> SecretCodeRegistry.allCodes
                                "101" -> {
                                    val state = viewModel.player.playerState.value
                                    developerDiagnostics(context, state, viewModel.mediaList.value.size, viewModel.sourcePlaylists.value.size)
                                }
                                "103" -> "PANDUAN GESTUR NSiO\\n• Gunakan gestur yang tersedia pada pemutar media.\\n• Gunakan DPAD/remote untuk berpindah fokus.\\n• Tekan Back untuk kembali."
                                "104" -> { viewModel.toggleDisplayMode(); "Mode tampilan diganti. Periksa Pengaturan untuk melihat mode aktif." }
                                "111" -> CrashLogStore.read(context) ?: "Belum ada catatan crash tersimpan."
                                "333" -> "INFORMASI SISTEM\\nPerangkat: ${Build.MANUFACTURER} ${Build.MODEL}\\nAndroid: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\\nArsitektur: ${Build.SUPPORTED_ABIS.joinToString()}"
                                "555" -> {
                                    val cm = context.getSystemService(ConnectivityManager::class.java)
                                    val capabilities = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
                                    "INFORMASI JARINGAN\\nStatus: ${if (capabilities != null) "Terhubung" else "Tidak terhubung"}\\nWi-Fi: ${capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true}\\nSeluler: ${capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true}"
                                }
                                "666" -> permissionDiagnostics(context)
                                "777" -> {
                                    secretMessage = "Menghitung penggunaan penyimpanan NSiO…"
                                    Thread { val report = appStorageDiagnostics(context); runOnUiThread { secretMessage = report } }.start()
                                    null
                                }
                                "888" -> "STATISTIK NSiO\\nMedia terdaftar: ${viewModel.mediaList.value.size}\\nPlaylist/sumber: ${viewModel.sourcePlaylists.value.size}"
                                "123" -> { showCacheResetConfirm = true; null }
                                "444" -> playerDiagnostics(null, viewModel.player.playerState.value)
                                "456" -> { viewModel.refreshAllSourcePlaylists(); "Penyegaran sumber playlist dimulai." }
                                "200" -> { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${context.packageName}") }); null }
                                "202" -> {
                                    runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName) }) }
                                        .onFailure { secretMessage = "Pengaturan notifikasi tidak tersedia pada perangkat ini." }
                                    null
                                }
                                else -> "Kode tidak dikenal."
                            }
                            if (message != null) secretMessage = message
                        }
                    )
                }
            } else {
                MainApplicationContent(viewModel, isPipMode)
            }

            secretMessage?.let { message ->
                AlertDialog(
                    onDismissRequest = { secretMessage = null },
                    title = { Text(if (message == SecretCodeRegistry.allCodes) "Kode Rahasia NSiO" else "NSiO • Secret Tools") },
                    text = { Text(text = message, modifier = Modifier.verticalScroll(rememberScrollState()), maxLines = 24) },
                    confirmButton = { Button(onClick = { secretMessage = null }) { Text("Tutup") } }
                )
            }
            if (showCacheResetConfirm) {
                AlertDialog(
                    onDismissRequest = { showCacheResetConfirm = false },
                    title = { Text("Bersihkan cache NSiO?") },
                    text = { Text("File sementara aplikasi akan dihapus. Media, playlist, profil, dan pengaturan tidak dihapus.") },
                    confirmButton = {
                        Button(onClick = {
                            val dirs = listOfNotNull(context.cacheDir, context.externalCacheDir)
                            val success = dirs.map { dir -> runCatching { dir.deleteRecursively() }.getOrDefault(false) }.all { it }
                            showCacheResetConfirm = false
                            secretMessage = if (success) "Cache sementara berhasil dibersihkan. Data media, playlist, profil, dan pengaturan tidak disentuh." else "Pembersihan cache tidak sepenuhnya berhasil. Sebagian file mungkin sedang digunakan."
                        }) { Text("Bersihkan") }
                    },
                    dismissButton = { Button(onClick = { showCacheResetConfirm = false }) { Text("Batal") } }
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val media = viewModel.currentlyPlaying.value ?: return
        if (media.type.equals("PHOTO", ignoreCase = true)) return
        if (!viewModel.player.playerState.value.isPlaying) return
        val uiMode = resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        if (uiMode == Configuration.UI_MODE_TYPE_TELEVISION) return

        val videoSize = viewModel.player.exoPlayer?.videoSize
        val rawWidth = videoSize?.width?.takeIf { it > 0 } ?: 16
        val rawHeight = videoSize?.height?.takeIf { it > 0 } ?: 9
        val ratio = (rawWidth.toFloat() / rawHeight.toFloat()).coerceIn(0.42f, 2.35f)
        val numerator = (ratio * 1000).toInt().coerceAtLeast(1)
        val builder = android.app.PictureInPictureParams.Builder()
            .setAspectRatio(Rational(numerator, 1000))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(true)
        }
        runCatching { enterPictureInPictureMode(builder.build()) }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPipMode = isInPictureInPictureMode
    }

    override fun onPause() {
        // MediaSessionService now owns background playback; do not pause on Activity onPause.
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemNotificationBar()
    }

    private fun hideSystemNotificationBar() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.statusBars())
    }
}

@Composable
private fun MainApplicationContent(viewModel: MainViewModel, isPipMode: Boolean) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val devMode by viewModel.devMode.collectAsState()
    val currentlyPlaying by viewModel.currentlyPlaying.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    var crashLog by remember { mutableStateOf(CrashLogStore.read(context)) }
    var currentNav by rememberSaveable { mutableStateOf(NavDestination.HOME) }
    var showMultiview by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(settings.displayMode) {
        (context as? Activity)?.requestedOrientation = when (settings.displayMode) {
            "TV" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            "HP" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    LaunchedEffect(statusMessage, settings.language) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(translateAppText(msg, settings.language))
            viewModel.clearStatus()
        }
    }

    MyApplicationTheme(themeMode = settings.themeMode) {
        CompositionLocalProvider(
            LocalAppLanguage provides settings.language,
            LocalDevModeTextSelection provides devMode
        ) {
            Box(Modifier.fillMaxSize()) {
                val isDark = androidx.compose.material3.MaterialTheme.colorScheme.background.red < 0.2f
                AmbientFluidBackground(isDark = isDark)
                if (showMultiview) {
                    MultiviewScreen(viewModel = viewModel, onClose = { showMultiview = false })
                } else if (currentlyPlaying != null) {
                    PlayerScreen(media = currentlyPlaying!!, viewModel = viewModel, onClose = { viewModel.closePlayer() }, isInPipMode = isPipMode)
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        containerColor = Color.Transparent
                    ) { innerPadding ->
                        BoxWithConstraints(Modifier.fillMaxSize().padding(innerPadding)) {
                            val isLandscape = maxWidth >= maxHeight && maxWidth >= 520.dp
                            if (isLandscape) {
                                Box(Modifier.fillMaxSize()) {
                                    AppNavContent(currentNav, viewModel, devMode) { currentNav = it }
                                    VerticalNavToolbar(currentNav, { currentNav = it }, activeProfile?.avatarEmoji ?: "🎬", Modifier.align(Alignment.CenterStart))
                                }
                            } else {
                                Box(Modifier.fillMaxSize()) {
                                    Box(Modifier.fillMaxSize().padding(bottom = 48.05.dp)) {
                                        AppNavContent(currentNav, viewModel, devMode) { currentNav = it }
                                    }
                                    BottomNavSafeAreaGlass(Modifier.align(Alignment.BottomCenter))
                                    BottomNavGlassBar(currentNav, { currentNav = it }, Modifier.align(Alignment.BottomCenter))
                                }
                            }
                        }
                    }
                }
            }
            if (crashLog != null) {
                val log = crashLog!!
                AlertDialog(
                    onDismissRequest = { crashLog = null },
                    title = { Text("Aplikasi mengalami crash") },
                    text = { Text(text = log, modifier = Modifier.verticalScroll(rememberScrollState()), maxLines = 18) },
                    confirmButton = {
                        Button(onClick = {
                            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("NSiO crash log", log))
                            CrashLogStore.clear(context); crashLog = null
                        }) { Text("Salin Log") }
                    },
                    dismissButton = { Button(onClick = { CrashLogStore.clear(context); crashLog = null }) { Text("Hapus") } }
                )
            }
        }
    }
}



private fun formatMb(bytes: Long): String = String.format(java.util.Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))

private fun directoryBytes(directory: File?): Long {
    if (directory == null || !directory.exists()) return 0L
    return runCatching { directory.walkTopDown().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)
}

private fun appStorageDiagnostics(context: android.content.Context): String {
    val internalFiles = directoryBytes(context.filesDir)
    val cache = directoryBytes(context.cacheDir)
    val noBackup = directoryBytes(context.noBackupFilesDir)
    val codeCache = directoryBytes(context.codeCacheDir)
    val externalFiles = directoryBytes(context.getExternalFilesDir(null))
    val externalCache = directoryBytes(context.externalCacheDir)
    val databaseBytes = context.databaseList().sumOf { directoryBytes(context.getDatabasePath(it)) }
    val preferencesBytes = directoryBytes(File(context.applicationInfo.dataDir, "shared_prefs"))
    val appData = internalFiles + cache + noBackup + codeCache + externalFiles + externalCache + databaseBytes + preferencesBytes
    val stat = StatFs(context.filesDir.absolutePath)
    return buildString {
        appendLine("PENYIMPANAN NSiO")
        appendLine("Data aplikasi terukur: ${formatMb(appData)}")
        appendLine("• File internal: ${formatMb(internalFiles)}")
        appendLine("• Cache internal: ${formatMb(cache)}")
        appendLine("• File tanpa backup: ${formatMb(noBackup)}")
        appendLine("• Code cache: ${formatMb(codeCache)}")
        appendLine("• Database: ${formatMb(databaseBytes)}")
        appendLine("• Preferensi: ${formatMb(preferencesBytes)}")
        appendLine("• File eksternal aplikasi: ${formatMb(externalFiles)}")
        appendLine("• Cache eksternal: ${formatMb(externalCache)}")
        appendLine()
        appendLine("PARTISI DATA PERANGKAT")
        appendLine("Total: ${formatMb(stat.totalBytes)}")
        appendLine("Tersedia: ${formatMb(stat.availableBytes)}")
        append("Kapasitas partisi bukan ukuran data NSiO.")
    }
}

private fun permissionDiagnostics(context: android.content.Context): String {
    val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
    val requested = info.requestedPermissions.orEmpty()
    val legacyStorage = requested.filter {
        it == android.Manifest.permission.READ_EXTERNAL_STORAGE ||
            it == android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    }
    return buildString {
        appendLine("STATUS IZIN NSiO")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        if (legacyStorage.isEmpty()) appendLine("Izin runtime penyimpanan lama: tidak diminta.")
        legacyStorage.forEach { permission ->
            val granted = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
            appendLine("${permission.substringAfterLast('.')}: ${if (granted) "DIIZINKAN" else "BELUM DIIZINKAN"}")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            "android.permission.MANAGE_EXTERNAL_STORAGE" in requested
        ) {
            appendLine("Akses semua file: ${if (Environment.isExternalStorageManager()) "DIIZINKAN" else "BELUM DIIZINKAN"}")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            "android.permission.REQUEST_INSTALL_PACKAGES" in requested
        ) {
            appendLine("Izin memasang paket: ${if (context.packageManager.canRequestPackageInstalls()) "DIIZINKAN" else "BELUM DIIZINKAN"}")
        }
        appendLine("Internet dan status jaringan: izin normal, tidak memakai dialog runtime.")
        append("Hanya izin yang dideklarasikan aplikasi yang ditampilkan.")
    }
}

private fun playerDiagnostics(mediaTitle: String?, state: com.example.player.PlayerState): String {
    val status = when {
        state.error != null -> "ERROR"
        state.isBuffering -> "BUFFERING"
        state.isPlaying -> "PLAYING"
        state.title.isNotBlank() -> "PAUSED / READY"
        else -> "IDLE"
    }
    fun time(ms: Long): String {
        val seconds = ms.coerceAtLeast(0L) / 1000L
        return "%02d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
    }
    return buildString {
        appendLine("DIAGNOSTIK MEDIA PLAYER")
        appendLine("Status engine: $status")
        appendLine("Media: ${mediaTitle ?: state.title.ifBlank { "Tidak ada media" }}")
        appendLine("Sedang diputar: ${if (state.isPlaying) "Ya" else "Tidak"}")
        appendLine("Buffering: ${if (state.isBuffering) "Ya" else "Tidak"}")
        appendLine("Posisi: ${time(state.currentPositionMs)}")
        appendLine("Durasi: ${if (state.durationMs > 0L) time(state.durationMs) else "Belum tersedia"}")
        appendLine("Seek: ${if (state.isSeekable) "Didukung" else "Tidak tersedia"}")
        appendLine("Kecepatan: ${state.playbackSpeed}x")
        appendLine("Volume: ${(state.volume * 100).toInt()}%")
        append("Error: ${state.error ?: "Tidak ada"}")
    }
}

private fun developerDiagnostics(
    context: android.content.Context,
    playerState: com.example.player.PlayerState,
    mediaCount: Int,
    playlistCount: Int
): String {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    return buildString {
        appendLine("NSiO • DEVELOPER DIAGNOSTICS")
        appendLine("Package: ${context.packageName}")
        appendLine("Versi: ${info.versionName ?: "unknown"} (${info.versionCode})")
        appendLine("Android API: ${Build.VERSION.SDK_INT}")
        appendLine("Perangkat: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Media terdaftar: $mediaCount")
        appendLine("Playlist/sumber: $playlistCount")
        appendLine("Player: ${when { playerState.error != null -> "ERROR"; playerState.isBuffering -> "BUFFERING"; playerState.isPlaying -> "PLAYING"; else -> "IDLE / PAUSED" }}")
        appendLine("Crash log tersedia: ${if (CrashLogStore.read(context) != null) "Ya" else "Tidak"}")
        appendLine()
        append("Tools aktif: player (444), crash log (111), izin (666), penyimpanan (777), jaringan (555).")
    }
}

@Composable
private fun AppNavContent(
    destination: NavDestination,
    viewModel: MainViewModel,
    devMode: Boolean,
    onNavigate: (NavDestination) -> Unit
) {
    AnimatedContent(
        targetState = destination,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "screen_nav_transition"
    ) { targetDest ->
        DevModeSelection(enabled = devMode) {
        when (targetDest) {
            NavDestination.HOME -> HomeScreen(
                viewModel = viewModel,
                onNavigateToExtension = { onNavigate(NavDestination.EXTENSION) },
                onNavigateToMultiview = { showMultiview = true }
            )
            NavDestination.EXTENSION -> ExtensionScreen(
                viewModel = viewModel,
                onNavigateToHome = { onNavigate(NavDestination.HOME) }
            )
            NavDestination.SETTINGS -> SettingsScreen(
                viewModel = viewModel
            )
            NavDestination.PROFILE -> ProfileScreen(
                viewModel = viewModel
            )
        }
        }
    }
}


@Composable
private fun DevModeSelection(enabled: Boolean, content: @Composable () -> Unit) {
    // Avoid wrapping dialogs and their separate Compose window in the parent selection hierarchy.
    // SelectionContainer can crash on long-press when Dialog content belongs to another layout tree.
    content()
}
