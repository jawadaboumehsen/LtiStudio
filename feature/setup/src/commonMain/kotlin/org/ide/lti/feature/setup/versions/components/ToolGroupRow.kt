/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.versions.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.actions.GlassSecondaryButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.tooltip.GlassTooltipArea
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.feature.setup.versions.ToolGroupRowModel
import org.ide.lti.feature.setup.versions.ToolGroupStatus

@Composable
public fun ToolGroupRow(
    model: ToolGroupRowModel,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
) {
    val groupOutputs =
        ToolGroupCatalog
            .group(ToolGroupId(model.groupId))
            ?.outputs
            ?.joinToString { it.toolId } ?: ""

    GlassCard(
        modifier =
        modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.border(
                        StrokeWidth.Focused,
                        MaterialTheme.colorScheme.primary,
                        GlassShapes.Card,
                    )
                } else {
                    Modifier
                },
            ).semantics {
                contentDescription = "Tool group ${model.groupId}, status ${model.status.label}"
                selected = isSelected
            },
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier =
                Modifier
                    .weight(1.5f)
                    .padding(end = Spacing.Medium),
            ) {
                Text(
                    text = model.groupId,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (groupOutputs.isNotEmpty()) {
                    GlassTooltipArea(tooltipText = "Tools: $groupOutputs") {
                        Text(
                            text = groupOutputs,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Column(
                modifier =
                Modifier
                    .weight(1.2f)
                    .padding(end = Spacing.Medium),
            ) {
                Text(
                    text = "Installed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = model.installedLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = codeFontFamily(),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(
                modifier =
                Modifier
                    .weight(1.2f)
                    .padding(end = Spacing.Medium),
            ) {
                Text(
                    text = "Selected",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = model.selectedLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = codeFontFamily(),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.weight(1.8f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                GlassChip(
                    label = model.status.label,
                    modifier = Modifier.padding(end = Spacing.Small),
                )

                if (model.status == ToolGroupStatus.CHANGE_PENDING) {
                    GlassPrimaryButton(
                        onClick = onActionClick,
                        modifier = Modifier.widthIn(min = ComponentSize.ActionCardHeight),
                    ) {
                        Text(model.status.primaryAction)
                    }
                } else {
                    GlassSecondaryButton(
                        onClick = onActionClick,
                        modifier = Modifier.widthIn(min = ComponentSize.ActionCardHeight),
                    ) {
                        Text(model.status.primaryAction)
                    }
                }
            }
        }
    }
}
