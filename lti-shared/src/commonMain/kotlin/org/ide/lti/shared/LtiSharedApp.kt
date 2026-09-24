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
import androidx.compose.material3.MaterialTheme
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
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.GlassSceneHost
import org.ide.lti.core.designsystem.theme.LtiTheme
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
    lowMemory: Boolean = false,
    initialShowSplash: Boolean = true,
    appSettingsState: AppSettingsState = koinInject(),
) {
    var showSplash by remember { mutableStateOf(initialShowSplash) }
    val splashScope = rememberCoroutineScope()
    val splashBridge = remember { SplashInitializationBridge(scope = splashScope) }

    val appTheme = AppTheme.fromId(appSettingsState.theme)

    GlassSceneHost(
        effectsEnabled = appSettingsState.effectsEnabled,
    ) {
        LtiTheme(
            appTheme = appTheme,
            effectsEnabled = appSettingsState.effectsEnabled,
            reducedMotion = appSettingsState.reducedMotion,
        ) {
            if (showSplash) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    LtiRomSplashScreen(
                        bridge = splashBridge,
                        onDismissed = { showSplash = false },
                    )
                }
            } else {
                SharedApp(
                    openSettingsRequest = openSettingsRequest,
                    lowMemory = lowMemory,
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun SharedApp(
    openSettingsRequest: Long,
    lowMemory: Boolean,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    LaunchedEffect(openSettingsRequest) {
        if (openSettingsRequest > 0) {
            navController.navigateToSettings()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GlassBackdrop()
        RootNavGraph(
            navHostController = navController,
            startDestination = SETUP_ROUTE,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
