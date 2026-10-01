/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.repository.user

import kotlinx.coroutines.flow.Flow
import org.ide.lti.core.model.user.EditorSettings
import org.ide.lti.core.model.user.NotificationSettings
import org.ide.lti.core.model.user.UserData

/**
 * Repository for persisted user preferences.
 */
public interface UserDataRepository {
    public val userData: Flow<UserData>

    public val currentUserData: UserData

    public suspend fun setTheme(theme: String)

    public suspend fun setEffectsEnabled(enabled: Boolean)

    public suspend fun setReducedMotion(reducedMotion: Boolean)

    public suspend fun setEditorSettings(editorSettings: EditorSettings)

    public suspend fun setNotificationSettings(notificationSettings: NotificationSettings)
}
