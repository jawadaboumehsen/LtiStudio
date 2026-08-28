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
 */
class AppSettingsState(
    initialDarkTheme: Boolean = true,
    initialEffectsEnabled: Boolean = true,
    initialReducedMotion: Boolean = false,
) {
    var isDarkTheme: Boolean by mutableStateOf(initialDarkTheme)
    var effectsEnabled: Boolean by mutableStateOf(initialEffectsEnabled)
    var reducedMotion: Boolean by mutableStateOf(initialReducedMotion)
}
