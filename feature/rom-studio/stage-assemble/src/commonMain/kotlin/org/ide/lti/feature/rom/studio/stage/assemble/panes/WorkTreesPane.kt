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
import androidx.compose.material3.MaterialTheme
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
import org.ide.lti.core.designsystem.theme.IconSize
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
                .ideCardSurface(shape = GlassShapes.ShellCard)
                .padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Text(
                text = "Included partitions",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.TitleSmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ideFontFamily(),
            )
            Text(
                text = "Select dynamic partitions to unpack and assemble into the staging work tree.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )

            IdeTable(
                modifier = Modifier.fillMaxWidth(),
            ) {
                IdeTableHeader {
                    IdeTableHeaderCell(
                        text = "Select",
                        modifier = Modifier.width(GlassDimens.AcquireTableIconColWidth),
                    )
                    IdeTableHeaderCell(
                        text = "Partition",
                        modifier = Modifier.weight(2f),
                    )
                    IdeTableHeaderCell(
                        text = "Status",
                        modifier = Modifier.weight(1f),
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
                                checkedColor = MaterialTheme.colorScheme.primary,
                                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                tint = if (isIncluded) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(IconSize.Small),
                            )
                            Text(
                                text = name,
                                color = if (isIncluded) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontSize = FontSize.BodySmall,
                                fontFamily = codeFontFamily(),
                            )
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            IdeStatusBadge(
                                label = if (isIncluded) "Included" else "Excluded",
                                severity = if (isIncluded) IdeStatusSeverity.Ready else IdeStatusSeverity.Neutral,
                                useDot = true,
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
                .ideCardSurface(shape = GlassShapes.ShellCard)
                .padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Text(
                    text = "Staged work tree status",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = FontSize.TitleSmall,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ideFontFamily(),
                )
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconSize.ExtraSmall),
                )
            }

            Text(
                text = "Work tree staging root: D:\\ROM\\workspace\\staging",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = codeFontFamily(),
            )
        }
    }
}
