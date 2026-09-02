/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.data.settings.AppSettingsState
import org.ide.lti.core.designsystem.component.GlassCard
import org.ide.lti.core.designsystem.component.GlassIconButton
import org.ide.lti.core.designsystem.component.GlassToggle
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.icon.ideGeneralLanguage
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.koin.compose.koinInject

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    appSettingsState: AppSettingsState = koinInject(),
) {
    SettingsScreenContent(
        modifier = modifier.fillMaxSize(),
        onBackClick = onBackClick,
        appSettingsState = appSettingsState,
    )
}

@Composable
fun SettingsScreenContent(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    appSettingsState: AppSettingsState = koinInject(),
) {
    var pushEnabled by remember { mutableStateOf(true) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = ComponentSize.SettingsMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.ScreenVertical)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.SectionGap),
        ) {
            // Header with back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(onClick = onBackClick) {
                    Icon(
                        painter = AppIcons.ArrowBackPainterResource(),
                        contentDescription = "Back",
                        tint = GlassTheme.colors.onGlass,
                        modifier = Modifier.size(IconSize.Medium),
                    )
                }

                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = GlassTheme.colors.onGlass,
                )
            }

            Spacer(Modifier.height(Spacing.ExtraSmall))

            // Appearance Section
            GlassCard(
                contentPadding = Spacing.CardPadding,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.CardPadding)) {
                    Text(
                        text = "Appearance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = GlassTheme.colors.onGlass,
                    )

                    SettingsToggleItem(
                        icon = AppIcons.Sparkles,
                        title = "Dark Mode",
                        description = "AMOLED true-black surfaces with light-blue accents",
                        checked = appSettingsState.isDarkTheme,
                        onCheckedChange = { appSettingsState.isDarkTheme = it },
                    )

                    HorizontalDivider(
                        color = GlassTheme.colors.glassBorder.copy(alpha = AlphaTokens.Faint),
                        thickness = StrokeWidth.Hairline,
                    )

                    SettingsToggleItem(
                        icon = AppIcons.LayoutSplit,
                        title = "Fluid Glass Effects",
                        description = "GPU-accelerated lens refraction and vibrancy",
                        checked = appSettingsState.effectsEnabled,
                        onCheckedChange = { appSettingsState.effectsEnabled = it },
                    )

                    HorizontalDivider(
                        color = GlassTheme.colors.glassBorder.copy(alpha = AlphaTokens.Faint),
                        thickness = StrokeWidth.Hairline,
                    )

                    SettingsValueItem(
                        icon = AppIcon.Painted(AppIcons.ideGeneralLanguage),
                        title = "Language",
                        description = "Display language for IDE interface",
                        value = "English (US)",
                    )
                }
            }

            // Notifications Section
            GlassCard(
                contentPadding = Spacing.CardPadding,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.CardPadding)) {
                    Text(
                        text = "Notifications",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = GlassTheme.colors.onGlass,
                    )

                    SettingsToggleItem(
                        icon = AppIcon.Painted(AppIcons.NotificationsPainterResource),
                        title = "Push Notifications",
                        description = "Receive workspace and background task alerts",
                        checked = pushEnabled,
                        onCheckedChange = { pushEnabled = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleItem(
    icon: AppIcon,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).padding(end = Spacing.Medium),
        ) {
            when (icon) {
                is AppIcon.Vector -> Icon(
                    imageVector = icon.imageVector,
                    contentDescription = null,
                    tint = GlassTheme.colors.glassAccent,
                    modifier = Modifier.size(IconSize.Large),
                )
                is AppIcon.Painted -> Icon(
                    painter = icon.painter(),
                    contentDescription = null,
                    tint = GlassTheme.colors.glassAccent,
                    modifier = Modifier.size(IconSize.Large),
                )
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = GlassTheme.colors.onGlass,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTheme.colors.onGlassSecondary,
                )
            }
        }

        GlassToggle(
            selected = { checked },
            onSelect = onCheckedChange,
        )
    }
}

@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) = SettingsToggleItem(
    icon = AppIcon.Vector(icon),
    title = title,
    description = description,
    checked = checked,
    onCheckedChange = onCheckedChange,
    modifier = modifier,
)

@Composable
private fun SettingsValueItem(
    icon: AppIcon,
    title: String,
    description: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).padding(end = Spacing.Medium),
        ) {
            when (icon) {
                is AppIcon.Vector -> Icon(
                    imageVector = icon.imageVector,
                    contentDescription = null,
                    tint = GlassTheme.colors.glassAccent,
                    modifier = Modifier.size(IconSize.Large),
                )
                is AppIcon.Painted -> Icon(
                    painter = icon.painter(),
                    contentDescription = null,
                    tint = GlassTheme.colors.glassAccent,
                    modifier = Modifier.size(IconSize.Large),
                )
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = GlassTheme.colors.onGlass,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTheme.colors.onGlassSecondary,
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(CornerRadius.Compact))
                .background(GlassTheme.colors.glassSurfaceElevated)
                .border(
                    StrokeWidth.Hairline,
                    GlassTheme.colors.glassBorder,
                    RoundedCornerShape(CornerRadius.Compact),
                )
                .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.ExtraSmall),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = GlassTheme.colors.glassAccent,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SettingsValueItem(
    icon: ImageVector,
    title: String,
    description: String,
    value: String,
    modifier: Modifier = Modifier,
) = SettingsValueItem(
    icon = AppIcon.Vector(icon),
    title = title,
    description = description,
    value = value,
    modifier = modifier,
)
