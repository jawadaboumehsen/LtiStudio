/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mobile-wallet/blob/master/LICENSE.md
 */

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.LocalNucleusWindow
import dev.nucleusframework.application.NucleusWindow
import dev.nucleusframework.application.nucleusApplication
import org.ide.lti.core.designsystem.component.actions.LocalWindowControlActions
import org.ide.lti.core.designsystem.component.actions.WindowControlActions
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.desktop.key.WindowKeyShortcutHandler
import org.ide.lti.desktop.menu.dispatcher.DefaultStudioActionDispatcher
import org.ide.lti.desktop.menu.model.StudioAction
import org.ide.lti.desktop.menu.model.StudioMenuState
import org.ide.lti.desktop.menu.ui.StudioMenuBar
import org.ide.lti.desktop.tray.AppTray
import org.ide.lti.desktop.window.restoreWindow
import org.ide.lti.core.common.timing.StartupTiming
import org.ide.lti.shared.LtiSharedApp
import org.ide.lti.shared.di.initKoin

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

fun main(args: Array<String>) {
    StartupTiming.log("Process started")
    nucleusApplication(args) {
        remember {
            StartupTiming.log("Koin init started")
            initKoin()
            StartupTiming.log("Koin init finished")
        }
    var mainWinRef by remember { mutableStateOf<NucleusWindow?>(null) }
    var openSettingsRequest by remember { mutableLongStateOf(0L) }
    val windowState = rememberWindowState(
        width = ComponentSize.MaxPanelWidth * 2,
        height = ComponentSize.MaxPanelHeight * 1.4f,
    )
    val openSettingsInMainWindow: () -> Unit = {
        restoreWindow(windowState, mainWinRef)
        openSettingsRequest += 1
    }

    val actionDispatcher = remember { DefaultStudioActionDispatcher() }

    AppTray(
        onOpenStudio = { restoreWindow(windowState, mainWinRef) },
        onOpenSettings = openSettingsInMainWindow,
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
        remember {
            StartupTiming.log("DecoratedWindow composition entered")
        }
        val nucleusWin = LocalNucleusWindow.current
        val windowControlActions = buildWindowControlActions(
            windowState = windowState,
            nucleusWin = nucleusWin,
            onClose = ::exitApplication,
        )

        DisposableEffect(nucleusWin) {
            StartupTiming.log("DecoratedWindow native window attached")
            mainWinRef = nucleusWin
            onDispose { mainWinRef = null }
        }

        DisposableEffect(actionDispatcher, windowControlActions) {
            actionDispatcher.register(StudioAction.OPEN_SETTINGS) { openSettingsInMainWindow() }
            actionDispatcher.register(StudioAction.EXIT) { exitApplication() }
            actionDispatcher.register(StudioAction.MINIMIZE_WINDOW) { windowControlActions.onMinimize() }
            actionDispatcher.register(StudioAction.ZOOM_WINDOW) { windowControlActions.onMaximize() }
            actionDispatcher.register(StudioAction.BRING_ALL_TO_FRONT) {
                restoreWindow(windowState, nucleusWin)
            }
            onDispose {
                actionDispatcher.unregister(StudioAction.OPEN_SETTINGS)
                actionDispatcher.unregister(StudioAction.EXIT)
                actionDispatcher.unregister(StudioAction.MINIMIZE_WINDOW)
                actionDispatcher.unregister(StudioAction.ZOOM_WINDOW)
                actionDispatcher.unregister(StudioAction.BRING_ALL_TO_FRONT)
            }
        }

        CompositionLocalProvider(
            LocalWindowControlActions provides windowControlActions,
        ) {
            (this@DecoratedWindow as? FrameWindowScope)?.let { frameScope ->
                with(frameScope) {
                    StudioMenuBar(state = StudioMenuState(), dispatcher = actionDispatcher)
                }
            }
            LtiSharedApp(
                openSettingsRequest = openSettingsRequest,
            )
        }
    }
}
}
