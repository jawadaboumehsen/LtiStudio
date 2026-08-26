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
import androidx.navigation.compose.composable
import org.ide.lti.core.data.utils.NetworkMonitor
import org.ide.lti.core.data.utils.TimeZoneMonitor
import org.ide.lti.feature.setup.setupScreen
import org.ide.lti.feature.workspace.workspaceScreen
import org.ide.lti.shared.ui.Lti

@Composable
internal fun RootNavGraph(
    networkMonitor: NetworkMonitor,
    timeZoneMonitor: TimeZoneMonitor,
    navHostController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
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
        workspaceScreen()

        // Legacy main screen (kept for reference)
        composable(LtiNavGraph.MAIN_GRAPH) {
            Lti(
                networkMonitor = networkMonitor,
                timeZoneMonitor = timeZoneMonitor,
            )
        }
    }
}
