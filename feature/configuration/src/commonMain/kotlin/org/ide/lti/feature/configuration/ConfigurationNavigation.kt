/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.configuration

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.savedstate.read

const val CONFIGURATION_ROUTE = "configuration_route"
private const val TARGET_ID_ARG = "targetId"
private const val MODE_ARG = "mode"
private const val CONFIGURATION_ROUTE_PATTERN =
    "$CONFIGURATION_ROUTE?$TARGET_ID_ARG={$TARGET_ID_ARG}&$MODE_ARG={$MODE_ARG}"

private fun configurationRoute(
    targetId: String? = null,
    mode: ConfigurationNavMode = ConfigurationNavMode.EDIT,
): String {
    val query = buildString {
        append(CONFIGURATION_ROUTE)
        append("?$MODE_ARG=${mode.name}")
        if (!targetId.isNullOrBlank()) {
            append("&$TARGET_ID_ARG=$targetId")
        }
    }
    return query
}

fun NavController.navigateToConfiguration(navOptions: NavOptions? = null) {
    navigate(configurationRoute(mode = ConfigurationNavMode.EDIT), navOptions)
}

fun NavController.navigateToEditConfiguration(targetId: String, navOptions: NavOptions? = null) {
    navigate(configurationRoute(targetId = targetId, mode = ConfigurationNavMode.EDIT), navOptions)
}

fun NavController.navigateToCreateConfiguration(navOptions: NavOptions? = null) {
    navigate(configurationRoute(mode = ConfigurationNavMode.CREATE), navOptions)
}

fun NavGraphBuilder.configurationScreen(onBackClick: () -> Unit) {
    composable(
        route = CONFIGURATION_ROUTE_PATTERN,
        arguments = listOf(
            navArgument(TARGET_ID_ARG) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument(MODE_ARG) {
                type = NavType.StringType
                defaultValue = ConfigurationNavMode.EDIT.name
            },
        ),
    ) { backStackEntry ->
        val arguments = backStackEntry.arguments
        val targetId = arguments?.read { getStringOrNull(TARGET_ID_ARG) }
        val mode = arguments?.read { getStringOrNull(MODE_ARG) }
            ?.let { raw -> runCatching { ConfigurationNavMode.valueOf(raw) }.getOrNull() }
            ?: ConfigurationNavMode.EDIT

        ConfigurationScreen(
            onBackClick = onBackClick,
            initialTargetId = targetId,
            initialMode = mode,
        )
    }
}
