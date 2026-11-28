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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.common.utils.formatBytes
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.GlassHorizontalDivider
import org.ide.lti.core.designsystem.component.feedback.GlassBanner
import org.ide.lti.core.designsystem.component.feedback.GlassBannerAction
import org.ide.lti.core.designsystem.component.feedback.GlassBannerSeverity
import org.ide.lti.core.designsystem.component.navigation.GlassTab
import org.ide.lti.core.designsystem.component.navigation.GlassTabBar
import org.ide.lti.core.designsystem.component.progress.GlassConsoleDrawer
import org.ide.lti.core.designsystem.component.progress.GlassLoadingStatus
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.domain.pipeline.PipelineStageSequence
import org.ide.lti.core.domain.pipeline.stages.PreflightFix
import org.ide.lti.core.domain.workspace.TimelineLogFilter
import org.ide.lti.core.domain.workspace.TimelineStepNode
import org.ide.lti.core.model.run.Artifact
import org.ide.lti.core.model.run.ArtifactKind
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.run.VerificationState
import org.ide.lti.core.model.setup.EnvironmentReadinessState
import org.ide.lti.core.ui.timeline.InteractiveBuildTimeline
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RunPanel(
    viewModel: BuildRunViewModel,
    historyViewModel: RunHistoryViewModel = koinViewModel(),
    profilesViewModel: ProfilesViewModel = koinViewModel(),
    onNavigateToSetup: (() -> Unit)? = null,
    /** Opens a stage editor at an object: where a failed build's fix is made. */
    onOpen: (StageId, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val activeUiState by viewModel.uiState.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf("run") }
    var selectedStageFilter by remember(activeUiState.latestRun?.id) { mutableStateOf<StageId?>(null) }
    var selectedStepFilter by remember(activeUiState.latestRun?.id) { mutableStateOf<TimelineStepNode?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        GlassTabBar(
            tabs = listOf(
                GlassTab(id = "run", title = "Run"),
                GlassTab(id = "timeline", title = "Timeline & DAG"),
                GlassTab(id = "history", title = "History"),
                GlassTab(id = "profiles", title = "Profiles"),
            ),
            selectedId = selectedTab,
            onTabSelected = { selectedTab = it },
            modifier = Modifier.fillMaxWidth(),
        )

        when (selectedTab) {
            "profiles" -> ProfilesPanel(viewModel = profilesViewModel, modifier = Modifier.fillMaxSize())

            "timeline" -> {
                RunTimelineTabContent(
                    run = activeUiState.latestRun,
                    selectedStageId = selectedStageFilter,
                    onSelectStage = { stageId ->
                        selectedStageFilter = stageId
                        selectedStepFilter = null
                    },
                    onJumpToLogs = { stageId, step ->
                        selectedStageFilter = stageId
                        selectedStepFilter = step
                        selectedTab = "run"
                    },
                    onInspectDiff = { stageId -> onOpen(stageId, "") },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            "history" -> {
                RunHistoryPanel(
                    viewModel = historyViewModel,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                ActiveRunContent(
                    viewModel = viewModel,
                    selectedStageFilter = selectedStageFilter,
                    selectedStepFilter = selectedStepFilter,
                    onClearStageFilter = {
                        selectedStageFilter = null
                        selectedStepFilter = null
                    },
                    onNavigateToSetup = onNavigateToSetup,
                    onOpen = onOpen,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun RunTimelineTabContent(
    run: org.ide.lti.core.model.run.BuildRun?,
    selectedStageId: StageId?,
    onSelectStage: (StageId?) -> Unit,
    onJumpToLogs: (StageId, TimelineStepNode?) -> Unit,
    onInspectDiff: (StageId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val typography = GlassTheme.typography
    if (run == null) {
        GlassCard(modifier = modifier.fillMaxWidth()) {
            Text(
                text = "Start a build or open a run in History to explore its interactive Task DAG and Waterfall.",
                style = typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Spacing.Medium),
            )
        }
        return
    }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        InteractiveBuildTimeline(
            run = run,
            selectedStageId = selectedStageId,
            onSelectStage = onSelectStage,
            onJumpToLogs = onJumpToLogs,
            onInspectDiff = onInspectDiff,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Top action bar for the active build run panel. */
@Composable
private fun RunTopActionBar(uiState: BuildRunUiState, onCancel: () -> Unit, onStart: () -> Unit) {
    val typography = GlassTheme.typography

    // Top action bar.
    // Below PanelHeaderStackBreakpoint even a truncated title and the (never-wrapped)
    // action buttons don't both fit on one line — GlassLayout's split-panel divider can
    // legitimately drag this panel down to MinPanelWidth (180dp), well under that. Read
    // the available width once, at this single root, to pick Row (side-by-side) vs.
    // Column (stacked) — a stable breakpoint boolean, not a continuous/animated read, so
    // it stays cheap (see avoiding-subcomposition-pitfalls: nested or per-frame reads are
    // the expensive case, a single top-level breakpoint check is not).
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isNarrow = maxWidth < ComponentSize.PanelHeaderStackBreakpoint

        val titleBlock: @Composable () -> Unit = {
            Column {
                Text(
                    text = "ROM Build Pipeline",
                    style = typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val workspace = uiState.currentWorkspace
                if (workspace == null && uiState.errorMessage == null) {
                    GlassLoadingStatus("Loading the workspace…")
                } else {
                    Text(
                        text = workspace?.let { "Workspace: ${it.name}" } ?: "Workspace unavailable",
                        style = typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        val actionsBlock: @Composable () -> Unit = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (uiState.canCancelRun) {
                    GlassButton(
                        onClick = onCancel,
                        loading = uiState.isCancelling,
                        variant = GlassButtonVariant.Secondary,
                    ) {
                        Text(
                            text = if (uiState.isCancelling) "Cancelling..." else "Cancel",
                            style = typography.labelMedium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                GlassButton(
                    onClick = onStart,
                    enabled = !uiState.isRunDisabled,
                    loading = uiState.isStartingRun || uiState.isRunning,
                    variant = GlassButtonVariant.Primary,
                ) {
                    Text(
                        text = when {
                            uiState.isStartingRun -> "Starting…"
                            uiState.isRunning -> "Building…"
                            else -> "Start Pipeline"
                        },
                        style = typography.labelMedium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (isNarrow) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                titleBlock()
                actionsBlock()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f, fill = false)) { titleBlock() }
                actionsBlock()
            }
        }
    }
}

/** Where the next run stops: the whole build, or after one stage (to inspect or configure what it produced). */
@Composable
private fun RunThroughSelector(selected: StageId?, onSelect: (StageId?) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().testTag("RunThroughSelector"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Run through",
            style = GlassTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GlassChip(label = "Whole build", selected = selected == null, onClick = { onSelect(null) })
        PipelineStageSequence.ORDER.filter { it != StageId.PUBLISH_RELEASE }.forEach { stage ->
            GlassChip(label = stageLabel(stage), selected = selected == stage, onClick = { onSelect(stage) })
        }
    }
}

/** Status and state banner alerts for the active build run. */
@Composable
private fun RunStateBanners(uiState: BuildRunUiState, onNavigateToSetup: (() -> Unit)?, onReattach: () -> Unit) {
    if (uiState.environmentReadiness != null &&
        uiState.environmentReadiness.state != EnvironmentReadinessState.READY
    ) {
        GlassBanner(
            severity = GlassBannerSeverity.WARNING,
            title = uiState.failingCheck ?: "Build Environment Unavailable",
            description = uiState.remediation
                ?: "The build environment or service daemon is currently unavailable.",
            primaryAction = if (onNavigateToSetup != null) {
                GlassBannerAction("Go to Setup") { onNavigateToSetup() }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        )
    } else if (uiState.isPaused) {
        GlassBanner(
            severity = GlassBannerSeverity.WARNING,
            title = "Build paused waiting for application",
            description = "Reattach to resume execution from the current stage boundary.",
            primaryAction = GlassBannerAction("Resume") { onReattach() },
            modifier = Modifier.fillMaxWidth(),
        )
    } else if (uiState.isInterrupted) {
        GlassBanner(
            severity = GlassBannerSeverity.WARNING,
            title = "Build interrupted (outcome unknown)",
            description = "Execution service or process terminated unexpectedly.",
            modifier = Modifier.fillMaxWidth(),
        )
    } else if (uiState.isCancelling || uiState.latestRun?.state == RunState.CANCELLING) {
        GlassBanner(
            severity = GlassBannerSeverity.INFO,
            title = "Cancelling build run...",
            description = "Waiting for stage processes to terminate cleanly.",
            modifier = Modifier.fillMaxWidth(),
        )
    } else if (uiState.latestRun?.state == RunState.CANCELLED) {
        GlassBanner(
            severity = GlassBannerSeverity.INFO,
            title = "Build run cancelled",
            description = "Run was cancelled by user. Partial artifacts are unverified.",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ActiveRunContent(
    viewModel: BuildRunViewModel,
    selectedStageFilter: StageId? = null,
    selectedStepFilter: TimelineStepNode? = null,
    onClearStageFilter: () -> Unit = {},
    onNavigateToSetup: (() -> Unit)? = null,
    onOpen: (StageId, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    ActiveRunContent(
        uiState = uiState,
        selectedStageFilter = selectedStageFilter,
        selectedStepFilter = selectedStepFilter,
        onClearStageFilter = onClearStageFilter,
        onCancel = { viewModel.cancelBuild() },
        onStart = { viewModel.startBuild() },
        onSelectThroughStage = viewModel::selectThroughStage,
        onClearError = { viewModel.clearError() },
        onReattach = { viewModel.reattachBuild() },
        onNavigateToSetup = onNavigateToSetup,
        onPreview = viewModel::previewImpact,
        onReport = viewModel::makeReport,
        onVerifyProvenance = viewModel::verifyProvenance,
        onCloseReport = viewModel::closeReport,
        onCheckPhone = viewModel::checkPhone,
        onApplyFix = viewModel::applyFix,
        onOpen = onOpen,
        modifier = modifier,
    )
}

@Composable
fun ActiveRunContent(
    uiState: BuildRunUiState,
    onCancel: () -> Unit = {},
    onStart: () -> Unit = {},
    onSelectThroughStage: (StageId?) -> Unit = {},
    onClearError: () -> Unit = {},
    onReattach: () -> Unit = {},
    onNavigateToSetup: (() -> Unit)? = null,
    onPreview: () -> Unit = {},
    onReport: () -> Unit = {},
    onVerifyProvenance: () -> Unit = {},
    onCloseReport: () -> Unit = {},
    onCheckPhone: () -> Unit = {},
    onApplyFix: (PreflightFix.Setting) -> Unit = {},
    onOpen: (StageId, String) -> Unit = { _, _ -> },
    selectedStageFilter: StageId? = null,
    selectedStepFilter: TimelineStepNode? = null,
    onClearStageFilter: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val typography = GlassTheme.typography
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(bottom = Spacing.ExtraExtraLarge * 2),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        RunTopActionBar(
            uiState = uiState,
            onCancel = onCancel,
            onStart = onStart,
        )

        if (!uiState.isRunning) {
            RunThroughSelector(selected = uiState.throughStage, onSelect = onSelectThroughStage)
            ImpactPreview(uiState = uiState, onPreview = onPreview)
            FailureDiagnosis(uiState = uiState, onApplyFix = onApplyFix, onOpen = onOpen)
        }

        RunStateBanners(
            uiState = uiState,
            onNavigateToSetup = onNavigateToSetup,
            onReattach = onReattach,
        )

        if (uiState.blockingIssues.isNotEmpty() && !uiState.isRunning) {
            DeviceReadinessChecklist(issues = uiState.blockingIssues)
        }

        // Error message banner
        if (uiState.errorMessage != null) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.Small),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = uiState.errorMessage,
                        style = typography.bodySmall,
                        color = GlassTheme.diagnosticColors.error,
                    )
                    GlassChip(
                        label = "Dismiss",
                        onClick = onClearError,
                    )
                }
            }
        }

        // Stepper
        StagePipelineView(
            latestRun = uiState.latestRun,
            isBusy = uiState.isRunning,
            modifier = Modifier.fillMaxWidth(),
        )

        GlassHorizontalDivider()

        // Artifacts section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Verified Artifacts",
                style = typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (uiState.latestRun?.state == RunState.SUCCEEDED) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                    // After installing the package: does the phone run this build, and is it healthy?
                    GlassButton(
                        onClick = onCheckPhone,
                        loading = uiState.isCheckingPhone,
                        variant = GlassButtonVariant.Standard,
                        modifier = Modifier.testTag("CheckPhoneButton"),
                    ) { Text(if (uiState.isCheckingPhone) "Checking…" else "Check the phone") }
                    GlassButton(
                        onClick = onReport,
                        loading = uiState.isReporting,
                        variant = GlassButtonVariant.Standard,
                        modifier = Modifier.testTag("BuildReportButton"),
                    ) { Text(if (uiState.isReporting) "Reading…" else "Build report") }
                    GlassButton(
                        onClick = onVerifyProvenance,
                        loading = uiState.isVerifyingProvenance,
                        variant = GlassButtonVariant.Standard,
                        modifier = Modifier.testTag("VerifyProvenanceButton"),
                    ) { Text(if (uiState.isVerifyingProvenance) "Verifying…" else "Verify SBOM") }
                }
            }
        }
        BuildReportDialog(uiState.report, uiState.reportTitle, onClose = onCloseReport)

        val artifacts = uiState.latestRun?.artifacts ?: emptyList()
        if (artifacts.isEmpty()) {
            Text(
                text = if (uiState.isRunning) {
                    "Artifacts will appear after build completes."
                } else {
                    "No build artifacts produced yet."
                },
                style = typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                artifacts.forEach { artifact ->
                    ArtifactItem(artifact = artifact)
                }
            }
        }

        // Console log drawer
        ActiveRunConsoleSection(
            logs = uiState.logs,
            isRunning = uiState.isRunning,
            hasArtifacts = artifacts.isNotEmpty(),
            selectedStageFilter = selectedStageFilter,
            selectedStepFilter = selectedStepFilter,
            onClearStageFilter = onClearStageFilter,
        )

        Spacer(modifier = Modifier.height(Spacing.Large))
    }
}

@Composable
private fun ActiveRunConsoleSection(
    logs: List<String>,
    isRunning: Boolean,
    hasArtifacts: Boolean,
    selectedStageFilter: StageId?,
    selectedStepFilter: TimelineStepNode?,
    onClearStageFilter: () -> Unit,
) {
    val filteredLogs = remember(logs, selectedStageFilter, selectedStepFilter) {
        TimelineLogFilter.filter(logs, selectedStageFilter, selectedStepFilter)
    }
    selectedStageFilter?.let { stage ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val stepSuffix = selectedStepFilter?.label?.let { " -> $it" }.orEmpty()
            GlassChip(
                label = "Filtered: ${PipelineStageSequence.label(stage)}$stepSuffix (${filteredLogs.size})",
                selected = true,
                onClick = onClearStageFilter,
            )
        }
    }
    GlassConsoleDrawer(
        logs = filteredLogs,
        isRunning = isRunning,
        modifier = Modifier.fillMaxWidth(),
        logMaxHeight = if (hasArtifacts) {
            ComponentSize.ActionCardHeight
        } else {
            ComponentSize.DialogDefaultHeight
        },
    )
}

@Composable
internal fun ArtifactItem(artifact: Artifact) {
    val typography = GlassTheme.typography

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
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
                    text = artifact.linuxPath.substringAfterLast("/"),
                    style = typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                GlassChip(
                    label = when (artifact.kind) {
                        ArtifactKind.FLASHABLE_ZIP -> "ZIP"
                        ArtifactKind.AB_OTA_ZIP -> "A/B OTA"
                        ArtifactKind.OTA_MANIFEST -> "MANIFEST"
                        ArtifactKind.CHECKSUM -> "SHA256"
                        ArtifactKind.IMAGE -> "IMG"
                        ArtifactKind.EXTRA_PACKAGE -> "EXTRA"
                        ArtifactKind.PROVENANCE_SBOM -> "SBOM"
                    },
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val size = artifact.sizeBytes
                if (size != null) {
                    GlassChip(label = sizeLabel(size))
                }
                val sha = artifact.sha256
                if (sha != null) {
                    GlassChip(label = "SHA: ${sha.take(8)}...")
                }
                GlassChip(
                    label = when (artifact.verification) {
                        VerificationState.VERIFIED -> "VERIFIED"
                        VerificationState.FAILED -> "INVALID"
                        VerificationState.NOT_VERIFIED -> "UNVERIFIED"
                    },
                    selected = artifact.verification == VerificationState.VERIFIED,
                )
            }
        }
    }
}

/** The artifact's size at a glance. */
internal fun sizeLabel(bytes: Long): String = formatBytes(bytes)
