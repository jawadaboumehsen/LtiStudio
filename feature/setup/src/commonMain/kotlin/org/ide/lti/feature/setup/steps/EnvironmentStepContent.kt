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
import org.ide.lti.feature.setup.SetupUiActions
import org.ide.lti.feature.setup.SetupUiState
import org.ide.lti.feature.setup.components.DoctorDiagnosticsDrawer
import org.ide.lti.feature.setup.components.EnvironmentSubsystemHeader
import org.ide.lti.core.designsystem.component.progress.GlassConsoleDrawer
import org.ide.lti.feature.setup.components.ToolMatrixTable
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
        isAutoDoctorEnabled = state.isAutoDoctorEnabled,
        onSetAutoDoctorEnabled = actions.onSetAutoDoctorEnabled,
        onResetToolchainCache = actions.onResetToolchainCache,
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
    onRunFullSetup: () -> Unit,
    onRetrySetupStep: () -> Unit,
    onProceedToLaunchpad: () -> Unit,
    resolvedBackdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    onNavigateToProjects: (() -> Unit)? = null,
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
    isAutoDoctorEnabled: Boolean = true,
    onSetAutoDoctorEnabled: (Boolean) -> Unit = {},
    onResetToolchainCache: () -> Unit = {},
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
            isAutoDoctorEnabled = isAutoDoctorEnabled,
            onSetAutoDoctorEnabled = onSetAutoDoctorEnabled,
            onResetToolchainCache = onResetToolchainCache,
        )

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

        ToolchainPipelineTracker(
            steps = toolchainSetupState.steps,
            onRetryStep = onRetrySetupStep,
            isBusy = toolchainSetupState.isBusy,
            backdrop = resolvedBackdrop,
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
                GlassConsoleDrawer(
                    logs = toolchainSetupState.logs,
                    isRunning = toolchainSetupState.isBusy,
                    backdrop = resolvedBackdrop,
                )
            }
        }

        EnvironmentStepActionBar(
            isBusy = toolchainSetupState.isBusy,
            isAllReady = toolchainSetupState.isAllReady,
            isFixing = toolchainSetupState.isFixing,
            isChecking = toolchainSetupState.isChecking,
            onNavigateToProjects = onNavigateToProjects,
            onProceedToLaunchpad = onProceedToLaunchpad,
            onRunFullSetup = onRunFullSetup,
            resolvedBackdrop = resolvedBackdrop,
            glassColors = glassColors
        )
    }
}

@Composable
private fun EnvironmentStepActionBar(
    isBusy: Boolean,
    isAllReady: Boolean,
    isFixing: Boolean,
    isChecking: Boolean,
    onNavigateToProjects: (() -> Unit)?,
    onProceedToLaunchpad: () -> Unit,
    onRunFullSetup: () -> Unit,
    resolvedBackdrop: LayerBackdrop,
    glassColors: org.ide.lti.core.designsystem.theme.GlassColorTokens
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
                GlassSecondaryButton(
                    onClick = onNavigateToProjects,
                    enabled = !isBusy,
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

            if (isAllReady) {
                GlassPrimaryButton(
                    onClick = onProceedToLaunchpad,
                    enabled = !isBusy,
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
                    enabled = !isBusy,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(IconSize.Small),
                                strokeWidth = StrokeWidth.Hairline,
                                color = glassColors.onGlass,
                            )
                            Text(
                                text = when {
                                    isFixing -> "Remediating Packages..."
                                    isChecking -> "Checking Environment..."
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

@Composable
private fun BridgeFailureBanner(
    bridgeStep: org.ide.lti.core.domain.setup.ToolchainSetupStep,
    launcherLogTail: List<String>,
    resolvedBackdrop: org.ide.lti.core.designsystem.component.layout.LayerBackdrop,
    onCheckEnvironment: () -> Unit,
    isBusy: Boolean,
    diagnostics: org.ide.lti.core.designsystem.theme.DiagnosticColors,
    glassColors: org.ide.lti.core.designsystem.theme.GlassColorTokens
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
