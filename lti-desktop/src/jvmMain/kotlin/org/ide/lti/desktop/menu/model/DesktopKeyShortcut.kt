/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.ide.lti.desktop.menu.model

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut

/**
 * Encapsulates resolved keyboard shortcut modifier flags across operating systems.
 */
data class ShortcutModifiers(
    val ctrl: Boolean,
    val meta: Boolean,
    val shift: Boolean = false,
    val alt: Boolean = false,
)

/**
 * Cross-platform keyboard shortcut builder.
 *
 * Compose Desktop's [KeyShortcut] maps `ctrl = true` strictly to the Ctrl key across all platforms,
 * including macOS. This helper automatically translates primary action shortcuts to `meta = true`
 * (⌘ Command) on macOS and `ctrl = true` (Ctrl) on Windows and Linux, complying with OS-native guidelines.
 */
object DesktopKeyShortcut {

    /**
     * Whether the host operating system is macOS.
     */
    val isMac: Boolean by lazy {
        System.getProperty("os.name")?.contains("Mac", ignoreCase = true) == true
    }

    /**
     * Resolves modifier states for primary actions based on the target operating system.
     */
    fun resolveModifiers(
        shift: Boolean = false,
        alt: Boolean = false,
        isMacOs: Boolean = isMac,
    ): ShortcutModifiers = ShortcutModifiers(
        ctrl = !isMacOs,
        meta = isMacOs,
        shift = shift,
        alt = alt,
    )

    /**
     * Creates a standard primary application shortcut:
     * - macOS: `Cmd + [key]` (plus optional `shift` or `alt`)
     * - Windows / Linux: `Ctrl + [key]` (plus optional `shift` or `alt`)
     */
    fun primary(
        key: Key,
        shift: Boolean = false,
        alt: Boolean = false,
        isMacOs: Boolean = isMac,
    ): KeyShortcut {
        val mods = resolveModifiers(shift = shift, alt = alt, isMacOs = isMacOs)
        return KeyShortcut(
            key = key,
            ctrl = mods.ctrl,
            meta = mods.meta,
            shift = mods.shift,
            alt = mods.alt,
        )
    }

    /**
     * Creates a custom application shortcut with explicit modifiers.
     */
    fun custom(
        key: Key,
        ctrl: Boolean = false,
        meta: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
    ): KeyShortcut {
        return KeyShortcut(
            key = key,
            ctrl = ctrl,
            meta = meta,
            alt = alt,
            shift = shift,
        )
    }
}
