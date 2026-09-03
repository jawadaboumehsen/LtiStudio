/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Session-scoped in-memory state holder for application-wide settings.
 *
 * Stores theme preferences using decoupled identifier strings
 * to preserve strict architectural modularization between data and design systems.
 */
class AppSettingsState(
    initialTheme: String = THEME_BLUE,
    initialEffectsEnabled: Boolean = true,
    initialReducedMotion: Boolean = false,
) {
    var theme: String by mutableStateOf(initialTheme)
    var effectsEnabled: Boolean by mutableStateOf(initialEffectsEnabled)
    var reducedMotion: Boolean by mutableStateOf(initialReducedMotion)

    /**
     * Backward-compatible constructor accepting boolean darkTheme.
     */
    constructor(
        initialDarkTheme: Boolean,
        initialEffectsEnabled: Boolean = true,
        initialReducedMotion: Boolean = false,
    ) : this(
        initialTheme = if (initialDarkTheme) THEME_BLUE else THEME_LIGHT,
        initialEffectsEnabled = initialEffectsEnabled,
        initialReducedMotion = initialReducedMotion,
    )

    /**
     * Backward-compatible property accessor for boolean dark mode.
     */
    var isDarkTheme: Boolean
        get() = theme != THEME_LIGHT
        set(value) {
            theme = if (value) {
                if (theme == THEME_LIGHT) THEME_BLUE else theme
            } else {
                THEME_LIGHT
            }
        }

    companion object {
        const val THEME_BLUE = "blue"
        const val THEME_DARK = "dark"
        const val THEME_LIGHT = "light"
    }
}

