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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.ide.lti.core.common.timing.StartupTiming
import org.ide.lti.core.common.utils.runCatchingCancellable
import org.ide.lti.core.designsystem.component.actions.LocalWindowControlActions
import org.ide.lti.core.designsystem.component.actions.WindowControlActions
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.domain.desktop.BackgroundSessionJumpTarget
import org.ide.lti.core.domain.desktop.BackgroundSessionManager
import org.ide.lti.core.domain.desktop.WorkspaceDropCoordinator
import org.ide.lti.core.domain.repository.device.ConnectedDeviceRepository
import org.ide.lti.core.domain.tools.AppShutdownPort
import org.ide.lti.core.domain.usecase.build.ObserveRunUseCase
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.ui.desktop.DetachablePanelId
import org.ide.lti.core.ui.desktop.DetachablePanelOpener
import org.ide.lti.core.ui.settings.AppSettingsState
import org.ide.lti.desktop.dialog.StudioSmartToolsDialogs
import org.ide.lti.desktop.drop.AwtFileDropBridge
import org.ide.lti.desktop.key.WindowKeyShortcutHandler
import org.ide.lti.desktop.menu.dispatcher.DefaultStudioActionDispatcher
import org.ide.lti.desktop.menu.model.StudioAction
import org.ide.lti.desktop.menu.model.StudioMenuState
import org.ide.lti.desktop.menu.ui.StudioMenuBar
import org.ide.lti.desktop.tray.AppTray
import org.ide.lti.desktop.tray.DesktopOs
import org.ide.lti.desktop.tray.rememberClasspathPainter
import org.ide.lti.desktop.window.DetachedPanelWindowsState
import org.ide.lti.desktop.window.DetachedToolWindows
import org.ide.lti.desktop.window.restoreWindow
import org.ide.lti.shared.LtiSharedApp
import org.ide.lti.shared.di.initKoin
import org.koin.core.context.GlobalContext
import kotlin.time.Duration.Companion.seconds

private fun buildWindowControlActions(
    windowState: WindowState,
    window: ComposeWindow,
    onClose: () -> Unit,
): WindowControlActions {
    var dragStartMouseX = 0
    var dragStartMouseY = 0
    var dragStartWindowX = 0
    var dragStartWindowY = 0
    var isDragging = false

    return WindowControlActions(
        onMinimize = {
            windowState.isMinimized = true
            try {
                window.extendedState = window.extendedState or java.awt.Frame.ICONIFIED
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
        },
        onClose = onClose,
        onDragWindow = {
            isDragging = true
            val mouseLoc = java.awt.MouseInfo.getPointerInfo()?.location
            if (mouseLoc != null) {
                dragStartMouseX = mouseLoc.x
                dragStartMouseY = mouseLoc.y
                dragStartWindowX = window.x
                dragStartWindowY = window.y
            }
        },
        onDragDelta = { dx, dy ->
            if (isDragging) {
                val mouseLoc = java.awt.MouseInfo.getPointerInfo()?.location
                if (windowState.placement == WindowPlacement.Maximized) {
                    if (mouseLoc != null && dragStartMouseY != 0 && (mouseLoc.y - dragStartMouseY) > 15) {
                        windowState.placement = WindowPlacement.Floating
                        dragStartWindowX = mouseLoc.x - (window.width / 2)
                        dragStartWindowY = mouseLoc.y - 15
                        dragStartMouseX = mouseLoc.x
                        dragStartMouseY = mouseLoc.y
                        window.setLocation(dragStartWindowX, dragStartWindowY)
                    }
                } else {
                    if (mouseLoc != null && (dragStartMouseX != 0 || dragStartMouseY != 0)) {
                        val newX = dragStartWindowX + (mouseLoc.x - dragStartMouseX)
                        val newY = dragStartWindowY + (mouseLoc.y - dragStartMouseY)
                        window.setLocation(newX, newY)
                    } else if (dx != 0f || dy != 0f) {
                        window.setLocation(window.x + dx.toInt(), window.y + dy.toInt())
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
                (window.extendedState and java.awt.Frame.MAXIMIZED_BOTH) != 0
        },
    )
}

private const val SYNCED_RUNS_LIMIT = 5

fun main() {
    StartupTiming.log("Process started")
    val instance = SingleInstanceGuard.acquireOrExplain(SingleInstanceGuard.defaultLockFile())
    if (instance == null) {
        System.err.println("LtiRom Studio is already running; close the other window first.")
        kotlin.system.exitProcess(1)
    }
    // Load the persisted theme and preferences before starting the AWT event loop.
    // The shared settings file also contains large workspace records; parsing it in
    // composition blocks the event thread before it can paint or handle input.
    StartupTiming.log("Koin init started")
    initKoin()
    StartupTiming.log("Koin init finished")
    val initialAppSettings = GlobalContext.get().get<AppSettingsState>()
    StartupTiming.log("Preferences loaded")
    application {
        var mainWinRef by remember { mutableStateOf<java.awt.Window?>(null) }
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
                        kotlinx.coroutines.withTimeoutOrNull(10.seconds) {
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
        var isSessionDrawerOpen by remember { mutableStateOf(false) }
        var detachedPanelsState by remember { mutableStateOf(DetachedPanelWindowsState()) }

        val backgroundSessionManager = remember {
            org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<BackgroundSessionManager>()
        }
        val workspaceDropCoordinator = remember {
            org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<WorkspaceDropCoordinator>()
        }
        val panelOpener = remember {
            DetachablePanelOpener { panel, argument ->
                detachedPanelsState = detachedPanelsState.openPanel(panel, argument)
            }
        }

        LaunchedEffect(workspaceDropCoordinator) {
            val drop = workspaceDropCoordinator ?: return@LaunchedEffect
            launch {
                drop.partitionOpenRequests.collect { imagePath ->
                    detachedPanelsState = detachedPanelsState.openPanel(
                        DetachablePanelId.PARTITION_EXPLORER,
                        imagePath,
                    )
                }
            }
            launch {
                drop.apkAuditRequests.collect { apkPath ->
                    detachedPanelsState = detachedPanelsState.openPanel(
                        DetachablePanelId.DIFF_INSPECTOR,
                        apkPath,
                    )
                }
            }
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        LaunchedEffect(backgroundSessionManager) {
            val sessions = backgroundSessionManager ?: return@LaunchedEffect
            val koin = org.koin.core.context.GlobalContext.getOrNull()
            val devicesRepo = koin?.getOrNull<ConnectedDeviceRepository>()
            val wsManager = koin?.getOrNull<WorkspaceManager>()
            val observeRuns = koin?.getOrNull<ObserveRunUseCase>()

            if (devicesRepo != null) {
                launch {
                    devicesRepo.getConnectedDevices().collect { devices ->
                        sessions.syncConnectedDevices(devices)
                    }
                }
            }
            if (wsManager != null && observeRuns != null) {
                launch {
                    wsManager.currentWorkspace
                        .flatMapLatest { ws ->
                            if (ws == null) flowOf(emptyList()) else observeRuns.observeRuns(ws.id)
                        }
                        .collect { runs ->
                            runs.sortedByDescending { it.startedAt }.take(SYNCED_RUNS_LIMIT).forEach { run ->
                                sessions.syncBuildRun(run)
                            }
                        }
                }
            }
            sessions.jumpRequests.collect { target ->
                when (target) {
                    is BackgroundSessionJumpTarget.PartitionExplorer -> {
                        detachedPanelsState = detachedPanelsState.openPanel(
                            DetachablePanelId.PARTITION_EXPLORER,
                            target.imagePath,
                        )
                    }

                    BackgroundSessionJumpTarget.DiffInspector -> {
                        detachedPanelsState = detachedPanelsState.openPanel(
                            DetachablePanelId.DIFF_INSPECTOR,
                            null,
                        )
                    }

                    else -> restoreWindow(windowState, mainWinRef)
                }
            }
        }

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
                onToggleSessionDrawer = { isSessionDrawerOpen = !isSessionDrawerOpen },
                onPopOutCopilot = { panelOpener.open(DetachablePanelId.LOGCAT_COPILOT, null) },
                onPopOutPartitionExplorer = { panelOpener.open(DetachablePanelId.PARTITION_EXPLORER, null) },
                onPopOutDiffInspector = { panelOpener.open(DetachablePanelId.DIFF_INSPECTOR, null) },
            )
        }

        val requestExit: () -> Unit = {
            if (!isShuttingDown) {
                isShuttingDown = true
                coroutineScope.launch {
                    // Asking must not stop the app from closing: no answer means no warning.
                    val builds = runCatchingCancellable {
                        kotlinx.coroutines.withTimeoutOrNull(5.seconds) {
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

        Window(
            onCloseRequest = requestExit,
            state = windowState,
            title = "LtiRom Studio",
            icon = rememberClasspathPainter("icons/ic_launcher.png"),
            undecorated = true,
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
                StartupTiming.log("MainWindow composition entered")
            }
            val composeWindow = window
            val windowControlActions = remember(composeWindow, windowState) {
                buildWindowControlActions(
                    windowState = windowState,
                    window = composeWindow,
                    onClose = requestExit,
                )
            }

            DisposableEffect(composeWindow, workspaceDropCoordinator) {
                StartupTiming.log("MainWindow native window attached")
                mainWinRef = composeWindow
                composeWindow.minimumSize = java.awt.Dimension(
                    ComponentSize.MainWindowMinWidth.value.toInt(),
                    ComponentSize.MainWindowMinHeight.value.toInt(),
                )
                val detachDrop = workspaceDropCoordinator?.let { coordinator ->
                    AwtFileDropBridge.attach(composeWindow, coordinator, coroutineScope)
                }
                onDispose {
                    detachDrop?.invoke()
                    mainWinRef = null
                }
            }

            DisposableEffect(actionDispatcher, windowControlActions) {
                actionDispatcher.register(StudioAction.OPEN_SETTINGS) { openSettingsInMainWindow() }
                actionDispatcher.register(StudioAction.EXIT) { requestExit() }
                actionDispatcher.register(StudioAction.MINIMIZE_WINDOW) { windowControlActions.onMinimize() }
                actionDispatcher.register(StudioAction.ZOOM_WINDOW) { windowControlActions.onMaximize() }
                actionDispatcher.register(StudioAction.BRING_ALL_TO_FRONT) {
                    restoreWindow(windowState, composeWindow, includeDetached = true)
                }
                actionDispatcher.register(StudioAction.TOGGLE_COMMAND_PALETTE) {
                    isCommandPaletteOpen = !isCommandPaletteOpen
                }
                actionDispatcher.register(StudioAction.TOGGLE_BACKGROUND_SESSIONS) {
                    isSessionDrawerOpen = !isSessionDrawerOpen
                }
                actionDispatcher.register(StudioAction.POP_OUT_COPILOT) {
                    panelOpener.open(DetachablePanelId.LOGCAT_COPILOT, null)
                }
                actionDispatcher.register(StudioAction.POP_OUT_PARTITION_EXPLORER) {
                    panelOpener.open(DetachablePanelId.PARTITION_EXPLORER, null)
                }
                actionDispatcher.register(StudioAction.POP_OUT_DIFF_INSPECTOR) {
                    panelOpener.open(DetachablePanelId.DIFF_INSPECTOR, null)
                }
                onDispose {
                    actionDispatcher.unregister(StudioAction.OPEN_SETTINGS)
                    actionDispatcher.unregister(StudioAction.EXIT)
                    actionDispatcher.unregister(StudioAction.MINIMIZE_WINDOW)
                    actionDispatcher.unregister(StudioAction.ZOOM_WINDOW)
                    actionDispatcher.unregister(StudioAction.BRING_ALL_TO_FRONT)
                    actionDispatcher.unregister(StudioAction.TOGGLE_COMMAND_PALETTE)
                    actionDispatcher.unregister(StudioAction.TOGGLE_BACKGROUND_SESSIONS)
                    actionDispatcher.unregister(StudioAction.POP_OUT_COPILOT)
                    actionDispatcher.unregister(StudioAction.POP_OUT_PARTITION_EXPLORER)
                    actionDispatcher.unregister(StudioAction.POP_OUT_DIFF_INSPECTOR)
                }
            }

            CompositionLocalProvider(
                LocalWindowControlActions provides windowControlActions,
            ) {
                if (remember { DesktopOs.current == DesktopOs.MacOS }) {
                    StudioMenuBar(
                        state = StudioMenuState(isTerminalVisible = isSessionDrawerOpen),
                        dispatcher = actionDispatcher,
                    )
                }
            LtiSharedApp(
                appSettingsState = initialAppSettings,
                    openSettingsRequest = openSettingsRequest,
                    backgroundSessionManager = backgroundSessionManager,
                    workspaceDropCoordinator = workspaceDropCoordinator,
                    detachablePanelOpener = panelOpener,
                    isSessionDrawerOpen = isSessionDrawerOpen,
                    onSessionDrawerOpenChange = { isSessionDrawerOpen = it },
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
                            onPopOutCopilot = { panelOpener.open(DetachablePanelId.LOGCAT_COPILOT, null) },
                            onPopOutDiffViewer = { panelOpener.open(DetachablePanelId.DIFF_INSPECTOR, null) },
                            onPopOutPartitionExplorer = {
                                panelOpener.open(DetachablePanelId.PARTITION_EXPLORER, null)
                            },
                        )
                    },
                )
            }
        }

        DetachedToolWindows(
            state = detachedPanelsState,
            onStateChange = { detachedPanelsState = it },
            dropCoordinator = workspaceDropCoordinator,
        )
    }
}
