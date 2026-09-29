/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.repository.user

import kotlinx.coroutines.flow.Flow
import org.ide.lti.core.datastore.LtiPreferencesDataSource
import org.ide.lti.core.domain.repository.user.UserDataRepository
import org.ide.lti.core.model.user.EditorSettings
import org.ide.lti.core.model.user.NotificationSettings
import org.ide.lti.core.model.user.UserData

/**
 * Thin [UserDataRepository] wrapper over [LtiPreferencesDataSource].
 */
public class UserDataRepositoryImpl(
    private val preferencesDataSource: LtiPreferencesDataSource,
) : UserDataRepository {
    override val userData: Flow<UserData> = preferencesDataSource.userData

    override val currentUserData: UserData get() = preferencesDataSource.currentUserData

    override suspend fun setTheme(theme: String) = preferencesDataSource.setTheme(theme)

    override suspend fun setEffectsEnabled(enabled: Boolean) = preferencesDataSource.setEffectsEnabled(enabled)

    override suspend fun setReducedMotion(reducedMotion: Boolean) =
        preferencesDataSource.setReducedMotion(reducedMotion)

    override suspend fun setEditorSettings(editorSettings: EditorSettings) =
        preferencesDataSource.setEditorSettings(editorSettings)

    override suspend fun setNotificationSettings(notificationSettings: NotificationSettings) =
        preferencesDataSource.setNotificationSettings(notificationSettings)
}
