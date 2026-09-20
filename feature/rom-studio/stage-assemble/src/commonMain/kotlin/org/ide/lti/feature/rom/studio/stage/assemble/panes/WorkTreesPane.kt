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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.IdeStatusBadge
import org.ide.lti.core.designsystem.component.display.IdeStatusSeverity
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.IdeShellDimens
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.AssemblySettings

/**
 * Stage Assemble subobject pane for Work trees and partition selection.
 */
@Suppress("LongParameterList", "LongMethod")
@Composable
public fun WorkTreesPane(
    settings: AssemblySettings,
    target: TargetDevice,
    onChange: (AssemblySettings) -> Unit,
    onValidate: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
) {
    val partitions = target.dynamicPartitions.ifEmpty {
        listOf("system", "system_ext", "product", "vendor", "odm")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("WorkTreesPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        AssembleHeader(
            title = "Work trees",
            subtitle = "Configure included partitions and work tree structure.",
            targetSpec = "${target.codename} / Development",
            targetRevision = "Target revision ${target.revision}",
            additionalContext = "Snapshot 12 · Workspace active",
            onValidate = onValidate,
            onSave = onSave,
            colors = colors,
        )

        // Included Partitions Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            surfaceColor = colors.cardBackground,
            borderColor = colors.border,
            contentPadding = Spacing.Medium,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                Text(
                    text = "Included partitions",
                    color = colors.textMain,
                    fontSize = FontSize.BodySmall,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GlassFontFamily.ide(),
                )
                Text(
                    text = "Select dynamic partitions to unpack and assemble into the staging work tree.",
                    color = colors.textMuted,
                    fontSize = FontSize.Micro,
                    fontFamily = GlassFontFamily.ide(),
                )

                HorizontalDivider(thickness = IdeShellDimens.HairlineBorder, color = colors.borderMuted)

                partitions.forEach { name ->
                    val isIncluded = name in settings.includedPartitions || settings.includedPartitions.isEmpty()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.ExtraExtraSmall),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                        ) {
                            Checkbox(
                                checked = isIncluded,
                                onCheckedChange = { checked ->
                                    val updated = if (checked) {
                                        settings.includedPartitions + name
                                    } else {
                                        settings.includedPartitions - name
                                    }
                                    onChange(settings.copy(includedPartitions = updated))
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = colors.accentBlue,
                                    uncheckedColor = colors.textDim,
                                ),
                            )
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = if (isIncluded) colors.accentBlue else colors.textDim,
                                modifier = Modifier.size(IconSize.Small),
                            )
                            Text(
                                text = name,
                                color = if (isIncluded) colors.textMain else colors.textMuted,
                                fontSize = FontSize.Micro,
                                fontFamily = GlassFontFamily.code(),
                            )
                        }

                        IdeStatusBadge(
                            label = if (isIncluded) "Included" else "Excluded",
                            severity = if (isIncluded) IdeStatusSeverity.Ready else IdeStatusSeverity.Neutral,
                            useDot = true,
                            colors = colors,
                        )
                    }
                }
            }
        }

        // Staged Partitions Footprint Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            surfaceColor = colors.cardBackground,
            borderColor = colors.border,
            contentPadding = Spacing.Medium,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                ) {
                    Text(
                        text = "Staged work tree status",
                        color = colors.textMain,
                        fontSize = FontSize.BodySmall,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GlassFontFamily.ide(),
                    )
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = colors.accentBlue,
                        modifier = Modifier.size(IconSize.ExtraSmall),
                    )
                }

                Text(
                    text = "Work tree staging root: D:\\ROM\\workspace\\staging",
                    color = colors.textDim,
                    fontSize = FontSize.Micro,
                    fontFamily = GlassFontFamily.code(),
                )
            }
        }
    }
}
