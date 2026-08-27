/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.GlassCard
import org.ide.lti.core.designsystem.component.GlassIconButton
import org.ide.lti.core.designsystem.component.GlassIconToggleButton
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing

@Composable
internal fun SettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScreenContent(
        modifier = modifier.fillMaxSize(),
        onBackClick = onBackClick,
    )
}

@Composable
internal fun SettingsScreenContent(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(Spacing.ExtraLarge)) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = GlassTheme.colors.onGlass)
                }

                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = GlassTheme.colors.onGlass,
                )
            }
        }

        Spacer(Modifier.height(Spacing.ExtraLarge))

        // Settings sections
        GlassCard(
            contentPadding = Spacing.Medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                Text(
                    "Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = GlassTheme.colors.onGlass,
                )

                SettingsItem(
                    icon = Icons.Default.DarkMode,
                    title = "Dark Mode",
                    description = "Use dark color scheme",
                )

                SettingsItem(
                    icon = Icons.Default.Language,
                    title = "Language",
                    description = "English (US)",
                )
            }
        }

        Spacer(Modifier.height(Spacing.Medium))

        GlassCard(
            contentPadding = Spacing.Medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                Text(
                    "Notifications",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = GlassTheme.colors.onGlass,
                )

                SettingsItem(
                    icon = Icons.Default.Notifications,
                    title = "Push Notifications",
                    description = "Enable push notifications",
                )
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    var enabled by remember { mutableStateOf(true) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = GlassTheme.colors.glassAccent,
            )

            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = GlassTheme.colors.onGlass,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTheme.colors.onGlassSecondary,
                )
            }
        }

        GlassIconToggleButton(
            selected = enabled,
            onSelectedChange = { enabled = it },
        ) {
            if (enabled) {
                Icon(Icons.Default.Check, "Enabled", tint = GlassTheme.colors.glassAccent)
            }
        }
    }
}
