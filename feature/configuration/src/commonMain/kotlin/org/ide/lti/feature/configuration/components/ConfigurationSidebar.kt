/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.configuration.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.display.GlassHorizontalDivider
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.model.target.TargetDevice

@Composable
fun ConfigurationSidebar(
    targets: List<TargetDevice>,
    selectedTargetId: String,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSelectTarget: (String) -> Unit,
    onAddTarget: () -> Unit,
    onDuplicateTarget: (TargetDevice) -> Unit,
    onDeleteTarget: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filteredTargets = remember(targets, searchQuery) {
        if (searchQuery.isBlank()) {
            targets
        } else {
            targets.filter { target ->
                target.name.contains(searchQuery, ignoreCase = true) ||
                    target.codename.contains(searchQuery, ignoreCase = true) ||
                    target.id.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(modifier = modifier.fillMaxHeight()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            GlassTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = "Search targets...",
                modifier = Modifier.weight(1f),
            )
            GlassIconButton(
                icon = AppIcons.Add,
                onClick = onAddTarget,
                contentDescription = "Add new target",
            )
        }

        GlassHorizontalDivider(specular = true)

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(Spacing.Small),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            items(filteredTargets, key = { it.id }) { target ->
                val isSelected = target.id == selectedTargetId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(GlassShapes.Small)
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Subtle)
                            } else {
                                Color.Transparent
                            },
                        )
                        .clickable { onSelectTarget(target.id) }
                        .padding(horizontal = Spacing.Small, vertical = Spacing.Compact),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = target.name.ifBlank { "Untitled Target" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = target.codename.ifBlank { target.id },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    GlassIconButton(
                        icon = AppIcons.Copy,
                        onClick = { onDuplicateTarget(target) },
                        contentDescription = "Duplicate ${target.name}",
                        size = ComponentSize.PanelHeaderAction,
                    )
                    GlassIconButton(
                        icon = AppIcons.Delete,
                        onClick = { onDeleteTarget(target.id) },
                        contentDescription = "Delete ${target.name}",
                        size = ComponentSize.PanelHeaderAction,
                    )
                }
            }
        }
    }
}
