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
 * iOS 26 Liquid Glass Modifier
 * Implements fluid translucency, refraction border highlights, ambient light sheen,
 * and dynamic TV D-pad focus interaction.
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

    // Surface brush: Sleek translucent in dark mode, creamy frosted glass in light mode
    val surfaceBrush = if (isDark) {
        val baseAlpha = alpha
        Brush.verticalGradient(
            colors = listOf(
                tint.copy(alpha = (baseAlpha * 1.4f).coerceAtMost(0.4f)),
                tint.copy(alpha = (baseAlpha * 0.8f).coerceAtMost(0.25f)),
                tint.copy(alpha = (baseAlpha * 1.1f).coerceAtMost(0.35f))
            )
        )
    } else {
        // Light mode: Clean, high-contrast milky frosted acrylic with subtle tint
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.90f),
                Color(0xFFF8FAFC).copy(alpha = 0.82f),
                Color(0xFFF1F5F9).copy(alpha = 0.86f)
            )
        )
    }

    // Specular refraction border: top-left catches light, bottom-right drops off
    val borderBrush = if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                tint.copy(alpha = (borderAlpha * 1.5f).coerceAtMost(0.9f)),
                tint.copy(alpha = (borderAlpha * 0.4f).coerceAtMost(0.5f)),
                tint.copy(alpha = (borderAlpha * 0.1f).coerceAtMost(0.2f)),
                tint.copy(alpha = (borderAlpha * 0.6f).coerceAtMost(0.7f))
            ),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.95f),
                Color(0xFFCBD5E1).copy(alpha = 0.65f),
                Color(0xFFE2E8F0).copy(alpha = 0.40f),
                Color.White.copy(alpha = 0.80f)
            ),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    }

    val shadowAmbient = if (isDark) Color.Black.copy(alpha = 0.4f) else Color(0xFF64748B).copy(alpha = 0.14f)
    val shadowSpot = if (isDark) CyanPrimary.copy(alpha = 0.15f) else Color(0xFF0F172A).copy(alpha = 0.08f)

    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = shadowAmbient,
            spotColor = shadowSpot
        )
        .clip(shape)
        .background(surfaceBrush)
        .border(width = 1.dp, brush = borderBrush, shape = shape)
        .then(
            if (hasSheen) {
                Modifier.drawBehind {
                    // Subtle light refraction highlight on the top edge
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                if (isDark) tint.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.6f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = size.height * 0.35f
                        )
                    )
                }
            } else {
                Modifier
            }
        )
}

/**
 * Liquid Glass with TV Remote Focus support:
 * Pulsing refraction border and smooth 1.05x magnification on D-pad navigation.
 */
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

    return this
        .scale(scale)
        .focusable(interactionSource = interactionSource)
        .then(
            if (isFocused) {
                Modifier.border(
                    width = 2.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            focusedBorderColor,
                            Color(0xFF38BDF8),
                            Color(0xFFA855F7),
                            focusedBorderColor
                        )
                    ),
                    shape = shape
                )
            } else {
                Modifier
            }
        )
}
