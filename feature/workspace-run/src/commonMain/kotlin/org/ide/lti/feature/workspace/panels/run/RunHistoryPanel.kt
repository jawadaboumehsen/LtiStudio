/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.panels.run

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.GlassHorizontalDivider
import org.ide.lti.core.designsystem.component.progress.GlassConsoleDrawer
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.model.run.Artifact
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.Presence

@Composable
public fun RunHistoryPanel(viewModel: RunHistoryViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsState()
    val typography = GlassTheme.typography

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        // Top action bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Build History",
                    style = typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (uiState.currentWorkspace != null) {
                        "${uiState.runs.size} previous runs recorded"
                    } else {
                        "No workspace opened"
                    },
                    style = typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            GlassButton(
                onClick = { viewModel.refreshHistory() },
                enabled = !uiState.isLoading && uiState.currentWorkspace != null,
                variant = GlassButtonVariant.Secondary,
            ) {
                Text(
                    text = if (uiState.isLoading) "Refreshing..." else "Refresh",
                    style = typography.labelMedium,
                )
            }
        }

        if (uiState.runs.isEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.Large),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No recorded build runs for this workspace yet.",
                        style = typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                // Left: List of runs
                LazyColumn(
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    items(uiState.runs, key = { it.id }) { run ->
                        val isSelected = run.id == uiState.selectedRun?.id
                        RunListItemCard(
                            run = run,
                            isSelected = isSelected,
                            onSelect = { viewModel.selectRun(run) },
                            onDelete = { viewModel.deleteRun(run.id) },
                        )
                    }
                }

                // Right: Details for selected run
                val selected = uiState.selectedRun
                if (selected != null) {
                    Column(
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                    ) {
                        Text(
                            text = "Run ${selected.id}",
                            style = typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        StagePipelineView(
                            latestRun = selected,
                            isBusy = false,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        GlassHorizontalDivider()

                        Text(
                            text = "Artifacts (${selected.artifacts.size})",
                            style = typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        if (selected.artifacts.isEmpty()) {
                            Text(
                                text = "No artifacts recorded for this run.",
                                style = typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                            ) {
                                selected.artifacts.forEach { artifact ->
                                    HistoryArtifactItem(artifact = artifact)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        val logsToDisplay = if (uiState.selectedRunLogs.isNotEmpty()) {
                            uiState.selectedRunLogs
                        } else {
                            listOf("[Log not cached for run ${selected.id}]")
                        }

                        GlassConsoleDrawer(
                            logs = logsToDisplay,
                            isRunning = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RunListItemCard(run: BuildRun, isSelected: Boolean, onSelect: () -> Unit, onDelete: () -> Unit) {
    val typography = GlassTheme.typography

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Small),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = run.id,
                    style = typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                GlassChip(
                    label = run.state.name,
                    selected = isSelected,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${run.stages.size} stages | ${run.artifacts.size} artifacts",
                    style = typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GlassChip(
                    label = "Delete",
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun HistoryArtifactItem(artifact: Artifact) {
    val typography = GlassTheme.typography

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.Small),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artifact.linuxPath.substringAfterLast("/"),
                    style = typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val sizeStr = artifact.sizeBytes?.let { "${it / (1024 * 1024)} MB" } ?: "Unknown size"
                Text(
                    text = sizeStr,
                    style = typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            GlassChip(
                label = artifact.presence.name,
                selected = artifact.presence == Presence.PRESENT,
            )
        }
    }
}
