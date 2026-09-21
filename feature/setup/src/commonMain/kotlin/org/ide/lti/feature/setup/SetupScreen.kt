/*
 * Copyright 2025-2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.LocalWindowControlActions
import org.ide.lti.core.designsystem.component.actions.WindowControlActions
import org.ide.lti.core.designsystem.component.display.GlassVerticalDivider
import org.ide.lti.core.designsystem.component.feedback.GlassBanner
import org.ide.lti.core.designsystem.component.feedback.GlassBannerAction
import org.ide.lti.core.designsystem.component.feedback.GlassBannerSeverity
import org.ide.lti.core.designsystem.component.feedback.GlassBottomSheetScaffold
import org.ide.lti.core.designsystem.component.layout.GlobalAppDestination
import org.ide.lti.core.designsystem.component.layout.GlobalAppTopBar
import org.ide.lti.core.designsystem.component.layout.IdeAppFrame
import org.ide.lti.core.designsystem.component.layout.IdeNavigatorItem
import org.ide.lti.core.designsystem.component.layout.IdeNavigatorPanel
import org.ide.lti.core.designsystem.component.layout.IdePipelineRail
import org.ide.lti.core.designsystem.component.layout.IdePipelineStageItem
import org.ide.lti.core.designsystem.component.layout.IdeStatusBar
import org.ide.lti.core.designsystem.component.primitives.GlassSurface
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.MotionEasing
import org.ide.lti.core.designsystem.theme.SetupTokens
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.config.AppTheme
import org.ide.lti.core.designsystem.theme.glass.GlassSceneState
import org.ide.lti.core.designsystem.theme.glass.rememberGlassSceneState
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.feature.setup.components.ProvisioningPreviewSheet
import org.ide.lti.feature.setup.steps.DocumentationStepContent
import org.ide.lti.feature.setup.steps.EnvironmentStepContent
import org.ide.lti.feature.setup.steps.ProjectsStepContent
import org.ide.lti.feature.setup.steps.RecoveryStepContent
import org.ide.lti.feature.setup.steps.ToolsStepContent
import org.koin.compose.viewmodel.koinViewModel

/**
 * Top-level entry composable for Setup and Workspaces screen.
 *
 * Gathers Presentation UI state and binds actions to [SetupViewModel].
 */
@Composable
fun SetupScreen(
    onOpenWorkspace: (Workspace) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onCreateWorkspace: () -> Unit = {},
    onOpenConfigurations: () -> Unit = {},
    onOpenManageTargets: () -> Unit = onOpenConfigurations,
    onOpenHelp: () -> Unit = {},
    backdrop: GlassSceneState? = null,
    viewModel: SetupViewModel = koinViewModel(),
) {
    val toolchainSetupState by viewModel.toolchainSetupState.collectAsState()
    val recentProjects by viewModel.recentProjects.collectAsState()
    val filteredProjects by viewModel.filteredRecentProjects.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedWorkspaceId by viewModel.selectedWorkspaceId.collectAsState()
    val selectedCockpitTab by viewModel.selectedCockpitTab.collectAsState()
    val toolSearchQuery by viewModel.toolSearchQuery.collectAsState()
    val selectedToolCategory by viewModel.selectedToolCategory.collectAsState()
    val filteredToolsMatrix by viewModel.filteredToolsMatrix.collectAsState()
    val isAutoDoctorEnabled by viewModel.isAutoDoctorEnabled.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val environmentNotice by viewModel.environmentNotice.collectAsState()
    val selectedTarget by viewModel.selectedTarget.collectAsState()
    val availableTargets by viewModel.availableTargets.collectAsState()
    val environmentReadiness by viewModel.environmentReadiness.collectAsState()
    val activeOperation by viewModel.activeOperation.collectAsState()
    val environmentPresentation by viewModel.environmentPresentation.collectAsState()
    val workspaceError by viewModel.workspaceError.collectAsState()

    var activeTab by remember { mutableStateOf(SetupTab.LAUNCHPAD) }
    var isHelpSheetOpen by remember { mutableStateOf(false) }

    val uiState = SetupUiState(
        toolchainSetupState = toolchainSetupState,
        selectedTarget = selectedTarget,
        availableTargets = availableTargets,
        recentProjects = recentProjects,
        filteredProjects = filteredProjects,
        searchQuery = searchQuery,
        selectedWorkspaceId = selectedWorkspaceId,
        selectedCockpitTab = selectedCockpitTab,
        toolSearchQuery = toolSearchQuery,
        selectedToolCategory = selectedToolCategory,
        filteredToolsMatrix = filteredToolsMatrix,
        isAutoDoctorEnabled = isAutoDoctorEnabled,
        selectedTab = activeTab,
        selectedDestination = SetupDestination.fromId(activeTab.id),
        environmentNotice = environmentNotice,
        environmentReadiness = environmentReadiness,
        activeOperation = activeOperation,
        environmentPresentation = environmentPresentation,
        workspaceError = workspaceError,
    )

    val uiActions = SetupUiActions(
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onRemoveRecentProject = viewModel::removeRecentProject,
        onOpenFolder = { viewModel.openFolder(onOpenWorkspace) },
        onRecentProjectClick = { viewModel.openRecentProject(it, onOpenWorkspace) },
        onSelectWorkspace = viewModel::selectWorkspace,
        onCheckEnvironment = viewModel::checkEnvironmentStatus,
        onReconnect = viewModel::reconnectPendingAttempt,
        onRetrySetupStep = viewModel::retrySetupStep,
        onProvisionAvbKey = viewModel::onProvisionAvbKey,
        onSelectTarget = viewModel::selectTarget,
        onAutoRemediateDoctor = viewModel::autoRemediateDoctorIssues,
        onSelectCockpitTab = viewModel::selectCockpitTab,
        onToolSearchQueryChange = viewModel::onToolSearchQueryChange,
        onSelectToolCategory = viewModel::selectToolCategory,
        onTestTool = viewModel::testTool,
        onRecompileTool = viewModel::recompileTool,
        onNavigateToEnvironment = {
            activeTab = SetupTab.ENVIRONMENT
            viewModel.navigateToEnvironment()
        },
        onOpenSettings = onOpenSettings,
        onSelectTab = { tab ->
            activeTab = tab
            viewModel.selectTab(tab)
        },
        onSelectDestination = { dest ->
            val tab = when (dest) {
                SetupDestination.WORKSPACES -> SetupTab.PROJECTS
                SetupDestination.ENVIRONMENT -> SetupTab.ENVIRONMENT
                SetupDestination.TOOLS -> SetupTab.TOOLS
                SetupDestination.RECOVERY -> SetupTab.RECOVERY
            }
            activeTab = tab
            viewModel.selectTab(tab)
        },
        onDismissEnvironmentNotice = viewModel::dismissEnvironmentNotice,
        onSetAutoDoctorEnabled = viewModel::setAutoDoctorEnabled,
        onResetToolchainCache = viewModel::previewResetToolchainCache,
        onCreateWorkspace = onCreateWorkspace,
        onOpenManageTargets = onOpenManageTargets,
        onOpenHelp = {
            onOpenHelp()
            isHelpSheetOpen = true
        },
        onPreviewSetup = viewModel::previewSetup,
        onConfirmOperation = viewModel::confirmOperation,
        onDismissPreview = viewModel::dismissPreview,
        onRetryStage = viewModel::retryStage,
        onDismissWorkspaceError = viewModel::dismissWorkspaceError,
    )

    SetupPerformanceScenarioHook(
        modifier = modifier,
        onOpenWorkspace = onOpenWorkspace,
        onOpenSettings = onOpenSettings,
        onCreateWorkspace = onCreateWorkspace,
        onOpenConfigurations = onOpenConfigurations,
        onOpenManageTargets = onOpenManageTargets,
        onOpenHelp = onOpenHelp,
        backdrop = backdrop,
        viewModel = viewModel,
    ) {
        SetupScreenContent(
            state = uiState,
            actions = uiActions,
            onOpenConfigurations = onOpenConfigurations,
            backdrop = backdrop,
            modifier = modifier,
            isHelpSheetOpen = isHelpSheetOpen,
            onDismissHelpSheet = { isHelpSheetOpen = false },
            toolBrowserState = viewModel.toolBrowserState,
            activityState = viewModel.setupActivityState,
        )
    }
}

/**
 * Modern Clean Architecture layout composable for Setup & Workspaces screen.
 *
 * Encapsulates state and actions into cohesive [SetupUiState] and [SetupUiActions] models.
 * Implements adaptive sidebar and gutters (SetupTokens.SidebarWidthWide / Compact, GutterWide / Compact).
 */
@Composable
fun SetupScreenContent(
    state: SetupUiState,
    actions: SetupUiActions,
    modifier: Modifier = Modifier,
    onOpenConfigurations: () -> Unit = {},
    windowControls: WindowControlActions? = null,
    backdrop: GlassSceneState? = null,
    initialTab: SetupTab? = null,
    isHelpSheetOpen: Boolean = false,
    onDismissHelpSheet: () -> Unit = {},
    toolBrowserState: ToolBrowserState? = null,
    activityState: SetupActivityState? = null,
    workspacesListState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    toolsScrollState: androidx.compose.foundation.ScrollState = androidx.compose.foundation.rememberScrollState(),
    activityListState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
) {
    val colors = GlassTheme.ideShellColors
    val isBlueGlass = GlassTheme.appTheme == AppTheme.Blue && GlassTheme.effectsEnabled
    val ambientBackdrop = (backdrop as? GlassSceneState) ?: (GlassTheme.scene as? GlassSceneState)
    val resolvedBackdrop = if (isBlueGlass) {
        rememberGlassSceneState()
    } else {
        ambientBackdrop ?: rememberGlassSceneState()
    }
    var localSelectedTab by remember { mutableStateOf(initialTab ?: state.selectedTab) }
    val effectiveTab = initialTab?.let { localSelectedTab } ?: state.selectedTab
    var internalHelpSheetOpen by remember { mutableStateOf(isHelpSheetOpen) }

    val rootFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
    val toolSearchFocusRequester = remember { FocusRequester() }
    val helpButtonFocusRequester = remember { FocusRequester() }
    val previewInvokerFocusRequester = remember { FocusRequester() }
    var lastInvokingFocusRequester by remember { mutableStateOf<FocusRequester?>(null) }
    var pendingSearchFocus by remember { mutableStateOf(false) }

    val isPreviewOpen = state.activeOperation?.executionState == SetupOperationState.PREVIEW

    LaunchedEffect(Unit) {
        rootFocusRequester.requestFocus()
    }

    LaunchedEffect(isPreviewOpen) {
        // The Set Up / Retry Stage buttons that open the preview share previewInvokerFocusRequester
        // (attached in EnvironmentStepContent); record it as the invoker so Escape/Cancel/Confirm
        // restore focus to the button that actually opened the modal, not just the root.
        if (isPreviewOpen) {
            lastInvokingFocusRequester = previewInvokerFocusRequester
        }
    }

    LaunchedEffect(effectiveTab) {
        // ProjectsStepContent and ToolsStepContent own the actual requestFocus() call on mount
        // via focusSearchOnMount; this only clears the one-shot intent once observed.
        if (effectiveTab == SetupTab.PROJECTS || effectiveTab == SetupTab.TOOLS) {
            pendingSearchFocus = false
        }
    }

    val onTabChange: (SetupTab) -> Unit = { newTab ->
        if (initialTab != null) {
            localSelectedTab = newTab
        }
        actions.onSelectTab(newTab)
    }

    val currentRailId = when (effectiveTab) {
        SetupTab.LAUNCHPAD, SetupTab.PROJECTS -> "machine_setup"
        SetupTab.ENVIRONMENT -> "environment_overview"
        SetupTab.TOOLS -> "tool_packages"
        SetupTab.RECOVERY -> "diagnostics_recovery"
    }

    val setupRailStages = remember {
        listOf(
            IdePipelineStageItem(
                id = "machine_setup",
                label = "Machine setup",
                icon = "machine_setup",
            ),
            IdePipelineStageItem(
                id = "environment_overview",
                label = "Environment overview",
                icon = "environment_overview",
            ),
            IdePipelineStageItem(
                id = "wsl_runtime",
                label = "WSL runtime",
                icon = "wsl_runtime",
            ),
            IdePipelineStageItem(
                id = "build_service",
                label = "Build service",
                icon = "build_service",
            ),
            IdePipelineStageItem(
                id = "tool_packages",
                label = "Tool packages",
                icon = "tool_packages",
            ),
            IdePipelineStageItem(
                id = "diagnostics_recovery",
                label = "Recovery",
                icon = "diagnostics_recovery",
            ),
        )
    }

    val isToolsActive = effectiveTab == SetupTab.TOOLS
    val density = LocalDensity.current
    var isCompact by remember { mutableStateOf(false) }

    val toolPackagesNavItems = remember {
        listOf(
            IdeNavigatorItem(id = "all_tools", label = "All tools", icon = "all_tools"),
            IdeNavigatorItem(id = "firmware", label = "Firmware", icon = "firmware"),
            IdeNavigatorItem(id = "filesystems", label = "Filesystems", icon = "filesystems"),
            IdeNavigatorItem(id = "signing", label = "Signing", icon = "signing"),
        )
    }

    val selectedNavToolId = when (state.selectedToolCategory.lowercase()) {
        "all" -> "all_tools"
        "boot & kernel", "dynamic partitions", "firmware" -> "firmware"
        "filesystem & images", "filesystems" -> "filesystems"
        "signing & security", "signing" -> "signing"
        else -> "all_tools"
    }

    IdeAppFrame(
        modifier = modifier
            .fillMaxSize()
            .background(colors.appBackground)
            .widthIn(min = SetupTokens.MinWindowWidth)
            .onSizeChanged { size ->
                val widthDp = with(density) { size.width.toDp() }
                isCompact = widthDp < SetupTokens.CompactBreakpoint
            }
            .focusRequester(rootFocusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    val isPreviewOpen = state.activeOperation?.executionState == SetupOperationState.PREVIEW
                    val showHelp = isHelpSheetOpen || internalHelpSheetOpen
                    val isModalOpen = isPreviewOpen || showHelp

                    if (isModalOpen && event.isCtrlPressed) {
                        when (event.key) {
                            Key.N, Key.O, Key.F -> return@onKeyEvent true
                        }
                    }

                    if (event.isCtrlPressed) {
                        when (event.key) {
                            Key.N -> {
                                actions.onCreateWorkspace()
                                true
                            }
                            Key.O -> {
                                actions.onOpenFolder()
                                true
                            }
                            Key.F -> {
                                when (effectiveTab) {
                                    SetupTab.PROJECTS, SetupTab.LAUNCHPAD -> {
                                        searchFocusRequester.requestFocus()
                                    }
                                    SetupTab.TOOLS -> {
                                        toolSearchFocusRequester.requestFocus()
                                    }
                                    else -> {
                                        pendingSearchFocus = true
                                        onTabChange(SetupTab.PROJECTS)
                                    }
                                }
                                true
                            }
                            else -> false
                        }
                    } else if (event.key == Key.Escape) {
                        if (isPreviewOpen) {
                            actions.onDismissPreview()
                            (lastInvokingFocusRequester ?: rootFocusRequester).requestFocus()
                            true
                        } else if (showHelp) {
                            internalHelpSheetOpen = false
                            onDismissHelpSheet()
                            (lastInvokingFocusRequester ?: rootFocusRequester).requestFocus()
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                } else {
                    false
                }
            },
        topBar = {
            GlobalAppTopBar(
                selectedDestination = GlobalAppDestination.Setup,
                onSelectDestination = { destination ->
                    when (destination) {
                        GlobalAppDestination.Setup -> onTabChange(SetupTab.LAUNCHPAD)
                        GlobalAppDestination.Workspace -> actions.onOpenFolder()
                        GlobalAppDestination.Settings -> actions.onOpenSettings()
                    }
                },
                contextLabel = "",
                actions = {},
                windowControlActions = windowControls ?: LocalWindowControlActions.current,
            )
        },
        pipelineRail = {
            IdePipelineRail(
                stages = setupRailStages,
                selectedStageId = currentRailId,
                onSelectStage = { railId ->
                    when (railId) {
                        "machine_setup" -> onTabChange(SetupTab.LAUNCHPAD)
                        "environment_overview" -> onTabChange(SetupTab.ENVIRONMENT)
                        "wsl_runtime" -> onTabChange(SetupTab.ENVIRONMENT)
                        "build_service" -> onTabChange(SetupTab.ENVIRONMENT)
                        "tool_packages" -> onTabChange(SetupTab.TOOLS)
                        "diagnostics_recovery" -> onTabChange(SetupTab.RECOVERY)
                    }
                },
                header = {
                    Text(
                        text = "Machine environment",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textDim,
                        modifier = Modifier.padding(horizontal = Spacing.Small, vertical = Spacing.Small),
                    )
                },
                colors = colors,
            )
        },
        navigator = if (isToolsActive) {
            {
                IdeNavigatorPanel(
                    headerTitle = "TOOL PACKAGES",
                    headerSubtitle = null,
                    items = toolPackagesNavItems,
                    selectedItemId = selectedNavToolId,
                    onSelectItem = { navId ->
                        val cat = when (navId) {
                            "all_tools" -> "All"
                            "firmware" -> "Boot & Kernel"
                            "filesystems" -> "Filesystem & Images"
                            "signing" -> "Signing & Security"
                            else -> "All"
                        }
                        actions.onSelectToolCategory(cat)
                    },
                    colors = colors,
                )
            }
        } else {
            null
        },
        statusBar = {
            IdeStatusBar(
                modifier = Modifier.fillMaxWidth(),
                colors = colors,
                leadingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = colors.textDim,
                            modifier = Modifier.size(IconSize.Small),
                        )
                        val receipt = state.activeOperation?.operationId
                            ?: state.toolchainSetupState.pendingAttemptId
                            ?: "setup-042"
                        Text(
                            text = "Machine environment setup • Setup receipt: $receipt",
                            fontSize = FontSize.Micro,
                            color = colors.textMuted,
                            fontFamily = GlassFontFamily.ide(),
                        )
                    }
                },
                trailingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(IconSize.Indicator)
                                .clip(GlassShapes.Capsule)
                                .background(colors.warningAmber),
                        )
                        Text(
                            text = "Service disconnected",
                            fontSize = FontSize.Micro,
                            color = colors.textMuted,
                            fontFamily = GlassFontFamily.ide(),
                        )
                        Text(
                            text = "No active maintenance",
                            fontSize = FontSize.Micro,
                            color = colors.textDim,
                            fontFamily = GlassFontFamily.ide(),
                        )
                    }
                },
            )
        },
        colors = colors,
        content = {
            Column(modifier = Modifier.fillMaxSize()) {
                if (state.environmentNotice != null) {
                    GlassBanner(
                        severity = GlassBannerSeverity.WARNING,
                        title = "Environment Verification Required",
                        description = state.environmentNotice,
                        primaryAction = GlassBannerAction(
                            label = "Verify Now",
                            onClick = actions.onNavigateToEnvironment,
                        ),
                        secondaryAction = GlassBannerAction(
                            label = "Dismiss",
                            onClick = actions.onDismissEnvironmentNotice,
                        ),
                        backdrop = resolvedBackdrop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
                    )
                }

                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val fadeDuration = SetupTokens.DurationFadeMs
                    val feedbackDuration = SetupTokens.DurationFeedbackMs

                    AnimatedContent(
                        targetState = effectiveTab,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(durationMillis = fadeDuration, easing = MotionEasing.Standard))
                                .togetherWith(
                                    fadeOut(
                                        animationSpec = tween(
                                            durationMillis = feedbackDuration,
                                            easing = MotionEasing.Standard,
                                        ),
                                    ),
                                )
                        },
                        label = "SetupDestinationTransition",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(
                                if (effectiveTab == SetupTab.RECOVERY) {
                                    Modifier
                                } else {
                                    Modifier.widthIn(max = SetupTokens.ContentMaxWidth)
                                },
                            ),
                        contentAlignment = Alignment.TopCenter,
                    ) { currentTab ->
                        when (currentTab) {
                            SetupTab.ENVIRONMENT -> {
                                EnvironmentStepContent(
                                    state = state,
                                    actions = actions,
                                    onNavigateToProjects = { onTabChange(SetupTab.PROJECTS) },
                                    resolvedBackdrop = resolvedBackdrop,
                                    activityState = activityState,
                                    activityListState = activityListState,
                                    previewInvokerFocusRequester = previewInvokerFocusRequester,
                                )
                            }
                            SetupTab.PROJECTS -> {
                                ProjectsStepContent(
                                    state = state,
                                    actions = actions,
                                    resolvedBackdrop = resolvedBackdrop,
                                    onNavigateToEnvironment = { onTabChange(SetupTab.ENVIRONMENT) },
                                    searchFocusRequester = searchFocusRequester,
                                    focusSearchOnMount = pendingSearchFocus,
                                    lazyListState = workspacesListState,
                                )
                            }
                            SetupTab.LAUNCHPAD -> SetupOverviewContent(
                                state = state,
                                onOpenEnvironment = { onTabChange(SetupTab.ENVIRONMENT) },
                            )
                            SetupTab.TOOLS -> {
                                ToolsStepContent(
                                    state = state,
                                    actions = actions,
                                    resolvedBackdrop = resolvedBackdrop,
                                    toolBrowserState = toolBrowserState,
                                    activityState = activityState,
                                    searchFocusRequester = toolSearchFocusRequester,
                                    focusSearchOnMount = pendingSearchFocus,
                                    scrollState = toolsScrollState,
                                )
                            }
                            SetupTab.RECOVERY -> {
                                RecoveryStepContent(
                                    state = state,
                                    actions = actions,
                                    resolvedBackdrop = resolvedBackdrop,
                                    activityState = activityState,
                                    onOpenHelp = actions.onOpenHelp,
                                )
                            }
                        }
                    }

                    if (!isCompact && effectiveTab == SetupTab.ENVIRONMENT) {
                        GlassVerticalDivider(specular = true)
                        SetupEnvironmentSummary(
                            state = state,
                            modifier = Modifier
                                .widthIn(min = SetupTokens.SidebarWidthCompact, max = SetupTokens.SidebarWidthWide)
                                .padding(Spacing.Medium),
                        )
                    }
                }
            }
        },
    )

    val showHelp = isHelpSheetOpen || internalHelpSheetOpen
    GlassBottomSheetScaffold(
        visible = showHelp,
        onDismissRequest = {
            internalHelpSheetOpen = false
            onDismissHelpSheet()
            (lastInvokingFocusRequester ?: rootFocusRequester).requestFocus()
        },
        modifier = Modifier.semantics {
            paneTitle = "Help & Documentation"
        },
        backdrop = resolvedBackdrop,
    ) {
        DocumentationStepContent(resolvedBackdrop = resolvedBackdrop)
    }

    val activeOperationForPreview = state.activeOperation
    if (isPreviewOpen && activeOperationForPreview != null) {
        ProvisioningPreviewSheet(
            plannedChanges = activeOperationForPreview.plannedChanges,
            isAutoDoctorEnabled = state.isAutoDoctorEnabled,
            onSetAutoDoctorEnabled = actions.onSetAutoDoctorEnabled,
            onConfirm = {
                actions.onConfirmOperation()
                (lastInvokingFocusRequester ?: rootFocusRequester).requestFocus()
            },
            onDismiss = {
                actions.onDismissPreview()
                (lastInvokingFocusRequester ?: rootFocusRequester).requestFocus()
            },
            backdrop = resolvedBackdrop,
            plan = activeOperationForPreview.plan,
            contextualError = activeOperationForPreview.failure,
        )
    }
}

@Composable
private fun SetupOverviewContent(state: SetupUiState, onOpenEnvironment: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GlassTheme.colors
    val toolchain = state.toolchainSetupState
    val readyCount = toolchain.steps.count { step -> step.status == StepStatus.SUCCESS }
    val wslStatus = setupStatusLabel(
        toolchain.steps.firstOrNull { step ->
            step.stage == SetupStepStage.WSL_DETECTION
        }?.status,
    )
    val serviceStatus = setupStatusLabel(
        toolchain.steps.firstOrNull { step ->
            step.stage == SetupStepStage.SERVER_CONNECTIVITY
        }?.status,
    )
    val toolsStatus = setupStatusLabel(
        toolchain.steps.firstOrNull { step ->
            step.stage == SetupStepStage.TOOLCHAIN_COMPILATION
        }?.status,
    )

    Column(modifier = modifier.fillMaxSize().padding(top = Spacing.Large)) {
        Text(
            text = "Set up your build environment",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.onGlass,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Prepare this machine before configuring your ROM.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onGlassSecondary,
            modifier = Modifier.padding(top = Spacing.ExtraSmall, bottom = Spacing.Medium),
        )
        GlassSurface(modifier = Modifier.fillMaxWidth(), shape = GlassShapes.Medium) {
            Column(modifier = Modifier.padding(Spacing.PanelPadding)) {
                Text(
                    "$readyCount of ${toolchain.steps.size} checks ready",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onGlass,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Review the local services required to build and sign a ROM.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onGlassSecondary,
                    modifier = Modifier.padding(top = Spacing.ExtraSmall, bottom = Spacing.Small),
                )
                SetupOverviewReadinessRow("1", "Host system", "Detected", colors.onGlassSecondary)
                SetupOverviewReadinessRow("2", "WSL runtime", wslStatus, colors.onGlassSecondary)
                SetupOverviewReadinessRow("3", "Build service", serviceStatus, colors.onGlass)
                SetupOverviewReadinessRow("4", "Tool packages", toolsStatus, colors.onGlassSecondary)
            }
        }
        GlassSurface(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.Medium),
            shape = GlassShapes.Medium,
        ) {
            Column(modifier = Modifier.padding(Spacing.PanelPadding)) {
                Text(
                    "Next step",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onGlass,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Verify the local build service, then check pinned tool packages.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onGlassSecondary,
                    modifier = Modifier.padding(top = Spacing.ExtraSmall),
                )
                Text(
                    "Open Build service",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.glassAccent,
                    modifier = Modifier.padding(top = Spacing.Small).clickable(onClick = onOpenEnvironment),
                )
            }
        }
        GlassSurface(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.Medium),
            shape = GlassShapes.Medium,
        ) {
            Column(modifier = Modifier.padding(Spacing.PanelPadding)) {
                Text(
                    "Setup activity",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onGlass,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (toolchain.steps.isEmpty()) {
                        "No environment checks have run yet."
                    } else {
                        "${toolchain.steps.size} environment checks are available for review."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onGlassSecondary,
                    modifier = Modifier.padding(top = Spacing.ExtraSmall),
                )
            }
        }
    }
}

@Composable
private fun SetupOverviewReadinessRow(
    index: String,
    title: String,
    status: String,
    titleColor: androidx.compose.ui.graphics.Color,
) {
    val colors = GlassTheme.colors
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.ExtraSmall)) {
        Text(index, style = MaterialTheme.typography.labelMedium, color = colors.onGlassMuted)
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = titleColor,
            modifier = Modifier.padding(start = Spacing.Small).weight(1f),
        )
        Text(status, style = MaterialTheme.typography.labelMedium, color = colors.onGlassSecondary)
    }
}

private fun setupStatusLabel(status: StepStatus?): String = when (status) {
    StepStatus.SUCCESS -> "Ready"
    StepStatus.RUNNING -> "Checking"
    StepStatus.FAILED -> "Needs attention"
    else -> "Not checked"
}

/** Persistent right-hand context panel matching the Setup dashboard reference. */
@Composable
private fun SetupEnvironmentSummary(state: SetupUiState, modifier: Modifier = Modifier) {
    val colors = GlassTheme.colors
    val toolchain = state.toolchainSetupState
    val serviceStatus = when {
        toolchain.isBusy -> "Checking"
        toolchain.steps.any { it.status == StepStatus.FAILED } -> "Needs attention"
        toolchain.isAllReady -> "Ready"
        else -> "Not checked"
    }
    val readiness = if (toolchain.isAllReady) "Ready" else "Not ready"

    Column(modifier = modifier) {
        GlassSurface(modifier = Modifier.fillMaxWidth(), shape = GlassShapes.Medium) {
            Column(modifier = Modifier.padding(Spacing.PanelPadding)) {
                Text("Environment summary", color = colors.onGlass)
                Text("Platform", color = colors.onGlassSecondary)
                Text("Windows", color = colors.onGlass)
                Text("Runtime", color = colors.onGlassSecondary)
                Text("WSL 2", color = colors.onGlass)
                Text("Build service", color = colors.onGlassSecondary)
                Text(serviceStatus, color = colors.onGlass)
                Text("Tool readiness", color = colors.onGlassSecondary)
                Text(readiness, color = colors.onGlass)
            }
        }
        GlassSurface(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.Medium),
            shape = GlassShapes.Medium,
        ) {
            Column(modifier = Modifier.padding(Spacing.PanelPadding)) {
                Text("Setup does not start a ROM build.", color = colors.onGlass)
                Text(
                    "This screen prepares your machine before you configure a ROM.",
                    color = colors.onGlassSecondary,
                )
            }
        }
    }
}
