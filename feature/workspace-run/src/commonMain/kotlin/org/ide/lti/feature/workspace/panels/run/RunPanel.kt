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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
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
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.model.run.Artifact
import org.ide.lti.core.model.run.ArtifactKind
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.run.VerificationState
import org.ide.lti.core.model.setup.EnvironmentReadinessState
import org.koin.compose.viewmodel.koinViewModel

@Composable
public fun RunPanel(
    viewModel: BuildRunViewModel,
    historyViewModel: RunHistoryViewModel = koinViewModel(),
    onNavigateToSetup: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf("run") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        GlassTabBar(
            tabs = listOf(
                GlassTab(id = "run", title = "Run"),
                GlassTab(id = "history", title = "History"),
            ),
            selectedId = selectedTab,
            onTabSelected = { selectedTab = it },
            modifier = Modifier.fillMaxWidth(),
        )

        when (selectedTab) {
            "history" -> {
                RunHistoryPanel(
                    viewModel = historyViewModel,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                ActiveRunContent(
                    viewModel = viewModel,
                    onNavigateToSetup = onNavigateToSetup,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
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
                Text(
                    text = if (uiState.currentWorkspace != null) {
                        "Workspace: ${uiState.currentWorkspace?.name}"
                    } else {
                        "No workspace opened"
                    },
                    style = typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                        enabled = !uiState.isCancelling,
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
                    variant = GlassButtonVariant.Primary,
                ) {
                    Text(
                        text = if (uiState.isRunning) "Building..." else "Start Pipeline",
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

/** Status and state banner alerts for the active build run. */
@Composable
private fun RunStateBanners(uiState: BuildRunUiState, onNavigateToSetup: (() -> Unit)?, onReattach: () -> Unit) {
    if (uiState.environmentReadiness != null &&
        uiState.environmentReadiness?.state != EnvironmentReadinessState.READY
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
    onNavigateToSetup: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    ActiveRunContent(
        uiState = uiState,
        onCancel = { viewModel.cancelBuild() },
        onStart = { viewModel.startBuild() },
        onClearError = { viewModel.clearError() },
        onReattach = { viewModel.reattachBuild() },
        onNavigateToSetup = onNavigateToSetup,
        modifier = modifier,
    )
}

@Composable
public fun ActiveRunContent(
    uiState: BuildRunUiState,
    onCancel: () -> Unit = {},
    onStart: () -> Unit = {},
    onClearError: () -> Unit = {},
    onReattach: () -> Unit = {},
    onNavigateToSetup: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val typography = GlassTheme.typography

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        RunTopActionBar(
            uiState = uiState,
            onCancel = onCancel,
            onStart = onStart,
        )

        RunStateBanners(
            uiState = uiState,
            onNavigateToSetup = onNavigateToSetup,
            onReattach = onReattach,
        )

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
                        text = uiState.errorMessage ?: "",
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
        Text(
            text = "Verified Artifacts",
            style = typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

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
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                items(artifacts) { artifact ->
                    ArtifactItem(artifact = artifact)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Console log drawer
        GlassConsoleDrawer(
            logs = uiState.logs,
            isRunning = uiState.isRunning,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ArtifactItem(artifact: Artifact) {
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
                        ArtifactKind.OTA_MANIFEST -> "MANIFEST"
                        ArtifactKind.CHECKSUM -> "SHA256"
                        ArtifactKind.IMAGE -> "IMG"
                    },
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val size = artifact.sizeBytes
                if (size != null) {
                    val sizeMb = size / (1024 * 1024)
                    GlassChip(label = "$sizeMb MB")
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
