package com.example

import android.content.pm.ActivityInfo
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.ui.MainViewModel
import com.example.ui.components.AmbientFluidBackground
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
        // Enforce landscape orientation for Android TV and Landscape phone mode
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        enableEdgeToEdge()

        setContent {
            val settings by viewModel.settings.collectAsState()
            val currentlyPlaying by viewModel.currentlyPlaying.collectAsState()
            val activeProfile by viewModel.activeProfile.collectAsState()
            val statusMessage by viewModel.statusMessage.collectAsState()

            // Dynamic orientation response according to user settings
            LaunchedEffect(settings.displayMode) {
                requestedOrientation = when (settings.displayMode) {
                    "TV" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    "HP" -> ActivityInfo.SCREEN_ORIENTATION_USER
                    else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            }

            var currentNav by remember { mutableStateOf(NavDestination.HOME) }
            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(statusMessage) {
                statusMessage?.let { msg ->
                    snackbarHostState.showSnackbar(msg)
                    viewModel.clearStatus()
                }
            }

            MyApplicationTheme(themeMode = settings.themeMode) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 1. iOS 26 Ambient Fluid Glow Canvas (Underlying mesh gradient for Liquid Glass refraction)
                    val isDark = settings.themeMode != "LIGHT"
                    AmbientFluidBackground(isDark = isDark)

                    // 2. Active Screen Content / Fullscreen Player
                    if (currentlyPlaying != null) {
                        PlayerScreen(
                            media = currentlyPlaying!!,
                            viewModel = viewModel,
                            onClose = { viewModel.closePlayer() }
                        )
                    } else {
                        // Translucent Landscape Layout with Floating Liquid Glass Toolbar
                        Scaffold(
                            modifier = Modifier
                                .fillMaxSize()
                                .safeDrawingPadding(),
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            containerColor = Color.Transparent
                        ) { innerPadding ->
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                // Floating Liquid Glass Capsule Toolbar
                                VerticalNavToolbar(
                                    currentDestination = currentNav,
                                    onNavigate = { dest -> currentNav = dest },
                                    activeProfileName = activeProfile?.name ?: "NSiO",
                                    activeProfileAvatar = activeProfile?.avatarEmoji ?: "🎬"
                                )

                                // Right Content Area
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                ) {
                                    AnimatedContent(
                                        targetState = currentNav,
                                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                                        label = "screen_nav_transition"
                                    ) { destination ->
                                        when (destination) {
                                            NavDestination.HOME -> HomeScreen(
                                                viewModel = viewModel,
                                                onNavigateToExtension = { currentNav = NavDestination.EXTENSION }
                                            )
                                            NavDestination.EXTENSION -> ExtensionScreen(
                                                viewModel = viewModel,
                                                onNavigateToHome = { currentNav = NavDestination.HOME }
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
                        }
                    }
                }
            }
        }
    }
}
