/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.ide.lti.desktop.key

import androidx.compose.ui.input.key.KeyEvent
import org.ide.lti.desktop.menu.dispatcher.DefaultStudioActionDispatcher
import org.ide.lti.desktop.menu.model.StudioAction
import java.awt.Canvas
import java.awt.event.InputEvent
import java.awt.event.KeyEvent as AwtKeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowKeyShortcutHandlerTest {

    private val dummyComponent = Canvas()
    private val toComposeEventMethod = Class.forName("androidx.compose.ui.input.key.KeyEvent_desktopKt")
        .getDeclaredMethod("toComposeEvent", java.awt.event.KeyEvent::class.java)
        .apply { isAccessible = true }

    private fun createKeyEvent(
        id: Int = AwtKeyEvent.KEY_PRESSED,
        keyCode: Int,
        modifiers: Int = 0,
        keyChar: Char = AwtKeyEvent.CHAR_UNDEFINED,
    ): KeyEvent {
        val awt = AwtKeyEvent(
            dummyComponent,
            id,
            System.currentTimeMillis(),
            modifiers,
            keyCode,
            keyChar,
        )
        val internalEvent = toComposeEventMethod.invoke(null, awt)
        return KeyEvent(internalEvent)
    }

    @Test
    fun handleKeyEvent_escapeKey_dismissesOverlayWhenAvailable() {
        var overlayDismissed = false
        val dispatcher = DefaultStudioActionDispatcher()

        val event = createKeyEvent(keyCode = AwtKeyEvent.VK_ESCAPE)
        val handled = WindowKeyShortcutHandler.handleKeyEvent(
            event = event,
            dispatcher = dispatcher,
            onDismissOverlay = {
                overlayDismissed = true
                true
            },
        )

        assertTrue(handled, "Expected Escape key to be handled when overlay is present")
        assertTrue(overlayDismissed, "Expected overlay dismissal callback to be invoked")
    }

    @Test
    fun handleKeyEvent_escapeKey_notConsumedWhenNoOverlay() {
        val dispatcher = DefaultStudioActionDispatcher()

        val event = createKeyEvent(keyCode = AwtKeyEvent.VK_ESCAPE)
        val handled = WindowKeyShortcutHandler.handleKeyEvent(
            event = event,
            dispatcher = dispatcher,
            onDismissOverlay = { false },
        )

        assertFalse(handled, "Expected Escape key not to be consumed when no overlay is open")
    }

    @Test
    fun handleKeyEvent_ctrlS_triggersSaveActionOnWindows() {
        var dispatchedAction: StudioAction? = null
        val dispatcher = DefaultStudioActionDispatcher().apply {
            register(StudioAction.SAVE) { dispatchedAction = StudioAction.SAVE }
        }

        val event = createKeyEvent(
            keyCode = AwtKeyEvent.VK_S,
            modifiers = InputEvent.CTRL_DOWN_MASK,
        )
        val handled = WindowKeyShortcutHandler.handleKeyEvent(
            event = event,
            dispatcher = dispatcher,
            isMacOs = false,
        )

        assertTrue(handled, "Expected Ctrl+S to be handled on Windows")
        assertEquals(StudioAction.SAVE, dispatchedAction)
    }

    @Test
    fun handleKeyEvent_metaS_triggersSaveActionOnMac() {
        var dispatchedAction: StudioAction? = null
        val dispatcher = DefaultStudioActionDispatcher().apply {
            register(StudioAction.SAVE) { dispatchedAction = StudioAction.SAVE }
        }

        val event = createKeyEvent(
            keyCode = AwtKeyEvent.VK_S,
            modifiers = InputEvent.META_DOWN_MASK,
        )
        val handled = WindowKeyShortcutHandler.handleKeyEvent(
            event = event,
            dispatcher = dispatcher,
            isMacOs = true,
        )

        assertTrue(handled, "Expected Cmd+S to be handled on macOS")
        assertEquals(StudioAction.SAVE, dispatchedAction)
    }

    @Test
    fun handleKeyEvent_ctrlShiftS_triggersSaveAllAction() {
        var dispatchedAction: StudioAction? = null
        val dispatcher = DefaultStudioActionDispatcher().apply {
            register(StudioAction.SAVE_ALL) { dispatchedAction = StudioAction.SAVE_ALL }
        }

        val event = createKeyEvent(
            keyCode = AwtKeyEvent.VK_S,
            modifiers = InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK,
        )
        val handled = WindowKeyShortcutHandler.handleKeyEvent(
            event = event,
            dispatcher = dispatcher,
            isMacOs = false,
        )

        assertTrue(handled, "Expected Ctrl+Shift+S to be handled")
        assertEquals(StudioAction.SAVE_ALL, dispatchedAction)
    }

    @Test
    fun handleKeyEvent_ctrlB_triggersToggleExplorerAction() {
        var dispatchedAction: StudioAction? = null
        val dispatcher = DefaultStudioActionDispatcher().apply {
            register(StudioAction.TOGGLE_EXPLORER) { dispatchedAction = StudioAction.TOGGLE_EXPLORER }
        }

        val event = createKeyEvent(
            keyCode = AwtKeyEvent.VK_B,
            modifiers = InputEvent.CTRL_DOWN_MASK,
        )
        val handled = WindowKeyShortcutHandler.handleKeyEvent(
            event = event,
            dispatcher = dispatcher,
            isMacOs = false,
        )

        assertTrue(handled, "Expected Ctrl+B to trigger toggle explorer")
        assertEquals(StudioAction.TOGGLE_EXPLORER, dispatchedAction)
    }

    @Test
    fun handleKeyEvent_ctrlShiftA_triggersToggleAiAssistantAction() {
        var dispatchedAction: StudioAction? = null
        val dispatcher = DefaultStudioActionDispatcher().apply {
            register(StudioAction.TOGGLE_AI_ASSISTANT) { dispatchedAction = StudioAction.TOGGLE_AI_ASSISTANT }
        }

        val event = createKeyEvent(
            keyCode = AwtKeyEvent.VK_A,
            modifiers = InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK,
        )
        val handled = WindowKeyShortcutHandler.handleKeyEvent(
            event = event,
            dispatcher = dispatcher,
            isMacOs = false,
        )

        assertTrue(handled, "Expected Ctrl+Shift+A to trigger toggle AI assistant")
        assertEquals(StudioAction.TOGGLE_AI_ASSISTANT, dispatchedAction)
    }

    @Test
    fun handleKeyEvent_ctrlShiftG_triggersCloneGitAction() {
        var dispatchedAction: StudioAction? = null
        val dispatcher = DefaultStudioActionDispatcher().apply {
            register(StudioAction.CLONE_GIT) { dispatchedAction = StudioAction.CLONE_GIT }
        }

        val event = createKeyEvent(
            keyCode = AwtKeyEvent.VK_G,
            modifiers = InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK,
        )
        val handled = WindowKeyShortcutHandler.handleKeyEvent(
            event = event,
            dispatcher = dispatcher,
            isMacOs = false,
        )

        assertTrue(handled, "Expected Ctrl+Shift+G to trigger clone git")
        assertEquals(StudioAction.CLONE_GIT, dispatchedAction)
    }

    @Test
    fun handleKeyEvent_ctrlShiftW_triggersOpenStagingWorkDirAction() {
        var dispatchedAction: StudioAction? = null
        val dispatcher = DefaultStudioActionDispatcher().apply {
            register(StudioAction.OPEN_STAGING_WORKDIR) { dispatchedAction = StudioAction.OPEN_STAGING_WORKDIR }
        }

        val event = createKeyEvent(
            keyCode = AwtKeyEvent.VK_W,
            modifiers = InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK,
        )
        val handled = WindowKeyShortcutHandler.handleKeyEvent(
            event = event,
            dispatcher = dispatcher,
            isMacOs = false,
        )

        assertTrue(handled, "Expected Ctrl+Shift+W to trigger open staging workdir")
        assertEquals(StudioAction.OPEN_STAGING_WORKDIR, dispatchedAction)
    }

    @Test
    fun handleKeyEvent_ctrlComma_triggersSettingsAction() {
        var dispatchedAction: StudioAction? = null
        val dispatcher = DefaultStudioActionDispatcher().apply {
            register(StudioAction.OPEN_SETTINGS) { dispatchedAction = StudioAction.OPEN_SETTINGS }
        }

        val event = createKeyEvent(
            keyCode = AwtKeyEvent.VK_COMMA,
            modifiers = InputEvent.CTRL_DOWN_MASK,
        )
        val handled = WindowKeyShortcutHandler.handleKeyEvent(
            event = event,
            dispatcher = dispatcher,
            isMacOs = false,
        )

        assertTrue(handled, "Expected Ctrl+, to trigger open settings")
        assertEquals(StudioAction.OPEN_SETTINGS, dispatchedAction)
    }

    @Test
    fun handleKeyEvent_ctrlBacktick_triggersToggleTerminalAction() {
        var dispatchedAction: StudioAction? = null
        val dispatcher = DefaultStudioActionDispatcher().apply {
            register(StudioAction.TOGGLE_TERMINAL) { dispatchedAction = StudioAction.TOGGLE_TERMINAL }
        }

        val event = createKeyEvent(
            keyCode = AwtKeyEvent.VK_BACK_QUOTE,
            modifiers = InputEvent.CTRL_DOWN_MASK,
            keyChar = '`',
        )
        val handled = WindowKeyShortcutHandler.handleKeyEvent(
            event = event,
            dispatcher = dispatcher,
            isMacOs = false,
        )

        assertTrue(handled, "Expected Ctrl+` to trigger toggle terminal")
        assertEquals(StudioAction.TOGGLE_TERMINAL, dispatchedAction)
    }

    @Test
    fun handleKeyEvent_ctrlQ_triggersExitAction() {
        var dispatchedAction: StudioAction? = null
        val dispatcher = DefaultStudioActionDispatcher().apply {
            register(StudioAction.EXIT) { dispatchedAction = StudioAction.EXIT }
        }

        val event = createKeyEvent(
            keyCode = AwtKeyEvent.VK_Q,
            modifiers = InputEvent.CTRL_DOWN_MASK,
        )
        val handled = WindowKeyShortcutHandler.handleKeyEvent(
            event = event,
            dispatcher = dispatcher,
            isMacOs = false,
        )

        assertTrue(handled, "Expected Ctrl+Q to trigger exit")
        assertEquals(StudioAction.EXIT, dispatchedAction)
    }

    @Test
    fun matchesKey_matchesAcrossLocationsAndRejectsNonMatching() {
        // Location 0 (KEY_LOCATION_UNKNOWN) vs standard location
        val keyLocation0 = androidx.compose.ui.input.key.Key(AwtKeyEvent.VK_B, 0)
        val keyLocationStandard = androidx.compose.ui.input.key.Key(AwtKeyEvent.VK_B, 1)
        val differentKey = androidx.compose.ui.input.key.Key(AwtKeyEvent.VK_A, 0)

        assertTrue(
            WindowKeyShortcutHandler.matchesKey(keyLocation0, androidx.compose.ui.input.key.Key.B),
            "Key with location 0 should match Key.B via nativeKeyCode"
        )
        assertTrue(
            WindowKeyShortcutHandler.matchesKey(keyLocationStandard, androidx.compose.ui.input.key.Key.B),
            "Key with standard location should match Key.B"
        )
        assertFalse(
            WindowKeyShortcutHandler.matchesKey(differentKey, androidx.compose.ui.input.key.Key.B),
            "Different key should not match Key.B"
        )
    }

    @Test
    fun handleKeyEvent_keyRelease_isIgnored() {
        val dispatcher = DefaultStudioActionDispatcher()

        val event = createKeyEvent(
            id = AwtKeyEvent.KEY_RELEASED,
            keyCode = AwtKeyEvent.VK_S,
            modifiers = InputEvent.CTRL_DOWN_MASK,
        )
        val handled = WindowKeyShortcutHandler.handleKeyEvent(
            event = event,
            dispatcher = dispatcher,
            isMacOs = false,
        )

        assertFalse(handled, "Expected KeyRelease events not to be handled by shortcut dispatcher")
    }
}
