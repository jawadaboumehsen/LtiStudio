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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.backdrops.LayerBackdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import org.ide.lti.core.designsystem.component.actions.LocalWindowControlActions
import org.ide.lti.core.designsystem.component.actions.WindowControlActions
import org.ide.lti.core.designsystem.component.display.GlassPanel
import org.ide.lti.core.designsystem.component.display.GlassVerticalDivider
import org.ide.lti.core.designsystem.component.feedback.GlassBanner
import org.ide.lti.core.designsystem.component.feedback.GlassBannerAction
import org.ide.lti.core.designsystem.component.feedback.GlassBannerSeverity
import org.ide.lti.core.designsystem.component.layout.GlassTopBar
import org.ide.lti.core.designsystem.component.layout.TopBarNavigation
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.MotionDuration
import org.ide.lti.core.designsystem.theme.MotionEasing
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.feature.setup.components.SetupJourneySidebar
import org.ide.lti.feature.setup.steps.ConnectionsStepContent
import org.ide.lti.feature.setup.steps.DocumentationStepContent
import org.ide.lti.feature.setup.steps.EnvironmentStepContent
import org.ide.lti.feature.setup.steps.LaunchpadStepContent
import org.ide.lti.feature.setup.steps.PluginsStepContent
import org.ide.lti.feature.setup.steps.ProjectsStepContent
import org.koin.compose.viewmodel.koinViewModel

/**
 * Top-level entry composable for Setup and Workspace Launchpad.
 *
 * Gathers Presentation UI state and binds actions to [SetupViewModel].
 */
@Composable
fun SetupScreen(
    onOpenWorkspace: (Workspace) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    backdrop: Backdrop? = null,
    viewModel: SetupViewModel = koinViewModel(),
) {
    val toolchainSetupState by viewModel.toolchainSetupState.collectAsState()
    val recentProjects by viewModel.recentProjects.collectAsState()
    val filteredProjects by viewModel.filteredRecentProjects.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCockpitTab by viewModel.selectedCockpitTab.collectAsState()
    val toolSearchQuery by viewModel.toolSearchQuery.collectAsState()
    val selectedToolCategory by viewModel.selectedToolCategory.collectAsState()
    val filteredToolsMatrix by viewModel.filteredToolsMatrix.collectAsState()
    val isAutoDoctorEnabled by viewModel.isAutoDoctorEnabled.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val environmentNotice by viewModel.environmentNotice.collectAsState()
    val selectedTarget by viewModel.selectedTarget.collectAsState()
    val availableTargets by viewModel.availableTargets.collectAsState()

    val uiState = SetupUiState(
        toolchainSetupState = toolchainSetupState,
        selectedTarget = selectedTarget,
        availableTargets = availableTargets,
        recentProjects = recentProjects,
        filteredProjects = filteredProjects,
        searchQuery = searchQuery,
        selectedCockpitTab = selectedCockpitTab,
        toolSearchQuery = toolSearchQuery,
        selectedToolCategory = selectedToolCategory,
        filteredToolsMatrix = filteredToolsMatrix,
        isAutoDoctorEnabled = isAutoDoctorEnabled,
        selectedTab = selectedTab,
        environmentNotice = environmentNotice,
    )

    val uiActions = SetupUiActions(
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onRemoveRecentProject = viewModel::removeRecentProject,
        onOpenFolder = { viewModel.openFolder(onOpenWorkspace) },
        onCloneGit = { viewModel.cloneGitRepository(onWorkspaceOpened = onOpenWorkspace) },
        onRecentProjectClick = { viewModel.openRecentProject(it, onOpenWorkspace) },
        onCheckEnvironment = viewModel::checkEnvironmentStatus,
        onRunFullSetup = viewModel::runFullEnvironmentSetup,
        onRetrySetupStep = viewModel::retrySetupStep,
        onProvisionAvbKey = viewModel::onProvisionAvbKey,
        onProceedToWorkspace = { viewModel.openDefaultWorkspace(onOpenWorkspace) },
        onSelectTargetAndLaunch = { target -> viewModel.selectTargetAndLaunch(target, onOpenWorkspace) },
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
        onDismissEnvironmentNotice = viewModel::dismissEnvironmentNotice,
    )

    SetupScreenContent(
        state = uiState,
        actions = uiActions,
        backdrop = backdrop,
        modifier = modifier,
    )
}

/**
 * Modern Clean Architecture layout composable for Setup & Welcome screen.
 *
 * Encapsulates state and actions into cohesive [SetupUiState] and [SetupUiActions] models,
 * keeping the parameter list clean and recomposition overhead minimal.
 */
@Composable
fun SetupScreenContent(
    state: SetupUiState,
    actions: SetupUiActions,
    modifier: Modifier = Modifier,
    windowControls: WindowControlActions? = null,
    backdrop: Backdrop? = null,
    initialTab: SetupTab? = null,
) {
    val localBackdrop = rememberLayerBackdrop()
    val resolvedBackdrop = (backdrop as? LayerBackdrop) ?: (GlassTheme.backdrop as? LayerBackdrop) ?: localBackdrop
    var localSelectedTab by remember { mutableStateOf(initialTab ?: state.selectedTab) }
    val effectiveTab = initialTab?.let { localSelectedTab } ?: state.selectedTab

    val onTabChange: (SetupTab) -> Unit = { newTab ->
        if (initialTab != null) {
            localSelectedTab = newTab
        }
        actions.onSelectTab(newTab)
    }


    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Standard Unified Liquid Glass Top Bar
            GlassTopBar(
                navigation = TopBarNavigation.Brand(
                    title = "LtiRom",
                    workspaceName = "Setup",
                ),
                search = null,
                panelToggles = null,
                actions = null,
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
                // Modern Linear/Fleet-tier Left Setup Journey Sidebar
                GlassPanel(
                    width = ComponentSize.SetupSidebarWidth,
                    shape = GlassShapes.None,
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    SetupJourneySidebar(
                        currentStep = effectiveTab,
                        onStepSelected = onTabChange,
                        toolchainSetupState = state.toolchainSetupState,
                        onOpenDocumentation = { onTabChange(SetupTab.DOCS) },
                        onOpenSettings = actions.onOpenSettings,
                    )
                }

                // Sleek Specular Hairline Vertical Divider
                GlassVerticalDivider(specular = true)

                // Main Content Area (Center-constrained for proportional layout)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    AnimatedContent(
                        targetState = effectiveTab,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(durationMillis = MotionDuration.Moderate, easing = MotionEasing.Standard))
                                .togetherWith(fadeOut(animationSpec = tween(durationMillis = MotionDuration.Fast, easing = MotionEasing.Standard)))
                        },
                        label = "SetupTabTransition",
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter,
                    ) { currentTab ->
                        when (currentTab) {
                            SetupTab.ENVIRONMENT -> {
                                EnvironmentStepContent(
                                    state = state,
                                    actions = actions,
                                    onNavigateToProjects = { onTabChange(SetupTab.PROJECTS) },
                                    onProceedToLaunchpad = { onTabChange(SetupTab.LAUNCHPAD) },
                                    resolvedBackdrop = resolvedBackdrop,
                                )
                            }
                            SetupTab.LAUNCHPAD -> {
                                LaunchpadStepContent(
                                    state = state,
                                    actions = actions,
                                    onNavigateToEnvironment = { onTabChange(SetupTab.ENVIRONMENT) },
                                    resolvedBackdrop = resolvedBackdrop,
                                )
                            }
                            SetupTab.CONNECTIONS -> {
                                ConnectionsStepContent(
                                    state = state,
                                    actions = actions,
                                    resolvedBackdrop = resolvedBackdrop,
                                )
                            }
                            SetupTab.DOCS -> {
                                DocumentationStepContent(
                                    state = state,
                                    actions = actions,
                                    resolvedBackdrop = resolvedBackdrop,
                                )
                            }
                            SetupTab.PLUGINS -> {
                                PluginsStepContent(
                                    state = state,
                                    actions = actions,
                                    resolvedBackdrop = resolvedBackdrop,
                                )
                            }
                            SetupTab.PROJECTS -> {
                                ProjectsStepContent(
                                    state = state,
                                    actions = actions,
                                    onNavigateToEnvironment = { onTabChange(SetupTab.ENVIRONMENT) },
                                    resolvedBackdrop = resolvedBackdrop,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
