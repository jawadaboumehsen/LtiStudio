/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import org.ide.lti.core.model.workspace.Workspace

typealias SetupTab = org.ide.lti.feature.setup.api.SetupTab
typealias SetupDestination = org.ide.lti.feature.setup.api.SetupDestination
const val SETUP_ROUTE: String = org.ide.lti.feature.setup.api.SETUP_ROUTE

fun NavController.navigateToSetup(navOptions: NavOptions? = null) {
    navigate(SETUP_ROUTE, navOptions)
}

// Per contracts/studio-ui.md: "Setup chooses/creates workspace then opens the same workspace-scoped
// studio route as Workspace." RootNavGraph now routes onOpenWorkspace (and onWorkspaceCreated)
// straight to the studio route instead of the legacy "workspace" destination, so there is no
// separate onOpenStudio hook needed here - opening a workspace from Setup already lands in Studio.
fun NavGraphBuilder.setupScreen(
    onOpenWorkspace: (Workspace) -> Unit,
    onOpenSettings: () -> Unit = {},
    onCreateWorkspace: () -> Unit = {},
    onOpenConfigurations: () -> Unit = {},
    onOpenManageTargets: () -> Unit = onOpenConfigurations,
    onOpenHelp: () -> Unit = {},
    initialTab: SetupTab? = null,
) {
    composable(route = SETUP_ROUTE) {
        SetupScreen(
            onOpenWorkspace = onOpenWorkspace,
            onOpenSettings = onOpenSettings,
            onCreateWorkspace = onCreateWorkspace,
            onOpenConfigurations = onOpenConfigurations,
            onOpenManageTargets = onOpenManageTargets,
            onOpenHelp = onOpenHelp,
            initialTab = initialTab,
        )
    }
}
