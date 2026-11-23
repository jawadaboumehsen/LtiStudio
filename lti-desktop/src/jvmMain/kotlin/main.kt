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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import org.ide.lti.core.common.timing.StartupTiming
import org.ide.lti.core.common.utils.runCatchingCancellable
import org.ide.lti.core.designsystem.component.actions.LocalWindowControlActions
import org.ide.lti.core.designsystem.component.actions.WindowControlActions
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.domain.tools.AppShutdownPort
import org.ide.lti.desktop.dialog.StudioSmartToolsDialogs
import org.ide.lti.desktop.key.WindowKeyShortcutHandler
import org.ide.lti.desktop.menu.dispatcher.DefaultStudioActionDispatcher
import org.ide.lti.desktop.menu.model.StudioAction
import org.ide.lti.desktop.menu.model.StudioMenuState
import org.ide.lti.desktop.menu.ui.StudioMenuBar
import org.ide.lti.desktop.tray.AppTray
import org.ide.lti.desktop.window.restoreWindow
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
    var isDragging = false

    return WindowControlActions(
        onMinimize = {
            windowState.isMinimized = true
            try {
                nucleusWin.setMinimized(true)
            } catch (_: Throwable) {
            }
            try {
                awtWin?.extendedState = java.awt.Frame.ICONIFIED
            } catch (_: Throwable) {
            }
        },
        onMaximize = {
            windowState.placement =
                if (windowState.placement == WindowPlacement.Maximized) {
                    WindowPlacement.Floating
                } else {
                    WindowPlacement.Maximized
                }
            try {
                nucleusWin.setMaximized(!nucleusWin.isMaximized)
            } catch (_: Throwable) {
            }
        },
        onClose = onClose,
        onDragWindow = {
            isDragging = true
            try {
                nucleusWin.unsafe.taoWindow?.dragWindow()
            } catch (_: Throwable) {
            }
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
        onDragDelta = { dx, dy ->
            if (isDragging) {
                awtWin?.let { win ->
                    val mouseLoc = java.awt.MouseInfo.getPointerInfo()?.location
                    if (windowState.placement == WindowPlacement.Maximized) {
                        if (mouseLoc != null && dragStartMouseY != 0 && (mouseLoc.y - dragStartMouseY) > 15) {
                            windowState.placement = WindowPlacement.Floating
                            try {
                                nucleusWin.setMaximized(false)
                            } catch (_: Throwable) {
                            }
                            dragStartWindowX = mouseLoc.x - (win.width / 2)
                            dragStartWindowY = mouseLoc.y - 15
                            dragStartMouseX = mouseLoc.x
                            dragStartMouseY = mouseLoc.y
                            win.setLocation(dragStartWindowX, dragStartWindowY)
                        }
                    } else {
                        if (mouseLoc != null && (dragStartMouseX != 0 || dragStartMouseY != 0)) {
                            val newX = dragStartWindowX + (mouseLoc.x - dragStartMouseX)
                            val newY = dragStartWindowY + (mouseLoc.y - dragStartMouseY)
                            win.setLocation(newX, newY)
                        } else if (dx != 0f || dy != 0f) {
                            win.setLocation(win.x + dx.toInt(), win.y + dy.toInt())
                        }
                    }
                }
            }
        },
        onDragEnd = {
            isDragging = false
            dragStartMouseX = 0
            dragStartMouseY = 0
        },
        isMaximized = {
            windowState.placement == WindowPlacement.Maximized ||
                try {
                    nucleusWin.isMaximized
                } catch (_: Throwable) {
                    false
                }
        },
    )
}

@Suppress("DEPRECATION")
fun main(args: Array<String>) {
    StartupTiming.log("Process started")
    val instance = SingleInstanceGuard.acquireOrExplain(SingleInstanceGuard.defaultLockFile())
    if (instance == null) {
        System.err.println("LtiRom Studio is already running; close the other window first.")
        kotlin.system.exitProcess(1)
    }
    nucleusApplication(args) {
        remember {
            StartupTiming.log("Koin init started")
            initKoin()
            StartupTiming.log("Koin init finished")
        }
        var mainWinRef by remember { mutableStateOf<NucleusWindow?>(null) }
        var openSettingsRequest by remember { mutableLongStateOf(0L) }
        val windowState =
            rememberWindowState(
                width = ComponentSize.MaxPanelWidth * 2,
                height = ComponentSize.MaxPanelHeight * 1.4f,
            )
        val openSettingsInMainWindow: () -> Unit = {
            restoreWindow(windowState, mainWinRef)
            openSettingsRequest += 1
        }

        val actionDispatcher = remember { DefaultStudioActionDispatcher() }
        val coroutineScope = rememberCoroutineScope()
        var isShuttingDown by remember { mutableStateOf(false) }
        var unconfirmedShutdown by remember {
            mutableStateOf<org.ide.lti.core.domain.tools.ShutdownReport.Unconfirmed?>(
                null,
            )
        }

        val performShutdown: () -> Unit = {
            coroutineScope.launch {
                try {
                    val coordinator =
                        org.koin.core.context.GlobalContext.getOrNull()
                            ?.getOrNull<AppShutdownPort>()
                    val report =
                        kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                            coordinator?.shutdown()
                        } ?: org.ide.lti.core.domain.tools.ShutdownReport.Unconfirmed(emptyList())

                    when (report) {
                        is org.ide.lti.core.domain.tools.ShutdownReport.Stopped -> {
                            exitApplication()
                        }

                        is org.ide.lti.core.domain.tools.ShutdownReport.Unconfirmed -> {
                            unconfirmedShutdown = report
                        }
                    }
                } catch (
                    @Suppress("TooGenericExceptionCaught") _: Exception,
                ) {
                    unconfirmedShutdown = org.ide.lti.core.domain.tools.ShutdownReport.Unconfirmed(emptyList())
                }
            }
        }

        var buildsAtExit by remember { mutableStateOf(emptyList<String>()) }
        var isCommandPaletteOpen by remember { mutableStateOf(false) }
        var isCopilotOpen by remember { mutableStateOf(false) }
        var isSuperBudgeterOpen by remember { mutableStateOf(false) }
        var isDiffViewerOpen by remember { mutableStateOf(false) }
        var isPartitionExplorerOpen by remember { mutableStateOf(false) }
        var isMacroStudioOpen by remember { mutableStateOf(false) }

        val commandRegistry = remember<org.ide.lti.core.domain.command.CommandPaletteRegistry> {
            org.ide.lti.core.ui.command.DefaultStudioCommands.createDefaultRegistry(
                onNavigateSettings = openSettingsInMainWindow,
                onToggleTerminal = { actionDispatcher.dispatch(StudioAction.TOGGLE_TERMINAL) },
                onToggleExplorer = { actionDispatcher.dispatch(StudioAction.TOGGLE_EXPLORER) },
                onToggleAiAssistant = { actionDispatcher.dispatch(StudioAction.TOGGLE_AI_ASSISTANT) },
                onOpenCopilot = { isCopilotOpen = true },
                onOpenSuperBudgeter = { isSuperBudgeterOpen = true },
                onOpenDiffViewer = { isDiffViewerOpen = true },
                onOpenPartitionExplorer = { isPartitionExplorerOpen = true },
                onOpenMacroStudio = { isMacroStudioOpen = true },
            )
        }

        val requestExit: () -> Unit = {
            if (!isShuttingDown) {
                isShuttingDown = true
                coroutineScope.launch {
                    // Asking must not stop the app from closing: no answer means no warning.
                    val builds = runCatchingCancellable {
                        kotlinx.coroutines.withTimeoutOrNull(5_000L) {
                            org.koin.core.context.GlobalContext.getOrNull()
                                ?.getOrNull<AppShutdownPort>()
                                ?.activeWork()
                        }
                    }.getOrNull().orEmpty()
                    if (builds.isEmpty()) {
                        performShutdown()
                    } else {
                        // Tray Exit comes here with the window hidden, and the question is asked in it.
                        restoreWindow(windowState, mainWinRef)
                        buildsAtExit = builds
                    }
                }
            }
        }

        AppTray(
            onOpenStudio = { restoreWindow(windowState, mainWinRef) },
            onOpenSettings = openSettingsInMainWindow,
            onExit = requestExit,
        )

        DecoratedWindow(
            onCloseRequest = requestExit,
            state = windowState,
            title = "LtiRom Studio",
            icon = painterResource("icons/ic_launcher.png"),
            minimumSize = DpSize(ComponentSize.MainWindowMinWidth, ComponentSize.MainWindowMinHeight),
            onPreviewKeyEvent = { event ->
                WindowKeyShortcutHandler.handleKeyEvent(
                    event = event,
                    dispatcher = actionDispatcher,
                    onDismissOverlay = {
                        if (isCommandPaletteOpen) {
                            isCommandPaletteOpen = false
                            true
                        } else if (isCopilotOpen) {
                            isCopilotOpen = false
                            true
                        } else if (isSuperBudgeterOpen) {
                            isSuperBudgeterOpen = false
                            true
                        } else {
                            false
                        }
                    },
                )
            },
        ) {
            remember {
                StartupTiming.log("DecoratedWindow composition entered")
            }
            val nucleusWin = LocalNucleusWindow.current
            val windowControlActions = remember(nucleusWin, windowState) {
                buildWindowControlActions(
                    windowState = windowState,
                    nucleusWin = nucleusWin,
                    onClose = requestExit,
                )
            }

            DisposableEffect(nucleusWin) {
                StartupTiming.log("DecoratedWindow native window attached")
                mainWinRef = nucleusWin
                onDispose { mainWinRef = null }
            }

            DisposableEffect(actionDispatcher, windowControlActions) {
                actionDispatcher.register(StudioAction.OPEN_SETTINGS) { openSettingsInMainWindow() }
                actionDispatcher.register(StudioAction.EXIT) { requestExit() }
                actionDispatcher.register(StudioAction.MINIMIZE_WINDOW) { windowControlActions.onMinimize() }
                actionDispatcher.register(StudioAction.ZOOM_WINDOW) { windowControlActions.onMaximize() }
                actionDispatcher.register(StudioAction.BRING_ALL_TO_FRONT) {
                    restoreWindow(windowState, nucleusWin)
                }
                actionDispatcher.register(StudioAction.TOGGLE_COMMAND_PALETTE) {
                    isCommandPaletteOpen = !isCommandPaletteOpen
                }
                onDispose {
                    actionDispatcher.unregister(StudioAction.OPEN_SETTINGS)
                    actionDispatcher.unregister(StudioAction.EXIT)
                    actionDispatcher.unregister(StudioAction.MINIMIZE_WINDOW)
                    actionDispatcher.unregister(StudioAction.ZOOM_WINDOW)
                    actionDispatcher.unregister(StudioAction.BRING_ALL_TO_FRONT)
                    actionDispatcher.unregister(StudioAction.TOGGLE_COMMAND_PALETTE)
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
                    windowDialogs = {
                        if (buildsAtExit.isNotEmpty()) {
                            val stay = {
                                buildsAtExit = emptyList()
                                isShuttingDown = false
                            }
                            org.ide.lti.core.designsystem.component.feedback.GlassDialog(
                                onDismissRequest = stay,
                                title = "Work is still running",
                                confirmButton = {
                                    org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton(onClick = stay) {
                                        androidx.compose.material3.Text("Keep the app open")
                                    }
                                },
                                dismissButton = {
                                    org.ide.lti.core.designsystem.component.actions.GlassTextButton(
                                        onClick = {
                                            buildsAtExit = emptyList()
                                            performShutdown()
                                        },
                                    ) {
                                        androidx.compose.material3.Text("Close anyway")
                                    }
                                },
                            ) {
                                androidx.compose.material3.Text(
                                    text = buildsAtExit.joinToString(", ") +
                                        ": closing the app stops it. Starting it again reuses the work that is " +
                                        "still valid.",
                                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }

                        unconfirmedShutdown?.let { report ->
                            org.ide.lti.core.designsystem.component.feedback.GlassDialog(
                                onDismissRequest = {
                                    // Nondismissible: must choose Retry Shutdown or Exit Anyway
                                },
                                title = "Shutdown Unconfirmed",
                                confirmButton = {
                                    org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton(
                                        onClick = {
                                            unconfirmedShutdown = null
                                            performShutdown()
                                        },
                                    ) {
                                        androidx.compose.material3.Text("Retry Shutdown")
                                    }
                                },
                                dismissButton = {
                                    org.ide.lti.core.designsystem.component.actions.GlassTextButton(
                                        onClick = {
                                            val coordinator =
                                                org.koin.core.context.GlobalContext.getOrNull()
                                                    ?.getOrNull<AppShutdownPort>()
                                            coroutineScope.launch {
                                                coordinator?.forceShutdown()
                                                exitApplication()
                                            }
                                        },
                                    ) {
                                        androidx.compose.material3.Text("Exit Anyway")
                                    }
                                },
                            ) {
                                androidx.compose.foundation.layout.Column(
                                    verticalArrangement =
                                    androidx.compose.foundation.layout.Arrangement.spacedBy(
                                        org.ide.lti.core.designsystem.theme.Spacing.Small,
                                    ),
                                ) {
                                    val ids = report.executionIds
                                    val message =
                                        if (ids.isEmpty()) {
                                            "Background executions or build services did not confirm clean termination. Exiting now may leave processes running in WSL."
                                        } else {
                                            "The following executions did not confirm termination: ${ids.joinToString(
                                                ", ",
                                            )}. Exiting now may leave background processes running in WSL."
                                        }
                                    androidx.compose.material3.Text(
                                        text = message,
                                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }

                        StudioSmartToolsDialogs(
                            isCommandPaletteOpen = isCommandPaletteOpen,
                            onDismissCommandPalette = { isCommandPaletteOpen = false },
                            commandRegistry = commandRegistry,
                            isCopilotOpen = isCopilotOpen,
                            onDismissCopilot = { isCopilotOpen = false },
                            isSuperBudgeterOpen = isSuperBudgeterOpen,
                            onDismissSuperBudgeter = { isSuperBudgeterOpen = false },
                            isDiffViewerOpen = isDiffViewerOpen,
                            onDismissDiffViewer = { isDiffViewerOpen = false },
                            isPartitionExplorerOpen = isPartitionExplorerOpen,
                            onDismissPartitionExplorer = { isPartitionExplorerOpen = false },
                            isMacroStudioOpen = isMacroStudioOpen,
                            onDismissMacroStudio = { isMacroStudioOpen = false },
                        )
                    },
                )
            }
        }
    }
}
