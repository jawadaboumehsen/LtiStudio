/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import org.ide.lti.core.common.event.PanelId
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.actions.onKeyShortcut
import org.ide.lti.core.designsystem.component.layout.GlassLayout
import org.ide.lti.core.designsystem.component.layout.GlobalAppDestination
import org.ide.lti.core.designsystem.component.layout.GlobalAppTopBar
import org.ide.lti.core.designsystem.component.layout.LayoutPanel
import org.ide.lti.core.designsystem.component.layout.rememberLtiLayoutState
import org.ide.lti.core.designsystem.component.navigation.CommandCategory
import org.ide.lti.core.designsystem.component.navigation.CommandItem
import org.ide.lti.core.designsystem.component.navigation.GlassCommandPalette
import org.ide.lti.core.designsystem.component.navigation.GlassSidePanelRail
import org.ide.lti.core.designsystem.component.navigation.GlassSidePanelTab
import org.ide.lti.core.designsystem.component.navigation.SidePanelRailPosition
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.model.target.TargetRegion
import org.ide.lti.core.model.target.WirelessPairingRequest
import org.ide.lti.core.ui.WorkspaceStatusBar
import org.ide.lti.feature.workspace.panels.ai.AIAssistantPanel
import org.ide.lti.feature.workspace.panels.editor.EditorPanel
import org.ide.lti.feature.workspace.panels.editor.EditorViewModel
import org.ide.lti.feature.workspace.panels.explorer.ExplorerPanel
import org.ide.lti.feature.workspace.panels.explorer.ExplorerViewModel
import org.ide.lti.feature.workspace.panels.run.BuildRunViewModel
import org.ide.lti.feature.workspace.panels.run.RunPanel
import org.ide.lti.feature.workspace.panels.target.GlassWifiPairingDialog
import org.ide.lti.feature.workspace.panels.target.TargetDetailPanel
import org.ide.lti.feature.workspace.panels.target.TargetRunConfigurationBar
import org.ide.lti.feature.workspace.panels.target.WorkspaceConfigurationPanel
import org.ide.lti.feature.workspace.panels.terminal.TerminalPanel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Main workspace screen with complete glass theme integration.
 *
 * Features:
 * - Glass toolbar with navigation controls
 * - Glass file explorer panel (left)
 * - Glass target device configuration cockpit (left)
 * - Glass code editor (center)
 * - Glass AI assistant panel (right)
 * - Glass terminal panel (bottom)
 * - Glass status bar
 *
 * All components use the glass morphism effects provided by LtiTheme.
 */
@Composable
fun WorkspaceScreen(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenConfigurations: () -> Unit = {},
    onOpenEditConfiguration: (String) -> Unit = { onOpenConfigurations() },
    onOpenAddConfiguration: () -> Unit = onOpenConfigurations,
    onNavigateToSetup: () -> Unit = {},
    onOpenStudio: (workspaceId: String, stageId: String?, objectId: String?) -> Unit = { _, _, _ -> },
    viewModel: WorkspaceViewModel = koinViewModel(),
    editorViewModel: EditorViewModel = koinViewModel(),
    explorerViewModel: ExplorerViewModel = koinViewModel(),
    buildRunViewModel: BuildRunViewModel = koinViewModel(),
) {
    val currentWorkspace by viewModel.currentWorkspace.collectAsState()
    val workspaceName by viewModel.workspaceName.collectAsState()
    val selectedTarget by viewModel.selectedTarget.collectAsState()
    val effectiveSnapshot by viewModel.effectiveSnapshot.collectAsState()
    val connectedDevices by viewModel.connectedDevices.collectAsState()
    val selectedConnectedDevice by viewModel.selectedConnectedDevice.collectAsState()
    val isRefreshingDevices by viewModel.isRefreshingDevices.collectAsState()
    val initialPanelsState = viewModel.panelsState.value

    val initialLeftState = initialPanelsState[PanelId.LEFT]
    val initialRightState = initialPanelsState[PanelId.RIGHT]
    val initialBottomState = initialPanelsState[PanelId.BOTTOM]

    val ltiLayoutState = rememberLtiLayoutState(
        initialLeftWeight = initialLeftState?.size ?: 0.2f,
        initialRightWeight = initialRightState?.size ?: 0.25f,
        initialBottomWeight = initialBottomState?.size ?: 0.3f,
        initialShowLeft = initialLeftState?.visible ?: true,
        initialShowRight = initialRightState?.visible ?: true,
        initialShowBottom = initialBottomState?.visible ?: false,
        onDragEnd = { panel, size ->
            viewModel.setPanelSize(panel.toPanelId(), size)
        },
    )
    var leftTabIndex by rememberSaveable { mutableStateOf(0) }
    var rightTabIndex by rememberSaveable { mutableStateOf(0) }

    var activeFilePath by remember { mutableStateOf<String?>(null) }
    var activeFileType by remember { mutableStateOf<String?>(null) }
    var cursorPosition by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val branch by viewModel.branch.collectAsState()
    val deviceOperationStatus by viewModel.deviceOperationStatus.collectAsState()
    var isCommandPaletteOpen by rememberSaveable { mutableStateOf(false) }
    var isWifiPairingDialogOpen by rememberSaveable { mutableStateOf(false) }

    val commands = remember(selectedTarget, ltiLayoutState, leftTabIndex) {
        buildList {
            // 1. Navigation
            add(
                CommandItem(
                    id = "nav_files",
                    title = "Open File Explorer",
                    subtitle = "Browse workspace directory and source tree",
                    category = CommandCategory.NAVIGATION,
                    icon = AppIcon.Painted(AppIcons.FolderPainterResource),
                    shortcut = "Ctrl+1",
                ) {
                    leftTabIndex = 0
                    if (!ltiLayoutState.showLeft) {
                        ltiLayoutState.toggleLeft()
                        viewModel.togglePanelVisibility(PanelId.LEFT)
                    }
                },
            )
            add(
                CommandItem(
                    id = "nav_target",
                    title = "Target Device & Config",
                    subtitle = "Silicon telemetry, partition geometry, and BoardConfig",
                    category = CommandCategory.NAVIGATION,
                    icon = AppIcon.Painted(AppIcons.SettingsPainterResource),
                    shortcut = "Ctrl+2",
                ) {
                    currentWorkspace?.id?.let { workspaceId -> onOpenStudio(workspaceId, null, null) }
                },
            )
            add(
                CommandItem(
                    id = "nav_run",
                    title = "ROM Build Pipeline",
                    subtitle = "Start or monitor ROM build runs and verify artifacts",
                    category = CommandCategory.NAVIGATION,
                    icon = AppIcon.Painted(AppIcons.PlayPainterResource),
                    shortcut = "Ctrl+3",
                ) {
                    leftTabIndex = 2
                    if (!ltiLayoutState.showLeft) {
                        ltiLayoutState.toggleLeft()
                        viewModel.togglePanelVisibility(PanelId.LEFT)
                    }
                },
            )
            add(
                CommandItem(
                    id = "nav_telemetry",
                    title = "Hardware Telemetry",
                    subtitle = "SoC platform, super container, and AVB status",
                    category = CommandCategory.NAVIGATION,
                    icon = AppIcon.Painted(AppIcons.SparklesPainterResource),
                ) {
                    leftTabIndex = 1
                    if (!ltiLayoutState.showLeft) {
                        ltiLayoutState.toggleLeft()
                        viewModel.togglePanelVisibility(PanelId.LEFT)
                    }
                },
            )
            add(
                CommandItem(
                    id = "nav_studio",
                    title = "ROM Setup Studio...",
                    subtitle = "Open 8-stage ROM configuration and validation studio",
                    category = CommandCategory.NAVIGATION,
                    icon = AppIcon.Painted(AppIcons.SettingsPainterResource),
                ) {
                    // Studio is keyed by workspace ID, not the display name shown in the command's own subtitle.
                    currentWorkspace?.id?.let { workspaceId -> onOpenStudio(workspaceId, null, null) }
                },
            )

            // 2. Workspace target configuration (the bound target is not switched from here)
            add(
                CommandItem(
                    id = "workspace_config",
                    title = "Configure Workspace...",
                    subtitle = "Region, firmware, acquisition, signing and OTA settings for this workspace",
                    category = CommandCategory.TARGET,
                    icon = AppIcon.Painted(AppIcons.SettingsPainterResource),
                ) {
                    leftTabIndex = 1
                    if (!ltiLayoutState.showLeft) {
                        ltiLayoutState.toggleLeft()
                        viewModel.togglePanelVisibility(PanelId.LEFT)
                    }
                },
            )

            // 3. Configuration Actions
            add(
                CommandItem(
                    id = "config_edit",
                    title = "Edit Target Configurations...",
                    subtitle = "Open Target & Run Configuration Studio",
                    category = CommandCategory.CONFIG,
                    icon = AppIcon.Painted(AppIcons.EditPainterResource),
                ) {
                    onOpenEditConfiguration(selectedTarget.id)
                },
            )
            add(
                CommandItem(
                    id = "config_add",
                    title = "Add Target Configuration...",
                    subtitle = "Open Configuration Studio to scaffold a new device target",
                    category = CommandCategory.CONFIG,
                    icon = AppIcon.Painted(AppIcons.AddPainterResource),
                ) {
                    onOpenAddConfiguration()
                },
            )

            // 4. Panels & Docks
            add(
                CommandItem(
                    id = "toggle_left",
                    title = "Toggle Left Sidebar",
                    subtitle = if (ltiLayoutState.showLeft) "Collapse left activity panel" else "Expand left activity panel",
                    category = CommandCategory.PANELS,
                    icon = AppIcon.Painted(AppIcons.leftPanelPainterResource),
                    shortcut = "Ctrl+B",
                ) {
                    ltiLayoutState.toggleLeft()
                    viewModel.togglePanelVisibility(PanelId.LEFT)
                },
            )
            add(
                CommandItem(
                    id = "toggle_bottom",
                    title = "Toggle Bottom Terminal",
                    subtitle = if (ltiLayoutState.showBottom) "Hide build terminal drawer" else "Show build terminal drawer",
                    category = CommandCategory.PANELS,
                    icon = AppIcon.Painted(AppIcons.bottomPanelPainterResource),
                    shortcut = "Ctrl+`",
                ) {
                    ltiLayoutState.toggleBottom()
                    viewModel.togglePanelVisibility(PanelId.BOTTOM)
                },
            )
            add(
                CommandItem(
                    id = "toggle_right",
                    title = "Toggle AI Assistant",
                    subtitle = if (ltiLayoutState.showRight) "Hide AI assistant drawer" else "Show AI assistant drawer",
                    category = CommandCategory.PANELS,
                    icon = AppIcon.Painted(AppIcons.rightPanelPainterResource),
                ) {
                    ltiLayoutState.toggleRight()
                    viewModel.togglePanelVisibility(PanelId.RIGHT)
                },
            )
        }
    }

    val leftTabs = remember(editorViewModel, explorerViewModel, viewModel, buildRunViewModel) {
        listOf(
            GlassSidePanelTab(
                icon = AppIcon.Painted(AppIcons.FolderPainterResource),
                label = "Files",
            ) {
                ExplorerPanel(
                    viewModel = explorerViewModel,
                    onFileClick = { path ->
                        editorViewModel.openFile(path)
                    },
                )
            },
            GlassSidePanelTab(
                icon = AppIcon.Painted(AppIcons.SettingsPainterResource),
                label = "Target & Config",
            ) {
                WorkspaceConfigurationPanel()
            },
            GlassSidePanelTab(
                icon = AppIcon.Painted(AppIcons.PlayPainterResource),
                label = "Run",
            ) {
                RunPanel(viewModel = buildRunViewModel, onNavigateToSetup = onNavigateToSetup)
            },
        )
    }

    val rightTabs = remember {
        listOf(
            GlassSidePanelTab(
                icon = AppIcons.Sparkles,
                label = "AI Assistant",
            ) {
                AIAssistantPanel()
            },
        )
    }

    val screenModifier = modifier
        .onKeyShortcut(key = Key.K, isCtrlOrCmd = true) {
            isCommandPaletteOpen = !isCommandPaletteOpen
        }

    GlassLayout(
        state = ltiLayoutState,
        modifier = screenModifier,
        leftRail = {
            GlassSidePanelRail(
                tabs = leftTabs,
                selectedIndex = leftTabIndex,
                onSelectedIndexChange = { index ->
                    if (leftTabIndex == index) {
                        ltiLayoutState.toggleLeft()
                        viewModel.togglePanelVisibility(PanelId.LEFT)
                    } else {
                        leftTabIndex = index
                        if (!ltiLayoutState.showLeft) {
                            ltiLayoutState.toggleLeft()
                            viewModel.togglePanelVisibility(PanelId.LEFT)
                        }
                    }
                },
                railPosition = SidePanelRailPosition.Left,
            )
        },
        leftPanel = {
            leftTabs.getOrNull(leftTabIndex)?.content?.invoke()
        },
        mainContent = {
            when (leftTabIndex) {
                0 -> {
                    EditorPanel(
                        viewModel = editorViewModel,
                        onOpenFolder = { explorerViewModel.openFolder() },
                        onActiveFileChange = { path, language ->
                            activeFilePath = path
                            activeFileType = language
                        },
                        onCursorPositionChange = { line, col ->
                            cursorPosition = line to col
                        },
                    )
                }
                1 -> {
                    TargetDetailPanel(
                        selectedTarget = selectedTarget,
                    )
                }
                2 -> {
                    RunPanel(
                        viewModel = buildRunViewModel,
                    )
                }
                else -> {
                    EditorPanel(
                        viewModel = editorViewModel,
                        onOpenFolder = { explorerViewModel.openFolder() },
                        onActiveFileChange = { path, language ->
                            activeFilePath = path
                            activeFileType = language
                        },
                        onCursorPositionChange = { line, col ->
                            cursorPosition = line to col
                        },
                    )
                }
            }
        },
        bottomPanel = { TerminalPanel() },
        rightPanel = {
            rightTabs.getOrNull(rightTabIndex)?.content?.invoke()
        },
        rightRail = {
            GlassSidePanelRail(
                tabs = rightTabs,
                selectedIndex = rightTabIndex,
                onSelectedIndexChange = { index ->
                    if (rightTabIndex == index) {
                        ltiLayoutState.toggleRight()
                        viewModel.togglePanelVisibility(PanelId.RIGHT)
                    } else {
                        rightTabIndex = index
                        if (!ltiLayoutState.showRight) {
                            ltiLayoutState.toggleRight()
                            viewModel.togglePanelVisibility(PanelId.RIGHT)
                        }
                    }
                },
                railPosition = SidePanelRailPosition.Right,
            )
        },
        topBar = {
            GlobalAppTopBar(
                selectedDestination = GlobalAppDestination.Workspace,
                onSelectDestination = { destination ->
                    when (destination) {
                        GlobalAppDestination.Setup -> onNavigateToSetup()
                        GlobalAppDestination.Workspace -> Unit
                        GlobalAppDestination.Settings -> onOpenSettings()
                    }
                },
                contextLabel = workspaceName,
                centerContent = {
                    TargetRunConfigurationBar(
                        selectedTarget = selectedTarget,
                        region = effectiveSnapshot?.acquisition?.region ?: TargetRegion.GLOBAL,
                        onOpenWorkspaceConfiguration = {
                            currentWorkspace?.id?.let { workspaceId -> onOpenStudio(workspaceId, null, null) }
                        },
                        onOpenEditConfiguration = { onOpenEditConfiguration(selectedTarget.id) },
                        onOpenAddConfiguration = onOpenAddConfiguration,
                        onCloseWorkspace = { viewModel.closeWorkspace() },
                        connectedDevices = connectedDevices,
                        selectedConnectedDevice = selectedConnectedDevice,
                        isRefreshingDevices = isRefreshingDevices,
                        onSelectConnectedDevice = { serial -> viewModel.selectConnectedDevice(serial) },
                        onRefreshConnectedDevices = { viewModel.refreshConnectedDevices() },
                        onRebootConnectedDevice = { serial, mode -> viewModel.rebootConnectedDevice(serial, mode) },
                        onOpenWifiPairing = { isWifiPairingDialogOpen = true },
                    )
                },
                actions = {
                    GlassIconButton(
                        onClick = { isCommandPaletteOpen = true },
                        size = ComponentSize.TopBarButtonSize,
                    ) {
                        Icon(
                            painter = AppIcons.SearchPainterResource(),
                            contentDescription = "Search actions, targets, and partitions",
                            modifier = Modifier.size(IconSize.Small),
                        )
                    }
                    GlassIconButton(
                        onClick = {
                            ltiLayoutState.toggleLeft()
                            viewModel.togglePanelVisibility(PanelId.LEFT)
                        },
                        size = ComponentSize.TopBarButtonSize,
                    ) {
                        Icon(
                            painter = AppIcons.LayoutSplitPainterResource(),
                            contentDescription = "Toggle left panel",
                            modifier = Modifier.size(IconSize.Small),
                        )
                    }
                    GlassIconButton(
                        onClick = {
                            ltiLayoutState.toggleRight()
                            viewModel.togglePanelVisibility(PanelId.RIGHT)
                        },
                        size = ComponentSize.TopBarButtonSize,
                    ) {
                        Icon(
                            painter = AppIcons.LayoutSplitPainterResource(),
                            contentDescription = "Toggle right panel",
                            modifier = Modifier.size(IconSize.Small),
                        )
                    }
                    GlassIconButton(
                        onClick = {
                            ltiLayoutState.toggleBottom()
                            viewModel.togglePanelVisibility(PanelId.BOTTOM)
                        },
                        size = ComponentSize.TopBarButtonSize,
                    ) {
                        Icon(
                            painter = AppIcons.LayoutSplitPainterResource(),
                            contentDescription = "Toggle bottom panel",
                            modifier = Modifier.size(IconSize.Small),
                        )
                    }
                    GlassIconButton(
                        onClick = onOpenNotifications,
                        size = ComponentSize.TopBarButtonSize,
                    ) {
                        Icon(
                            painter = AppIcons.NotificationsPainterResource(),
                            contentDescription = "Notifications",
                            modifier = Modifier.size(IconSize.Small),
                        )
                    }
                },
            )
        },
        statusBar = {
            val activeRegionCode = effectiveSnapshot?.acquisition?.region?.code ?: TargetRegion.GLOBAL.code
            WorkspaceStatusBar(
                branchName = branch,
                module = "${selectedTarget.formattedSuperSize} • ${selectedTarget.filesystemType.uppercase()}",
                filePath = activeFilePath ?: "Target: ${selectedTarget.name} ($activeRegionCode) • AVB 2.0 Active",
                cursorPosition = cursorPosition,
                fileType = activeFileType ?: selectedTarget.codename,
                drawOwnChrome = false,
            )
        },
    )

    GlassCommandPalette(
        isOpen = isCommandPaletteOpen,
        onDismissRequest = { isCommandPaletteOpen = false },
        commands = commands,
    )

    GlassWifiPairingDialog(
        isOpen = isWifiPairingDialogOpen,
        onDismissRequest = {
            isWifiPairingDialogOpen = false
            viewModel.clearDeviceOperationStatus()
        },
        onPairAndConnect = { host, port, code ->
            viewModel.pairWirelessDevice(
                WirelessPairingRequest(host, port, code)
            ) { success, _ ->
                if (success) {
                    isWifiPairingDialogOpen = false
                }
            }
        },
        errorMessage = deviceOperationStatus,
    )
}

/**
 * Maps design-system layout [LayoutPanel] identity to domain [PanelId] slot.
 */
private fun LayoutPanel.toPanelId(): PanelId = when (this) {
    LayoutPanel.Left -> PanelId.LEFT
    LayoutPanel.Center -> PanelId.MAIN
    LayoutPanel.Bottom -> PanelId.BOTTOM
    LayoutPanel.Right -> PanelId.RIGHT
    is LayoutPanel.Custom -> PanelId.MAIN
}
