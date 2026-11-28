/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.ide.lti.desktop.window

import androidx.compose.ui.window.WindowState
import java.awt.Frame
import java.awt.Window
import java.util.concurrent.ConcurrentHashMap

/** Registry of active detached secondary tool windows for multi-monitor window focus orchestration. */
object WindowRegistry {
    private val detachedWindows = ConcurrentHashMap<String, Window>()

    fun register(id: String, window: Window) {
        detachedWindows[id] = window
    }

    fun unregister(id: String) {
        detachedWindows.remove(id)
    }

    fun bringAllDetachedToFront() {
        detachedWindows.values.forEach { win ->
            bringAwtWindowToFront(win)
        }
    }
}

private fun bringAwtWindowToFront(
    window: Window,
    pulseAlwaysOnTop: Boolean = false,
) {
    try {
        if ((window is Frame) && (window.extendedState and Frame.ICONIFIED) != 0) {
            window.extendedState = window.extendedState and Frame.ICONIFIED.inv()
        }
        window.isVisible = true
        window.toFront()
        window.requestFocus()
        if (pulseAlwaysOnTop) {
            // Momentarily toggle alwaysOnTop to bring window above all background windows reliably
            window.isAlwaysOnTop = true
            window.isAlwaysOnTop = false
        }
    } catch (_: Throwable) {
        // Ignore platform window focus errors
    }
}

/**
 * Restores the main [Window] and any detached secondary windows from minimized, hidden,
 * or background state and brings them into active focus.
 */
fun restoreWindow(
    windowState: WindowState,
    window: Window?,
    includeDetached: Boolean = false,
) {
    windowState.isMinimized = false
    window?.let { bringAwtWindowToFront(it, pulseAlwaysOnTop = true) }
    if (includeDetached) {
        WindowRegistry.bringAllDetachedToFront()
    }
}
