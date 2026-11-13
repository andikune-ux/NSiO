package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.FocusHighlight
import com.example.ui.theme.NeumorphBlackShadow
import com.example.ui.theme.NeumorphDarkHighlight
import com.example.ui.theme.NeumorphDarkShadow
import com.example.ui.theme.NeumorphLightShadow

/**
 * Raised neumorphic material: the component shares the screen's base tone and
 * uses two soft opposing shadows to establish physical depth.
 */
@Composable
fun Modifier.neumorphicSurface(
    shape: Shape = RoundedCornerShape(16.dp),
    elevation: Dp = 7.dp
): Modifier {
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    val surface = MaterialTheme.colorScheme.surface
    val light = if (isDark) NeumorphDarkHighlight else NeumorphLightShadow
    val dark = if (isDark) NeumorphBlackShadow else NeumorphDarkShadow
    val shift = (elevation.value * 0.24f).coerceIn(1.5f, 4f).dp
    val blur = elevation.coerceIn(2.dp, 12.dp)

    return this
        .offset(x = -shift, y = -shift)
        .shadow(blur, shape, ambientColor = light.copy(alpha = 0.38f), spotColor = light.copy(alpha = 0.30f))
        .offset(x = shift, y = shift)
        .offset(x = shift, y = shift)
        .shadow(blur, shape, ambientColor = dark.copy(alpha = 0.62f), spotColor = dark.copy(alpha = 0.48f))
        .offset(x = -shift, y = -shift)
        .clip(shape)
        .background(surface)
        .border(
            0.65.dp,
            Brush.linearGradient(
                if (isDark) listOf(
                    NeumorphDarkHighlight.copy(alpha = 0.34f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.34f)
                ) else listOf(
                    Color.White.copy(alpha = 0.82f),
                    Color.Transparent,
                    NeumorphDarkShadow.copy(alpha = 0.42f)
                )
            ),
            shape
        )
}

/** Recessed/inset material for selected states, wells and input surfaces. */
@Composable
fun Modifier.neumorphicInset(
    shape: Shape = RoundedCornerShape(14.dp),
    depth: Dp = 5.dp
): Modifier {
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    val surface = MaterialTheme.colorScheme.surface
    val upperEdge = if (isDark) Color.Black.copy(alpha = 0.48f) else NeumorphDarkShadow.copy(alpha = 0.78f)
    val lowerEdge = if (isDark) NeumorphDarkHighlight.copy(alpha = 0.76f) else Color.White.copy(alpha = 0.92f)
    return this
        .clip(shape)
        .background(surface)
        .border(
            depth.coerceIn(1.dp, 8.dp) / 2,
            Brush.linearGradient(listOf(upperEdge, Color.Transparent, lowerEdge)),
            shape
        )
}

/**
 * Legacy API compatibility: old Liquid Glass panels now render as matte
 * neumorphic surfaces. No translucent glass, glow or acrylic is painted.
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(16.dp),
    tint: Color = Color.White,
    alpha: Float = 0.08f,
    borderAlpha: Float = 0.28f,
    elevation: Dp = 8.dp,
    hasSheen: Boolean = false
): Modifier = neumorphicSurface(shape = shape, elevation = elevation)

/** Focus-only outline for a single control; no panel/background is added. */
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
        label = "focusBorderScale"
    )
    return this
        .scale(scale)
        .focusable(interactionSource = interactionSource)
        .then(if (isFocused) Modifier.border(1.5.dp, focusedBorderColor, shape) else Modifier)
}
