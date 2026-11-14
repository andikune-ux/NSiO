package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text as Material3Text
import com.example.ui.components.AppText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanSecondary

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
    val itemFocusRequesters = remember { List(4) { FocusRequester() } }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        val navShape = RoundedCornerShape(24.dp)
        // One continuous white surface: item backgrounds no longer appear as
        // disconnected tiles. The glass safe-area is rendered separately below.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(navShape)
                .background(Color.White)
                .border(1.dp, Color.White.copy(alpha = 0.62f), navShape)
                .padding(horizontal = 4.dp)
                .testTag("bottom_nav_glass_bar")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItem(
                    title = "Beranda",
                    selectedIcon = Icons.Filled.Home,
                    unselectedIcon = Icons.Outlined.Home,
                    isSelected = currentDestination == NavDestination.HOME,
                    testTag = "bottom_nav_home",
                    index = 0,
                    focusRequesters = itemFocusRequesters,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(NavDestination.HOME) }
                )
                BottomNavItem(
                    title = "Extension",
                    selectedIcon = Icons.Filled.Extension,
                    unselectedIcon = Icons.Outlined.Extension,
                    isSelected = currentDestination == NavDestination.EXTENSION,
                    testTag = "bottom_nav_extension",
                    index = 1,
                    focusRequesters = itemFocusRequesters,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(NavDestination.EXTENSION) }
                )
                BottomNavItem(
                    title = "Pengaturan",
                    selectedIcon = Icons.Filled.Settings,
                    unselectedIcon = Icons.Outlined.Settings,
                    isSelected = currentDestination == NavDestination.SETTINGS,
                    testTag = "bottom_nav_settings",
                    index = 2,
                    focusRequesters = itemFocusRequesters,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(NavDestination.SETTINGS) }
                )
                BottomNavItem(
                    title = "Profil",
                    selectedIcon = Icons.Filled.Person,
                    unselectedIcon = Icons.Outlined.Person,
                    isSelected = currentDestination == NavDestination.PROFILE,
                    testTag = "bottom_nav_profile",
                    index = 3,
                    focusRequesters = itemFocusRequesters,
                    modifier = Modifier.weight(1f),
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int,
    focusRequesters: List<FocusRequester>
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else if (isSelected) 1.02f else 1.0f,
        label = "bottom_nav_scale"
    )

    val iconColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primary
            isFocused -> Color.White
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "bottom_nav_icon_color"
    )

    val textColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primary
            isFocused -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "bottom_nav_text_color"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusRequester(focusRequesters[index])
            .focusProperties {
                if (index > 0) left = focusRequesters[index - 1]
                if (index < focusRequesters.lastIndex) right = focusRequesters[index + 1]
            }
            .focusable(interactionSource = interactionSource)
            .then(
                if (isFocused) Modifier.border(1.5.dp, Color.White, RoundedCornerShape(16.dp)) else Modifier
            )
            .semantics {
                role = Role.Button
                selected = isSelected
            }
            .fillMaxWidth()
            .height(48.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .then(
                        if (isSelected) Modifier.neumorphicInset(CircleShape, depth = 4.dp)
                        else Modifier.background(Color.Transparent)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSelected) selectedIcon else unselectedIcon,
                    contentDescription = title,
                    tint = iconColor,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
