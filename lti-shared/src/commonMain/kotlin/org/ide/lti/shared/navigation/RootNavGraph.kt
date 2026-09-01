/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.shared.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import org.ide.lti.feature.setup.setupScreen
import org.ide.lti.feature.settings.navigateToNotification
import org.ide.lti.feature.settings.notificationScreen
import org.ide.lti.feature.workspace.workspaceScreen

@Composable
internal fun RootNavGraph(
    navHostController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
) {
    NavHost(
        navController = navHostController,
        startDestination = startDestination,
        route = LtiNavGraph.ROOT_GRAPH,
        modifier = modifier,
    ) {
        // Setup Screen (Entry point)
        setupScreen(
            onOpenWorkspace = { workspace ->
                // Navigate to workspace screen
                navHostController.navigate("workspace")
            },
            onNavigateToConnections = {
                // TODO: Navigate to connections screen
            },
        )

        // Workspace Screen (4-panel IDE layout)
        workspaceScreen(
            onOpenSettings = onOpenSettings,
            onOpenNotifications = { navHostController.navigateToNotification() },
        )

        // Notifications (reachable from the workspace top bar)
        notificationScreen(
            onBackClick = { navHostController.popBackStack() },
        )
    }
}
