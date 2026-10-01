/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.ide.lti.desktop.menu

import androidx.compose.ui.input.key.Key
import org.ide.lti.desktop.menu.dispatcher.DefaultStudioActionDispatcher
import org.ide.lti.desktop.menu.model.DesktopKeyShortcut
import org.ide.lti.desktop.menu.model.StudioAction
import org.ide.lti.desktop.menu.model.StudioMenuState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StudioMenuBarTest {

    @Test
    fun testKeyShortcut_resolvesMetaOnMac() {
        val modifiers = DesktopKeyShortcut.resolveModifiers(
            shift = true,
            isMacOs = true,
        )

        assertTrue(modifiers.meta, "macOS shortcut must use meta (Command)")
        assertFalse(modifiers.ctrl, "macOS shortcut must not use ctrl")
        assertTrue(modifiers.shift, "Shift modifier must be retained")
        assertFalse(modifiers.alt)

        val shortcut = DesktopKeyShortcut.primary(Key.S, shift = true, isMacOs = true)
        assertNotNull(shortcut)
    }

    @Test
    fun testKeyShortcut_resolvesCtrlOnWindowsAndLinux() {
        val modifiers = DesktopKeyShortcut.resolveModifiers(
            shift = false,
            isMacOs = false,
        )

        assertTrue(modifiers.ctrl, "Windows/Linux shortcut must use ctrl")
        assertFalse(modifiers.meta, "Windows/Linux shortcut must not use meta")
        assertFalse(modifiers.shift)
        assertFalse(modifiers.alt)

        val shortcut = DesktopKeyShortcut.primary(Key.S, isMacOs = false)
        assertNotNull(shortcut)
    }

    @Test
    fun testDefaultStudioActionDispatcher_registersAndDispatches() {
        var saveInvoked = false
        var exitInvoked = false

        val dispatcher = DefaultStudioActionDispatcher()
        dispatcher.register(StudioAction.SAVE) { saveInvoked = true }
        dispatcher.register(StudioAction.EXIT) { exitInvoked = true }

        dispatcher.dispatch(StudioAction.SAVE)
        assertTrue(saveInvoked, "Registered SAVE action should have been invoked")
        assertFalse(exitInvoked, "EXIT should not have been invoked yet")

        dispatcher.dispatch(StudioAction.EXIT)
        assertTrue(exitInvoked, "Registered EXIT action should have been invoked")
    }

    @Test
    fun testDefaultStudioActionDispatcher_unregistersHandler() {
        var count = 0
        val dispatcher = DefaultStudioActionDispatcher()
        dispatcher.register(StudioAction.UNDO) { count++ }

        dispatcher.dispatch(StudioAction.UNDO)
        assertEquals(1, count)

        dispatcher.unregister(StudioAction.UNDO)
        dispatcher.dispatch(StudioAction.UNDO)
        assertEquals(1, count, "Unregistered action should not be dispatched")
    }

    @Test
    fun testDefaultStudioActionDispatcher_safeWhenNoHandler() {
        val dispatcher = DefaultStudioActionDispatcher()
        // Must not throw NPE or exception when dispatching unbound action
        dispatcher.dispatch(StudioAction.ABOUT)
    }

    @Test
    fun testStudioMenuState_defaultsAndCopy() {
        val defaultState = StudioMenuState()
        assertFalse(defaultState.isWorkspaceActive)
        assertTrue(defaultState.isExplorerVisible)
        assertFalse(defaultState.isTerminalVisible)

        val updatedState = defaultState.copy(
            isWorkspaceActive = true,
            isTerminalVisible = true,
        )
        assertTrue(updatedState.isWorkspaceActive)
        assertTrue(updatedState.isTerminalVisible)
    }
}
