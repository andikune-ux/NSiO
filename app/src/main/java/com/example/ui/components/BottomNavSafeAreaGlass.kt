package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Bottom glass safe-area boundary. Its height is also reserved from the
 * screen-content layout by MainActivity; it is not an overlay over thumbnails.
 */
@Composable
fun BottomNavSafeAreaGlass(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "bottom_safe_glass")
    val reflectionPosition by transition.animateFloat(
        initialValue = -0.85f,
        targetValue = 1.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing)
        ),
        label = "bottom_safe_glass_reflection"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.05.dp)
            .drawBehind {
                drawRect(scheme.surface.copy(alpha = 0.90f))
                val sweepX = size.width * reflectionPosition
                drawRect(
                    brush = Brush.linearGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.30f to scheme.primary.copy(alpha = 0.10f),
                            0.52f to scheme.secondary.copy(alpha = 0.12f),
                            0.72f to scheme.tertiary.copy(alpha = 0.08f),
                            1.00f to Color.Transparent
                        ),
                        start = Offset(sweepX - size.width * 0.58f, 0f),
                        end = Offset(sweepX + size.width * 0.58f, size.height)
                    )
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            scheme.onSurface.copy(alpha = 0.035f),
                            Color.Transparent,
                            scheme.primary.copy(alpha = 0.035f)
                        )
                    )
                )
                drawLine(
                    color = scheme.outline.copy(alpha = 0.72f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 0.7.dp.toPx()
                )
            }
            .testTag("bottom_nav_safe_area_glass")
    )
}
