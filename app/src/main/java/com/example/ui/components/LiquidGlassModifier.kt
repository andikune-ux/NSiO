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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.offset
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.FocusHighlight
import com.example.ui.theme.NeumorphBlackShadow
import com.example.ui.theme.NeumorphDarkShadow
import com.example.ui.theme.NeumorphLightShadow
import com.example.ui.theme.NeumorphDarkHighlight

/**
 * Matte neumorphic surface. Kept under the historical name so existing screens
 * retain their API while the visual language changes from glass to soft relief.
 * Raised controls use one consistent top-left light source and a paired shadow.
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(16.dp),
    tint: Color = Color.White,
    alpha: Float = 0.08f,
    borderAlpha: Float = 0.28f,
    elevation: Dp = 8.dp,
    hasSheen: Boolean = false
): Modifier {
    val isDark = MaterialTheme.colorScheme.background == MaterialTheme.colorScheme.surface &&
        MaterialTheme.colorScheme.background.red < 0.2f
    val surface = MaterialTheme.colorScheme.surface
    val lightShadow = if (isDark) NeumorphDarkHighlight else NeumorphLightShadow
    val darkShadow = if (isDark) NeumorphBlackShadow else NeumorphDarkShadow
    val shadowDepth = elevation.coerceIn(3.dp, 18.dp)
    val relief = (0.42f + elevation.value / 36f).coerceIn(0.42f, 0.78f)

    val shadowShift = (elevation.value * 0.28f).coerceIn(2f, 5f).dp
    return this
        // Offset paired shadows in opposite directions to model one upper-left light source.
        .offset(x = -shadowShift, y = -shadowShift)
        .shadow(shadowDepth, shape, ambientColor = lightShadow.copy(alpha = relief * 0.54f), spotColor = lightShadow.copy(alpha = relief * 0.42f))
        .offset(x = shadowShift, y = shadowShift)
        .offset(x = shadowShift, y = shadowShift)
        .shadow(shadowDepth, shape, ambientColor = darkShadow.copy(alpha = relief), spotColor = darkShadow.copy(alpha = relief * 0.82f))
        .offset(x = -shadowShift, y = -shadowShift)
        .clip(shape)
        .background(surface)
        // A restrained bevel replaces the former bright glass rim.
        .border(
            0.65.dp,
            Brush.linearGradient(
                if (isDark) listOf(NeumorphDarkHighlight.copy(alpha = 0.38f), Color.Transparent, Color.Black.copy(alpha = 0.28f))
                else listOf(Color.White.copy(alpha = 0.72f), Color.Transparent, NeumorphDarkShadow.copy(alpha = 0.34f))
            ),
            shape
        )
}

/** Concave/inset state for selected controls and fields. */
@Composable
fun Modifier.neumorphicInset(
    shape: Shape = RoundedCornerShape(14.dp),
    depth: Dp = 5.dp
): Modifier {
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    val surface = MaterialTheme.colorScheme.surface
    val upperEdge = if (isDark) Color.Black.copy(alpha = 0.42f) else NeumorphDarkShadow.copy(alpha = 0.72f)
    val lowerEdge = if (isDark) NeumorphDarkHighlight.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.88f)
    return this
        .clip(shape)
        .background(surface)
        .border(
            depth.coerceIn(1.dp, 8.dp) / 2,
            Brush.linearGradient(listOf(upperEdge, Color.Transparent, lowerEdge)),
            shape
        )
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
            Brush.linearGradient(listOf(focusedBorderColor, CyanPrimary.copy(alpha = 0.55f))),
            shape
        ) else Modifier
    )
}
