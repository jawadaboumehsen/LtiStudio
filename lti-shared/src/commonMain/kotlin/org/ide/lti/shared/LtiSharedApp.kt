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
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import org.ide.lti.core.data.settings.AppSettingsState
import org.ide.lti.core.designsystem.component.CosmicAuroraBackdrop
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.feature.setup.SETUP_ROUTE
import org.ide.lti.shared.navigation.RootNavGraph
import org.koin.compose.koinInject

@Composable
fun LtiSharedApp(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    appSettingsState: AppSettingsState = koinInject(),
) {
    SharedApp(
        onOpenSettings = onOpenSettings,
        appSettingsState = appSettingsState,
        modifier = modifier,
    )
}

@Composable
private fun SharedApp(
    onOpenSettings: () -> Unit,
    appSettingsState: AppSettingsState,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backdrop = rememberLayerBackdrop()

    LtiTheme(
        darkTheme = appSettingsState.isDarkTheme,
        effectsEnabled = appSettingsState.effectsEnabled,
        reducedMotion = appSettingsState.reducedMotion,
        sharedBackdrop = backdrop,
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            CosmicAuroraBackdrop(backdrop = backdrop)

            RootNavGraph(
                navHostController = navController,
                startDestination = SETUP_ROUTE,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
