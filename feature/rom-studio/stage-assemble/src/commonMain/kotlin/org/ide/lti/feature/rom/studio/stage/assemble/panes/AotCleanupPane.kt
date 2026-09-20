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
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
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
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
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
                .background(colors.infoBannerFill)
                .padding(Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = colors.accentBlue,
                modifier = Modifier.size(IconSize.Small),
            )
            val notice = "Removes precompiled ART artifacts (oat, odex, vdex) from the work tree " +
                "so the system will compile fresh bytecode\n" +
                "on first boot. This helps avoid runtime issues after framework or app changes."
            Text(
                text = notice,
                color = colors.textMuted,
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
                    color = colors.accentBlue,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = colors.accentBlue,
                    modifier = Modifier.size(IconSize.ExtraSmall),
                )
            }
        }

        // Toggle 1: Enable baseline cleanup (recommended)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GlassShapes.ShellCard)
                .ideCardSurface(colors, shape = GlassShapes.ShellCard)
                .padding(Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            GlassToggle(
                checked = settings.baselineCleanup,
                onCheckedChange = { onChange(settings.copy(baselineCleanup = it)) },
                activeColor = colors.accentBlue,
            )

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
                Text(
                    text = "Enable baseline cleanup (recommended)",
                    color = colors.textMain,
                    fontSize = FontSize.BodySmall,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ideFontFamily(),
                )
                Text(
                    text = "Remove existing oat, odex and vdex files from " +
                        "system, system_ext and product work trees.",
                    color = colors.textMuted,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
                Text(
                    text = "Safe to keep enabled for most builds.",
                    color = colors.accentBlue,
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
                .ideCardSurface(colors, shape = GlassShapes.ShellCard)
                .padding(Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            GlassToggle(
                checked = false,
                onCheckedChange = { },
                enabled = false,
                activeColor = colors.textDim,
            )

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = colors.textMuted,
                        modifier = Modifier.size(IconSize.ExtraSmall),
                    )
                    Text(
                        text = "Mandatory cleanup after bytecode changes",
                        color = colors.textMain,
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
                    color = colors.textMuted,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
                Text(
                    text = "This option is always enabled and cannot be disabled.",
                    color = colors.textDim,
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
                .ideCardSurface(colors, shape = GlassShapes.ShellCard)
                .padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Text(
                text = "Affected paths in work tree",
                color = colors.textMain,
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
                    color = colors.textDim,
                    fontSize = FontSize.Micro,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ideFontFamily(),
                    modifier = Modifier.width(Spacing.ExtraLarge),
                )
                Text(
                    text = "Path (relative to work tree)",
                    color = colors.textDim,
                    fontSize = FontSize.Micro,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ideFontFamily(),
                    modifier = Modifier.weight(2.5f),
                )
                Text(
                    text = "Type",
                    color = colors.textDim,
                    fontSize = FontSize.Micro,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ideFontFamily(),
                    modifier = Modifier.weight(1.5f),
                )
                Text(
                    text = "Status",
                    color = colors.textDim,
                    fontSize = FontSize.Micro,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ideFontFamily(),
                    modifier = Modifier.weight(1.5f),
                )
            }

            HorizontalDivider(thickness = GlassDimens.HairlineBorder, color = colors.borderMuted)

            // Table Rows
            AffectedPathRow(1, "system/framework", "oat / odex / vdex", "Found (342 files)", colors)
            AffectedPathRow(2, "system/app", "oat / odex / vdex", "Found (612 files)", colors)
            AffectedPathRow(3, "system/priv-app", "oat / odex / vdex", "Found (428 files)", colors)
            AffectedPathRow(4, "system_ext", "oat / odex / vdex", "Found (96 files)", colors)
            AffectedPathRow(5, "product", "oat / odex / vdex", "Found (118 files)", colors)
        }

        // Bottom Console / Logs Drawer
        AotLogsDrawer(colors = colors)
    }
}

@Composable
private fun AffectedPathRow(index: Int, path: String, type: String, status: String, colors: IdeShellColorScheme) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.ExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = index.toString(),
            color = colors.textDim,
            fontSize = FontSize.Micro,
            fontFamily = codeFontFamily(),
            modifier = Modifier.width(Spacing.ExtraLarge),
        )
        Text(
            text = path,
            color = colors.textMain,
            fontSize = FontSize.Micro,
            fontFamily = codeFontFamily(),
            modifier = Modifier.weight(2.5f),
        )
        Text(
            text = type,
            color = colors.textMuted,
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
            modifier = Modifier.weight(1.5f),
        )
        Text(
            text = status,
            color = colors.textMuted,
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
            modifier = Modifier.weight(1.5f),
        )
    }
}

@Composable
private fun AotLogsDrawer(colors: IdeShellColorScheme) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Logs", "Preview changes", "Problems")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(colors, shape = GlassShapes.ShellCard)
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
                                Modifier.background(colors.controlSelected)
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
                        tint = if (isTabSelected) colors.accentBlue else colors.textDim,
                        modifier = Modifier.size(IconSize.ExtraSmall),
                    )
                    Text(
                        text = tabTitle,
                        color = if (isTabSelected) colors.textMain else colors.textMuted,
                        fontSize = FontSize.Micro,
                        fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = ideFontFamily(),
                    )
                }
            }
        }

        HorizontalDivider(thickness = GlassDimens.HairlineBorder, color = colors.borderMuted)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.Small)
                .background(colors.cardBackgroundMuted, GlassShapes.ShellControl)
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
                    color = colors.textMuted,
                    fontSize = FontSize.Micro,
                    fontFamily = codeFontFamily(),
                )
            }
        }
    }
}
