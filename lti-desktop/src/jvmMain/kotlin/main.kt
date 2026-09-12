/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mobile-wallet/blob/master/LICENSE.md
 */

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import com.kashif_e.backdrop.backdrops.layerBackdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.LocalNucleusWindow
import dev.nucleusframework.application.NucleusWindow
import dev.nucleusframework.application.nucleusApplication
import androidx.compose.ui.window.FrameWindowScope
import org.ide.lti.core.data.settings.AppSettingsState
import org.ide.lti.core.designsystem.component.layout.GlassBackdrop
import org.ide.lti.core.designsystem.component.layout.GlassTopBar
import org.ide.lti.core.designsystem.component.layout.TopBarNavigation
import org.ide.lti.core.designsystem.component.actions.LocalWindowControlActions
import org.ide.lti.core.designsystem.component.actions.WindowControlActions
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.config.AppTheme
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.feature.settings.SettingsScreen
import org.ide.lti.desktop.di.toolingModule
import org.ide.lti.desktop.key.WindowKeyShortcutHandler
import org.ide.lti.desktop.menu.dispatcher.DefaultStudioActionDispatcher
import org.ide.lti.desktop.menu.model.StudioAction
import org.ide.lti.desktop.menu.model.StudioMenuState
import org.ide.lti.desktop.menu.ui.StudioMenuBar
import org.ide.lti.desktop.tray.AppTray
import org.ide.lti.desktop.window.restoreWindow
import org.ide.lti.feature.setup.splash.LtiRomSplashScreen
import org.ide.lti.feature.setup.splash.SplashInitializationBridge
import org.ide.lti.shared.LtiSharedApp
import org.ide.lti.shared.di.initKoin
import org.koin.compose.koinInject

private fun buildWindowControlActions(
    windowState: WindowState,
    nucleusWin: NucleusWindow,
    onClose: () -> Unit,
): WindowControlActions {
    val awtWin = nucleusWin.unsafe.awtWindow
    var dragStartMouseX = 0
    var dragStartMouseY = 0
    var dragStartWindowX = 0
    var dragStartWindowY = 0

    return WindowControlActions(
        onMinimize = {
            windowState.isMinimized = true
            try { nucleusWin.setMinimized(true) } catch (_: Throwable) {}
            try { awtWin?.extendedState = java.awt.Frame.ICONIFIED } catch (_: Throwable) {}
        },
        onMaximize = {
            windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                WindowPlacement.Floating
            } else {
                WindowPlacement.Maximized
            }
            try { nucleusWin.setMaximized(!nucleusWin.isMaximized) } catch (_: Throwable) {}
        },
        onClose = onClose,
        onDragWindow = {
            try { nucleusWin.unsafe.taoWindow?.dragWindow() } catch (_: Throwable) {}
            awtWin?.let { win ->
                val mouseLoc = java.awt.MouseInfo.getPointerInfo()?.location
                if (mouseLoc != null) {
                    dragStartMouseX = mouseLoc.x
                    dragStartMouseY = mouseLoc.y
                    dragStartWindowX = win.x
                    dragStartWindowY = win.y
                }
            }
        },
        onDragDelta = { _, _ ->
            if (windowState.placement != WindowPlacement.Maximized) {
                awtWin?.let { win ->
                    val mouseLoc = java.awt.MouseInfo.getPointerInfo()?.location
                    if (mouseLoc != null) {
                        val newX = dragStartWindowX + (mouseLoc.x - dragStartMouseX)
                        val newY = dragStartWindowY + (mouseLoc.y - dragStartMouseY)
                        win.setLocation(newX, newY)
                    }
                }
            }
        },
        isMaximized = {
            windowState.placement == WindowPlacement.Maximized ||
                try { nucleusWin.isMaximized } catch (_: Throwable) { false }
        },
    )
}

fun main(args: Array<String>) = nucleusApplication(args) {
    remember {
        initKoin {
            modules(toolingModule)
        }
    }
    var showSettingsWindow by remember { mutableStateOf(false) }
    var settingsWinRef by remember { mutableStateOf<NucleusWindow?>(null) }
    var mainWinRef by remember { mutableStateOf<NucleusWindow?>(null) }
    val windowState = rememberWindowState(
        width = ComponentSize.MaxPanelWidth * 2,
        height = ComponentSize.MaxPanelHeight * 1.4f,
    )
    val settingsWindowState = rememberWindowState(
        width = ComponentSize.SettingsWindowWidth,
        height = ComponentSize.SettingsWindowHeight,
    )

    val bringSettingsToFront: () -> Unit = {
        if (!showSettingsWindow) {
            showSettingsWindow = true
        } else {
            restoreWindow(settingsWindowState, settingsWinRef)
        }
    }

    val actionDispatcher = remember { DefaultStudioActionDispatcher() }

    var showSplash by remember { mutableStateOf(true) }
    val splashScope = rememberCoroutineScope()
    val splashBridge = remember { SplashInitializationBridge(scope = splashScope) }

    AppTray(
        onOpenStudio = { restoreWindow(windowState, mainWinRef) },
        onOpenSettings = bringSettingsToFront,
        onExit = ::exitApplication,
    )

    DecoratedWindow(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "LtiRom Studio",
        icon = painterResource("icons/ic_launcher.png"),
        minimumSize = DpSize(ComponentSize.MainWindowMinWidth, ComponentSize.MainWindowMinHeight),
        onPreviewKeyEvent = { event -> WindowKeyShortcutHandler.handleKeyEvent(event, actionDispatcher) },
    ) {
        val nucleusWin = LocalNucleusWindow.current
        val windowControlActions = buildWindowControlActions(
            windowState = windowState,
            nucleusWin = nucleusWin,
            onClose = ::exitApplication,
        )

        DisposableEffect(nucleusWin) {
            mainWinRef = nucleusWin
            onDispose { mainWinRef = null }
        }

        DisposableEffect(actionDispatcher, windowControlActions) {
            actionDispatcher.register(StudioAction.OPEN_SETTINGS) { bringSettingsToFront() }
            actionDispatcher.register(StudioAction.EXIT) { exitApplication() }
            actionDispatcher.register(StudioAction.MINIMIZE_WINDOW) { windowControlActions.onMinimize() }
            actionDispatcher.register(StudioAction.ZOOM_WINDOW) { windowControlActions.onMaximize() }
            actionDispatcher.register(StudioAction.BRING_ALL_TO_FRONT) {
                restoreWindow(windowState, nucleusWin)
                settingsWinRef?.let { restoreWindow(settingsWindowState, it) }
            }
            onDispose {
                actionDispatcher.unregister(StudioAction.OPEN_SETTINGS)
                actionDispatcher.unregister(StudioAction.EXIT)
                actionDispatcher.unregister(StudioAction.MINIMIZE_WINDOW)
                actionDispatcher.unregister(StudioAction.ZOOM_WINDOW)
                actionDispatcher.unregister(StudioAction.BRING_ALL_TO_FRONT)
            }
        }

        val appSettingsState: AppSettingsState = koinInject()

        CompositionLocalProvider(
            LocalWindowControlActions provides windowControlActions,
        ) {
            (this@DecoratedWindow as? FrameWindowScope)?.let { frameScope ->
                with(frameScope) {
                    StudioMenuBar(state = StudioMenuState(), dispatcher = actionDispatcher)
                }
            }
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
                LtiSharedApp(
                    onOpenSettings = bringSettingsToFront,
                )
            }
        }
    }

    if (showSettingsWindow) {
        DecoratedWindow(
            onCloseRequest = {
                showSettingsWindow = false
                settingsWinRef = null
            },
            state = settingsWindowState,
            title = "Settings",
            icon = painterResource("icons/ic_launcher.png"),
            resizable = true,
        ) {
            val settingsWin = LocalNucleusWindow.current
            DisposableEffect(settingsWin) {
                settingsWinRef = settingsWin
                onDispose {
                    settingsWinRef = null
                }
            }
            val appSettingsState: AppSettingsState = koinInject()

            CompositionLocalProvider(
                LocalWindowControlActions provides buildWindowControlActions(
                    windowState = settingsWindowState,
                    nucleusWin = settingsWin,
                    onClose = {
                        showSettingsWindow = false
                        settingsWinRef = null
                    },
                ),
            ) {
                val backdrop = rememberLayerBackdrop()

                LtiTheme(
                    appTheme = AppTheme.fromId(appSettingsState.theme),
                    effectsEnabled = appSettingsState.effectsEnabled,
                    reducedMotion = appSettingsState.reducedMotion,
                    sharedBackdrop = backdrop,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        GlassBackdrop(backdrop = backdrop)

                        Column(
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            GlassTopBar(
                                navigation = TopBarNavigation.Back(
                                    onClick = { showSettingsWindow = false },
                                ),
                                search = null,
                                panelToggles = null,
                                backdrop = backdrop,
                                drawOwnChrome = true,
                                centerContent = {
                                    Text(
                                        text = "Preferences",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = GlassTheme.colors.onGlass,
                                    )
                                },
                            )

                            SettingsScreen(
                                onBackClick = { showSettingsWindow = false },
                                modifier = Modifier.weight(1f),
                                showHeader = false,
                                backdrop = backdrop,
                                appSettingsState = appSettingsState,
                            )
                        }
                    }
                }
            }
        }
    }
}
