/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.ide.lti.desktop.menu.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.MenuBarScope
import androidx.compose.ui.window.MenuScope
import org.ide.lti.desktop.menu.dispatcher.StudioActionDispatcher
import org.ide.lti.desktop.menu.model.DesktopKeyShortcut
import org.ide.lti.desktop.menu.model.StudioAction
import org.ide.lti.desktop.menu.model.StudioMenuState

/**
 * Enterprise-grade, clean, and modular desktop menu bar for LtiRom Studio.
 *
 * Directly binds to Compose Desktop's [MenuBar] inside [FrameWindowScope].
 * - On Windows & Linux: Renders at the top of the window frame.
 * - On macOS: Automatically routes to the native system menu bar (screen menu bar).
 */
@Composable
fun FrameWindowScope.StudioMenuBar(
    state: StudioMenuState,
    dispatcher: StudioActionDispatcher,
) {
    MenuBar {
        FileMenu(state = state, dispatcher = dispatcher)
        EditMenu(state = state, dispatcher = dispatcher)
        ViewMenu(state = state, dispatcher = dispatcher)
        WindowMenu(state = state, dispatcher = dispatcher)
        HelpMenu(state = state, dispatcher = dispatcher)
    }
}

@Composable
private fun MenuBarScope.FileMenu(
    state: StudioMenuState,
    dispatcher: StudioActionDispatcher,
) {

    Menu("File", mnemonic = 'F') {
        Item(
            text = "New File...",
            mnemonic = 'N',
            shortcut = DesktopKeyShortcut.primary(Key.N),
            onClick = { dispatcher.dispatch(StudioAction.NEW_FILE) },
        )
        Item(
            text = "Open Workspace...",
            mnemonic = 'O',
            shortcut = DesktopKeyShortcut.primary(Key.O),
            onClick = { dispatcher.dispatch(StudioAction.OPEN_WORKSPACE) },
        )
        Separator()
        Item(
            text = "Save",
            mnemonic = 'S',
            enabled = state.isWorkspaceActive,
            shortcut = DesktopKeyShortcut.primary(Key.S),
            onClick = { dispatcher.dispatch(StudioAction.SAVE) },
        )
        Item(
            text = "Save All",
            enabled = state.isWorkspaceActive,
            shortcut = DesktopKeyShortcut.primary(Key.S, shift = true),
            onClick = { dispatcher.dispatch(StudioAction.SAVE_ALL) },
        )
        Separator()
        Item(
            text = "Settings...",
            mnemonic = 'E',
            shortcut = DesktopKeyShortcut.primary(Key.Comma),
            onClick = { dispatcher.dispatch(StudioAction.OPEN_SETTINGS) },
        )
        Separator()
        Item(
            text = "Exit",
            mnemonic = 'X',
            shortcut = DesktopKeyShortcut.primary(Key.Q),
            onClick = { dispatcher.dispatch(StudioAction.EXIT) },
        )
    }
}

@Composable
private fun MenuBarScope.EditMenu(
    state: StudioMenuState,
    dispatcher: StudioActionDispatcher,
) {
    Menu("Edit", mnemonic = 'E') {
        Item(
            text = "Undo",
            mnemonic = 'U',
            enabled = state.canUndo,
            shortcut = DesktopKeyShortcut.primary(Key.Z),
            onClick = { dispatcher.dispatch(StudioAction.UNDO) },
        )
        Item(
            text = "Redo",
            mnemonic = 'R',
            enabled = state.canRedo,
            shortcut = DesktopKeyShortcut.primary(Key.Z, shift = true),
            onClick = { dispatcher.dispatch(StudioAction.REDO) },
        )
        Separator()
        Item(
            text = "Cut",
            mnemonic = 'T',
            shortcut = DesktopKeyShortcut.primary(Key.X),
            onClick = { dispatcher.dispatch(StudioAction.CUT) },
        )
        Item(
            text = "Copy",
            mnemonic = 'C',
            shortcut = DesktopKeyShortcut.primary(Key.C),
            onClick = { dispatcher.dispatch(StudioAction.COPY) },
        )
        Item(
            text = "Paste",
            mnemonic = 'P',
            shortcut = DesktopKeyShortcut.primary(Key.V),
            onClick = { dispatcher.dispatch(StudioAction.PASTE) },
        )
        Separator()
        Item(
            text = "Find in Files...",
            mnemonic = 'F',
            shortcut = DesktopKeyShortcut.primary(Key.F, shift = true),
            onClick = { dispatcher.dispatch(StudioAction.FIND) },
        )
        Item(
            text = "Select All",
            mnemonic = 'A',
            shortcut = DesktopKeyShortcut.primary(Key.A),
            onClick = { dispatcher.dispatch(StudioAction.SELECT_ALL) },
        )
    }
}

@Composable
private fun MenuBarScope.ViewMenu(
    state: StudioMenuState,
    dispatcher: StudioActionDispatcher,
) {
    Menu("View", mnemonic = 'V') {
        CheckboxItem(
            text = "Project Explorer",
            mnemonic = 'P',
            checked = state.isExplorerVisible,
            shortcut = DesktopKeyShortcut.primary(Key.B),
            onCheckedChange = { dispatcher.dispatch(StudioAction.TOGGLE_EXPLORER) },
        )
        CheckboxItem(
            text = "AI Assistant",
            mnemonic = 'A',
            checked = state.isAiAssistantVisible,
            shortcut = DesktopKeyShortcut.primary(Key.A, shift = true),
            onCheckedChange = { dispatcher.dispatch(StudioAction.TOGGLE_AI_ASSISTANT) },
        )
        CheckboxItem(
            text = "Terminal",
            mnemonic = 'T',
            checked = state.isTerminalVisible,
            shortcut = DesktopKeyShortcut.primary(Key.Grave),
            onCheckedChange = { dispatcher.dispatch(StudioAction.TOGGLE_TERMINAL) },
        )
        Separator()
        Item(
            text = "Reset Layout",
            mnemonic = 'R',
            onClick = { dispatcher.dispatch(StudioAction.RESET_LAYOUT) },
        )
    }
}

@Composable
private fun MenuBarScope.WindowMenu(
    @Suppress("UNUSED_PARAMETER") state: StudioMenuState,
    dispatcher: StudioActionDispatcher,
) {
    Menu("Window", mnemonic = 'W') {
        Item(
            text = "Minimize",
            mnemonic = 'M',
            shortcut = DesktopKeyShortcut.primary(Key.M),
            onClick = { dispatcher.dispatch(StudioAction.MINIMIZE_WINDOW) },
        )
        Item(
            text = "Zoom / Maximize",
            mnemonic = 'Z',
            onClick = { dispatcher.dispatch(StudioAction.ZOOM_WINDOW) },
        )
        Separator()
        Item(
            text = "Bring All to Front",
            mnemonic = 'B',
            onClick = { dispatcher.dispatch(StudioAction.BRING_ALL_TO_FRONT) },
        )
    }
}

@Composable
private fun MenuBarScope.HelpMenu(
    @Suppress("UNUSED_PARAMETER") state: StudioMenuState,
    dispatcher: StudioActionDispatcher,
) {
    Menu("Help", mnemonic = 'H') {
        Item(
            text = "About LtiRom Studio",
            mnemonic = 'A',
            onClick = { dispatcher.dispatch(StudioAction.ABOUT) },
        )
        Item(
            text = "Documentation...",
            mnemonic = 'D',
            onClick = { dispatcher.dispatch(StudioAction.DOCUMENTATION) },
        )
        Separator()
        Item(
            text = "Check for Updates...",
            mnemonic = 'U',
            onClick = { dispatcher.dispatch(StudioAction.CHECK_UPDATES) },
        )
    }
}
