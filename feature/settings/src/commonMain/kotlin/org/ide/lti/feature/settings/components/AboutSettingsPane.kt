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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassHorizontalDivider
import org.ide.lti.core.designsystem.component.display.LtiRomStudioMark
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.domain.ports.AppEnvironment

/**
 * Settings › About: what is running (version, Kotlin, Java runtime, OS) and where the app keeps its data and logs,
 * read from the running app ([environment]); [onOpenFolder] opens one of those folders.
 */
// LongMethod suppressed on @Composable screen pane for about settings.
@Suppress("LongMethod")
@Composable
fun AboutSettingsPane(
    modifier: Modifier = Modifier,
    environment: AppEnvironment? = null,
    onOpenFolder: (String) -> Unit = {},
) {
    val appVersion = environment?.version

    Column(
        modifier = modifier.fillMaxWidth().testTag("AboutSettingsPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Large),
    ) {
        // Breadcrumb Navigation
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Text(
                text = "Settings",
                fontSize = FontSize.BodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "/",
                fontSize = FontSize.BodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "About",
                fontSize = FontSize.BodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Hero Header Card
        GlassCard(
            modifier = Modifier.fillMaxWidth().testTag("AboutHeroHeaderCard"),
            surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentPadding = Spacing.CardPadding,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left 3D Brand Mark & Headings
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Large),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(ComponentSize.AboutLogoBoxSize)
                            .clip(GlassShapes.MediumSmall)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Faded))
                            .border(
                                width = GlassDimens.HairlineBorder,
                                color = MaterialTheme.colorScheme.outline,
                                shape = GlassShapes.MediumSmall,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        LtiRomStudioMark(
                            modifier = Modifier.size(IconSize.ExtraLarge),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
                        Text(
                            text = "About LtiRom Studio",
                            fontSize = FontSize.HeadlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Build. Modify. Publish with confidence.",
                            fontSize = FontSize.TitleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "LtiRom Studio · A modern workspace for ROM building",
                            fontSize = FontSize.BodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Right Version Badge Box
                Box(
                    modifier = Modifier
                        .clip(GlassShapes.MediumSmall)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(
                            width = GlassDimens.HairlineBorder,
                            color = MaterialTheme.colorScheme.outline,
                            shape = GlassShapes.MediumSmall,
                        )
                        .padding(Spacing.Medium),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                        Text(
                            text = "Version",
                            fontSize = FontSize.LabelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = appVersion?.let { "Version $it" } ?: "Development build",
                            fontSize = FontSize.BodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (appVersion == null) "Run from source" else "Installed app",
                            fontSize = FontSize.LabelSmall,
                            fontFamily = codeFontFamily(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Description Paragraph
        Text(
            text = "LtiRom Studio acquires firmware, prepares a work tree, applies your debloat choices and " +
                "plugins, then builds, signs and publishes a flashable ROM, all through its build service in WSL.",
            fontSize = FontSize.BodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.ExtraSmall),
        )

        // Lower Grid: Technical Information (weight 1.1f) & Diagnostics (weight 0.9f)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Large),
        ) {
            // Technical Information Card
            GlassCard(
                modifier = Modifier.weight(1.15f),
                surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentPadding = Spacing.CardPadding,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(IconSize.Medium),
                        )
                        Text(
                            text = "Technical information",
                            fontSize = FontSize.TitleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium)) {
                        TechInfoRow("Application version", appVersion ?: "Development build")
                        GlassHorizontalDivider()
                        TechInfoRow("Kotlin", KotlinVersion.CURRENT.toString())
                        GlassHorizontalDivider()
                        TechInfoRow("Runtime", environment?.runtime ?: "Unknown")
                        GlassHorizontalDivider()
                        TechInfoRow("Operating system", environment?.operatingSystem ?: "Unknown")
                        GlassHorizontalDivider()
                        TechInfoRow("Data folder", environment?.dataDirectory ?: "Unknown")
                        GlassHorizontalDivider()
                        TechInfoRow("Logs folder", environment?.logsDirectory ?: "Unknown")
                    }
                }
            }

            // Diagnostics Card
            GlassCard(
                modifier = Modifier.weight(0.85f),
                surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentPadding = Spacing.CardPadding,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(IconSize.Medium),
                        )
                        Text(
                            text = "Logs",
                            fontSize = FontSize.TitleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Text(
                        text = "Setup checks write one log per attempt here. Attach them when you report a problem.",
                        fontSize = FontSize.BodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(Spacing.Small))

                    // Generate Diagnostic Report Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(GlassShapes.Small)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(
                                width = GlassDimens.HairlineBorder,
                                color = MaterialTheme.colorScheme.primary,
                                shape = GlassShapes.Small,
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = environment != null,
                                onClick = { environment?.let { onOpenFolder(it.logsDirectory) } },
                            )
                            .padding(vertical = Spacing.SmallMedium),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = BrandColors.OnLogoBadge,
                                modifier = Modifier.size(IconSize.Medium),
                            )
                            Text(
                                text = "Open logs folder",
                                fontSize = FontSize.BodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = BrandColors.OnLogoBadge,
                            )
                        }
                    }
                }
            }
        }

        // Bottom Status Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.Small)
                .testTag("AboutStatusBar"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.Small),
                )
                Text(
                    text = "Settings are stored in the data folder",
                    fontSize = FontSize.LabelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TechInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = FontSize.BodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            fontSize = FontSize.BodySmall,
            fontFamily = codeFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
