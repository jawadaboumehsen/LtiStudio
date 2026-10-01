/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.studio

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.savedstate.read
import org.ide.lti.core.model.run.StageId

// feature:workspace cannot depend on lti-shared (lti-shared depends on feature:workspace, the other
// direction), so this route pattern cannot reuse org.ide.lti.shared.navigation.StudioRoutes.STUDIO_PATTERN
// directly - it is a second, necessarily-independent copy of the SAME string. lti-shared's own test suite
// (StudioRoutesTest) asserts the two stay byte-identical, since it is the one module able to see both.
public const val STUDIO_ROUTE_PATTERN: String =
    "studio/{workspaceId}?stageId={stageId}&objectId={objectId}"

public fun NavGraphBuilder.studioScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToRun: (workspaceId: String, runId: String) -> Unit = { _, _ -> },
    onNavigateToPlugins: (workspaceId: String?, packageId: String?, destination: String) -> Unit = { _, _, _ -> },
    onNavigateToSetup: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenEditConfiguration: (targetId: String) -> Unit = {},
) {
    composable(
        route = STUDIO_ROUTE_PATTERN,
        arguments = listOf(
            navArgument("workspaceId") {
                type = NavType.StringType
            },
            navArgument("stageId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument("objectId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) { backStackEntry ->
        val args = backStackEntry.arguments
        val workspaceId = args?.read { getStringOrNull("workspaceId") }.orEmpty()
        val stageIdStr = args?.read { getStringOrNull("stageId") }
        val stageId = stageIdStr?.let { raw ->
            StageId.entries.firstOrNull {
                it.name.equals(raw, ignoreCase = true) ||
                    it.name.replace('_', '-').equals(raw, ignoreCase = true)
            }
        }
        val objectId = args?.read { getStringOrNull("objectId") }

        StudioScreen(
            workspaceId = workspaceId,
            initialStageId = stageId,
            initialObjectId = objectId,
            onNavigateBack = onNavigateBack,
            onNavigateToRun = onNavigateToRun,
            onNavigateToPlugins = onNavigateToPlugins,
            onNavigateToSetup = onNavigateToSetup,
            onOpenSettings = onOpenSettings,
            onOpenEditConfiguration = onOpenEditConfiguration,
        )
    }
}
