package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.FocusHighlight

/**
 * iOS 26 Floating Liquid Glass Bottom Navigation Capsule
 * Used in Portrait orientation or compact widths for maximum space and clean aesthetics.
 */
@Composable
fun BottomNavGlassBar(
    currentDestination: NavDestination,
    onNavigate: (NavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(
                    shape = RoundedCornerShape(28.dp),
                    alpha = 0.14f,
                    borderAlpha = 0.38f,
                    elevation = 12.dp
                )
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .testTag("bottom_nav_glass_bar")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItem(
                    title = "Beranda",
                    selectedIcon = Icons.Filled.Home,
                    unselectedIcon = Icons.Outlined.Home,
                    isSelected = currentDestination == NavDestination.HOME,
                    testTag = "bottom_nav_home",
                    onClick = { onNavigate(NavDestination.HOME) }
                )
                BottomNavItem(
                    title = "Extension",
                    selectedIcon = Icons.Filled.Extension,
                    unselectedIcon = Icons.Outlined.Extension,
                    isSelected = currentDestination == NavDestination.EXTENSION,
                    testTag = "bottom_nav_extension",
                    onClick = { onNavigate(NavDestination.EXTENSION) }
                )
                BottomNavItem(
                    title = "Pengaturan",
                    selectedIcon = Icons.Filled.Settings,
                    unselectedIcon = Icons.Outlined.Settings,
                    isSelected = currentDestination == NavDestination.SETTINGS,
                    testTag = "bottom_nav_settings",
                    onClick = { onNavigate(NavDestination.SETTINGS) }
                )
                BottomNavItem(
                    title = "Profil",
                    selectedIcon = Icons.Filled.Person,
                    unselectedIcon = Icons.Outlined.Person,
                    isSelected = currentDestination == NavDestination.PROFILE,
                    testTag = "bottom_nav_profile",
                    onClick = { onNavigate(NavDestination.PROFILE) }
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    title: String,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else if (isSelected) 1.04f else 1.0f,
        label = "bottom_nav_scale"
    )

    val iconColor by animateColorAsState(
        targetValue = when {
            isSelected -> CyanPrimary
            isFocused -> FocusHighlight
            else -> Color.White.copy(alpha = 0.65f)
        },
        label = "bottom_nav_icon_color"
    )

    val textColor by animateColorAsState(
        targetValue = when {
            isSelected -> CyanPrimary
            isFocused -> FocusHighlight
            else -> Color.White.copy(alpha = 0.6f)
        },
        label = "bottom_nav_text_color"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .then(
                        if (isSelected) {
                            Modifier.background(
                                Brush.radialGradient(
                                    listOf(CyanPrimary.copy(alpha = 0.28f), Color.Transparent)
                                )
                            )
                        } else {
                            Modifier.background(Color.Transparent)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSelected) selectedIcon else unselectedIcon,
                    contentDescription = title,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )
        }
    }
}
