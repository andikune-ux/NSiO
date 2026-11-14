package com.example

import android.content.pm.ActivityInfo
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.StatFs
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
                                    "101" -> {
                                        val prefs = context.getSharedPreferences("nsio_developer", android.content.Context.MODE_PRIVATE)
                                        val enabled = !prefs.getBoolean("enabled", false)
                                        prefs.edit().putBoolean("enabled", enabled).apply()
                                        "Developer Mode ${if (enabled) "diaktifkan" else "dinonaktifkan"}."
                                    }
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
                                    "666" -> "INFO IZIN\nPeriksa izin aplikasi NSiO melalui Pengaturan Android. Aplikasi hanya dapat menampilkan status izin yang memang diminta oleh versi terpasang."
                                    "777" -> {
                                        val stat = StatFs(context.filesDir.absolutePath)
                                        val total = stat.totalBytes / (1024 * 1024)
                                        val free = stat.availableBytes / (1024 * 1024)
                                        "PENYIMPANAN APLIKASI\nTerpakai: ${total - free} MB\nTersedia: ${free} MB\nTotal partisi: ${total} MB"
                                    }
                                    "888" -> "STATISTIK NSiO\nMedia terdaftar: ${viewModel.mediaList.value.size}\nPlaylist/sumber: ${viewModel.sourcePlaylists.value.size}"
                                    "123" -> {
                                        showCacheResetConfirm = true
                                        null
                                    }
                                    "444" -> "DIAGNOSTIK MEDIA PLAYER\nStatus: ${if (currentlyPlaying != null) "Sedang memutar media" else "Player tidak aktif"}"
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
                                runCatching {
                                    context.cacheDir.deleteRecursively()
                                    context.externalCacheDir?.deleteRecursively()
                                }
                                showCacheResetConfirm = false
                                secretDialogText = "Cache sementara berhasil dibersihkan."
                            }) { Text("Bersihkan") }
                        },
                        dismissButton = { Button(onClick = { showCacheResetConfirm = false }) { Text("Batal") } }
                    )
                }

                crashLog?.let { log ->
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
