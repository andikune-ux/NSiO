package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Soft tactile base with restrained liquid-glass color refraction. */
@Composable
fun AmbientFluidBackground(isDark: Boolean = true, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        drawRect(if (isDark) Color(0xFF090A0D) else Color(0xFFE6EAF0))
        if (isDark) {
            drawRect(brush = Brush.linearGradient(
                colors = listOf(Color(0xFF151920).copy(alpha = 0.7f), Color.Transparent, Color(0xFF050608).copy(alpha = 0.5f)),
                start = Offset(0f, 0f), end = Offset(width, height)
            ))
            drawCircle(Brush.radialGradient(listOf(Color(0xFF00C7D8).copy(alpha = 0.12f), Color.Transparent), Offset(width * 0.12f, height * 0.22f), width * 0.42f), radius = width * 0.42f, center = Offset(width * 0.12f, height * 0.22f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFF5866A8).copy(alpha = 0.10f), Color.Transparent), Offset(width * 0.86f, height * 0.78f), width * 0.48f), radius = width * 0.48f, center = Offset(width * 0.86f, height * 0.78f))
        } else {
            drawRect(brush = Brush.linearGradient(
                colors = listOf(Color.White.copy(alpha = 0.42f), Color(0xFFE1E6EE).copy(alpha = 0.10f), Color(0xFFD0D8E3).copy(alpha = 0.35f)),
                start = Offset(0f, 0f), end = Offset(width, height)
            ))
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.82f), Color.Transparent), Offset(width * 0.12f, height * 0.18f), width * 0.48f), radius = width * 0.48f, center = Offset(width * 0.12f, height * 0.18f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFF8BC9D7).copy(alpha = 0.18f), Color.Transparent), Offset(width * 0.88f, height * 0.78f), width * 0.46f), radius = width * 0.46f, center = Offset(width * 0.88f, height * 0.78f))
        }
    }
}
