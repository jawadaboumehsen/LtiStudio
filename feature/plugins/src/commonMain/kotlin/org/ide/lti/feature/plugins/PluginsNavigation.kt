/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.plugins

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.savedstate.read

// feature:plugins cannot depend on lti-shared (lti-shared depends on feature:plugins, the other
// direction), so this route pattern cannot reuse org.ide.lti.shared.navigation.StudioRoutes.PLUGINS_PATTERN
// directly - it is a second, necessarily-independent copy of the SAME string. lti-shared's own test suite
// (StudioRoutesTest) asserts the two stay byte-identical.
public const val PLUGINS_ROUTE_PATTERN: String =
    "plugins/{destination}?workspaceId={workspaceId}&packageId={packageId}"

public fun NavGraphBuilder.pluginsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToSetup: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    composable(
        route = PLUGINS_ROUTE_PATTERN,
        arguments = listOf(
            navArgument("destination") {
                type = NavType.StringType
            },
            navArgument("workspaceId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument("packageId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) { backStackEntry ->
        val args = backStackEntry.arguments
        val destination = args?.read { getStringOrNull("destination") }.orEmpty()
        val workspaceId = args?.read { getStringOrNull("workspaceId") }
        val packageId = args?.read { getStringOrNull("packageId") }

        PluginManagerScreen(
            initialDestination = destination,
            initialWorkspaceId = workspaceId,
            initialPackageId = packageId,
            onNavigateBack = onNavigateBack,
            onNavigateToSetup = onNavigateToSetup,
            onOpenSettings = onOpenSettings,
        )
    }
}
