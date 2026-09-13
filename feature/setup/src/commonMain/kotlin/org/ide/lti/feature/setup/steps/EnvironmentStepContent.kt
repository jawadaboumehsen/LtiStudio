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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.kashif_e.backdrop.backdrops.LayerBackdrop
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.actions.GlassSecondaryButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.feedback.GlassBanner
import org.ide.lti.core.designsystem.component.feedback.GlassBannerSeverity
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolComponentItem
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.feature.setup.EnvironmentPresentation
import org.ide.lti.feature.setup.EnvironmentState
import org.ide.lti.feature.setup.PrimaryAction
import org.ide.lti.feature.setup.SetupPresentationMapper
import org.ide.lti.feature.setup.SetupUiActions
import org.ide.lti.feature.setup.SetupUiState
import org.ide.lti.core.designsystem.component.progress.GlassConsoleDrawer
import org.ide.lti.feature.setup.SetupOperation
import org.ide.lti.feature.setup.SetupOperationState
import org.ide.lti.feature.setup.components.DoctorDiagnosticsDrawer
import org.ide.lti.feature.setup.components.EnvironmentSubsystemHeader
import org.ide.lti.feature.setup.components.ProvisioningPreviewSheet
import org.ide.lti.feature.setup.components.SetupActivityPanel
import org.ide.lti.feature.setup.components.ToolMatrixTable
import org.ide.lti.feature.setup.components.ToolchainPipelineTracker
import org.ide.lti.feature.setup.SetupActivityState

/**
 * Structured overload for [EnvironmentStepContent] utilizing [SetupUiState] and [SetupUiActions]
 * to prevent parameter sprawl and ensure clean single-responsibility composition.
 */
@Suppress("LongParameterList")
@Composable
fun EnvironmentStepContent(
    state: SetupUiState,
    actions: SetupUiActions,
    resolvedBackdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    onProceedToLaunchpad: () -> Unit = {},
    onNavigateToProjects: (() -> Unit)? = null,
    autoRunOnLaunch: Boolean = true,
    activityState: SetupActivityState? = null,
    activityListState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
) {
    EnvironmentStepContent(
        toolchainSetupState = state.toolchainSetupState,
        environmentPresentation = state.environmentPresentation,
        activeOperation = state.activeOperation,
        onNavigateToProjects = onNavigateToProjects,
        onCheckEnvironment = actions.onCheckEnvironment,
        onRetrySetupStep = actions.onRetrySetupStep,
        onRetryStage = actions.onRetryStage,
        onPreviewSetup = actions.onPreviewSetup,
        onConfirmOperation = actions.onConfirmOperation,
        onDismissPreview = actions.onDismissPreview,
        onProceedToLaunchpad = onProceedToLaunchpad,
        resolvedBackdrop = resolvedBackdrop,
        modifier = modifier,
        onAutoRemediateDoctor = actions.onAutoRemediateDoctor,
        autoRunOnLaunch = autoRunOnLaunch,
        selectedCockpitTab = state.selectedCockpitTab,
        onSelectCockpitTab = actions.onSelectCockpitTab,
        toolSearchQuery = state.toolSearchQuery,
        onToolSearchQueryChange = actions.onToolSearchQueryChange,
        selectedToolCategory = state.selectedToolCategory,
        onSelectToolCategory = actions.onSelectToolCategory,
        filteredToolsMatrix = state.filteredToolsMatrix,
        onTestTool = actions.onTestTool,
        onRecompileTool = actions.onRecompileTool,
        isAutoDoctorEnabled = state.isAutoDoctorEnabled,
        onSetAutoDoctorEnabled = actions.onSetAutoDoctorEnabled,
        onResetToolchainCache = actions.onResetToolchainCache,
        activityState = activityState,
        activityListState = activityListState,
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
 * 4. Tools Matrix Table: 35-tool interactive table with category filter, search, test & rebuild actions.
 * 5. Setup Console Drawer: Real-time daemon execution logs with auto-scroll and run state.
 * 6. Bottom Navigation Action Bar.
 *
 * Adheres strictly to:
 * - Single Responsibility: Coordinates environment setup and toolchain provisioning cockpit.
 * - Design System Guardrails: 100% token usage (zero raw dp or alpha float literals).
 */
@Suppress("LongParameterList")
@Composable
fun EnvironmentStepContent(
    toolchainSetupState: ToolchainSetupState,
    onCheckEnvironment: () -> Unit,
    onRetrySetupStep: () -> Unit,
    onProceedToLaunchpad: () -> Unit,
    resolvedBackdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    environmentPresentation: EnvironmentPresentation? = null,
    activeOperation: SetupOperation? = null,
    onRetryStage: (SetupStepStage) -> Unit = {},
    onPreviewSetup: (SetupStepStage?) -> Unit = {},
    onConfirmOperation: () -> Unit = {},
    onDismissPreview: () -> Unit = {},
    onNavigateToProjects: (() -> Unit)? = null,
    onAutoRemediateDoctor: (() -> Unit)? = null,
    autoRunOnLaunch: Boolean = true,
    selectedCockpitTab: String = "doctor",
    onSelectCockpitTab: (String) -> Unit = {},
    toolSearchQuery: String = "",
    onToolSearchQueryChange: (String) -> Unit = {},
    selectedToolCategory: String = "All",
    onSelectToolCategory: (String) -> Unit = {},
    filteredToolsMatrix: List<ToolComponentItem> = toolchainSetupState.toolsMatrix,
    onTestTool: (String) -> Unit = {},
    onRecompileTool: (String) -> Unit = {},
    isAutoDoctorEnabled: Boolean = true,
    onSetAutoDoctorEnabled: (Boolean) -> Unit = {},
    onResetToolchainCache: () -> Unit = {},
    activityState: SetupActivityState? = null,
    activityListState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
) {
    val glassColors = GlassTheme.colors
    val diagnostics = glassColors.diagnostics

    val currentSearch = toolSearchQuery
    val currentCategory = selectedToolCategory
    val activeToolsList = filteredToolsMatrix
    val presentation = environmentPresentation ?: SetupPresentationMapper.mapEnvironment(
        state = toolchainSetupState,
        activeOperation = activeOperation,
    )

    LaunchedEffect(autoRunOnLaunch) {
        if (autoRunOnLaunch && !toolchainSetupState.isBusy && presentation.primaryAction == PrimaryAction.CHECK) {
            onCheckEnvironment()
        }
    }

    Box(
        modifier = modifier
            .widthIn(max = ComponentSize.SetupCockpitMaxWidth)
            .fillMaxWidth()
            .fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier
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
        // Header Row (FrameLayout / Box Anchored Layout)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Text(
                text = "Environment & Toolchains Cockpit",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = glassColors.onGlass,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Professional diagnostics, WSL 2 subsystem bridge, 35-tool matrix, and build pipeline",
                style = MaterialTheme.typography.bodyMedium,
                color = glassColors.onGlassSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // 1. Interactive Subsystem & Runtime Header
        EnvironmentSubsystemHeader(
            state = toolchainSetupState,
            onCheckEnvironment = onCheckEnvironment,
            onAutoRemediateDoctor = onAutoRemediateDoctor,
            backdrop = resolvedBackdrop,
            isAutoDoctorEnabled = isAutoDoctorEnabled,
            onSetAutoDoctorEnabled = onSetAutoDoctorEnabled,
            onResetToolchainCache = onResetToolchainCache,
        )

        EnvironmentStatusBanners(
            toolchainSetupState = toolchainSetupState,
            resolvedBackdrop = resolvedBackdrop,
            onCheckEnvironment = onCheckEnvironment,
            glassColors = glassColors,
            diagnostics = diagnostics,
        )

        ToolchainPipelineTracker(
            steps = toolchainSetupState.steps,
            onRetryStep = onRetrySetupStep,
            isBusy = toolchainSetupState.isBusy,
            backdrop = resolvedBackdrop,
            onRetryStage = onRetryStage,
        )

        val cockpitTabs = listOf(
            org.ide.lti.core.designsystem.component.navigation.GlassTab(
                id = "doctor",
                title = "Doctor Diagnostics",
            ),
            org.ide.lti.core.designsystem.component.navigation.GlassTab(
                id = "tools",
                title = "Toolchain Matrix",
            ),
            org.ide.lti.core.designsystem.component.navigation.GlassTab(
                id = "console",
                title = "Pipeline Console",
            )
        )

        org.ide.lti.core.designsystem.component.navigation.GlassTabBar(
            tabs = cockpitTabs,
            selectedId = selectedCockpitTab,
            onTabSelected = onSelectCockpitTab,
            modifier = Modifier.fillMaxWidth(),
            backdrop = resolvedBackdrop,
        )

        EnvironmentCockpitTabContent(
            selectedCockpitTab = selectedCockpitTab,
            toolchainSetupState = toolchainSetupState,
            activeToolsList = activeToolsList,
            currentSearch = currentSearch,
            onToolSearchQueryChange = onToolSearchQueryChange,
            currentCategory = currentCategory,
            onSelectToolCategory = onSelectToolCategory,
            onTestTool = onTestTool,
            onRecompileTool = onRecompileTool,
            onAutoRemediateDoctor = onAutoRemediateDoctor,
            activityState = activityState,
            activityListState = activityListState,
            resolvedBackdrop = resolvedBackdrop,
        )

        EnvironmentStepActionBar(
            toolchainSetupState = toolchainSetupState,
            presentation = presentation,
            onNavigateToProjects = onNavigateToProjects,
            onProceedToLaunchpad = onProceedToLaunchpad,
            onCheckEnvironment = onCheckEnvironment,
            onPreviewSetup = onPreviewSetup,
            onRetryStage = onRetryStage,
            resolvedBackdrop = resolvedBackdrop,
            glassColors = glassColors,
        )
    }

        if (activeOperation != null && activeOperation.executionState == SetupOperationState.PREVIEW) {
            ProvisioningPreviewSheet(
                plannedChanges = activeOperation.plannedChanges,
                isAutoDoctorEnabled = isAutoDoctorEnabled,
                onSetAutoDoctorEnabled = onSetAutoDoctorEnabled,
                onConfirm = onConfirmOperation,
                onDismiss = onDismissPreview,
                backdrop = resolvedBackdrop,
                plan = activeOperation.plan,
            )
        }
    }
}

@Composable
private fun EnvironmentStatusBanners(
    toolchainSetupState: ToolchainSetupState,
    resolvedBackdrop: LayerBackdrop,
    onCheckEnvironment: () -> Unit,
    glassColors: org.ide.lti.core.designsystem.theme.GlassColorScheme,
    diagnostics: org.ide.lti.core.designsystem.theme.DiagnosticColors,
) {
    val bridgeStep = toolchainSetupState.steps.firstOrNull { it.stage == SetupStepStage.SERVER_CONNECTIVITY }
    if (bridgeStep?.status == StepStatus.FAILED) {
        val launcherLogTail = toolchainSetupState.logs.takeLast(10)
        BridgeFailureBanner(
            bridgeStep = bridgeStep,
            launcherLogTail = launcherLogTail,
            resolvedBackdrop = resolvedBackdrop,
            onCheckEnvironment = onCheckEnvironment,
            isBusy = toolchainSetupState.isBusy,
            diagnostics = diagnostics,
            glassColors = glassColors
        )
    }

    val sudoDiag = toolchainSetupState.diagnostics.firstOrNull { it.id == "sudo_noninteractive" }
    if (sudoDiag != null && sudoDiag.status != StepStatus.SUCCESS) {
        val cmd = sudoDiag.copyableCommand ?: "echo \"\$USER ALL=(ALL) NOPASSWD: ALL\" | sudo tee /etc/sudoers.d/ltirom"
        GlassBanner(
            severity = GlassBannerSeverity.WARNING,
            title = "Non-Interactive Sudo Configuration Required",
            description = "Automated package remediation requires passwordless sudo without interactive TTY prompts. Run in WSL terminal:\n$cmd",
            backdrop = resolvedBackdrop,
        )
    }

    val activeRunIds = toolchainSetupState.stepRunIds.filterValues { it.isNotBlank() }
    if (activeRunIds.isNotEmpty()) {
        val runSummary = activeRunIds.entries.joinToString(", ") { "${it.key.displayName}: ${it.value}" }
        GlassBanner(
            severity = GlassBannerSeverity.INFO,
            title = "Active Durable Run",
            description = "Executing durable background run ($runSummary). Reattaches automatically across session restarts.",
            backdrop = resolvedBackdrop,
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun EnvironmentCockpitTabContent(
    selectedCockpitTab: String,
    toolchainSetupState: ToolchainSetupState,
    activeToolsList: List<ToolComponentItem>,
    currentSearch: String,
    onToolSearchQueryChange: (String) -> Unit,
    currentCategory: String,
    onSelectToolCategory: (String) -> Unit,
    onTestTool: (String) -> Unit,
    onRecompileTool: (String) -> Unit,
    onAutoRemediateDoctor: (() -> Unit)?,
    activityState: SetupActivityState?,
    activityListState: androidx.compose.foundation.lazy.LazyListState,
    resolvedBackdrop: LayerBackdrop,
) {
    when (selectedCockpitTab) {
        "doctor" -> {
            DoctorDiagnosticsDrawer(
                diagnosticsList = toolchainSetupState.diagnostics,
                backdrop = resolvedBackdrop,
                onAutoRemediateDoctor = onAutoRemediateDoctor,
            )
        }
        "tools" -> {
            ToolMatrixTable(
                tools = activeToolsList,
                searchQuery = currentSearch,
                onSearchQueryChange = onToolSearchQueryChange,
                selectedCategory = currentCategory,
                onSelectCategory = onSelectToolCategory,
                onTestTool = onTestTool,
                onRecompileTool = onRecompileTool,
                backdrop = resolvedBackdrop,
            )
        }
        "console" -> {
            if (activityState != null) {
                SetupActivityPanel(
                    activityState = activityState,
                    title = "Execution Terminal Logs",
                    isRunning = toolchainSetupState.isBusy,
                    backdrop = resolvedBackdrop,
                    listState = activityListState,
                )
            } else {
                GlassConsoleDrawer(
                    logs = toolchainSetupState.logs,
                    isRunning = toolchainSetupState.isBusy,
                    backdrop = resolvedBackdrop,
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
    onPreviewSetup: (SetupStepStage?) -> Unit,
    onRetryStage: (SetupStepStage) -> Unit,
    resolvedBackdrop: LayerBackdrop,
    glassColors: org.ide.lti.core.designsystem.theme.GlassColorScheme,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        backdrop = resolvedBackdrop,
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
                presentation = presentation,
                onNavigateToProjects = onNavigateToProjects,
                onProceedToLaunchpad = onProceedToLaunchpad,
                onCheckEnvironment = onCheckEnvironment,
                onPreviewSetup = onPreviewSetup,
                onRetryStage = onRetryStage,
                glassColors = glassColors,
            )
        }
    }
}

@Composable
private fun BackToProjectsButton(
    onNavigateToProjects: () -> Unit,
    enabled: Boolean,
) {
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
    onPreviewSetup: (SetupStepStage?) -> Unit,
    onRetryStage: (SetupStepStage) -> Unit,
    glassColors: org.ide.lti.core.designsystem.theme.GlassColorScheme,
) {
    val isBusy = toolchainSetupState.isBusy
    when (presentation.primaryAction) {
        PrimaryAction.CHECKING -> CheckingActionButton(glassColors)
        PrimaryAction.STAGE_PROGRESS -> StageProgressActionButton(toolchainSetupState.isFixing, glassColors)
        PrimaryAction.OPEN_WORKSPACES -> OpenWorkspacesActionButton(
            enabled = !isBusy,
            onNavigateToProjects = onNavigateToProjects,
            onProceedToLaunchpad = onProceedToLaunchpad,
        )
        PrimaryAction.RETRY_CONNECTION -> RetryConnectionActionButton(
            enabled = !isBusy,
            presentation = presentation,
            onRetryStage = onRetryStage,
        )
        PrimaryAction.RETRY_STAGE -> RetryStageActionButton(
            enabled = !isBusy,
            presentation = presentation,
            toolchainSetupState = toolchainSetupState,
            onPreviewSetup = onPreviewSetup,
        )
        PrimaryAction.CHECK -> CheckActionButton(
            enabled = !isBusy,
            onCheckEnvironment = onCheckEnvironment,
        )
        PrimaryAction.SET_UP -> SetUpActionButton(
            enabled = !isBusy,
            onPreviewSetup = onPreviewSetup,
        )
    }
}

@Composable
private fun CheckingActionButton(glassColors: org.ide.lti.core.designsystem.theme.GlassColorScheme) {
    GlassPrimaryButton(onClick = {}, enabled = false) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(IconSize.Small),
                strokeWidth = StrokeWidth.Hairline,
                color = glassColors.onGlass,
            )
            Text(text = "Checking...", maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun StageProgressActionButton(
    isFixing: Boolean,
    glassColors: org.ide.lti.core.designsystem.theme.GlassColorScheme,
) {
    GlassPrimaryButton(onClick = {}, enabled = false) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(IconSize.Small),
                strokeWidth = StrokeWidth.Hairline,
                color = glassColors.onGlass,
            )
            Text(
                text = if (isFixing) "Remediating Packages..." else "Running Setup...",
                maxLines = 1,
                softWrap = false,
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
private fun RetryConnectionActionButton(
    enabled: Boolean,
    presentation: EnvironmentPresentation,
    onRetryStage: (SetupStepStage) -> Unit,
) {
    val offlineStage = when (val env = presentation.environmentState) {
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
            Text(text = "Retry Connection", maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun RetryStageActionButton(
    enabled: Boolean,
    presentation: EnvironmentPresentation,
    toolchainSetupState: ToolchainSetupState,
    onPreviewSetup: (SetupStepStage?) -> Unit,
) {
    val failedStage = when (val env = presentation.environmentState) {
        is EnvironmentState.Failed -> env.stage
        else -> toolchainSetupState.steps.firstOrNull { it.status == StepStatus.FAILED }?.stage
    }
    GlassPrimaryButton(
        onClick = { onPreviewSetup(failedStage) },
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
            Text(
                text = if (failedStage != null) "Retry ${failedStage.displayName}" else "Retry Stage",
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun CheckActionButton(
    enabled: Boolean,
    onCheckEnvironment: () -> Unit,
) {
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
            Text(text = "Check Environment", maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun SetUpActionButton(
    enabled: Boolean,
    onPreviewSetup: (SetupStepStage?) -> Unit,
) {
    GlassPrimaryButton(
        onClick = { onPreviewSetup(null) },
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
            Text(text = "Set Up Environment", maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun BridgeFailureBanner(
    bridgeStep: org.ide.lti.core.domain.setup.SetupStepDetail,
    launcherLogTail: List<String>,
    resolvedBackdrop: LayerBackdrop,
    onCheckEnvironment: () -> Unit,
    isBusy: Boolean,
    diagnostics: org.ide.lti.core.designsystem.theme.DiagnosticColors,
    glassColors: org.ide.lti.core.designsystem.theme.GlassColorScheme
) {
    val failureReason = bridgeStep.description
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        backdrop = resolvedBackdrop,
        contentPadding = Spacing.CardPadding,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Icon(
                    painter = AppIcons.InfoPainterResource(),
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.Medium),
                    tint = diagnostics.error,
                )
                Text(
                    text = "Server Daemon Bridge Failure",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = glassColors.onGlass,
                )
                Spacer(Modifier.weight(1f))
                GlassPrimaryButton(
                    onClick = onCheckEnvironment,
                    enabled = !isBusy,
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
                        Text("Retry Bridge", maxLines = 1, softWrap = false)
                    }
                }
            }

            Text(
                text = failureReason,
                style = MaterialTheme.typography.bodyMedium,
                color = diagnostics.error,
            )

            if (launcherLogTail.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(GlassShapes.Small)
                        .background(glassColors.glassSurface)
                        .border(
                            width = StrokeWidth.Standard,
                            color = glassColors.glassBorder,
                            shape = GlassShapes.Small,
                        )
                        .padding(Spacing.Small),
                ) {
                    Text(
                        text = launcherLogTail.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = codeFontFamily(),
                        ),
                        color = glassColors.onGlassMuted,
                    )
                }
            }
        }
    }
}
