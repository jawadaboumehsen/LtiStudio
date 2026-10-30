/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.studio.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.feedback.GlassBanner
import org.ide.lti.core.designsystem.component.feedback.GlassBannerSeverity
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.domain.pipeline.toolkit.RestoreIndex
import org.ide.lti.core.domain.pipeline.toolkit.RestorePoint
import org.ide.lti.core.domain.pipeline.toolkit.RestorePointState

/**
 * Workspace Restore Points panel: displays all restore points created across all tools
 * in this workspace in one place (FR-038, US6, T073).
 */
@Composable
public fun WorkspaceRestorePointsPanel(
    restoreIndex: RestoreIndex,
    restoreMessage: String? = null,
    onRestoreOne: (String) -> Unit = {},
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().testTag("WorkspaceRestorePointsPanel"),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = GlassDimens.OverviewMaxContentWidth)
                .padding(horizontal = Spacing.ExtraLarge, vertical = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            item {
                HeaderCard(
                    totalCount = restoreIndex.items.size,
                    availableCount = restoreIndex.items.count { it.state == RestorePointState.Available },
                    onRefresh = onRefresh,
                )
            }

            if (restoreMessage != null) {
                item {
                    GlassBanner(
                        severity = GlassBannerSeverity.SUCCESS,
                        title = restoreMessage,
                    )
                }
            }

            if (restoreIndex.items.isEmpty()) {
                item {
                    EmptyCard()
                }
            } else {
                items(restoreIndex.items) { point ->
                    RestorePointCard(point = point, onRestore = { onRestoreOne(point.id) })
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(totalCount: Int, availableCount: Int, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Workspace Restore Points",
                    fontSize = FontSize.BodyMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = ideFontFamily(),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Single restore-point ledger across Debloat, Boot, APK patches, " +
                        "and Configuration editors (FR-038).",
                    fontSize = FontSize.Micro,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            GlassButton(onClick = onRefresh, variant = GlassButtonVariant.Standard) {
                Text("Refresh", fontSize = FontSize.Micro)
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassChip(label = "$totalCount total backup(s)", selected = false)
            GlassChip(label = "$availableCount available", selected = availableCount > 0)
        }
    }
}

@Composable
private fun RestorePointCard(point: RestorePoint, onRestore: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassChip(label = point.tool, selected = true)
                Text(
                    text = point.itemPath,
                    fontSize = FontSize.BodySmall,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = codeFontFamily(),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "Backed up: ${point.createdAt} • Path: ${point.backupPath}",
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (point.state == RestorePointState.Available) {
            GlassButton(onClick = onRestore, variant = GlassButtonVariant.Standard) {
                Text("Restore", fontSize = FontSize.Micro)
            }
        } else {
            GlassChip(label = point.state.name, selected = false)
        }
    }
}

@Composable
private fun EmptyCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Large),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No Restore Points",
            fontSize = FontSize.BodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Destructive tools automatically create restore points before modifying or removing files.",
            fontSize = FontSize.Micro,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
