package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * NSiO semantic design tokens inspired by IO Invitation Studio.
 * Components should consume these roles instead of defining local palettes.
 */

// Brand and semantic accents
val BrandPrimary = Color(0xFF7167D9)
val BrandPrimarySoft = Color(0xFF8A82E4)
val BrandSecondary = Color(0xFF6F91B8)
val BrandTertiary = Color(0xFF7B9FAD)
val AccentGreen = Color(0xFF299B73)
val AccentYellow = Color(0xFFB88736)
val AccentRed = Color(0xFFB95765)

// Light theme: cool porcelain, lavender undertone and restrained soft depth.
val LightBackground = Color(0xFFE9EDF6)
val LightSurface = Color(0xFFF5F6FB)
val LightSurfaceVariant = Color(0xFFE7EAF3)
val LightSurfaceRaised = Color(0xFFF9FAFD)
val LightSurfaceInset = Color(0xFFE6EAF3)
val LightTextPrimary = Color(0xFF20243A)
val LightTextSecondary = Color(0xFF69718A)
val LightTextMuted = Color(0xFF9299AF)
val LightBorder = Color(0xFFFFFFFF)
val LightBorderSubtle = Color(0xFFD9DEEA)
val NeumorphLightShadow = Color(0xFFFFFFFF)
val NeumorphDarkShadow = Color(0xFFBEC6D6)

// Dark theme: matte graphite, low-gloss surfaces and quiet contrast.
val DarkBackground = Color(0xFF17191D)
val DarkSurface = Color(0xFF202329)
val DarkSurfaceVariant = Color(0xFF282C33)
val DarkSurfaceRaised = Color(0xFF252930)
val DarkSurfaceInset = Color(0xFF191C21)
val DarkTextPrimary = Color(0xFFE7E8EC)
val DarkTextSecondary = Color(0xFFB0B4BD)
val DarkTextMuted = Color(0xFF858B96)
val DarkBorder = Color(0xFF363B44)
val DarkBorderSubtle = Color(0xFF2B3038)
val NeumorphBlackShadow = Color(0xFF0D0F12)
val NeumorphDarkHighlight = Color(0xFF30353D)

// Compatibility names retained for existing UI call sites.
val CyanPrimary = BrandPrimary
val CyanSecondary = BrandSecondary
val CyanTertiary = BrandTertiary
val PurplePrism = BrandPrimarySoft

// Focus is an outline on the focused control, never a full-screen glow.
val FocusHighlight = Color(0xFF8B83E8)
