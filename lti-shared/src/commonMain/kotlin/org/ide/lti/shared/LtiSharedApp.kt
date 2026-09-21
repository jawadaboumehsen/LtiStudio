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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import org.ide.lti.core.designsystem.component.layout.GlassBackdrop
import org.ide.lti.core.designsystem.component.layout.StudioGlassBackdrop
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.config.AppTheme
import org.ide.lti.core.designsystem.theme.glass.rememberGlassSceneState
import org.ide.lti.core.ui.settings.AppSettingsState
import org.ide.lti.feature.settings.navigateToSettings
import org.ide.lti.feature.setup.SETUP_ROUTE
import org.ide.lti.feature.setup.splash.LtiRomSplashScreen
import org.ide.lti.feature.setup.splash.SplashInitializationBridge
import org.ide.lti.shared.navigation.RootNavGraph
import org.koin.compose.koinInject

@Composable
fun LtiSharedApp(
    modifier: Modifier = Modifier,
    openSettingsRequest: Long = 0,
    appSettingsState: AppSettingsState = koinInject(),
) {
    var showSplash by remember { mutableStateOf(true) }
    val splashScope = rememberCoroutineScope()
    val splashBridge = remember { SplashInitializationBridge(scope = splashScope) }

    if (showSplash) {
        LtiTheme(
            appTheme = AppTheme.fromId(appSettingsState.theme),
            effectsEnabled = appSettingsState.effectsEnabled,
            reducedMotion = appSettingsState.reducedMotion,
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(GlassTheme.colors.canvasSurface),
                contentAlignment = Alignment.Center,
            ) {
                LtiRomSplashScreen(
                    bridge = splashBridge,
                    onDismissed = { showSplash = false },
                )
            }
        }
    } else {
        SharedApp(
            openSettingsRequest = openSettingsRequest,
            appSettingsState = appSettingsState,
            modifier = modifier,
        )
    }
}

@Composable
private fun SharedApp(openSettingsRequest: Long, appSettingsState: AppSettingsState, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backdrop = rememberGlassSceneState()

    LaunchedEffect(openSettingsRequest) {
        if (openSettingsRequest > 0) {
            navController.navigateToSettings()
        }
    }

    val appTheme = AppTheme.fromId(appSettingsState.theme)

    LtiTheme(
        appTheme = appTheme,
        effectsEnabled = appSettingsState.effectsEnabled,
        reducedMotion = appSettingsState.reducedMotion,
        sharedScene = backdrop,
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            if (appTheme == AppTheme.Blue) {
                StudioGlassBackdrop(backdrop = backdrop)
            } else {
                GlassBackdrop(backdrop = backdrop)
            }

            RootNavGraph(
                navHostController = navController,
                startDestination = SETUP_ROUTE,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
