/*
 * Copyright 2024-2026 Mifos Initiative
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.navigation.compose.rememberNavController
import org.ide.lti.core.common.timing.StartupTiming
import org.ide.lti.core.designsystem.component.layout.GlassBackdrop
import org.ide.lti.core.designsystem.theme.AccentColor
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.Appearance
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassSceneHost
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.domain.desktop.BackgroundSessionManager
import org.ide.lti.core.domain.desktop.DropOverlayState
import org.ide.lti.core.domain.desktop.WorkspaceDropCoordinator
import org.ide.lti.core.ui.desktop.DetachablePanelOpener
import org.ide.lti.core.ui.desktop.LocalBackgroundSessionManager
import org.ide.lti.core.ui.desktop.LocalDetachablePanelOpener
import org.ide.lti.core.ui.desktop.LocalSessionDrawerController
import org.ide.lti.core.ui.desktop.LocalWorkspaceDropCoordinator
import org.ide.lti.core.ui.drop.DropIngestOverlay
import org.ide.lti.core.ui.sessions.BackgroundSessionDrawer
import org.ide.lti.core.ui.settings.AppSettingsState
import org.ide.lti.feature.settings.navigateToSettings
import org.ide.lti.feature.setup.SETUP_ROUTE
import org.ide.lti.shared.navigation.RootNavGraph
import org.koin.compose.koinInject

@Composable
fun LtiSharedApp(
    modifier: Modifier = Modifier,
    openSettingsRequest: Long = 0,
    appSettingsState: AppSettingsState = koinInject(),
    /** Dialogs the window itself raises: they need the theme as much as any screen does. */
    windowDialogs: @Composable () -> Unit = {},
    backgroundSessionManager: BackgroundSessionManager? = null,
    workspaceDropCoordinator: WorkspaceDropCoordinator? = null,
    detachablePanelOpener: DetachablePanelOpener? = null,
    isSessionDrawerOpen: Boolean? = null,
    onSessionDrawerOpenChange: ((Boolean) -> Unit)? = null,
) {
    var internalDrawerOpen by remember { mutableStateOf(false) }
    val drawerOpen = isSessionDrawerOpen ?: internalDrawerOpen
    val toggleDrawer = {
        val next = !drawerOpen
        if (onSessionDrawerOpenChange != null) {
            onSessionDrawerOpenChange(next)
        } else {
            internalDrawerOpen = next
        }
    }
    val closeDrawer = {
        if (onSessionDrawerOpenChange != null) {
            onSessionDrawerOpenChange(false)
        } else {
            internalDrawerOpen = false
        }
    }

    val appTheme = AppTheme.fromId(appSettingsState.theme)
    val dropState by workspaceDropCoordinator?.state?.collectAsState()
        ?: remember { mutableStateOf(DropOverlayState()) }

    CompositionLocalProvider(
        LocalBackgroundSessionManager provides backgroundSessionManager,
        LocalWorkspaceDropCoordinator provides workspaceDropCoordinator,
        LocalDetachablePanelOpener provides detachablePanelOpener,
        LocalSessionDrawerController provides toggleDrawer,
    ) {
        GlassSceneHost(
            effectsEnabled = appSettingsState.effectsEnabled,
        ) {
            LtiTheme(
                appTheme = appTheme,
                effectsEnabled = appSettingsState.effectsEnabled,
                reducedMotion = appSettingsState.reducedMotion,
                appearance = appSettingsState.appearance.let {
                    Appearance(AccentColor.fromId(it.accent), it.compact, it.textScalePercent / PERCENT)
                },
            ) {
                Box(
                    modifier = modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    // Continuous native backdrop
                    GlassBackdrop()

                    // Render main destination directly on Frame 1 without splash delay
                    SharedApp(
                        openSettingsRequest = openSettingsRequest,
                        modifier = Modifier.fillMaxSize(),
                    )
                    windowDialogs()

                    if (drawerOpen && backgroundSessionManager != null) {
                        val sessionSnapshot by backgroundSessionManager.state.collectAsState()
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = Spacing.Large, bottom = Spacing.ExtraExtraLarge)
                                .widthIn(max = ComponentSize.StudioDialogMaxWidth),
                        ) {
                            BackgroundSessionDrawer(
                                snapshot = sessionSnapshot,
                                onJump = { target ->
                                    backgroundSessionManager.requestJump(target)
                                    closeDrawer()
                                },
                                onCancelSession = backgroundSessionManager::cancelSession,
                                onDismissSession = backgroundSessionManager::dismissSession,
                                onDismissAlert = backgroundSessionManager::dismissAlert,
                                onClearCompleted = backgroundSessionManager::clearCompleted,
                                onMarkAllRead = backgroundSessionManager::markAllAlertsRead,
                                onClose = closeDrawer,
                            )
                        }
                    }

                    if (workspaceDropCoordinator != null) {
                        DropIngestOverlay(
                            state = dropState,
                            onDismissBanner = workspaceDropCoordinator::clearBanner,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedApp(openSettingsRequest: Long, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    var firstLayoutDone by remember { mutableStateOf(false) }
    var firstFrameDrawn by remember { mutableStateOf(false) }

    LaunchedEffect(openSettingsRequest) {
        if (openSettingsRequest > 0) {
            navController.navigateToSettings()
        }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned {
                if (!firstLayoutDone) {
                    firstLayoutDone = true
                    StartupTiming.log("Destination first layout complete (onGloballyPositioned)")
                }
            }
            .drawWithContent {
                drawContent()
                if (!firstFrameDrawn) {
                    firstFrameDrawn = true
                    StartupTiming.log("Destination first frame drawn (drawWithContent)")
                }
            },
    ) {
        RootNavGraph(
            navHostController = navController,
            startDestination = SETUP_ROUTE,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private const val PERCENT = 100f
