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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import org.ide.lti.core.common.utils.ByteFormatter
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.GlassHorizontalDivider
import org.ide.lti.core.designsystem.component.display.IdeStatusBadge
import org.ide.lti.core.designsystem.component.display.IdeStatusSeverity
import org.ide.lti.core.designsystem.component.feedback.GlassDialog
import org.ide.lti.core.designsystem.component.progress.GlassConsoleDrawer
import org.ide.lti.core.designsystem.component.progress.GlassLoadingPlaceholder
import org.ide.lti.core.designsystem.component.progress.GlassLoadingStatus
import org.ide.lti.core.designsystem.component.progress.GlassProgressBar
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.domain.pipeline.PipelineStageSequence
import org.ide.lti.core.domain.workbench.DeviceCheckResult
import org.ide.lti.core.domain.workspace.TimelineLogFilter
import org.ide.lti.core.domain.workspace.TimelineStepNode
import org.ide.lti.core.model.run.Artifact
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.Presence
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.ui.timeline.InteractiveBuildTimeline

@Composable
fun RunHistoryPanel(viewModel: RunHistoryViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsState()
    val typography = GlassTheme.typography
    var pendingDelete by remember { mutableStateOf<BuildRun?>(null) }

    uiState.comparison?.let { comparison ->
        MarkdownDialog("Compare runs", comparison.markdown, emptyList(), onClose = viewModel::closeComparison)
    }
    uiState.inspection?.let { inspection ->
        MarkdownDialog("Run inspector", inspection, emptyList(), onClose = viewModel::closeInspection)
    }
    uiState.provenanceReport?.let { provenance ->
        MarkdownDialog(
            title = uiState.provenanceTitle ?: "SBOM Provenance",
            markdown = provenance,
            problems = emptyList(),
            onClose = viewModel::closeProvenance,
        )
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

        if (uiState.isLoading && uiState.runs.isEmpty()) {
            GlassLoadingPlaceholder("Loading run history…")
        } else if (uiState.runs.isEmpty()) {
            RunHistoryEmptyState()
        } else {
            if (uiState.isLoading) {
                GlassProgressBar(progress = null, modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.Small))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                RunMasterList(
                    uiState = uiState,
                    onSelectRun = viewModel::selectRun,
                    onDeleteRun = { pendingDelete = it },
                    modifier = Modifier.weight(0.45f).fillMaxHeight(),
                )

                uiState.selectedRun?.let { selected ->
                    RunDetailsPane(
                        selected = selected,
                        uiState = uiState,
                        onInspect = viewModel::inspect,
                        onVerifyProvenance = viewModel::verifyProvenance,
                        onCompareWith = viewModel::compareWith,
                        onSelectStageFilter = viewModel::selectStageFilter,
                        modifier = Modifier.weight(0.55f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun RunHistoryEmptyState(modifier: Modifier = Modifier) {
    val typography = GlassTheme.typography
    GlassCard(modifier = modifier.fillMaxWidth()) {
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
}

@Composable
private fun RunMasterList(
    uiState: RunHistoryUiState,
    onSelectRun: (BuildRun) -> Unit,
    onDeleteRun: (BuildRun) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        items(uiState.runs, key = { it.id }) { run ->
            val isSelected = run.id == uiState.selectedRun?.id
            RunListItemCard(
                run = run,
                deviceCheck = uiState.deviceChecks[run.id],
                isSelected = isSelected,
                onSelect = { onSelectRun(run) },
                onDelete = { onDeleteRun(run) },
            )
        }
    }
}

@Composable
private fun RunDetailsPane(
    selected: BuildRun,
    uiState: RunHistoryUiState,
    onInspect: () -> Unit,
    onVerifyProvenance: () -> Unit,
    onCompareWith: (BuildRun) -> Unit,
    onSelectStageFilter: (StageId?, TimelineStepNode?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val typography = GlassTheme.typography
    val rightScrollState = rememberScrollState()
    val previousSucceeded = remember(selected.id, uiState.runs) {
        uiState.runs
            .filter { it.id != selected.id && it.state == RunState.SUCCEEDED && it.startedAt < selected.startedAt }
            .maxByOrNull { it.startedAt }
    }
    Column(
        modifier = modifier
            .verticalScroll(rightScrollState)
            .padding(end = Spacing.Small, bottom = Spacing.ExtraLarge),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Run ${selected.id}",
                style = typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (selected.state == RunState.SUCCEEDED) {
                GlassButton(
                    onClick = onVerifyProvenance,
                    loading = uiState.isVerifyingProvenance,
                    variant = GlassButtonVariant.Standard,
                ) { Text(if (uiState.isVerifyingProvenance) "Verifying…" else "Verify SBOM") }
            }
            GlassButton(
                onClick = onInspect,
                enabled = !uiState.isComparing,
                variant = GlassButtonVariant.Standard,
            ) { Text("Inspect") }
        }
        CompareWithRow(
            others = uiState.runs.filter { it.id != selected.id }.take(COMPARE_CHOICES),
            busy = uiState.isComparing,
            onCompare = onCompareWith,
        )

        StagePipelineView(
            latestRun = selected,
            isBusy = false,
            modifier = Modifier.fillMaxWidth(),
        )

        InteractiveBuildTimeline(
            run = selected,
            selectedStageId = uiState.selectedStageFilter,
            onSelectStage = { stageId -> onSelectStageFilter(stageId, null) },
            onJumpToLogs = { stageId, step -> onSelectStageFilter(stageId, step) },
            onInspectDiff = previousSucceeded?.let { prev -> { _ -> onCompareWith(prev) } },
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

        val rawLogs = if (uiState.selectedRunLogs.isNotEmpty()) {
            uiState.selectedRunLogs
        } else {
            listOf("[Log not cached for run ${selected.id}]")
        }
        val filteredLogs = remember(rawLogs, uiState.selectedStageFilter, uiState.selectedStepFilter) {
            TimelineLogFilter.filter(rawLogs, uiState.selectedStageFilter, uiState.selectedStepFilter)
        }
        uiState.selectedStageFilter?.let { stage ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val stepLabel = uiState.selectedStepFilter?.label?.let { " -> $it" }.orEmpty()
                GlassChip(
                    label = "Filtered: ${PipelineStageSequence.label(stage)}$stepLabel (${filteredLogs.size})",
                    selected = true,
                    onClick = { onSelectStageFilter(null, null) },
                )
            }
        }

        GlassConsoleDrawer(
            logs = filteredLogs,
            isRunning = false,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(Spacing.Large))
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
            if (uiState.currentWorkspace == null && uiState.errorMessage == null) {
                GlassLoadingStatus("Loading the workspace…")
            } else {
                Text(
                    text = if (uiState.currentWorkspace != null) {
                        "${uiState.runs.size} previous runs recorded"
                    } else {
                        "Workspace unavailable"
                    },
                    style = typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        GlassButton(
            onClick = onRefresh,
            enabled = uiState.currentWorkspace != null,
            loading = uiState.isLoading,
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
        if (busy) {
            GlassLoadingStatus("Comparing…", Modifier.align(Alignment.CenterVertically))
        } else {
            Text(
                "Compare with",
                style = GlassTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
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
private fun RunListItemCard(
    run: BuildRun,
    deviceCheck: DeviceCheckResult?,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
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
                deviceCheck?.let { PhoneBadge(it) }
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

/** The run's newest phone check: passed, problems on the phone, or the phone runs another build. */
@Composable
private fun PhoneBadge(check: DeviceCheckResult) {
    val (label, severity) = when {
        !check.installed -> "Phone: other build" to IdeStatusSeverity.Warning
        check.passed -> "Phone: passed" to IdeStatusSeverity.Ready
        else -> "Phone: problems" to IdeStatusSeverity.Failed
    }
    IdeStatusBadge(label = label, severity = severity)
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
                val sizeStr = artifact.sizeBytes?.let { ByteFormatter.format(it) } ?: "Unknown size"
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
