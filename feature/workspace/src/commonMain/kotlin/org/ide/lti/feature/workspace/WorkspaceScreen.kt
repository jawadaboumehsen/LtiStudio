/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.ide.lti.core.designsystem.component.GlassLayout
import org.ide.lti.core.designsystem.component.GlassSidePanelRail
import org.ide.lti.core.designsystem.component.GlassSidePanelTab
import org.ide.lti.core.designsystem.component.GlassStatusBar
import org.ide.lti.core.designsystem.component.GlassTopBar
import org.ide.lti.core.designsystem.component.LineEnding
import org.ide.lti.core.designsystem.component.SidePanelRailPosition
import org.ide.lti.core.designsystem.component.rememberLtiLayoutState
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.feature.workspace.panels.ai.AIAssistantPanel
import org.ide.lti.feature.workspace.panels.editor.EditorPanel
import org.ide.lti.feature.workspace.panels.explorer.ExplorerPanel
import org.ide.lti.feature.workspace.panels.terminal.TerminalPanel

/**
 * Main workspace screen with complete glass theme integration.
 *
 * Features:
 * - Glass toolbar with navigation controls
 * - Glass file explorer panel (left)
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
) {
    val ltiLayoutState = rememberLtiLayoutState()
    var leftTabIndex by rememberSaveable { mutableStateOf(0) }
    var rightTabIndex by rememberSaveable { mutableStateOf(0) }

    var activeFilePath by remember { mutableStateOf<String?>(null) }
    var activeFileType by remember { mutableStateOf<String?>(null) }
    var cursorPosition by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val leftTabs = remember {
        listOf(
            GlassSidePanelTab(
                icon = AppIcons.Folder,
                label = "Explorer",
            ) {
                ExplorerPanel()
            }
        )
    }

    val rightTabs = remember {
        listOf(
            GlassSidePanelTab(
                icon = AppIcons.Sparkles,
                label = "AI Assistant",
            ) {
                AIAssistantPanel()
            }
        )
    }

    GlassLayout(
        state = ltiLayoutState,
        modifier = modifier,
        leftRail = {
            GlassSidePanelRail(
                tabs = leftTabs,
                selectedIndex = leftTabIndex,
                onSelectedIndexChange = { leftTabIndex = it },
                railPosition = SidePanelRailPosition.Left,
            )
        },
        leftPanel = {
            leftTabs.getOrNull(leftTabIndex)?.content?.invoke()
        },
        mainContent = {
            EditorPanel(
                onActiveFileChange = { path, language ->
                    activeFilePath = path
                    activeFileType = language
                },
                onCursorPositionChange = { line, col ->
                    cursorPosition = line to col
                },
            )
        },
        bottomPanel = { TerminalPanel() },
        rightPanel = {
            rightTabs.getOrNull(rightTabIndex)?.content?.invoke()
        },
        rightRail = {
            GlassSidePanelRail(
                tabs = rightTabs,
                selectedIndex = rightTabIndex,
                onSelectedIndexChange = { rightTabIndex = it },
                railPosition = SidePanelRailPosition.Right,
            )
        },
        topBar = {
            GlassTopBar(
                isLeftPanelVisible = ltiLayoutState.showLeft,
                isRightPanelVisible = ltiLayoutState.showRight,
                isMiddleDownPanelVisible = ltiLayoutState.showBottom,
                onClickLeftPanelIcon = { ltiLayoutState.toggleLeft() },
                onClickRightPanelIcon = { ltiLayoutState.toggleRight() },
                onClickMiddleDownPanelIcon = { ltiLayoutState.toggleBottom() },
                onNotificationsClick = onOpenNotifications,
                onSettingsClick = onOpenSettings,
                drawOwnChrome = false,
            )
        },
        statusBar = {
            GlassStatusBar(
                filePath = activeFilePath,
                cursorPosition = cursorPosition,
                encoding = "UTF-8",
                lineEnding = LineEnding.LF,
                fileType = activeFileType,
                drawOwnChrome = false,
            )
        },
    )
}
