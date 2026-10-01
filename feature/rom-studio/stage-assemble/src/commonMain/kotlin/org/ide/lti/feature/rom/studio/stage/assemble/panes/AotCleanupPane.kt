/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.assemble.panes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.AssemblySettings

/**
 * Stage Assemble subobject pane for configuring AOT cleanup.
 * Matches Blue Glass styling.
 */
@Suppress("LongParameterList", "LongMethod", "UnusedParameter")
@Composable
public fun AotCleanupPane(
    settings: AssemblySettings,
    target: TargetDevice,
    onChange: (AssemblySettings) -> Unit,
    onValidate: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("AotCleanupPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        // Top Notice Card (info banner)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GlassShapes.ShellCard)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Subtle))
                .padding(Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IconSize.Small),
            )
            val notice = "Removes precompiled ART artifacts (oat, odex, vdex) from the work tree " +
                "so the system will compile fresh bytecode\n" +
                "on first boot. This helps avoid runtime issues after framework or app changes."
            Text(
                text = notice,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier
                    .clip(GlassShapes.ShellControl)
                    .clickable { }
                    .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Text(
                    text = "Learn more",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconSize.ExtraSmall),
                )
            }
        }

        // Toggle 1: Enable baseline cleanup (recommended)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GlassShapes.ShellCard)
                .ideCardSurface(shape = GlassShapes.ShellCard)
                .padding(Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            GlassToggle(
                checked = settings.baselineCleanup,
                onCheckedChange = { onChange(settings.copy(baselineCleanup = it)) },
                activeColor = MaterialTheme.colorScheme.primary,
            )

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
                Text(
                    text = "Enable baseline cleanup (recommended)",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = FontSize.BodySmall,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ideFontFamily(),
                )
                Text(
                    text = "Remove existing oat, odex and vdex files from " +
                        "system, system_ext and product work trees.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
                Text(
                    text = "Safe to keep enabled for most builds.",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
            }
        }

        // Toggle 2: Mandatory cleanup after bytecode changes (locked)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GlassShapes.ShellCard)
                .ideCardSurface(shape = GlassShapes.ShellCard)
                .padding(Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            GlassToggle(
                checked = false,
                onCheckedChange = { },
                enabled = false,
                activeColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(IconSize.ExtraSmall),
                    )
                    Text(
                        text = "Mandatory cleanup after bytecode changes",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = FontSize.BodySmall,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = ideFontFamily(),
                    )
                }
                val mandatoryNotice = "Automatically performs a full AOT cleanup when framework code, " +
                    "core libraries or system applications\n" +
                    "have been modified in this workspace."
                Text(
                    text = mandatoryNotice,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
                Text(
                    text = "This option is always enabled and cannot be disabled.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
            }
        }

        // Table Card: Affected paths in work tree
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GlassShapes.ShellCard)
                .ideCardSurface(shape = GlassShapes.ShellCard)
                .padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Text(
                text = "Affected paths in work tree",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.BodySmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ideFontFamily(),
            )

            // Table Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "#",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.Micro,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ideFontFamily(),
                    modifier = Modifier.width(Spacing.ExtraLarge),
                )
                Text(
                    text = "Path (relative to work tree)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.Micro,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ideFontFamily(),
                    modifier = Modifier.weight(2.5f),
                )
                Text(
                    text = "Type",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.Micro,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ideFontFamily(),
                    modifier = Modifier.weight(1.5f),
                )
                Text(
                    text = "Status",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.Micro,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ideFontFamily(),
                    modifier = Modifier.weight(1.5f),
                )
            }

            HorizontalDivider(thickness = GlassDimens.HairlineBorder, color = MaterialTheme.colorScheme.outlineVariant)

            // Table Rows
            AffectedPathRow(1, "system/framework", "oat / odex / vdex", "Found (342 files)")
            AffectedPathRow(2, "system/app", "oat / odex / vdex", "Found (612 files)")
            AffectedPathRow(3, "system/priv-app", "oat / odex / vdex", "Found (428 files)")
            AffectedPathRow(4, "system_ext", "oat / odex / vdex", "Found (96 files)")
            AffectedPathRow(5, "product", "oat / odex / vdex", "Found (118 files)")
        }

        // Bottom Console / Logs Drawer
        AotLogsDrawer()
    }
}

@Composable
private fun AffectedPathRow(index: Int, path: String, type: String, status: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.ExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = index.toString(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            fontFamily = codeFontFamily(),
            modifier = Modifier.width(Spacing.ExtraLarge),
        )
        Text(
            text = path,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.Micro,
            fontFamily = codeFontFamily(),
            modifier = Modifier.weight(2.5f),
        )
        Text(
            text = type,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
            modifier = Modifier.weight(1.5f),
        )
        Text(
            text = status,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
            modifier = Modifier.weight(1.5f),
        )
    }
}

@Composable
private fun AotLogsDrawer() {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Logs", "Preview changes", "Problems")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Small),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.Small),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            tabs.forEachIndexed { index, tabTitle ->
                val isTabSelected = index == selectedTab
                Row(
                    modifier = Modifier
                        .clip(GlassShapes.ShellControl)
                        .then(
                            if (isTabSelected) {
                                Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Faded))
                            } else {
                                Modifier
                            },
                        )
                        .clickable { selectedTab = index }
                        .padding(horizontal = Spacing.Medium, vertical = Spacing.ExtraSmall),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                ) {
                    val icon = when (index) {
                        0 -> Icons.Default.Terminal
                        1 -> Icons.Default.Menu
                        else -> Icons.Default.Warning
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isTabSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(IconSize.ExtraSmall),
                    )
                    Text(
                        text = tabTitle,
                        color = if (isTabSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontSize = FontSize.Micro,
                        fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = ideFontFamily(),
                    )
                }
            }
        }

        HorizontalDivider(thickness = GlassDimens.HairlineBorder, color = MaterialTheme.colorScheme.outlineVariant)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.Small)
                .background(MaterialTheme.colorScheme.surfaceContainer, GlassShapes.ShellControl)
                .padding(Spacing.Small),
            verticalArrangement = Arrangement.spacedBy(Spacing.Hairline),
        ) {
            val logLines = listOf(
                "1  [10:24:17]  Scanning work tree for compiled artifacts...",
                "2  [10:24:17]  Found 1,596 files (oat/odex/vdex)",
                "3  [10:24:18]  Baseline cleanup is enabled",
                "4  [10:24:18]  AOT cleanup ready to run (no changes applied yet)",
            )
            logLines.forEach { line ->
                Text(
                    text = line,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.Micro,
                    fontFamily = codeFontFamily(),
                )
            }
        }
    }
}
