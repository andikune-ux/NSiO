package com.example.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.example.ui.components.AppText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class NavDestination(val label: String) {
    HOME("Beranda"),
    EXTENSION("Extension"),
    SETTINGS("Pengaturan"),
    PROFILE("Profil")
}

/**
 * iOS 26 Floating Liquid Glass Navigation Capsule
 */
@Composable
fun VerticalNavToolbar(
    currentDestination: NavDestination,
    onNavigate: (NavDestination) -> Unit,
    activeProfileAvatar: String,
    modifier: Modifier = Modifier
) {
    val homeFocusRequester = remember { FocusRequester() }
    val extensionFocusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }
    val profileFocusRequester = remember { FocusRequester() }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(start = 6.dp, top = 34.dp, bottom = 6.dp, end = 4.dp)
            .width(58.dp)
            .testTag("vertical_nav_toolbar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 8.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Brand Logo Header (Liquid Crystal Orb)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, Color(0xFFA855F7))
                            )
                        )
                        .border(
                            1.dp,
                            Brush.linearGradient(
                                listOf(Color.White.copy(alpha = 0.8f), Color.Transparent)
                            ),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "NSiO Logo",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Navigation Items (Top to Bottom: 1. Beranda, 2. Extension, 3. Pengaturan, 4. Profil)
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f, fill = false).focusGroup()
            ) {
                NavToolbarGlassItem(
                    title = "Beranda",
                    icon = if (currentDestination == NavDestination.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                    isSelected = currentDestination == NavDestination.HOME,
                    testTag = "nav_item_home",
                    focusRequester = homeFocusRequester,
                    downFocusRequester = extensionFocusRequester,
                    onClick = { onNavigate(NavDestination.HOME) }
                )

                NavToolbarGlassItem(
                    title = "Extension",
                    icon = if (currentDestination == NavDestination.EXTENSION) Icons.Filled.Extension else Icons.Outlined.Extension,
                    isSelected = currentDestination == NavDestination.EXTENSION,
                    testTag = "nav_item_extension",
                    focusRequester = extensionFocusRequester,
                    upFocusRequester = homeFocusRequester,
                    downFocusRequester = settingsFocusRequester,
                    onClick = { onNavigate(NavDestination.EXTENSION) }
                )

                NavToolbarGlassItem(
                    title = "Pengaturan",
                    icon = if (currentDestination == NavDestination.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                    isSelected = currentDestination == NavDestination.SETTINGS,
                    testTag = "nav_item_settings",
                    focusRequester = settingsFocusRequester,
                    upFocusRequester = extensionFocusRequester,
                    downFocusRequester = profileFocusRequester,
                    onClick = { onNavigate(NavDestination.SETTINGS) }
                )

                NavToolbarGlassItem(
                    title = "Profil",
                    icon = if (currentDestination == NavDestination.PROFILE) Icons.Filled.Person else Icons.Outlined.Person,
                    isSelected = currentDestination == NavDestination.PROFILE,
                    testTag = "nav_item_profile",
                    focusRequester = profileFocusRequester,
                    upFocusRequester = settingsFocusRequester,
                    avatarEmoji = activeProfileAvatar,
                    onClick = { onNavigate(NavDestination.PROFILE) }
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

@Composable
private fun NavToolbarGlassItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    avatarEmoji: String? = null,
    focusRequester: FocusRequester,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val isDark = (MaterialTheme.colorScheme.background.red + MaterialTheme.colorScheme.background.green + MaterialTheme.colorScheme.background.blue) < 1.5f
    val buttonSurface = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White

    val contentColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White
            isSelected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "nav_content_color"
    )

    Column(
        modifier = Modifier
            .width(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(buttonSurface, RoundedCornerShape(12.dp))
            .then(if (isSelected) Modifier.neumorphicInset(RoundedCornerShape(12.dp), depth = 3.dp) else Modifier)
            .then(
                if (isFocused) {
                    Modifier.border(
                        1.5.dp,
                        Color.White,
                        RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusRequester(focusRequester)
            .focusProperties {
                upFocusRequester?.let { up = it }
                downFocusRequester?.let { down = it }
            }
            .focusable(interactionSource = interactionSource)
            .semantics {
                role = Role.Button
                selected = isSelected
            }
            .size(46.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (avatarEmoji != null && avatarEmoji.isNotEmpty() && title == "Profil") {
            Text(
                text = avatarEmoji,
                fontSize = 21.sp,
                textAlign = TextAlign.Center
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = contentColor,
                modifier = Modifier.size(23.dp)
            )
        }
    }
}
