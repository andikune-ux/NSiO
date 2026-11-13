package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = Color(0xFF061014),
    secondary = CyanSecondary,
    onSecondary = Color(0xFF071116),
    tertiary = CyanTertiary,
    background = DarkBackground,
    onBackground = Color(0xFFE5E7EB),
    surface = DarkSurface,
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFADB4BE),
    outline = DarkBorder,
    error = AccentRed
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF087F91),
    onPrimary = Color.White,
    secondary = Color(0xFF397F91),
    onSecondary = Color.White,
    tertiary = Color(0xFF666EAA),
    background = LightBackground,
    onBackground = Color(0xFF252B34),
    surface = LightSurface,
    onSurface = Color(0xFF252B34),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF536171),
    outline = LightBorder,
    error = Color(0xFFB42335)
)

@Composable
fun MyApplicationTheme(
    themeMode: String = "AUTO",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
