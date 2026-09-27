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
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material3.HorizontalDivider
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
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import org.ide.lti.core.designsystem.component.actions.LocalWindowControlActions
import org.ide.lti.core.designsystem.component.actions.WindowControlActions
import org.ide.lti.core.designsystem.component.display.GlassVerticalDivider
import org.ide.lti.core.designsystem.component.display.IdeStatusSeverity
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
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.MotionEasing
import org.ide.lti.core.designsystem.theme.SetupTokens
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.feature.setup.components.ProvisioningPreviewSheet
import org.ide.lti.feature.setup.components.TerminalHandoffSheet
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
    initialTab: SetupTab? = null,
    viewModel: SetupViewModel = koinViewModel(),
) {
    val toolchainSetupState by viewModel.toolchainSetupState.collectAsState()
    val recentProjects by viewModel.recentProjects.collectAsState()
    val filteredProjects by viewModel.filteredRecentProjects.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedWorkspaceId by viewModel.selectedWorkspaceId.collectAsState()
    val selectedCockpitTab by viewModel.selectedCockpitTab.collectAsState()
    val toolSearchQuery by viewModel.toolSearchQuery.collectAsState()
    val toolSelection by viewModel.toolSelection.collectAsState()
    val filteredToolsMatrix by viewModel.filteredToolsMatrix.collectAsState()
    val isAutoDoctorEnabled by viewModel.isAutoDoctorEnabled.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val environmentNotice by viewModel.environmentNotice.collectAsState()
    val selectedTarget by viewModel.selectedTarget.collectAsState()
    val availableTargets by viewModel.availableTargets.collectAsState()
    val environmentReadiness by viewModel.environmentReadiness.collectAsState()
    val activeOperation by viewModel.activeOperation.collectAsState()
    val workspaceError by viewModel.workspaceError.collectAsState()
    val pendingWorkspaceRequest by viewModel.pendingWorkspaceRequest.collectAsState()
    val isHandoffSheetVisible by viewModel.isHandoffSheetVisible.collectAsState()

    var activeTab by remember(initialTab) {
        mutableStateOf(initialTab ?: SetupTab.PROJECTS)
    }
    var isHelpSheetOpen by remember { mutableStateOf(false) }

    val handleOpenWorkspace: (Workspace) -> Unit = { workspace ->
        onOpenWorkspace(workspace)
    }

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
        toolSelection = toolSelection,
        filteredToolsMatrix = filteredToolsMatrix,
        isAutoDoctorEnabled = isAutoDoctorEnabled,
        selectedTab = activeTab,
        selectedDestination = SetupDestination.fromId(activeTab.id),
        environmentNotice = environmentNotice,
        environmentReadiness = environmentReadiness,
        activeOperation = activeOperation,
        workspaceError = workspaceError,
        pendingWorkspaceRequest = pendingWorkspaceRequest,
        isHandoffSheetVisible = isHandoffSheetVisible,
        isToolchainBound = viewModel.isToolchainBound,
    )

    val uiActions = SetupUiActions(
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onRemoveRecentProject = viewModel::removeRecentProject,
        onOpenFolder = { viewModel.openFolder(handleOpenWorkspace) },
        onRecentProjectClick = { viewModel.openRecentProject(it, handleOpenWorkspace) },
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
        onSelectToolPackage = viewModel::selectToolPackage,
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
        onLaunchStudio = {
            val firstProject = recentProjects.firstOrNull()
            if (firstProject != null) {
                viewModel.openRecentProject(firstProject, handleOpenWorkspace)
            } else {
                activeTab = SetupTab.PROJECTS
            }
        },
        onCancelPendingWorkspaceRequest = viewModel::cancelPendingWorkspaceRequest,
        onResumeAwaitingOperation = viewModel::resumeAwaitingOperation,
        onDismissHandoff = viewModel::dismissHandoff,
        onAbandonAwaitingOperation = viewModel::abandonAwaitingOperation,
        onShowHandoffSheet = viewModel::showHandoffSheet,
        onOpenTerminal = viewModel::openTerminal,
    )

    SetupPerformanceScenarioHook(
        modifier = modifier,
        onOpenWorkspace = handleOpenWorkspace,
        onOpenSettings = onOpenSettings,
        onCreateWorkspace = onCreateWorkspace,
        onOpenConfigurations = onOpenConfigurations,
        onOpenManageTargets = onOpenManageTargets,
        onOpenHelp = onOpenHelp,
        viewModel = viewModel,
    ) {
        SetupScreenContent(
            state = uiState,
            actions = uiActions,
            onOpenConfigurations = onOpenConfigurations,
            modifier = Modifier,
            isHelpSheetOpen = isHelpSheetOpen,
            onDismissHelpSheet = { isHelpSheetOpen = false },
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
    initialTab: SetupTab? = null,
    isHelpSheetOpen: Boolean = false,
    onDismissHelpSheet: () -> Unit = {},
    activityState: SetupActivityState? = null,
    workspacesListState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    toolsScrollState: androidx.compose.foundation.ScrollState = androidx.compose.foundation.rememberScrollState(),
    activityListState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
) {
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

    val currentRailId = effectiveTab.id

    val setupRailStages = remember {
        listOf(
            IdePipelineStageItem(
                id = SetupTab.PROJECTS.id,
                label = "Projects",
                icon = "projects",
            ),
            IdePipelineStageItem(
                id = SetupTab.ENVIRONMENT.id,
                label = "Environment",
                icon = "environment",
            ),
            IdePipelineStageItem(
                id = SetupTab.TOOLS.id,
                label = "Tools",
                icon = "tools",
            ),
            IdePipelineStageItem(
                id = SetupTab.RECOVERY.id,
                label = "Recovery",
                icon = "recovery",
            ),
        )
    }

    val isToolsActive = effectiveTab == SetupTab.TOOLS
    val density = LocalDensity.current
    var isCompact by remember { mutableStateOf(false) }

    val toolPackagesNavItems = remember {
        ToolNavPackage.entries.map { pkg ->
            IdeNavigatorItem(id = pkg.id, label = pkg.label, icon = pkg.icon)
        }
    }

    val selectedNavToolId = state.toolSelection.navPackage.id

    IdeAppFrame(
        modifier = modifier
            .fillMaxSize()
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
                                    SetupTab.PROJECTS -> {
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
                        GlobalAppDestination.Setup -> onTabChange(SetupTab.PROJECTS)
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
                    SetupTab.find(railId)?.let(onTabChange)
                },
                header = {
                    Text(
                        text = "Machine environment",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.Small, vertical = Spacing.Small),
                    )
                },
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
                        ToolNavPackage.find(navId)?.let(actions.onSelectToolPackage)
                    },
                )
            }
        } else {
            null
        },
        statusBar = {
            val statusBarPresentation = state.statusBarPresentation
            IdeStatusBar(
                modifier = Modifier.fillMaxWidth(),
                leadingContent = {
                    val leadingText = statusBarPresentation.leadingStatusText
                    if (leadingText != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(IconSize.Small),
                            )
                            Text(
                                text = leadingText,
                                fontSize = FontSize.Micro,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = GlassFontFamily.ide(),
                            )
                        }
                    }
                },
                trailingContent = {
                    val failure = statusBarPresentation.failureText
                    if (failure != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(IconSize.Indicator)
                                    .clip(GlassShapes.Capsule)
                                    .background(GlassTheme.diagnosticColors.error),
                            )
                            Text(
                                text = failure,
                                fontSize = FontSize.Micro,
                                color = MaterialTheme.colorScheme.error,
                                fontFamily = GlassFontFamily.ide(),
                            )
                        }
                    }
                },
            )
        },
        content = {
            Column(modifier = Modifier.fillMaxSize()) {
                if (state.environmentNotice != null) {
                    val isAwaiting = state.activeOperation?.executionState == SetupOperationState.AWAITING_USER_ACTION
                    GlassBanner(
                        severity = if (isAwaiting) GlassBannerSeverity.INFO else GlassBannerSeverity.WARNING,
                        title = if (isAwaiting) {
                            "Terminal Authorization Required"
                        } else {
                            "Environment Verification Required"
                        },
                        description = state.environmentNotice,
                        primaryAction = if (isAwaiting) {
                            GlassBannerAction(
                                label = "Open Terminal Sheet",
                                onClick = actions.onShowHandoffSheet,
                            )
                        } else {
                            GlassBannerAction(
                                label = "Verify Now",
                                onClick = actions.onNavigateToEnvironment,
                            )
                        },
                        secondaryAction = if (isAwaiting) {
                            GlassBannerAction(
                                label = "Abandon Setup",
                                onClick = actions.onAbandonAwaitingOperation,
                            )
                        } else {
                            GlassBannerAction(
                                label = "Dismiss",
                                onClick = actions.onDismissEnvironmentNotice,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
                    )
                }

                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val isReducedMotion = GlassTheme.reducedMotionPolicy == GlassReducedMotionPolicy.Reduced
                    val fadeDuration = SetupTokens.DurationFadeMs
                    val feedbackDuration = SetupTokens.DurationFeedbackMs

                    AnimatedContent(
                        targetState = effectiveTab,
                        transitionSpec = {
                            if (isReducedMotion) {
                                fadeIn(animationSpec = snap()).togetherWith(fadeOut(animationSpec = snap()))
                            } else {
                                fadeIn(
                                    animationSpec = tween(
                                        durationMillis = fadeDuration,
                                        easing = MotionEasing.Standard,
                                    ),
                                ).togetherWith(
                                    fadeOut(
                                        animationSpec = tween(
                                            durationMillis = feedbackDuration,
                                            easing = MotionEasing.Standard,
                                        ),
                                    ),
                                )
                            }
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
                                    activityState = activityState,
                                    activityListState = activityListState,
                                    previewInvokerFocusRequester = previewInvokerFocusRequester,
                                )
                            }
                            SetupTab.PROJECTS -> {
                                ProjectsStepContent(
                                    state = state,
                                    actions = actions,
                                    onNavigateToEnvironment = { onTabChange(SetupTab.ENVIRONMENT) },
                                    searchFocusRequester = searchFocusRequester,
                                    focusSearchOnMount = pendingSearchFocus,
                                    lazyListState = workspacesListState,
                                )
                            }
                            SetupTab.TOOLS -> {
                                ToolsStepContent(
                                    state = state,
                                    actions = actions,
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
    ) {
        DocumentationStepContent()
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
            plan = activeOperationForPreview.plan,
            contextualError = activeOperationForPreview.failure,
        )
    }

    val activeOperationForHandoff = state.activeOperation
    val handoff = activeOperationForHandoff?.awaitingHandoff
    val isAwaiting = activeOperationForHandoff?.executionState == SetupOperationState.AWAITING_USER_ACTION
    if (state.isHandoffSheetVisible && isAwaiting && handoff != null) {
        TerminalHandoffSheet(
            handoff = handoff,
            onOpenTerminal = actions.onOpenTerminal,
            onResume = {
                actions.onResumeAwaitingOperation()
                (lastInvokingFocusRequester ?: rootFocusRequester).requestFocus()
            },
            onDismiss = {
                actions.onDismissHandoff()
                (lastInvokingFocusRequester ?: rootFocusRequester).requestFocus()
            },
            onAbandon = {
                actions.onAbandonAwaitingOperation()
                (lastInvokingFocusRequester ?: rootFocusRequester).requestFocus()
            },
            errorMessage = activeOperationForHandoff.failure,
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String, severity: IdeStatusSeverity = IdeStatusSeverity.Neutral) {
    val valueColor = when (severity) {
        IdeStatusSeverity.Ready -> GlassTheme.diagnosticColors.success
        IdeStatusSeverity.Running -> GlassTheme.diagnosticColors.warning
        IdeStatusSeverity.Warning -> GlassTheme.diagnosticColors.warning
        IdeStatusSeverity.Blocking -> GlassTheme.diagnosticColors.error
        IdeStatusSeverity.Failed -> GlassTheme.diagnosticColors.error
        IdeStatusSeverity.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.ExtraSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = valueColor,
        )
    }
}

/** Persistent right-hand context panel matching the Setup dashboard reference. */
@Composable
private fun SetupEnvironmentSummary(state: SetupUiState, modifier: Modifier = Modifier) {
    val summary = state.environmentSummary

    Column(modifier = modifier) {
        GlassSurface(modifier = Modifier.fillMaxWidth(), shape = GlassShapes.HazeMedium) {
            Column(modifier = Modifier.padding(Spacing.PanelPadding)) {
                Text(
                    text = "Environment summary",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = Spacing.Small),
                )
                HorizontalDivider(
                    modifier = Modifier.padding(bottom = Spacing.Small),
                    thickness = GlassDimens.HairlineBorder,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                summary.items.forEach { item ->
                    SummaryRow(
                        label = item.label,
                        value = item.value,
                        severity = item.severity,
                    )
                }
            }
        }
        GlassSurface(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.Medium),
            shape = GlassShapes.HazeMedium,
        ) {
            Column(modifier = Modifier.padding(Spacing.PanelPadding)) {
                Text(
                    text = "Setup does not start a ROM build.",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "This screen prepares your machine before you configure a ROM.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
