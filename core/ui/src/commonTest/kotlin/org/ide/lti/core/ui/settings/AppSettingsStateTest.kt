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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.repository.user.UserDataRepository
import org.ide.lti.core.model.user.AppearanceSettings
import org.ide.lti.core.model.user.EditorSettings
import org.ide.lti.core.model.user.NotificationSettings
import org.ide.lti.core.model.user.UserData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppSettingsStateTest {
    @Test
    fun failedAppearanceSaveStaysVisibleUntilThatSettingSaves() = runTest {
        val allowWrite = CompletableDeferred<Unit>()
        var fail = true
        val repository = object : UserDataRepository {
            override val currentUserData = UserData()
            override val userData = flowOf(currentUserData)
            override suspend fun setTheme(theme: String) {}
            override suspend fun setEffectsEnabled(enabled: Boolean) {}
            override suspend fun setReducedMotion(reducedMotion: Boolean) {}
            override suspend fun setEditorSettings(editorSettings: EditorSettings) {}
            override suspend fun setNotificationSettings(notificationSettings: NotificationSettings) {}
            override suspend fun setAppearance(appearance: AppearanceSettings) {
                allowWrite.await()
                check(!fail) { "disk full" }
            }
        }
        val state = AppSettingsState(userDataRepository = repository, coroutineScope = backgroundScope)
        state.appearance = AppearanceSettings(textScalePercent = 125)
        assertEquals("Saving changes…", state.saveStatus)
        allowWrite.complete(Unit)
        runCurrent()
        assertTrue(state.saveStatus.startsWith("Could not save: appearance"))
        assertEquals(125, state.appearance.textScalePercent)

        state.theme = AppSettingsState.THEME_DARK
        runCurrent()
        assertTrue(state.saveStatus.startsWith("Could not save: appearance"))

        fail = false
        state.appearance = AppearanceSettings(textScalePercent = 110)
        runCurrent()
        assertTrue(state.saveStatus.endsWith("are saved for this app."))
    }
}
