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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.kashif_e.backdrop.backdrops.LayerBackdrop
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.actions.GlassSecondaryButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.domain.setup.ToolComponentItem
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.feature.setup.SetupUiActions
import org.ide.lti.feature.setup.SetupUiState
import org.ide.lti.feature.setup.components.DoctorDiagnosticsDrawer
import org.ide.lti.feature.setup.components.EnvironmentSubsystemHeader
import org.ide.lti.feature.setup.components.SetupConsoleDrawer
import org.ide.lti.feature.setup.components.ToolMatrixTable
import org.ide.lti.feature.setup.components.ToolchainPipelineTracker

/**
 * Structured overload for [EnvironmentStepContent] utilizing [SetupUiState] and [SetupUiActions]
 * to prevent parameter sprawl and ensure clean single-responsibility composition.
 */
@Composable
fun EnvironmentStepContent(
    state: SetupUiState,
    actions: SetupUiActions,
    onProceedToLaunchpad: () -> Unit,
    resolvedBackdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    onNavigateToProjects: (() -> Unit)? = null,
    autoRunOnLaunch: Boolean = true,
) {
    EnvironmentStepContent(
        toolchainSetupState = state.toolchainSetupState,
        onNavigateToProjects = onNavigateToProjects,
        onCheckEnvironment = actions.onCheckEnvironment,
        onRunFullSetup = actions.onRunFullSetup,
        onRetrySetupStep = actions.onRetrySetupStep,
        onProceedToLaunchpad = onProceedToLaunchpad,
        resolvedBackdrop = resolvedBackdrop,
        modifier = modifier,
        onAutoRemediateDoctor = actions.onAutoRemediateDoctor,
        onAutoRunEnvironmentSetup = actions.onAutoRunEnvironmentSetup,
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
@Composable
fun EnvironmentStepContent(
    toolchainSetupState: ToolchainSetupState,
    onNavigateToProjects: (() -> Unit)? = null,
    onCheckEnvironment: () -> Unit,
    onRunFullSetup: () -> Unit,
    onRetrySetupStep: () -> Unit,
    onProceedToLaunchpad: () -> Unit,
    resolvedBackdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    onAutoRemediateDoctor: (() -> Unit)? = null,
    onAutoRunEnvironmentSetup: (() -> Unit)? = null,
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
) {
    val glassColors = GlassTheme.colors
    val diagnostics = glassColors.diagnostics

    val currentSearch = toolSearchQuery
    val currentCategory = selectedToolCategory
    val activeToolsList = filteredToolsMatrix

    LaunchedEffect(autoRunOnLaunch) {
        if (autoRunOnLaunch && !toolchainSetupState.isAllReady && !toolchainSetupState.isRunning) {
            onAutoRunEnvironmentSetup?.invoke() ?: onRunFullSetup()
        }
    }

    Column(
        modifier = modifier
            .widthIn(max = ComponentSize.SetupCockpitMaxWidth)
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
        )

        ToolchainPipelineTracker(
            steps = toolchainSetupState.steps,
            onRetryStep = onRetrySetupStep,
            isBusy = toolchainSetupState.isBusy,
            backdrop = resolvedBackdrop,
        )

        DoctorDiagnosticsDrawer(
            diagnosticsList = toolchainSetupState.diagnostics,
            backdrop = resolvedBackdrop,
            onAutoRemediateDoctor = onAutoRemediateDoctor,
        )

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

        SetupConsoleDrawer(
            logs = toolchainSetupState.logs,
            isRunning = toolchainSetupState.isBusy,
            backdrop = resolvedBackdrop,
        )

        // Bottom Navigation Action Bar
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            backdrop = resolvedBackdrop,
            contentPadding = Spacing.CardPadding,
        ) {
            // Responsive Action Bar: SpaceBetween anchoring with auto-wrap to prevent resize overlap
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                if (onNavigateToProjects != null) {
                    GlassSecondaryButton(
                        onClick = onNavigateToProjects,
                        enabled = !toolchainSetupState.isBusy,
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

                if (toolchainSetupState.isAllReady) {
                    GlassPrimaryButton(
                        onClick = onProceedToLaunchpad,
                        enabled = !toolchainSetupState.isBusy,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                        ) {
                            Text(
                                text = "Continue to Launchpad",
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
                } else {
                    GlassPrimaryButton(
                        onClick = onRunFullSetup,
                        enabled = !toolchainSetupState.isBusy,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                        ) {
                            if (toolchainSetupState.isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(IconSize.Small),
                                    strokeWidth = StrokeWidth.Hairline,
                                    color = glassColors.onGlass,
                                )
                                Text(
                                    text = when {
                                        toolchainSetupState.isFixing -> "Remediating Packages..."
                                        toolchainSetupState.isChecking -> "Checking Environment..."
                                        else -> "Running Setup Pipeline..."
                                    },
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            } else {
                                Icon(
                                    painter = AppIcons.SparklesPainterResource(),
                                    contentDescription = null,
                                    modifier = Modifier.size(IconSize.Small),
                                )
                                Text(
                                    text = "Run Full Setup & Build",
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
