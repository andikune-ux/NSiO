package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * iOS 26 Fluid Ambient Canvas
 * Renders luminous, deep chromatic orbs that shine through translucent Liquid Glass panels.
 */
@Composable
fun AmbientFluidBackground(
    isDark: Boolean = true,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Base background fill
        val baseColor = if (isDark) Color(0xFF06080F) else Color(0xFFF1F5F9)
        drawRect(color = baseColor)

        if (isDark) {
            // Orb 1: Glowing Electric Cyan (Top-Left / behind toolbar)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00E5FF).copy(alpha = 0.18f),
                        Color(0xFF00E5FF).copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.12f, height * 0.25f),
                    radius = width * 0.38f
                )
            )

            // Orb 2: Deep Prismatic Violet (Bottom-Right / under media cards)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF7C3AED).copy(alpha = 0.22f),
                        Color(0xFF4C1D95).copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.85f, height * 0.75f),
                    radius = width * 0.45f
                )
            )

            // Orb 3: Ocean Sapphire (Top-Right / behind top bar)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0284C7).copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.7f, height * 0.15f),
                    radius = width * 0.32f
                )
            )

            // Orb 4: Aurora Magenta (Center-Bottom ambient glow)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFD946EF).copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.4f, height * 0.85f),
                    radius = width * 0.35f
                )
            )
        } else {
            // Light Mode: Pearlescent Glass Sheen Orbs
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF38BDF8).copy(alpha = 0.14f),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.15f, height * 0.25f),
                    radius = width * 0.4f
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFA855F7).copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.8f, height * 0.75f),
                    radius = width * 0.45f
                )
            )
        }
    }
}
