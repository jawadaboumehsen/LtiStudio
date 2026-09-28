/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.shared.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.setup.ToolchainProvisioningService
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.feature.configuration.configurationScreen
import org.ide.lti.feature.configuration.navigateToConfiguration
import org.ide.lti.feature.configuration.navigateToEditConfiguration
import org.ide.lti.feature.plugins.pluginsScreen
import org.ide.lti.feature.settings.SETTINGS_ROUTE
import org.ide.lti.feature.settings.navigateToSettings
import org.ide.lti.feature.settings.notificationScreen
import org.ide.lti.feature.settings.settingsScreen
import org.ide.lti.feature.setup.SETUP_ROUTE
import org.ide.lti.feature.setup.api.SetupTab
import org.ide.lti.feature.setup.setupScreen
import org.ide.lti.feature.workspace.creation.createWorkspaceScreen
import org.ide.lti.feature.workspace.creation.navigateToCreateWorkspace
import org.ide.lti.feature.workspace.studio.studioScreen
import org.koin.compose.koinInject
import org.koin.core.context.GlobalContext

@Composable
internal fun RootNavGraph(
    navHostController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
    initialSetupTab: SetupTab? = null,
    workspaceManager: WorkspaceManager = koinInject(),
    toolchainService: ToolchainProvisioningService? = remember {
        GlobalContext.getOrNull()?.getOrNull<ToolchainProvisioningService>()
    },
) {
    val currentWorkspace by workspaceManager.currentWorkspace.collectAsState()
    val toolchainState by (
        toolchainService?.state ?: remember {
            MutableStateFlow(ToolchainSetupState())
        }
        ).collectAsState()
    val canLaunchWorkspace = toolchainState.canLaunchWorkspace

    LaunchedEffect(currentWorkspace, canLaunchWorkspace) {
        val workspace = currentWorkspace
        if (workspace != null && navHostController.currentDestination?.route == SETUP_ROUTE) {
            if (canLaunchWorkspace) {
                navHostController.navigate(buildStudioRoute(workspaceId = workspace.id)) {
                    launchSingleTop = true
                }
            }
        } else if (currentWorkspace == null && navHostController.currentDestination?.route == "workspace") {
            navHostController.navigate(SETUP_ROUTE) {
                popUpTo("workspace") { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()

    val navigateToSetup: () -> Unit = {
        coroutineScope.launch(Dispatchers.Main.immediate) {
            navHostController.navigate(SETUP_ROUTE) {
                popUpTo(navHostController.graph.startDestinationId) { inclusive = false }
                launchSingleTop = true
            }
        }
    }
    val navigateToWorkspace: () -> Unit = {
        coroutineScope.launch(Dispatchers.Main.immediate) {
            navHostController.openStudioOrSetup(currentWorkspace?.id, canLaunchWorkspace)
        }
    }
    val navigateToSettings: () -> Unit = {
        coroutineScope.launch(Dispatchers.Main.immediate) {
            navHostController.navigate(SETTINGS_ROUTE) {
                popUpTo(SETUP_ROUTE) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navHostController,
        startDestination = startDestination,
        route = LtiNavGraph.ROOT_GRAPH,
        modifier = modifier,
    ) {
        // Setup Screen (Entry point)
        setupScreen(
            onOpenWorkspace = { workspace ->
                coroutineScope.launch(Dispatchers.Main.immediate) {
                    navHostController.openStudioOrSetup(workspace.id, canLaunchWorkspace)
                }
            },
            onOpenSettings = navigateToSettings,
            onCreateWorkspace = { navHostController.navigateToCreateWorkspace() },
            onOpenConfigurations = { navHostController.navigateToConfiguration() },
            onOpenManageTargets = { navHostController.navigateToConfiguration() },
            initialTab = initialSetupTab,
        )

        // Create Workspace Screen
        createWorkspaceScreen(
            onBackClick = { navHostController.popBackStack() },
            onWorkspaceCreated = { workspace ->
                coroutineScope.launch(Dispatchers.Main.immediate) {
                    navHostController.openStudioOrSetup(workspace.id, canLaunchWorkspace)
                }
            },
            onNavigateToSetup = navigateToSetup,
        )

        // Workspace Route (redirects cleanly to StudioScreen)
        composable(route = "workspace") {
            val workspace = currentWorkspace
            if (workspace != null && canLaunchWorkspace) {
                LaunchedEffect(workspace.id) {
                    navHostController.navigate(buildStudioRoute(workspaceId = workspace.id)) {
                        popUpTo("workspace") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            } else {
                LaunchedEffect(Unit) {
                    navHostController.navigate(SETUP_ROUTE) {
                        popUpTo("workspace") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }

        // Notifications (reachable from the workspace top bar)
        notificationScreen(
            onBackClick = { navHostController.popBackStack() },
            onNavigateToSetup = navigateToSetup,
            onNavigateToWorkspace = navigateToWorkspace,
        )

        settingsScreen(
            onBackClick = {
                if (!navHostController.popBackStack()) {
                    navigateToSetup()
                }
            },
            onNavigateToSetup = navigateToSetup,
            onNavigateToWorkspace = navigateToWorkspace,
        )

        // Target & Run Configurations Studio
        configurationScreen(
            onBackClick = { navHostController.popBackStack() },
        )

        // ROM Setup Studio (8-stage ROM configuration and validation)
        studioScreen(
            onNavigateBack = { navHostController.popBackStack() },
            onNavigateToRun = { workspaceId, runId ->
                navHostController.navigateToRun(workspaceId = workspaceId, runId = runId)
            },
            onNavigateToPlugins = { workspaceId, packageId, destination ->
                navHostController.navigateToPlugins(
                    destination = destination,
                    workspaceId = workspaceId,
                    packageId = packageId,
                )
            },
            onNavigateToSetup = navigateToSetup,
            onOpenSettings = navigateToSettings,
            onOpenEditConfiguration = { targetId ->
                navHostController.navigateToEditConfiguration(targetId)
            },
        )

        // Plugin Manager (Installed, Marketplace, Import, Author Tools)
        pluginsScreen(
            onNavigateBack = { navHostController.popBackStack() },
            onNavigateToSetup = navigateToSetup,
            onOpenSettings = navigateToSettings,
        )
    }
}

/**
 * Opens the studio for [workspaceId] only when this machine can launch workspaces; otherwise the user
 * stays in (or returns to) Setup, which explains what is missing.
 */
private fun NavHostController.openStudioOrSetup(workspaceId: String?, canLaunchWorkspace: Boolean) {
    if (workspaceId == null || !canLaunchWorkspace) {
        navigate(SETUP_ROUTE) {
            popUpTo(SETUP_ROUTE) { inclusive = false }
            launchSingleTop = true
        }
    } else if (currentDestination?.route != StudioRoutes.STUDIO_PATTERN) {
        navigate(buildStudioRoute(workspaceId = workspaceId)) {
            popUpTo(SETUP_ROUTE) { inclusive = false }
            launchSingleTop = true
        }
    }
}
