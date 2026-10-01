/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassHorizontalDivider
import org.ide.lti.core.designsystem.component.display.GlassValueTile
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.icon.ideGeneralLanguage
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.feature.settings.model.SettingsCategory

/**
 * General & Generic settings pane rendering category options and environment parameters.
 */
@Composable
fun GenericCategoryPane(category: SettingsCategory, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().testTag("GenericCategoryPane_${category.name}"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Large),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
            Text(
                text = category.title,
                fontSize = FontSize.HeadlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = category.subtitle,
                fontSize = FontSize.BodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        GlassCard(
            surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentPadding = Spacing.CardPadding,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.CardPadding)) {
                Text(
                    text = "${category.title} Configuration",
                    fontSize = FontSize.TitleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                when (category) {
                    SettingsCategory.General -> {
                        GlassValueTile(
                            icon = AppIcon.Painted(AppIcons.ideGeneralLanguage),
                            title = "Interface Language",
                            description = "Display language for menus, dialogs, and workspace panes",
                            value = "English (US)",
                        )
                        GlassHorizontalDivider()
                        GlassValueTile(
                            icon = AppIcons.FolderOpen,
                            title = "Default Project Directory",
                            description = "Initial directory suggested when creating new ROM targets",
                            value = "~/LtiRomProjects",
                        )
                    }
                    SettingsCategory.BuildEnvironment -> {
                        GlassValueTile(
                            icon = Icons.Default.Build,
                            title = "Java Development Kit (JDK)",
                            description = "Active JDK runtime used for toolchain executions",
                            value = "JDK 21.0.2 (JetBrains Runtime)",
                        )
                        GlassHorizontalDivider()
                        GlassValueTile(
                            icon = AppIcons.FolderOpen,
                            title = "Android SDK Location",
                            description = "Path to installed platform-tools, build-tools, and emulator",
                            value = "C:\\Android\\Sdk",
                        )
                    }
                    SettingsCategory.PluginSources -> {
                        GlassValueTile(
                            icon = Icons.Default.Widgets,
                            title = "Default Plugin Registry",
                            description = "Primary remote repository for community tools & extractors",
                            value = "https://plugins.ltirom.dev/v1",
                        )
                        GlassHorizontalDivider()
                        GlassValueTile(
                            icon = AppIcons.Refresh,
                            title = "Auto-Update Plugins",
                            description = "Check for plugin security patches and stage upgrades",
                            value = "Enabled (Daily)",
                        )
                    }
                    SettingsCategory.Credentials -> {
                        GlassValueTile(
                            icon = Icons.Default.Key,
                            title = "Git Credential Helper",
                            description = "Authentication provider for GitHub and private ROM repos",
                            value = "System Credential Manager",
                        )
                        GlassHorizontalDivider()
                        GlassValueTile(
                            icon = Icons.Default.Lock,
                            title = "ROM Signing Keystore",
                            description = "Default keystore used for signing target system images",
                            value = "Configured (dev.keystore)",
                        )
                    }
                    SettingsCategory.Paths -> {
                        GlassValueTile(
                            icon = AppIcons.Folder,
                            title = "Working Scratch Cache",
                            description = "High-speed NVMe directory for unpacking system.img partitions",
                            value = "C:\\Users\\devuser\\AppData\\Local\\Temp\\ltirom",
                        )
                        GlassHorizontalDivider()
                        GlassValueTile(
                            icon = AppIcons.FolderOpen,
                            title = "ROM Artifacts Output",
                            description = "Destination for flashable zips, fastboot images, and AP packages",
                            value = "C:\\Users\\devuser\\ROM_Builds",
                        )
                    }
                    else -> {
                        GlassValueTile(
                            icon = AppIcons.Info,
                            title = category.title,
                            description = category.subtitle,
                            value = "Operational",
                        )
                    }
                }
            }
        }
    }
}
