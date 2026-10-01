/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.datastore

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.ide.lti.core.model.user.EditorSettings
import org.ide.lti.core.model.user.NotificationSettings
import org.ide.lti.core.model.user.UserData

/**
 * Multiplatform persistent preferences data source backed by [Settings].
 *
 * Adheres to:
 * - Single Responsibility: Manages key-value persistence and reactive flows for user preferences.
 * - Reactive Streams: Exposes [userData] and dedicated [theme] flows.
 * - Modularization: Pure data source in :core:datastore without direct domain or UI coupling.
 */
class LtiPreferencesDataSource(
    private val settings: Settings = Settings(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _userData = MutableStateFlow(readCurrentUserData())

    /**
     * Continuous reactive stream of user preferences.
     */
    val userData: Flow<UserData> = _userData.asStateFlow()

    /**
     * Synchronous snapshot of the current user preferences.
     */
    val currentUserData: UserData get() = _userData.value

    /**
     * Dedicated reactive stream of the active theme identifier.
     */
    val theme: Flow<String> = _userData.map { it.theme }.distinctUntilChanged()

    suspend fun setTheme(theme: String) = updateUserData { it.copy(theme = theme) }

    suspend fun setEffectsEnabled(enabled: Boolean) = updateUserData { it.copy(effectsEnabled = enabled) }

    suspend fun setReducedMotion(reducedMotion: Boolean) = updateUserData { it.copy(reducedMotion = reducedMotion) }

    suspend fun setEditorSettings(editorSettings: EditorSettings) = updateUserData { it.copy(editorSettings = editorSettings) }

    suspend fun setNotificationSettings(notificationSettings: NotificationSettings) =
        updateUserData { it.copy(notificationSettings = notificationSettings) }

    /**
     * Atomically transforms and persists the current [UserData].
     */
    suspend fun updateUserData(transform: (UserData) -> UserData) = withContext(ioDispatcher) {
        val updated = transform(_userData.value)
        persistUserData(updated)
        _userData.value = updated
    }

    private fun readCurrentUserData(): UserData {
        return UserData(
            theme = settings.getString(KEY_THEME, UserData.DEFAULT_THEME),
            effectsEnabled = settings.getBoolean(KEY_EFFECTS_ENABLED, true),
            reducedMotion = settings.getBoolean(KEY_REDUCED_MOTION, false),
            editorSettings = EditorSettings(
                autoSave = settings.getBoolean(KEY_EDITOR_AUTO_SAVE, true),
                lineNumbers = settings.getBoolean(KEY_EDITOR_LINE_NUMBERS, true),
                bracketMatching = settings.getBoolean(KEY_EDITOR_BRACKET_MATCHING, true),
                fontSize = settings.getInt(KEY_EDITOR_FONT_SIZE, EditorSettings.DEFAULT_FONT_SIZE),
                tabSize = settings.getInt(KEY_EDITOR_TAB_SIZE, EditorSettings.DEFAULT_TAB_SIZE),
            ),
            notificationSettings = NotificationSettings(
                pushEnabled = settings.getBoolean(KEY_NOTIF_PUSH_ENABLED, true),
                soundEnabled = settings.getBoolean(KEY_NOTIF_SOUND_ENABLED, false),
            ),
        )
    }

    private fun persistUserData(data: UserData) {
        settings[KEY_THEME] = data.theme
        settings[KEY_EFFECTS_ENABLED] = data.effectsEnabled
        settings[KEY_REDUCED_MOTION] = data.reducedMotion
        settings[KEY_EDITOR_AUTO_SAVE] = data.editorSettings.autoSave
        settings[KEY_EDITOR_LINE_NUMBERS] = data.editorSettings.lineNumbers
        settings[KEY_EDITOR_BRACKET_MATCHING] = data.editorSettings.bracketMatching
        settings[KEY_EDITOR_FONT_SIZE] = data.editorSettings.fontSize
        settings[KEY_EDITOR_TAB_SIZE] = data.editorSettings.tabSize
        settings[KEY_NOTIF_PUSH_ENABLED] = data.notificationSettings.pushEnabled
        settings[KEY_NOTIF_SOUND_ENABLED] = data.notificationSettings.soundEnabled
    }

    companion object {
        const val KEY_THEME = "pref_theme"
        const val KEY_EFFECTS_ENABLED = "pref_effects_enabled"
        const val KEY_REDUCED_MOTION = "pref_reduced_motion"
        const val KEY_EDITOR_AUTO_SAVE = "pref_editor_auto_save"
        const val KEY_EDITOR_LINE_NUMBERS = "pref_editor_line_numbers"
        const val KEY_EDITOR_BRACKET_MATCHING = "pref_editor_bracket_matching"
        const val KEY_EDITOR_FONT_SIZE = "pref_editor_font_size"
        const val KEY_EDITOR_TAB_SIZE = "pref_editor_tab_size"
        const val KEY_NOTIF_PUSH_ENABLED = "pref_notif_push_enabled"
        const val KEY_NOTIF_SOUND_ENABLED = "pref_notif_sound_enabled"
    }
}
