package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Continuous matte material plane. Neumorphism relies on a quiet, uniform
 * substrate; ambient glow and glass-like reflections are intentionally absent.
 */
@Composable
fun AmbientFluidBackground(isDark: Boolean = true, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(if (isDark) Color(0xFF17191D) else Color(0xFFE2E7EF))
    }
}
