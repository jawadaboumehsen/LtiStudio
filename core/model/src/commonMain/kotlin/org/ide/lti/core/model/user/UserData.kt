/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.user

/**
 * Aggregate root for persisted user preferences.
 */
public data class UserData(
    val theme: String = DEFAULT_THEME,
    val effectsEnabled: Boolean = true,
    val reducedMotion: Boolean = false,
    val editorSettings: EditorSettings = EditorSettings(),
    val notificationSettings: NotificationSettings = NotificationSettings(),
) {
    public companion object {
        public const val DEFAULT_THEME: String = "blue"
    }
}
