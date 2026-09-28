/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.actions.GlassSecondaryButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.feedback.GlassBanner
import org.ide.lti.core.designsystem.component.feedback.GlassBannerSeverity
import org.ide.lti.core.designsystem.component.progress.GlassConsoleDrawer
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.icon.ideStatusSuccess
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.model.setup.EnvironmentReadiness
import org.ide.lti.feature.setup.EnvironmentPresentation
import org.ide.lti.feature.setup.EnvironmentState
import org.ide.lti.feature.setup.PrimaryAction
import org.ide.lti.feature.setup.SetupActivityState
import org.ide.lti.feature.setup.SetupOperation
import org.ide.lti.feature.setup.SetupPresentationMapper
import org.ide.lti.feature.setup.SetupUiActions
import org.ide.lti.feature.setup.SetupUiState
import org.ide.lti.feature.setup.TerminalHandoffState
import org.ide.lti.feature.setup.components.DoctorDiagnosticsDrawer
import org.ide.lti.feature.setup.components.SetupActivityPanel
import org.ide.lti.feature.setup.components.ToolchainPipelineTracker

/**
 * Structured overload for [EnvironmentStepContent] utilizing [SetupUiState] and [SetupUiActions]
 * to prevent parameter sprawl and ensure clean single-responsibility composition.
 */
@Suppress("LongParameterList")
@Composable
fun EnvironmentStepContent(
    state: SetupUiState,
    actions: SetupUiActions,
    modifier: Modifier = Modifier,
    onProceedToLaunchpad: () -> Unit = {},
    onNavigateToProjects: (() -> Unit)? = null,
    activityState: SetupActivityState? = null,
    activityListState: androidx.compose.foundation.lazy.LazyListState =
        androidx.compose.foundation.lazy
            .rememberLazyListState(),
    previewInvokerFocusRequester: FocusRequester? = null,
) {
    EnvironmentStepContent(
        toolchainSetupState = state.toolchainSetupState,
        environmentPresentation = state.environmentPresentation,
        activeOperation = state.activeOperation,
        onNavigateToProjects = onNavigateToProjects,
        onCheckEnvironment = actions.onCheckEnvironment,
        onReconnect = actions.onReconnect,
        onRetrySetupStep = actions.onRetrySetupStep,
        onRetryStage = actions.onRetryStage,
        onPreviewSetup = actions.onPreviewSetup,
        onProceedToLaunchpad = onProceedToLaunchpad,
        modifier = modifier,
        onAutoRemediateDoctor = actions.onAutoRemediateDoctor,
        selectedCockpitTab = state.selectedCockpitTab,
        onSelectCockpitTab = actions.onSelectCockpitTab,
        readiness = state.environmentReadiness,
        activityState = activityState,
        activityListState = activityListState,
        previewInvokerFocusRequester = previewInvokerFocusRequester,
        handoffSheetState = state.handoffSheetState,
        onInstallPackages = actions.onInstallPackages,
        onShowHandoffSheet = actions.onShowHandoffSheet,
        onOpenDistro = actions.onOpenTerminal,
        onSelectDistro = actions.onSelectDistro,
        onOpenProject = actions.onOpenFolder,
        onCreateProject = actions.onCreateWorkspace,
        onCancelOperation = actions.onCancelOperation,
        onExportLogs = actions.onExportLogs,
    )
}

/**
 * Professional Environment, Toolchain & Diagnostics Cockpit.
 *
 * Consolidated single-scroll layout modeled after JetBrains CLion Toolchains, Android Studio SDK Manager,
 * Xcode Components, and Flutter Doctor.
 *
 * Structure:
 * 1. Interactive Subsystem & Runtime Header: WSL2 distro, daemon bridge ping latency, and ext4 storage meter.
 * 2. Toolchain Provisioning Pipeline: 5-stage sequential progress tracker with per-stage status and retry.
 * 3. Doctor Diagnostics Drawer: Categorized pre-flight system checks with 1-click automated remediation.
 * 4. Setup Console Drawer: Real-time daemon execution logs with auto-scroll and run state.
 * 5. Bottom Navigation Action Bar.
 *
 * Adheres strictly to:
 * - Single Responsibility: Coordinates environment setup and toolchain provisioning cockpit.
 * - Design System Guardrails: 100% token usage (zero raw dp or alpha float literals).
 */
@Suppress(
    "LongParameterList",
    "CyclomaticComplexMethod",
    "LongMethod",
)
@Composable
fun EnvironmentStepContent(
    toolchainSetupState: ToolchainSetupState,
    onCheckEnvironment: () -> Unit,
    onRetrySetupStep: () -> Unit,
    onProceedToLaunchpad: () -> Unit,
    modifier: Modifier = Modifier,
    environmentPresentation: EnvironmentPresentation? = null,
    activeOperation: SetupOperation? = null,
    onReconnect: () -> Unit = {},
    onRetryStage: (SetupStepStage) -> Unit = {},
    onPreviewSetup: (SetupStepStage?) -> Unit = {},
    onNavigateToProjects: (() -> Unit)? = null,
    onAutoRemediateDoctor: (() -> Unit)? = null,
    selectedCockpitTab: String = "doctor",
    onSelectCockpitTab: (String) -> Unit = {},
    readiness: EnvironmentReadiness? = null,
    activityState: SetupActivityState? = null,
    activityListState: androidx.compose.foundation.lazy.LazyListState =
        androidx.compose.foundation.lazy
            .rememberLazyListState(),
    previewInvokerFocusRequester: FocusRequester? = null,
    handoffSheetState: TerminalHandoffState = TerminalHandoffState.Idle,
    onInstallPackages: () -> Unit = {},
    onShowHandoffSheet: () -> Unit = {},
    onOpenDistro: (String) -> Unit = {},
    onSelectDistro: (String) -> Unit = {},
    onOpenProject: () -> Unit = {},
    onCreateProject: () -> Unit = {},
    onCancelOperation: () -> Unit = {},
    onExportLogs: (() -> Unit)? = null,
) {
    val presentation =
        environmentPresentation ?: SetupPresentationMapper.mapEnvironment(
            state = toolchainSetupState,
            activeOperation = activeOperation,
        )

    Box(
        modifier =
        modifier
            .widthIn(max = ComponentSize.SetupCockpitMaxWidth)
            .fillMaxWidth()
            .fillMaxHeight(),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = Spacing.ScreenHorizontal,
                    end = Spacing.ScreenHorizontal,
                    top = Spacing.Medium,
                    bottom = Spacing.ScreenVertical,
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.SectionGap),
        ) {
            // Phase 4 Header: Title, Subtitle, Last Verified, and Live Status Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Text(
                        text = "Machine setup",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle =
                        when {
                            toolchainSetupState.isChecking -> "Checking WSL and build tools…"
                            toolchainSetupState.steps.any { it.status == StepStatus.FAILED } -> {
                                val count = toolchainSetupState.steps.count { it.status == StepStatus.FAILED }
                                if (count == 1) "1 item needs attention" else "$count items need attention"
                            }
                            toolchainSetupState.isAllReady -> "Ready to build"
                            else -> "Verification pending"
                        }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // "Verified" only ever means a fully passing check; a live check that found problems
                    // reports when it ran ("Checked"), never a success it did not have.
                    val isRestored = toolchainSetupState.steps.any { it.provenance == StepProvenance.RESTORED }
                    val lastVerified =
                        toolchainSetupState.lastVerifiedTimestamp?.takeIf { it > 0 }?.let { millis ->
                            val relative = formatRelativeTime(Instant.fromEpochMilliseconds(millis))
                            if (isRestored) "Last verified $relative (historical)" else "Last verified $relative"
                        }
                    val checkedAt = toolchainSetupState.checkedAt?.takeIf { !toolchainSetupState.isAllReady }
                    val lastVerifiedLabel =
                        when {
                            checkedAt != null -> {
                                val checked = "Checked ${formatRelativeTime(Instant.fromEpochMilliseconds(checkedAt))}"
                                if (lastVerified != null) {
                                    "$checked · ${lastVerified.replaceFirstChar { it.lowercase() }}"
                                } else {
                                    checked
                                }
                            }
                            lastVerified != null -> lastVerified
                            else -> "Not checked yet"
                        }
                    Text(
                        text = lastVerifiedLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Top-right live status pills and verify action
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    val wslStep = toolchainSetupState.steps.firstOrNull { it.stage == SetupStepStage.WSL_DETECTION }
                    val isWslOk = wslStep?.status == StepStatus.SUCCESS
                    val bridgeStep =
                        toolchainSetupState.steps
                            .firstOrNull { it.stage == SetupStepStage.SERVER_CONNECTIVITY }
                    val sysStep =
                        toolchainSetupState.steps
                            .firstOrNull { it.stage == SetupStepStage.SYSTEM_PACKAGES }
                    val bridgePres = SetupPresentationMapper.mapBuildService(
                        step = bridgeStep,
                        daemonPingMs = toolchainSetupState.daemonPingMs,
                        isChecking = toolchainSetupState.isChecking,
                        systemPackagesStep = sysStep,
                    )

                    val wslStatus =
                        when (wslStep?.status) {
                            StepStatus.SUCCESS -> "Active"
                            StepStatus.RUNNING -> "Checking"
                            StepStatus.FAILED -> "Offline"
                            else -> "Pending"
                        }

                    GlassChip(
                        label = "WSL: $wslStatus",
                        selected = isWslOk,
                    )
                    GlassChip(
                        label = "Bridge: ${bridgePres.chipText}",
                        selected = bridgePres.isReady,
                    )
                    GlassSecondaryButton(
                        onClick = onCheckEnvironment,
                        enabled = !toolchainSetupState.isBusy,
                    ) {
                        Text(if (toolchainSetupState.isChecking) "Checking…" else "Check again")
                    }
                }
            }

            val activeRunIds = toolchainSetupState.stepRunIds.filterValues { it.isNotBlank() }
            if (activeRunIds.isNotEmpty()) {
                val runSummary = activeRunIds.entries.joinToString(", ") { "${it.key.displayName}: ${it.value}" }
                GlassBanner(
                    severity = GlassBannerSeverity.INFO,
                    title = "Active Durable Run",
                    description = "Executing durable background run ($runSummary). Reattaches automatically across session restarts.",
                )
            }

            // Grouped Inset List ("This machine") with 6 canonical rows - MAIN CONTENT FIRST
            ToolchainPipelineTracker(
                steps = toolchainSetupState.steps,
                onRetryStep = onRetrySetupStep,
                isBusy = toolchainSetupState.isBusy,
                onRetryStage = onRetryStage,
                activeOperation = activeOperation,
                handoffSheetState = handoffSheetState,
                onInstallPackages = onInstallPackages,
                onShowHandoffSheet = onShowHandoffSheet,
                distroStatuses = toolchainSetupState.distroStatuses,
                activeDistro = toolchainSetupState.activeDistro,
                onVerify = onCheckEnvironment,
                onOpenDistro = onOpenDistro,
                onSelectDistro = onSelectDistro,
                onShowDiagnostics = { onSelectCockpitTab("console") },
                onCancel = onCancelOperation,
                daemonPingMs = toolchainSetupState.daemonPingMs,
            )

            val isMachineReady =
                presentation.environmentState is EnvironmentState.Ready ||
                    (
                        toolchainSetupState.canLaunchWorkspace &&
                            (toolchainSetupState.lastReadyAt != null || toolchainSetupState.isAllReady)
                        )

            if (isMachineReady) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentPadding = Spacing.CardPadding,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            Icon(
                                painter = AppIcons.ideStatusSuccess(),
                                contentDescription = "Ready",
                                tint = GlassTheme.diagnosticColors.success,
                                modifier = Modifier.size(IconSize.Medium),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
                                Text(
                                    text = "This machine is ready to build ROMs",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "All toolchain dependencies, compilers, and repositories are verified.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GlassSecondaryButton(
                                onClick = onOpenProject,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                                ) {
                                    Icon(
                                        painter = AppIcons.FolderOpenPainterResource(),
                                        contentDescription = null,
                                        modifier = Modifier.size(IconSize.Small),
                                    )
                                    Text("Open project")
                                }
                            }
                            GlassPrimaryButton(
                                onClick = onCreateProject,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                                ) {
                                    Icon(
                                        painter = AppIcons.AddPainterResource(),
                                        contentDescription = null,
                                        modifier = Modifier.size(IconSize.Small),
                                    )
                                    Text("Create project")
                                }
                            }
                        }
                    }
                }
            }

            val cockpitTabs =
                listOf(
                    org.ide.lti.core.designsystem.component.navigation.GlassTab(
                        id = "doctor",
                        title = "Doctor Diagnostics",
                    ),
                    org.ide.lti.core.designsystem.component.navigation.GlassTab(
                        id = "console",
                        title = "Pipeline Console",
                    ),
                )

            org.ide.lti.core.designsystem.component.navigation.GlassTabBar(
                tabs = cockpitTabs,
                selectedId = selectedCockpitTab,
                onTabSelected = onSelectCockpitTab,
                modifier = Modifier.fillMaxWidth(),
            )

            EnvironmentCockpitTabContent(
                selectedCockpitTab = selectedCockpitTab,
                toolchainSetupState = toolchainSetupState,
                readiness = readiness,
                onAutoRemediateDoctor = onAutoRemediateDoctor,
                activityState = activityState,
                activityListState = activityListState,
                onExportLogs = onExportLogs,
            )

            EnvironmentStepActionBar(
                toolchainSetupState = toolchainSetupState,
                presentation = presentation,
                onNavigateToProjects = onNavigateToProjects,
                onProceedToLaunchpad = onProceedToLaunchpad,
                onCheckEnvironment = onCheckEnvironment,
                onReconnect = onReconnect,
                onPreviewSetup = onPreviewSetup,
                onRetryStage = onRetryStage,
                previewInvokerFocusRequester = previewInvokerFocusRequester,
            )
        }
    }
}

@Composable
private fun EnvironmentCockpitTabContent(
    selectedCockpitTab: String,
    toolchainSetupState: ToolchainSetupState,
    readiness: EnvironmentReadiness? = null,
    onAutoRemediateDoctor: (() -> Unit)?,
    activityState: SetupActivityState?,
    activityListState: androidx.compose.foundation.lazy.LazyListState,
    onExportLogs: (() -> Unit)? = null,
) {
    when (selectedCockpitTab) {
        "doctor" -> {
            DoctorDiagnosticsDrawer(
                diagnosticsList = toolchainSetupState.diagnostics,
                readiness = readiness,
                onAutoRemediateDoctor = onAutoRemediateDoctor,
            )
        }
        "console" -> {
            if (activityState != null) {
                SetupActivityPanel(
                    activityState = activityState,
                    title = "Execution Terminal Logs",
                    isRunning = toolchainSetupState.isBusy,
                    listState = activityListState,
                    onExport = onExportLogs,
                )
            } else {
                GlassConsoleDrawer(
                    logs = toolchainSetupState.logs,
                    isRunning = toolchainSetupState.isBusy,
                )
            }
        }
    }
}

@Composable
private fun EnvironmentStepActionBar(
    toolchainSetupState: ToolchainSetupState,
    presentation: EnvironmentPresentation,
    onNavigateToProjects: (() -> Unit)?,
    onProceedToLaunchpad: () -> Unit,
    onCheckEnvironment: () -> Unit,
    onReconnect: () -> Unit,
    onPreviewSetup: (SetupStepStage?) -> Unit,
    onRetryStage: (SetupStepStage) -> Unit,
    previewInvokerFocusRequester: FocusRequester? = null,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = Spacing.CardPadding,
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            if (onNavigateToProjects != null) {
                BackToProjectsButton(
                    onNavigateToProjects = onNavigateToProjects,
                    enabled = !toolchainSetupState.isBusy,
                )
            }

            EnvironmentPrimaryActionButton(
                toolchainSetupState = toolchainSetupState,
                previewInvokerFocusRequester = previewInvokerFocusRequester,
                presentation = presentation,
                onNavigateToProjects = onNavigateToProjects,
                onProceedToLaunchpad = onProceedToLaunchpad,
                onCheckEnvironment = onCheckEnvironment,
                onReconnect = onReconnect,
                onPreviewSetup = onPreviewSetup,
                onRetryStage = onRetryStage,
            )
        }
    }
}

@Composable
private fun BackToProjectsButton(onNavigateToProjects: () -> Unit, enabled: Boolean) {
    GlassSecondaryButton(
        onClick = onNavigateToProjects,
        enabled = enabled,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Icon(
                painter = AppIcons.ChevronLeftPainterResource(),
                contentDescription = null,
                modifier = Modifier.size(IconSize.Small),
            )
            Text(
                text = "Back to Projects",
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EnvironmentPrimaryActionButton(
    toolchainSetupState: ToolchainSetupState,
    presentation: EnvironmentPresentation,
    onNavigateToProjects: (() -> Unit)?,
    onProceedToLaunchpad: () -> Unit,
    onCheckEnvironment: () -> Unit,
    onReconnect: () -> Unit,
    onPreviewSetup: (SetupStepStage?) -> Unit,
    onRetryStage: (SetupStepStage) -> Unit,
    previewInvokerFocusRequester: FocusRequester? = null,
) {
    val isBusy = toolchainSetupState.isBusy
    when (presentation.primaryAction) {
        PrimaryAction.CHECKING -> CheckingActionButton()
        PrimaryAction.STAGE_PROGRESS -> StageProgressActionButton()
        PrimaryAction.OPEN_WORKSPACES ->
            OpenWorkspacesActionButton(
                enabled = !isBusy,
                onNavigateToProjects = onNavigateToProjects,
                onProceedToLaunchpad = onProceedToLaunchpad,
            )
        PrimaryAction.RETRY_CONNECTION ->
            RetryConnectionActionButton(
                enabled = !isBusy,
                presentation = presentation,
                onRetryStage = onRetryStage,
            )
        PrimaryAction.RECONNECT ->
            ReconnectActionButton(
                enabled = !isBusy,
                onReconnect = onReconnect,
            )
        PrimaryAction.RETRY_STAGE ->
            RetryStageActionButton(
                enabled = !isBusy,
                presentation = presentation,
                toolchainSetupState = toolchainSetupState,
                onPreviewSetup = onPreviewSetup,
                focusRequester = previewInvokerFocusRequester,
            )
        PrimaryAction.CHECK ->
            CheckActionButton(
                enabled = !isBusy,
                onCheckEnvironment = onCheckEnvironment,
            )
        PrimaryAction.SET_UP ->
            SetUpActionButton(
                enabled = !isBusy,
                onPreviewSetup = onPreviewSetup,
                focusRequester = previewInvokerFocusRequester,
            )
    }
}

@Composable
private fun CheckingActionButton() {
    val isReducedMotion = GlassTheme.reducedMotionPolicy == GlassReducedMotionPolicy.Reduced
    GlassPrimaryButton(onClick = {}, enabled = false) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            if (!isReducedMotion) {
                CircularProgressIndicator(
                    modifier = Modifier.size(IconSize.Small),
                    strokeWidth = StrokeWidth.Hairline,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(text = "Checking…", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StageProgressActionButton() {
    val isReducedMotion = GlassTheme.reducedMotionPolicy == GlassReducedMotionPolicy.Reduced
    GlassPrimaryButton(onClick = {}, enabled = false) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            if (!isReducedMotion) {
                CircularProgressIndicator(
                    modifier = Modifier.size(IconSize.Small),
                    strokeWidth = StrokeWidth.Hairline,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "Running Setup…",
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun OpenWorkspacesActionButton(
    enabled: Boolean,
    onNavigateToProjects: (() -> Unit)?,
    onProceedToLaunchpad: () -> Unit,
) {
    GlassPrimaryButton(
        onClick = { if (onNavigateToProjects != null) onNavigateToProjects() else onProceedToLaunchpad() },
        enabled = enabled,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Text(
                text = if (onNavigateToProjects != null) "Open Workspaces" else "Continue to Launchpad",
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                painter = AppIcons.SendPainterResource(),
                contentDescription = null,
                modifier = Modifier.size(IconSize.Small),
            )
        }
    }
}

@Composable
private fun ReconnectActionButton(enabled: Boolean, onReconnect: () -> Unit) {
    GlassPrimaryButton(
        onClick = onReconnect,
        enabled = enabled,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Icon(
                painter = AppIcons.RefreshPainterResource(),
                contentDescription = null,
                modifier = Modifier.size(IconSize.Small),
            )
            Text(text = "Reconnect", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RetryConnectionActionButton(
    enabled: Boolean,
    presentation: EnvironmentPresentation,
    onRetryStage: (SetupStepStage) -> Unit,
) {
    val offlineStage =
        when (val env = presentation.environmentState) {
            is EnvironmentState.Failed -> env.stage ?: SetupStepStage.SERVER_CONNECTIVITY
            else -> SetupStepStage.SERVER_CONNECTIVITY
        }
    GlassPrimaryButton(
        onClick = { onRetryStage(offlineStage) },
        enabled = enabled,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Icon(
                painter = AppIcons.RefreshPainterResource(),
                contentDescription = null,
                modifier = Modifier.size(IconSize.Small),
            )
            Text(text = "Retry Connection", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RetryStageActionButton(
    enabled: Boolean,
    presentation: EnvironmentPresentation,
    toolchainSetupState: ToolchainSetupState,
    onPreviewSetup: (SetupStepStage?) -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val failedStage =
        when (val env = presentation.environmentState) {
            is EnvironmentState.Failed -> env.stage
            else -> toolchainSetupState.steps.firstOrNull { it.status == StepStatus.FAILED }?.stage
        }
    GlassPrimaryButton(
        onClick = { onPreviewSetup(failedStage) },
        enabled = enabled,
        modifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Icon(
                painter = AppIcons.RefreshPainterResource(),
                contentDescription = null,
                modifier = Modifier.size(IconSize.Small),
            )
            Text(
                text = if (failedStage != null) "Retry ${failedStage.displayName}" else "Retry Stage",
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CheckActionButton(enabled: Boolean, onCheckEnvironment: () -> Unit) {
    GlassPrimaryButton(
        onClick = onCheckEnvironment,
        enabled = enabled,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Icon(
                painter = AppIcons.SparklesPainterResource(),
                contentDescription = null,
                modifier = Modifier.size(IconSize.Small),
            )
            Text(text = "Check again", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SetUpActionButton(
    enabled: Boolean,
    onPreviewSetup: (SetupStepStage?) -> Unit,
    focusRequester: FocusRequester? = null,
) {
    GlassPrimaryButton(
        onClick = { onPreviewSetup(null) },
        enabled = enabled,
        modifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Icon(
                painter = AppIcons.SparklesPainterResource(),
                contentDescription = null,
                modifier = Modifier.size(IconSize.Small),
            )
            Text(text = "Set Up Environment", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun formatRelativeTime(instant: Instant): String {
    val duration = Clock.System.now() - instant
    return when {
        duration.isNegative() || duration.inWholeMinutes < 1 -> "just now"
        duration.inWholeMinutes < 60 -> "${duration.inWholeMinutes}m ago"
        duration.inWholeHours < 24 -> "${duration.inWholeHours}h ago"
        duration.inWholeDays < 7 -> "${duration.inWholeDays}d ago"
        else -> "${duration.inWholeDays / 7}w ago"
    }
}
