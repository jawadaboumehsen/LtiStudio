/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.actions

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Registers a global keyboard shortcut on this node's subtree, e.g.
 * `Modifier.onKeyShortcut(key = Key.K, isCtrlOrCmd = true) { openCommandPalette() }`.
 *
 * [isCtrlOrCmd] matches either Ctrl (Windows/Linux) or Cmd/Meta (macOS) so callers don't need to
 * branch on platform.
 */
public fun Modifier.onKeyShortcut(
    key: Key,
    isCtrlOrCmd: Boolean = false,
    isShift: Boolean = false,
    isAlt: Boolean = false,
    onShortcut: () -> Unit,
): Modifier = onPreviewKeyEvent { event ->
    val matches = event.type == KeyEventType.KeyDown &&
        event.key == key &&
        (!isCtrlOrCmd || event.isCtrlPressed || event.isMetaPressed) &&
        event.isShiftPressed == isShift &&
        event.isAltPressed == isAlt
    if (matches) {
        onShortcut()
        true
    } else {
        false
    }
}
