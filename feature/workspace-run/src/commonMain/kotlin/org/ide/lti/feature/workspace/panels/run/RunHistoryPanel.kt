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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.GlassHorizontalDivider
import org.ide.lti.core.designsystem.component.feedback.GlassDialog
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
    var pendingDelete by remember { mutableStateOf<BuildRun?>(null) }

    uiState.comparison?.let { comparison ->
        MarkdownDialog("Compare runs", comparison.markdown, emptyList(), onClose = viewModel::closeComparison)
    }

    pendingDelete?.let { run ->
        DeleteRunDialog(
            run = run,
            onDelete = {
                viewModel.deleteRun(run.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        HistoryHeader(uiState, onRefresh = viewModel::refreshHistory)

        uiState.errorMessage?.let { error ->
            Text(text = error, style = typography.bodySmall, color = MaterialTheme.colorScheme.error)
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
                            onDelete = { pendingDelete = run },
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
                        CompareWithRow(
                            others = uiState.runs.filter { it.id != selected.id }.take(COMPARE_CHOICES),
                            busy = uiState.isComparing,
                            onCompare = viewModel::compareWith,
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

/** The title, how many runs are recorded, and Refresh. */
@Composable
private fun HistoryHeader(uiState: RunHistoryUiState, onRefresh: () -> Unit) {
    val typography = GlassTheme.typography
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
                    "Loading the workspace…"
                },
                style = typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        GlassButton(
            onClick = onRefresh,
            enabled = !uiState.isLoading && uiState.currentWorkspace != null,
            variant = GlassButtonVariant.Secondary,
        ) {
            Text(
                text = if (uiState.isLoading) "Refreshing..." else "Refresh",
                style = typography.labelMedium,
            )
        }
    }
}

/** "Compare with": one chip per other run (newest first), by when it started and how it ended. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompareWithRow(others: List<BuildRun>, busy: Boolean, onCompare: (BuildRun) -> Unit) {
    if (others.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        Text(
            if (busy) "Comparing…" else "Compare with",
            style = GlassTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        others.forEach { run ->
            GlassChip(
                label = "${run.startedAt.toString().take(STARTED_SHOWN).replace('T', ' ')} " +
                    run.state.name.lowercase(),
                onClick = { onCompare(run) },
                enabled = !busy,
            )
        }
    }
}

private const val COMPARE_CHOICES = 6

/** `2026-11-19T10:42` of an instant: day and minute. */
private const val STARTED_SHOWN = 16

@Composable
private fun DeleteRunDialog(run: BuildRun, onDelete: () -> Unit, onDismiss: () -> Unit) {
    GlassDialog(
        onDismissRequest = onDismiss,
        title = "Delete this run?",
        confirmButton = {
            GlassButton(onClick = onDelete, variant = GlassButtonVariant.Primary) { Text("Delete") }
        },
        dismissButton = {
            GlassButton(onClick = onDismiss, variant = GlassButtonVariant.Secondary) { Text("Cancel") }
        },
    ) {
        Text(
            text = "Run ${run.id} and its log leave the history. Files it built stay in the workspace.",
            style = GlassTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
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
                // A queued or running build cannot be deleted; cancel it first.
                if (!run.state.isActive) {
                    GlassChip(
                        label = "Delete",
                        onClick = onDelete,
                    )
                }
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
