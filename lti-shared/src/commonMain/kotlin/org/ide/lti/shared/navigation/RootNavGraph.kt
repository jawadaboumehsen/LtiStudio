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
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.run.StageId
import org.ide.lti.feature.configuration.configurationScreen
import org.ide.lti.feature.configuration.navigateToConfiguration
import org.ide.lti.feature.configuration.navigateToCreateConfiguration
import org.ide.lti.feature.configuration.navigateToEditConfiguration
import org.ide.lti.feature.settings.navigateToNotification
import org.ide.lti.feature.settings.notificationScreen
import org.ide.lti.feature.setup.SETUP_ROUTE
import org.ide.lti.feature.setup.setupScreen
import org.ide.lti.feature.workspace.creation.createWorkspaceScreen
import org.ide.lti.feature.workspace.creation.navigateToCreateWorkspace
import org.ide.lti.feature.workspace.studio.studioScreen
import org.ide.lti.feature.workspace.workspaceScreen
import org.koin.compose.koinInject

@Composable
internal fun RootNavGraph(
    navHostController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    workspaceManager: WorkspaceManager = koinInject(),
) {
    val currentWorkspace by workspaceManager.currentWorkspace.collectAsState()

    LaunchedEffect(currentWorkspace) {
        if (currentWorkspace != null && navHostController.currentDestination?.route == SETUP_ROUTE) {
            navHostController.navigate("workspace") {
                launchSingleTop = true
            }
        } else if (currentWorkspace == null && navHostController.currentDestination?.route == "workspace") {
            navHostController.navigate(SETUP_ROUTE) {
                popUpTo("workspace") { inclusive = true }
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
            onOpenWorkspace = { _ ->
                if (navHostController.currentDestination?.route != "workspace") {
                    navHostController.navigate("workspace") {
                        launchSingleTop = true
                    }
                }
            },
            onOpenSettings = onOpenSettings,
            onCreateWorkspace = { navHostController.navigateToCreateWorkspace() },
            onOpenConfigurations = { navHostController.navigateToConfiguration() },
            onOpenManageTargets = { navHostController.navigateToConfiguration() },
            onOpenStudio = { workspaceId, stageId, objectId ->
                navHostController.navigateToStudio(
                    workspaceId = workspaceId,
                    stageId = stageId?.let { raw ->
                        StageId.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
                    },
                    objectId = objectId,
                )
            },
        )

        // Create Workspace Screen
        createWorkspaceScreen(
            onBackClick = { navHostController.popBackStack() },
            onWorkspaceCreated = { _ ->
                navHostController.navigate("workspace") {
                    popUpTo(SETUP_ROUTE) { inclusive = false }
                    launchSingleTop = true
                }
            },
            onNavigateToSetup = {
                navHostController.navigate(SETUP_ROUTE) {
                    launchSingleTop = true
                }
            },
        )

        // Workspace Screen (4-panel IDE layout)
        workspaceScreen(
            onOpenSettings = onOpenSettings,
            onOpenNotifications = { navHostController.navigateToNotification() },
            onOpenConfigurations = {
                navHostController.navigateToConfiguration()
            },
            onOpenEditConfiguration = { targetId ->
                navHostController.navigateToEditConfiguration(targetId)
            },
            onOpenAddConfiguration = {
                navHostController.navigateToCreateConfiguration()
            },
            onNavigateToSetup = {
                navHostController.navigate(SETUP_ROUTE) {
                    launchSingleTop = true
                }
            },
            onOpenStudio = { workspaceId, stageId, objectId ->
                navHostController.navigateToStudio(
                    workspaceId = workspaceId,
                    stageId = stageId?.let { raw ->
                        StageId.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
                    },
                    objectId = objectId,
                )
            },
        )

        // Notifications (reachable from the workspace top bar)
        notificationScreen(
            onBackClick = { navHostController.popBackStack() },
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
            onNavigateToSetup = {
                navHostController.navigate(SETUP_ROUTE) {
                    launchSingleTop = true
                }
            },
            onOpenSettings = onOpenSettings,
        )
    }
}
