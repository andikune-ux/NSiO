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

    val baseAlpha = if (isDark) alpha else alpha * 2.5f
    val baseBorderAlpha = if (isDark) borderAlpha else borderAlpha * 1.5f

    // Specular refraction border: top-left catches light, bottom-right drops off
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            tint.copy(alpha = (baseBorderAlpha * 1.5f).coerceAtMost(0.9f)),
            tint.copy(alpha = (baseBorderAlpha * 0.4f).coerceAtMost(0.5f)),
            tint.copy(alpha = (baseBorderAlpha * 0.1f).coerceAtMost(0.2f)),
            tint.copy(alpha = (baseBorderAlpha * 0.6f).coerceAtMost(0.7f))
        ),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    // Liquid surface gradient: subtle gradient giving depth
    val surfaceBrush = Brush.verticalGradient(
        colors = listOf(
            tint.copy(alpha = (baseAlpha * 1.4f).coerceAtMost(0.4f)),
            tint.copy(alpha = (baseAlpha * 0.8f).coerceAtMost(0.25f)),
            tint.copy(alpha = (baseAlpha * 1.1f).coerceAtMost(0.35f))
        )
    )

    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.4f),
            spotColor = CyanPrimary.copy(alpha = 0.15f)
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
                                tint.copy(alpha = 0.15f),
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
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
        label = "tv_focus_scale"
    )

    return this
        .scale(scale)
        .focusable(interactionSource = interactionSource)
        .then(
            if (isFocused) {
                Modifier
                    .shadow(elevation = 16.dp, shape = shape, spotColor = focusedBorderColor)
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                focusedBorderColor,
                                focusedBorderColor.copy(alpha = 0.7f),
                                Color.White.copy(alpha = 0.9f)
                            )
                        ),
                        shape = shape
                    )
            } else {
                Modifier.border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.25f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    ),
                    shape = shape
                )
            }
        )
}
