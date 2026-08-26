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
import androidx.navigation.compose.NavHost
import org.ide.lti.feature.home.HOME_ROUTE
import org.ide.lti.feature.home.homeScreen
import org.ide.lti.feature.profile.profileScreen
import org.ide.lti.feature.settings.notificationScreen
import org.ide.lti.feature.settings.settingsScreen
import org.ide.lti.shared.ui.LtiState

@Composable
internal fun LtiNavHost(
    appState: LtiState,
    modifier: Modifier = Modifier,
) {
    val navController = appState.navController

    NavHost(
        route = LtiNavGraph.MAIN_GRAPH,
        startDestination = HOME_ROUTE,
        navController = navController,
        modifier = modifier,
    ) {
        homeScreen()

        profileScreen()

        settingsScreen(
            onBackClick = navController::popBackStack,
        )

        notificationScreen(
            onBackClick = navController::popBackStack,
        )
    }
}
