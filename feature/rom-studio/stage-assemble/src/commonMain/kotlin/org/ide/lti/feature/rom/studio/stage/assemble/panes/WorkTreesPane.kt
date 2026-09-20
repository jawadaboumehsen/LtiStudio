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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.display.IdeStatusBadge
import org.ide.lti.core.designsystem.component.display.IdeStatusSeverity
import org.ide.lti.core.designsystem.component.display.IdeTable
import org.ide.lti.core.designsystem.component.display.IdeTableHeader
import org.ide.lti.core.designsystem.component.display.IdeTableHeaderCell
import org.ide.lti.core.designsystem.component.display.IdeTableRow
import org.ide.lti.core.designsystem.component.display.ideCardSurface
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
 * Stage Assemble subobject pane for Work trees and partition selection.
 */
@Suppress("LongParameterList", "UnusedParameter")
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GlassShapes.ShellCard)
                .ideCardSurface(colors, shape = GlassShapes.ShellCard)
                .padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Text(
                text = "Included partitions",
                color = colors.textMain,
                fontSize = FontSize.TitleSmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ideFontFamily(),
            )
            Text(
                text = "Select dynamic partitions to unpack and assemble into the staging work tree.",
                color = colors.textMuted,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )

            IdeTable(
                modifier = Modifier.fillMaxWidth(),
                colors = colors,
            ) {
                IdeTableHeader(colors = colors) {
                    IdeTableHeaderCell(
                        text = "Select",
                        modifier = Modifier.width(GlassDimens.AcquireTableIconColWidth),
                        colors = colors,
                    )
                    IdeTableHeaderCell(
                        text = "Partition",
                        modifier = Modifier.weight(2f),
                        colors = colors,
                    )
                    IdeTableHeaderCell(
                        text = "Status",
                        modifier = Modifier.weight(1f),
                        colors = colors,
                    )
                }

                partitions.forEach { name ->
                    val isIncluded = name in settings.includedPartitions || settings.includedPartitions.isEmpty()
                    IdeTableRow(
                        isSelected = isIncluded,
                        onClick = {
                            val updated = if (isIncluded) {
                                settings.includedPartitions - name
                            } else {
                                settings.includedPartitions + name
                            }
                            onChange(settings.copy(includedPartitions = updated))
                        },
                        colors = colors,
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
                            modifier = Modifier.width(GlassDimens.AcquireTableIconColWidth),
                        )
                        Row(
                            modifier = Modifier.weight(2f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = if (isIncluded) colors.accentBlue else colors.textDim,
                                modifier = Modifier.size(IconSize.Small),
                            )
                            Text(
                                text = name,
                                color = if (isIncluded) colors.textMain else colors.textMuted,
                                fontSize = FontSize.BodySmall,
                                fontFamily = codeFontFamily(),
                            )
                        }

                        Box(modifier = Modifier.weight(1f)) {
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
        }

        // Staged Partitions Footprint Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GlassShapes.ShellCard)
                .ideCardSurface(colors, shape = GlassShapes.ShellCard)
                .padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Text(
                    text = "Staged work tree status",
                    color = colors.textMain,
                    fontSize = FontSize.TitleSmall,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ideFontFamily(),
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
                fontFamily = codeFontFamily(),
            )
        }
    }
}
