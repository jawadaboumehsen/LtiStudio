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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.backdrops.LayerBackdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.actions.LocalWindowControlActions
import org.ide.lti.core.designsystem.component.actions.WindowControlActions
import org.ide.lti.core.designsystem.component.display.GlassPanel
import org.ide.lti.core.designsystem.component.display.GlassVerticalDivider
import org.ide.lti.core.designsystem.component.feedback.GlassBanner
import org.ide.lti.core.designsystem.component.feedback.GlassBannerAction
import org.ide.lti.core.designsystem.component.feedback.GlassBannerSeverity
import org.ide.lti.core.designsystem.component.feedback.GlassBottomSheetScaffold
import org.ide.lti.core.designsystem.component.layout.GlassTopBar
import org.ide.lti.core.designsystem.component.layout.TopBarNavigation
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.MotionEasing
import org.ide.lti.core.designsystem.theme.SetupTokens
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.feature.setup.components.SetupJourneySidebar
import org.ide.lti.feature.setup.steps.DocumentationStepContent
import org.ide.lti.feature.setup.steps.EnvironmentStepContent
import org.ide.lti.feature.setup.steps.ProjectsStepContent
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
    backdrop: Backdrop? = null,
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
        selectedTab = selectedTab,
        selectedDestination = SetupDestination.fromId(selectedTab.id),
        environmentNotice = environmentNotice,
        environmentReadiness = environmentReadiness,
        activeOperation = activeOperation,
    )

    val uiActions = SetupUiActions(
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onRemoveRecentProject = viewModel::removeRecentProject,
        onOpenFolder = { viewModel.openFolder(onOpenWorkspace) },
        onRecentProjectClick = { viewModel.openRecentProject(it, onOpenWorkspace) },
        onSelectWorkspace = viewModel::selectWorkspace,
        onCheckEnvironment = viewModel::checkEnvironmentStatus,
        onRunFullSetup = viewModel::runFullEnvironmentSetup,
        onRetrySetupStep = viewModel::retrySetupStep,
        onProvisionAvbKey = viewModel::onProvisionAvbKey,
        onSelectTarget = viewModel::selectTarget,
        onAutoRemediateDoctor = viewModel::autoRemediateDoctorIssues,
        onAutoRunEnvironmentSetup = viewModel::autoRunEnvironmentSetup,
        onSelectCockpitTab = viewModel::selectCockpitTab,
        onToolSearchQueryChange = viewModel::onToolSearchQueryChange,
        onSelectToolCategory = viewModel::selectToolCategory,
        onTestTool = viewModel::testTool,
        onRecompileTool = viewModel::recompileTool,
        onNavigateToEnvironment = viewModel::navigateToEnvironment,
        onOpenSettings = onOpenSettings,
        onSelectTab = viewModel::selectTab,
        onSelectDestination = { dest ->
            when (dest) {
                SetupDestination.WORKSPACES -> viewModel.selectTab(SetupTab.PROJECTS)
                SetupDestination.ENVIRONMENT -> viewModel.selectTab(SetupTab.ENVIRONMENT)
                SetupDestination.TOOLS -> viewModel.selectTab(SetupTab.TOOLS)
            }
        },
        onDismissEnvironmentNotice = viewModel::dismissEnvironmentNotice,
        onSetAutoDoctorEnabled = viewModel::setAutoDoctorEnabled,
        onResetToolchainCache = viewModel::resetToolchainCacheAndRecheck,
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
    backdrop: Backdrop? = null,
    initialTab: SetupTab? = null,
    isHelpSheetOpen: Boolean = false,
    onDismissHelpSheet: () -> Unit = {},
    toolBrowserState: ToolBrowserState? = null,
    activityState: SetupActivityState? = null,
    workspacesListState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    toolsScrollState: androidx.compose.foundation.ScrollState = androidx.compose.foundation.rememberScrollState(),
    activityListState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
) {
    val ambientBackdrop = (backdrop as? LayerBackdrop) ?: (GlassTheme.backdrop as? LayerBackdrop)
    val resolvedBackdrop = ambientBackdrop ?: rememberLayerBackdrop()
    var localSelectedTab by remember { mutableStateOf(initialTab ?: state.selectedTab) }
    val effectiveTab = initialTab?.let { localSelectedTab } ?: state.selectedTab
    var internalHelpSheetOpen by remember { mutableStateOf(isHelpSheetOpen) }

    val rootFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
    val toolSearchFocusRequester = remember { FocusRequester() }
    val helpButtonFocusRequester = remember { FocusRequester() }
    var lastInvokingFocusRequester by remember { mutableStateOf<FocusRequester?>(null) }
    var pendingSearchFocus by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        rootFocusRequester.requestFocus()
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .widthIn(min = SetupTokens.MinWindowWidth)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
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
                        val showHelp = isHelpSheetOpen || internalHelpSheetOpen
                        if (showHelp) {
                            internalHelpSheetOpen = false
                            onDismissHelpSheet()
                            (lastInvokingFocusRequester ?: rootFocusRequester).requestFocus()
                            true
                        } else if (state.activeOperation?.executionState == SetupOperationState.PREVIEW) {
                            actions.onDismissPreview()
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
    ) {
        val isCompact = maxWidth < SetupTokens.CompactBreakpoint
        val sidebarWidth = if (isCompact) SetupTokens.SidebarWidthCompact else SetupTokens.SidebarWidthWide
        val gutter = if (isCompact) SetupTokens.GutterCompact else SetupTokens.GutterWide

        Column(modifier = Modifier.fillMaxSize()) {
            // Standard Unified Liquid Glass Top Bar
            GlassTopBar(
                navigation = TopBarNavigation.Brand(
                    title = "LtiRom",
                    workspaceName = "Setup",
                ),
                search = null,
                panelToggles = null,
                actions = {
                    GlassIconButton(onClick = actions.onOpenManageTargets) {
                        Icon(
                            painter = AppIcons.EditPainterResource(),
                            contentDescription = "Target Configuration",
                            modifier = Modifier.size(IconSize.Small),
                        )
                    }
                    GlassIconButton(onClick = actions.onNavigateToEnvironment) {
                        Icon(
                            painter = AppIcons.SparklesPainterResource(),
                            contentDescription = "Environment & Doctor",
                            modifier = Modifier.size(IconSize.Small),
                        )
                    }
                    GlassIconButton(onClick = actions.onOpenSettings) {
                        Icon(
                            painter = AppIcons.SettingsPainterResource(),
                            contentDescription = "Settings",
                            modifier = Modifier.size(IconSize.Small),
                        )
                    }
                    GlassIconButton(
                        onClick = {
                            lastInvokingFocusRequester = helpButtonFocusRequester
                            internalHelpSheetOpen = true
                            actions.onOpenHelp()
                        },
                        modifier = Modifier.focusRequester(helpButtonFocusRequester),
                    ) {
                        Icon(
                            painter = AppIcons.InfoPainterResource(),
                            contentDescription = "Help & Documentation",
                            modifier = Modifier.size(IconSize.Small),
                        )
                    }
                },
                windowControls = windowControls ?: LocalWindowControlActions.current,
                backdrop = resolvedBackdrop,
                drawOwnChrome = true,
            )

            if (state.environmentNotice != null) {
                GlassBanner(
                    severity = GlassBannerSeverity.WARNING,
                    title = "Environment Verification Required",
                    description = state.environmentNotice,
                    primaryAction = GlassBannerAction(label = "Verify Now", onClick = actions.onNavigateToEnvironment),
                    secondaryAction = GlassBannerAction(label = "Dismiss", onClick = actions.onDismissEnvironmentNotice),
                    backdrop = resolvedBackdrop,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
                )
            }

            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Adaptive Left Setup Journey Sidebar
                GlassPanel(
                    width = sidebarWidth,
                    shape = GlassShapes.None,
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    SetupJourneySidebar(
                        currentStep = effectiveTab,
                        onStepSelected = onTabChange,
                        toolchainSetupState = state.toolchainSetupState,
                        onOpenSettings = actions.onOpenSettings,
                        onOpenManageTargets = actions.onOpenManageTargets,
                        onOpenHelp = {
                            lastInvokingFocusRequester = helpButtonFocusRequester
                            internalHelpSheetOpen = true
                            actions.onOpenHelp()
                        },
                    )
                }

                // Sleek Specular Hairline Vertical Divider
                GlassVerticalDivider(specular = true)

                // Main Content Area with adaptive gutter padding and max content width constraint
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            if (SetupTokens.IsReducedTransparency) {
                                SetupTokens.OpaqueSurfaceColor
                            } else {
                                SetupTokens.ContentSurfaceColor
                            },
                        ),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    val fadeDuration = SetupTokens.DurationFadeMs
                    val feedbackDuration = SetupTokens.DurationFeedbackMs

                    AnimatedContent(
                        targetState = effectiveTab,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(durationMillis = fadeDuration, easing = MotionEasing.Standard))
                                .togetherWith(fadeOut(animationSpec = tween(durationMillis = feedbackDuration, easing = MotionEasing.Standard)))
                        },
                        label = "SetupDestinationTransition",
                        modifier = Modifier
                            .widthIn(max = SetupTokens.ContentMaxWidth)
                            .fillMaxSize()
                            .padding(horizontal = gutter),
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
                                )
                            }
                            SetupTab.PROJECTS, SetupTab.LAUNCHPAD -> {
                                // Legacy launchpad tab resolves to Workspaces content
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
                        }
                    }
                }
            }
        }
    }

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
}
