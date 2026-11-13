package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.FocusHighlight

/**
 * Tactile neumorphic surface fused with OS 26 Liquid Glass:
 * paired soft shadows, a restrained inset/specular rim and translucent acrylic.
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(16.dp),
    tint: Color = Color.White,
    alpha: Float = 0.08f,
    borderAlpha: Float = 0.28f,
    elevation: Dp = 8.dp,
    hasSheen: Boolean = true
): Modifier {
    val isDark = MaterialTheme.colorScheme.background.run { (red + green + blue) < 1.5f }
    val surface = MaterialTheme.colorScheme.surface
    val glassAlpha = if (isDark) (alpha * 0.72f).coerceIn(0.045f, 0.22f) else (0.56f + alpha * 0.5f).coerceIn(0.58f, 0.82f)
    val surfaceBrush = if (isDark) {
        Brush.verticalGradient(listOf(
            Color(0xFF272B32).copy(alpha = glassAlpha),
            Color(0xFF15171C).copy(alpha = glassAlpha + 0.05f),
            Color(0xFF1C1F25).copy(alpha = glassAlpha)
        ))
    } else {
        Brush.verticalGradient(listOf(
            Color.White.copy(alpha = glassAlpha),
            surface.copy(alpha = 0.94f),
            Color(0xFFDDE3EC).copy(alpha = 0.92f)
        ))
    }
    val rim = if (isDark) {
        Brush.linearGradient(listOf(
            Color.White.copy(alpha = (borderAlpha * 0.72f).coerceAtMost(0.42f)),
            Color(0xFF59616D).copy(alpha = 0.22f),
            Color.Black.copy(alpha = 0.30f)
        ))
    } else {
        Brush.linearGradient(listOf(
            Color.White.copy(alpha = 0.96f),
            Color(0xFFB7C1CE).copy(alpha = 0.62f),
            Color.White.copy(alpha = 0.42f)
        ))
    }
    val ambient = if (isDark) Color.Black.copy(alpha = 0.78f) else Color(0xFF8793A3).copy(alpha = 0.25f)
    val spot = if (isDark) Color(0xFF343A44).copy(alpha = 0.42f) else Color.White.copy(alpha = 0.88f)

    return this
        .shadow(elevation, shape, ambientColor = ambient, spotColor = spot)
        .clip(shape)
        .background(surfaceBrush)
        .border(0.8.dp, rim, shape)
        .then(if (hasSheen) Modifier.drawBehind {
            val highlight = if (isDark) Color.White.copy(alpha = 0.045f) else Color.White.copy(alpha = 0.72f)
            drawLine(highlight, Offset(size.width * 0.12f, 1f), Offset(size.width * 0.88f, 1f), strokeWidth = 1.dp.toPx())
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(highlight, Color.Transparent),
                    startY = 0f,
                    endY = size.height * 0.28f
                )
            )
            if (isDark) {
                drawLine(Color.Black.copy(alpha = 0.24f), Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), strokeWidth = 1.dp.toPx())
            }
        } else Modifier)
}

@Composable
fun Modifier.liquidGlassTvFocusable(
    shape: Shape = RoundedCornerShape(16.dp),
    scaleFocused: Float = 1.04f,
    focusedBorderColor: Color = FocusHighlight,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
): Modifier {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) scaleFocused else 1.0f,
        animationSpec = spring(stiffness = 400f, dampingRatio = 0.75f),
        label = "glassFocusScale"
    )
    return this.scale(scale).focusable(interactionSource = interactionSource).then(
        if (isFocused) Modifier.border(
            1.5.dp,
            Brush.linearGradient(listOf(focusedBorderColor, CyanPrimary.copy(alpha = 0.55f), Color.White.copy(alpha = 0.55f))),
            shape
        ) else Modifier
    )
}
