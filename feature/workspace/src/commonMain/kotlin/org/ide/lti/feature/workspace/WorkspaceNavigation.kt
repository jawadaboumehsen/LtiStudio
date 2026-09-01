/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable

const val WORKSPACE_ROUTE = "workspace"

fun NavController.navigateToWorkspace(navOptions: NavOptions? = null) {
    navigate(WORKSPACE_ROUTE, navOptions)
}

fun NavGraphBuilder.workspaceScreen(
    onOpenSettings: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
) {
    composable(route = WORKSPACE_ROUTE) {
        WorkspaceScreen(
            onOpenSettings = onOpenSettings,
            onOpenNotifications = onOpenNotifications,
        )
    }
}
