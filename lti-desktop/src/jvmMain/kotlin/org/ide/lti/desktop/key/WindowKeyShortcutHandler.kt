/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.ide.lti.desktop.key

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.utf16CodePoint
import org.ide.lti.desktop.menu.dispatcher.StudioActionDispatcher
import org.ide.lti.desktop.menu.model.DesktopKeyShortcut
import org.ide.lti.desktop.menu.model.StudioAction

/**
 * Handles window-level key events to trigger IDE shortcuts or dismiss overlays.
 * Follows the Single Responsibility Principle by encapsulating window-wide keyboard dispatch logic.
 */
object WindowKeyShortcutHandler {

    /**
     * Checks whether [eventKey] matches the [target] key.
     * Compares both direct equality and [Key.nativeKeyCode] to ensure robust matching on desktop platforms
     * (such as Windows, Tao, or Skiko) where runtime events may carry KEY_LOCATION_UNKNOWN (0) instead of KEY_LOCATION_STANDARD (1).
     */
    fun matchesKey(eventKey: Key, target: Key): Boolean {
        return eventKey == target || (eventKey.nativeKeyCode != 0 && eventKey.nativeKeyCode == target.nativeKeyCode)
    }

    /**
     * Intercepts preview key events at the window level.
     *
     * @param event The key event received by the window.
     * @param dispatcher Action dispatcher for triggering studio actions.
     * @param onDismissOverlay Callback to dismiss top-level overlays (e.g. exit confirmation dialog). Returns true if handled.
     * @param isMacOs Optional override for testing OS-specific modifier resolution.
     * @return True if the key event was consumed, false otherwise.
     */
    fun handleKeyEvent(
        event: KeyEvent,
        dispatcher: StudioActionDispatcher,
        onDismissOverlay: () -> Boolean = { false },
        isMacOs: Boolean = DesktopKeyShortcut.isMac,
    ): Boolean {
        if (event.type != KeyEventType.KeyDown) return false

        // 1. Escape key: dismiss open modal/overlay first
        if (matchesKey(event.key, Key.Escape)) {
            if (onDismissOverlay()) {
                return true
            }
        }

        val isPrimary = if (isMacOs) (event.isMetaPressed || event.isCtrlPressed) else (event.isCtrlPressed || event.isMetaPressed)
        val isShift = event.isShiftPressed
        val isAlt = event.isAltPressed

        if (!isPrimary) return false

        val nativeCode = event.key.nativeKeyCode
        val isBacktick = matchesKey(event.key, Key.Grave) || event.utf16CodePoint == '`'.code

        val action = when {
            // Primary + Shift shortcuts
            isShift && !isAlt -> when {
                matchesKey(event.key, Key.S) || nativeCode == Key.S.nativeKeyCode -> StudioAction.SAVE_ALL
                matchesKey(event.key, Key.A) || nativeCode == Key.A.nativeKeyCode -> StudioAction.TOGGLE_AI_ASSISTANT
                matchesKey(event.key, Key.Z) || nativeCode == Key.Z.nativeKeyCode -> StudioAction.REDO
                matchesKey(event.key, Key.F) || nativeCode == Key.F.nativeKeyCode -> StudioAction.FIND
                matchesKey(event.key, Key.G) || nativeCode == Key.G.nativeKeyCode -> StudioAction.CLONE_GIT
                matchesKey(event.key, Key.W) || nativeCode == Key.W.nativeKeyCode -> StudioAction.OPEN_STAGING_WORKDIR
                else -> null
            }
            // Primary shortcuts (no Shift, no Alt)
            !isShift && !isAlt -> when {
                matchesKey(event.key, Key.Comma) || nativeCode == Key.Comma.nativeKeyCode -> StudioAction.OPEN_SETTINGS
                matchesKey(event.key, Key.B) || nativeCode == Key.B.nativeKeyCode -> StudioAction.TOGGLE_EXPLORER
                isBacktick -> StudioAction.TOGGLE_TERMINAL
                matchesKey(event.key, Key.N) || nativeCode == Key.N.nativeKeyCode -> StudioAction.NEW_FILE
                matchesKey(event.key, Key.O) || nativeCode == Key.O.nativeKeyCode -> StudioAction.OPEN_WORKSPACE
                matchesKey(event.key, Key.S) || nativeCode == Key.S.nativeKeyCode -> StudioAction.SAVE
                matchesKey(event.key, Key.Z) || nativeCode == Key.Z.nativeKeyCode -> StudioAction.UNDO
                matchesKey(event.key, Key.X) || nativeCode == Key.X.nativeKeyCode -> StudioAction.CUT
                matchesKey(event.key, Key.C) || nativeCode == Key.C.nativeKeyCode -> StudioAction.COPY
                matchesKey(event.key, Key.V) || nativeCode == Key.V.nativeKeyCode -> StudioAction.PASTE
                matchesKey(event.key, Key.A) || nativeCode == Key.A.nativeKeyCode -> StudioAction.SELECT_ALL
                matchesKey(event.key, Key.M) || nativeCode == Key.M.nativeKeyCode -> StudioAction.MINIMIZE_WINDOW
                matchesKey(event.key, Key.Q) || nativeCode == Key.Q.nativeKeyCode -> StudioAction.EXIT
                else -> null
            }
            else -> null
        }

        return if (action != null) {
            dispatcher.dispatch(action)
            true
        } else {
            false
        }
    }
}
