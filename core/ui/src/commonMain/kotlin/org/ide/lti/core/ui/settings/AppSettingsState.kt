/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.ui.settings

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.repository.user.UserDataRepository
import org.ide.lti.core.model.user.EditorSettings
import org.ide.lti.core.model.user.NotificationSettings

/**
 * Session-scoped in-memory state holder for application-wide settings.
 *
 * Stores theme preferences using decoupled identifier strings
 * to preserve strict architectural modularization between data and design systems.
 *
 * When [userDataRepository] and [coroutineScope] are supplied, mutations are persisted
 * asynchronously; both are optional so this class stays usable as a plain in-memory holder
 * (e.g. in tests/previews).
 */
class AppSettingsState(
    initialTheme: String = THEME_BLUE,
    initialEffectsEnabled: Boolean = true,
    initialReducedMotion: Boolean = false,
    initialEditorSettings: EditorSettings = EditorSettings(),
    initialNotificationSettings: NotificationSettings = NotificationSettings(),
    private val userDataRepository: UserDataRepository? = null,
    private val coroutineScope: CoroutineScope? = null,
) {
    private val themeState = mutableStateOf(initialTheme)
    var theme: String
        get() = themeState.value
        set(value) {
            themeState.value = value
            persist { setTheme(value) }
        }

    private val effectsEnabledState = mutableStateOf(initialEffectsEnabled)
    var effectsEnabled: Boolean
        get() = effectsEnabledState.value
        set(value) {
            effectsEnabledState.value = value
            persist { setEffectsEnabled(value) }
        }

    private val reducedMotionState = mutableStateOf(initialReducedMotion)
    var reducedMotion: Boolean
        get() = reducedMotionState.value
        set(value) {
            reducedMotionState.value = value
            persist { setReducedMotion(value) }
        }

    var fluidAnimations: Boolean
        get() = !reducedMotionState.value
        set(value) {
            reducedMotion = !value
        }

    private val editorSettingsState = mutableStateOf(initialEditorSettings)
    var editorSettings: EditorSettings
        get() = editorSettingsState.value
        set(value) {
            editorSettingsState.value = value
            persist { setEditorSettings(value) }
        }

    private val notificationSettingsState = mutableStateOf(initialNotificationSettings)
    var notificationSettings: NotificationSettings
        get() = notificationSettingsState.value
        set(value) {
            notificationSettingsState.value = value
            persist { setNotificationSettings(value) }
        }

    private fun persist(action: suspend UserDataRepository.() -> Unit) {
        val repository = userDataRepository ?: return
        val scope = coroutineScope ?: return
        scope.launch { repository.action() }
    }

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
