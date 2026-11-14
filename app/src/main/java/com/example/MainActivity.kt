package com.example

import android.content.pm.ActivityInfo
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ui.MainViewModel
import com.example.ui.components.AmbientFluidBackground
import com.example.ui.components.LocalAppLanguage
import com.example.ui.components.translateAppText
import com.example.ui.components.BottomNavGlassBar
import com.example.ui.components.NavDestination
import com.example.ui.components.VerticalNavToolbar
import com.example.ui.screens.ExtensionScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemNotificationBar()

        setContent {
            val context = LocalContext.current
            var isCalculatorUnlocked by remember { mutableStateOf(false) }
            var secretDialogText by remember { mutableStateOf<String?>(null) }
            var showCacheResetConfirm by remember { mutableStateOf(false) }
            var crashLog by remember { mutableStateOf(CrashLogStore.read(context)) }
            val settings by viewModel.settings.collectAsState()
            val currentlyPlaying by viewModel.currentlyPlaying.collectAsState()
            val playerState by viewModel.player.playerState.collectAsState()
            val activeProfile by viewModel.activeProfile.collectAsState()
            val statusMessage by viewModel.statusMessage.collectAsState()

            // Dynamic orientation response according to user settings
            LaunchedEffect(settings.displayMode) {
                requestedOrientation = when (settings.displayMode) {
                    "TV" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    "HP" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }

            var currentNav by rememberSaveable { mutableStateOf(NavDestination.HOME) }
            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(statusMessage, settings.language) {
                statusMessage?.let { msg ->
                    snackbarHostState.showSnackbar(translateAppText(msg, settings.language))
                    viewModel.clearStatus()
                }
            }

            MyApplicationTheme(themeMode = settings.themeMode) {
                CompositionLocalProvider(LocalAppLanguage provides settings.language) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 1. iOS 26 Ambient Fluid Glow Canvas
                    val isDark = androidx.compose.material3.MaterialTheme.colorScheme.background.red < 0.2f
                    AmbientFluidBackground(isDark = isDark)

                    // Calculator gate: main application UI is not composed until the correct PIN is entered.
                    if (!isCalculatorUnlocked) {
                        CalculatorAccessGate(
                            onUnlock = { isCalculatorUnlocked = true },
                            onSecretCode = { code ->
                                val message = when (code) {
                                    "000" -> SecretCodeRegistry.allCodes
                                    "101" -> developerDiagnostics(context, playerState, viewModel.mediaList.value.size, viewModel.sourcePlaylists.value.size)
                                    "103" -> "PANDUAN GESTUR NSiO\n• Gunakan gestur yang tersedia pada pemutar media.\n• Gunakan DPAD/remote untuk berpindah fokus.\n• Tekan Back untuk kembali."
                                    "104" -> {
                                        viewModel.toggleDisplayMode()
                                        "Mode tampilan diganti. Periksa Pengaturan untuk melihat mode aktif."
                                    }
                                    "111" -> CrashLogStore.read(context) ?: "Belum ada catatan crash tersimpan."
                                    "333" -> "INFORMASI SISTEM\nPerangkat: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\nArsitektur: ${Build.SUPPORTED_ABIS.joinToString()}"
                                    "555" -> {
                                        val cm = context.getSystemService(ConnectivityManager::class.java)
                                        val network = cm?.activeNetwork
                                        val capabilities = network?.let { cm.getNetworkCapabilities(it) }
                                        "INFORMASI JARINGAN\nStatus: ${if (capabilities != null) "Terhubung" else "Tidak terhubung"}\nWi-Fi: ${capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true}\nSeluler: ${capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true}"
                                    }
                                    "666" -> permissionDiagnostics(context)
                                    "777" -> {
                                        secretDialogText = "Menghitung penggunaan penyimpanan NSiO…"
                                        Thread {
                                            val report = appStorageDiagnostics(context)
                                            runOnUiThread { secretDialogText = report }
                                        }.start()
                                        null
                                    }
                                    "888" -> "STATISTIK NSiO\nMedia terdaftar: ${viewModel.mediaList.value.size}\nPlaylist/sumber: ${viewModel.sourcePlaylists.value.size}"
                                    "123" -> {
                                        showCacheResetConfirm = true
                                        null
                                    }
                                    "444" -> playerDiagnostics(currentlyPlaying?.title, playerState)
                                    "456" -> {
                                        viewModel.refreshAllSourcePlaylists()
                                        "Penyegaran sumber playlist dimulai."
                                    }
                                    "200" -> {
                                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        })
                                        null
                                    }
                                    "202" -> {
                                        runCatching {
                                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            })
                                        }.onFailure { secretDialogText = "Pengaturan notifikasi tidak tersedia pada perangkat ini." }
                                        null
                                    }
                                    else -> "Kode tidak dikenal."
                                }
                                if (message != null) secretDialogText = message
                            }
                        )
                    } else if (currentlyPlaying != null) {
                        PlayerScreen(
                            media = currentlyPlaying!!,
                            viewModel = viewModel,
                            onClose = { viewModel.closePlayer() }
                        )
                    } else {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            contentWindowInsets = WindowInsets(0, 0, 0, 0),
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            containerColor = Color.Transparent
                        ) { innerPadding ->
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                val isLandscape = maxWidth >= maxHeight && maxWidth >= 520.dp

                                if (isLandscape) {
                                    // Landscape Layout: Sleek Left Vertical Liquid Glass Capsule
                                    Row(modifier = Modifier.fillMaxSize()) {
                                        VerticalNavToolbar(
                                            currentDestination = currentNav,
                                            onNavigate = { dest -> currentNav = dest },
                                            activeProfileName = activeProfile?.name ?: "NSiO",
                                            activeProfileAvatar = activeProfile?.avatarEmoji ?: "🎬"
                                        )

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxSize()
                                        ) {
                                            AppNavContent(
                                                destination = currentNav,
                                                viewModel = viewModel,
                                                onNavigate = { currentNav = it }
                                            )
                                        }
                                    }
                                } else {
                                    // Portrait Layout: Full Width Content + Floating Bottom Glass Capsule Bar
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxSize()
                                        ) {
                                            AppNavContent(
                                                destination = currentNav,
                                                viewModel = viewModel,
                                                onNavigate = { currentNav = it }
                                            )
                                        }

                                        BottomNavGlassBar(
                                            currentDestination = currentNav,
                                            onNavigate = { dest -> currentNav = dest }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                secretDialogText?.let { message ->
                    AlertDialog(
                        onDismissRequest = { secretDialogText = null },
                        title = { Text(if (message == SecretCodeRegistry.allCodes) "Kode Rahasia NSiO" else "NSiO • Secret Tools") },
                        text = { Text(text = message, modifier = Modifier.verticalScroll(rememberScrollState()), maxLines = 24) },
                        confirmButton = { Button(onClick = { secretDialogText = null }) { Text("Tutup") } }
                    )
                }

                if (showCacheResetConfirm) {
                    AlertDialog(
                        onDismissRequest = { showCacheResetConfirm = false },
                        title = { Text("Bersihkan cache NSiO?") },
                        text = { Text("File sementara aplikasi akan dihapus. Media, playlist, profil, dan pengaturan tidak dihapus.") },
                        confirmButton = {
                            Button(onClick = {
                                val cacheDirs = listOfNotNull(context.cacheDir, context.externalCacheDir)
                                val results = cacheDirs.map { dir -> runCatching { dir.deleteRecursively() }.getOrDefault(false) }
                                val success = results.all { it }
                                showCacheResetConfirm = false
                                secretDialogText = if (success) {
                                    "Cache sementara berhasil dibersihkan. Data media, playlist, profil, dan pengaturan tidak disentuh."
                                } else {
                                    "Pembersihan cache tidak sepenuhnya berhasil. Sebagian file mungkin sedang digunakan."
                                }
                            }) { Text("Bersihkan") }
                        },
                        dismissButton = { Button(onClick = { showCacheResetConfirm = false }) { Text("Batal") } }
                    )
                }

                if (isCalculatorUnlocked) crashLog?.let { log ->
                    AlertDialog(
                        onDismissRequest = { crashLog = null },
                        title = { Text("Aplikasi mengalami crash") },
                        text = {
                            Text(
                                text = log,
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                maxLines = 18
                            )
                        },
                        confirmButton = {
                            Button(onClick = {
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                clipboard?.setPrimaryClip(ClipData.newPlainText("NSiO crash log", log))
                                CrashLogStore.clear(context)
                                crashLog = null
                            }) { Text("Salin Log") }
                        },
                        dismissButton = {
                            Button(onClick = {
                                CrashLogStore.clear(context)
                                crashLog = null
                            }) { Text("Hapus") }
                        }
                    )
                }
                }
            }
        }
    }

    override fun onPause() {
        // Prevent native video audio from continuing after the app loses foreground.
        // MainViewModel persists the playback position without closing the player.
        viewModel.pausePlaybackForBackground()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemNotificationBar()
        }
    }

    private fun hideSystemNotificationBar() {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.statusBars())
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
    onNavigate: (NavDestination) -> Unit
) {
    AnimatedContent(
        targetState = destination,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "screen_nav_transition"
    ) { targetDest ->
        when (targetDest) {
            NavDestination.HOME -> HomeScreen(
                viewModel = viewModel,
                onNavigateToExtension = { onNavigate(NavDestination.EXTENSION) }
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
