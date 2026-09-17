/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import org.ide.lti.core.data.settings.AppSettingsState
import org.ide.lti.core.designsystem.component.layout.GlassBackdrop
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.config.AppTheme
import org.ide.lti.feature.settings.navigateToSettings
import org.ide.lti.feature.setup.SETUP_ROUTE
import org.ide.lti.feature.workspace.WORKSPACE_ROUTE
import org.ide.lti.shared.navigation.RootNavGraph
import org.koin.compose.koinInject

@Composable
fun LtiSharedApp(
    modifier: Modifier = Modifier,
    openSettingsRequest: Long = 0,
    appSettingsState: AppSettingsState = koinInject(),
) {
    SharedApp(
        openSettingsRequest = openSettingsRequest,
        appSettingsState = appSettingsState,
        modifier = modifier,
    )
}

@Composable
private fun SharedApp(
    openSettingsRequest: Long,
    appSettingsState: AppSettingsState,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backdrop = rememberLayerBackdrop()

    LaunchedEffect(openSettingsRequest) {
        if (openSettingsRequest > 0) {
            navController.navigateToSettings()
        }
    }

    LtiTheme(
        appTheme = AppTheme.fromId(appSettingsState.theme),
        effectsEnabled = appSettingsState.effectsEnabled,
        reducedMotion = appSettingsState.reducedMotion,
        sharedBackdrop = backdrop,
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            GlassBackdrop(backdrop = backdrop)

            RootNavGraph(
                navHostController = navController,
                startDestination = SETUP_ROUTE,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
