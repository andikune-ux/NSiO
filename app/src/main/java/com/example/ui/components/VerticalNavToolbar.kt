package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.FocusHighlight

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
    activeProfileName: String,
    activeProfileAvatar: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 6.dp)
            .width(76.dp)
            .liquidGlass(
                shape = RoundedCornerShape(22.dp),
                alpha = 0.12f,
                borderAlpha = 0.35f,
                elevation = 10.dp
            )
            .testTag("vertical_nav_toolbar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Brand Logo Header (Liquid Crystal Orb)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(CyanPrimary, CyanSecondary, Color(0xFFA855F7))
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
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "NSiO",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = CyanPrimary,
                    letterSpacing = 1.sp
                )
            }

            // Navigation Items (Top to Bottom: 1. Beranda, 2. Extension, 3. Pengaturan, 4. Profil)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                NavToolbarGlassItem(
                    title = "Beranda",
                    icon = if (currentDestination == NavDestination.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                    isSelected = currentDestination == NavDestination.HOME,
                    testTag = "nav_item_home",
                    onClick = { onNavigate(NavDestination.HOME) }
                )

                NavToolbarGlassItem(
                    title = "Extension",
                    icon = if (currentDestination == NavDestination.EXTENSION) Icons.Filled.Extension else Icons.Outlined.Extension,
                    isSelected = currentDestination == NavDestination.EXTENSION,
                    testTag = "nav_item_extension",
                    onClick = { onNavigate(NavDestination.EXTENSION) }
                )

                NavToolbarGlassItem(
                    title = "Pengaturan",
                    icon = if (currentDestination == NavDestination.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                    isSelected = currentDestination == NavDestination.SETTINGS,
                    testTag = "nav_item_settings",
                    onClick = { onNavigate(NavDestination.SETTINGS) }
                )

                NavToolbarGlassItem(
                    title = "Profil",
                    icon = if (currentDestination == NavDestination.PROFILE) Icons.Filled.Person else Icons.Outlined.Person,
                    isSelected = currentDestination == NavDestination.PROFILE,
                    testTag = "nav_item_profile",
                    avatarEmoji = activeProfileAvatar,
                    onClick = { onNavigate(NavDestination.PROFILE) }
                )
            }

            // Bottom Active Profile Glass Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(
                        0.8.dp,
                        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Transparent)),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = activeProfileName.take(5),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CyanPrimary,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
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
    avatarEmoji: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bgBrush = when {
        isFocused -> Brush.linearGradient(
            listOf(CyanPrimary.copy(alpha = 0.35f), Color.White.copy(alpha = 0.15f))
        )
        isSelected -> Brush.linearGradient(
            listOf(CyanPrimary.copy(alpha = 0.22f), CyanSecondary.copy(alpha = 0.08f))
        )
        else -> Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
    }

    val contentColor by animateColorAsState(
        targetValue = when {
            isFocused -> FocusHighlight
            isSelected -> CyanPrimary
            else -> Color(0xFFCBD5E1)
        },
        label = "nav_content_color"
    )

    Column(
        modifier = Modifier
            .width(66.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bgBrush)
            .then(
                if (isFocused) {
                    Modifier.border(
                        1.5.dp,
                        Brush.linearGradient(listOf(FocusHighlight, Color.White)),
                        RoundedCornerShape(14.dp)
                    )
                } else if (isSelected) {
                    Modifier.border(
                        1.dp,
                        Brush.linearGradient(listOf(CyanPrimary.copy(alpha = 0.5f), Color.Transparent)),
                        RoundedCornerShape(14.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource)
            .padding(vertical = 7.dp, horizontal = 2.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (avatarEmoji != null && avatarEmoji.isNotEmpty() && title == "Profil") {
            Text(
                text = avatarEmoji,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = title,
            fontSize = 9.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
