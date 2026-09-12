/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.ide.lti.desktop.window

import androidx.compose.ui.window.WindowState
import dev.nucleusframework.application.NucleusWindow
import java.awt.Frame

/**
 * Restores a [NucleusWindow] from minimized, hidden, or background state and brings it into active focus.
 */
fun restoreWindow(
    windowState: WindowState,
    nucleusWin: NucleusWindow?,
) {
    windowState.isMinimized = false
    nucleusWin?.let { win ->
        try { win.setMinimized(false) } catch (_: Throwable) {}
        try { win.show() } catch (_: Throwable) {}
        try { win.toFront() } catch (_: Throwable) {}
        try { win.requestFocus() } catch (_: Throwable) {}
        try {
            val awt = win.unsafe.awtWindow
            if (awt != null) {
                if ((awt.extendedState and Frame.ICONIFIED) != 0) {
                    awt.extendedState = awt.extendedState and Frame.ICONIFIED.inv()
                }
                awt.isVisible = true
                awt.toFront()
                awt.requestFocus()
                // Momentarily toggle alwaysOnTop to bring window above all background windows reliably
                awt.isAlwaysOnTop = true
                awt.isAlwaysOnTop = false
            }
        } catch (_: Throwable) {}
    }
}
