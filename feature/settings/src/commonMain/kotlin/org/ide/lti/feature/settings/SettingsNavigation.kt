/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.settings

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable

const val SETTINGS_ROUTE = "settings_route"
const val NOTIFICATION_ROUTE = "notification_route"

fun NavController.navigateToSettings(navOptions: NavOptions? = null) = navigate(SETTINGS_ROUTE, navOptions)

fun NavController.navigateToNotification(navOptions: NavOptions? = null) = navigate(NOTIFICATION_ROUTE, navOptions)

fun NavGraphBuilder.settingsScreen(
    onBackClick: () -> Unit,
    onNavigateToSetup: () -> Unit,
    onNavigateToWorkspace: () -> Unit,
) {
    composable(route = SETTINGS_ROUTE) {
        SettingsScreen(
            onBackClick = onBackClick,
            onNavigateToSetup = onNavigateToSetup,
            onNavigateToWorkspace = onNavigateToWorkspace,
        )
    }
}

fun NavGraphBuilder.notificationScreen(
    onBackClick: () -> Unit,
    onNavigateToSetup: () -> Unit,
    onNavigateToWorkspace: () -> Unit,
) {
    composable(route = NOTIFICATION_ROUTE) {
        NotificationScreen(
            onBackClick = onBackClick,
            onNavigateToSetup = onNavigateToSetup,
            onNavigateToWorkspace = onNavigateToWorkspace,
        )
    }
}
