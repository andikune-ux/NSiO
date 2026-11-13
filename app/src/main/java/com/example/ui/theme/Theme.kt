package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = BrandPrimarySoft,
    onPrimary = Color(0xFF17151F),
    primaryContainer = Color(0xFF39354F),
    onPrimaryContainer = Color(0xFFE8E4FF),
    secondary = BrandSecondary,
    onSecondary = Color(0xFF111820),
    tertiary = BrandTertiary,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorderSubtle,
    error = AccentRed
)

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E5FF),
    onPrimaryContainer = Color(0xFF39336F),
    secondary = BrandSecondary,
    onSecondary = Color.White,
    tertiary = BrandTertiary,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorderSubtle,
    outlineVariant = Color(0xFFE2E5EE),
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
