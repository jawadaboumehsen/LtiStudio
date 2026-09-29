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

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.model.user.EditorSettings
import org.ide.lti.core.model.user.NotificationSettings
import org.ide.lti.core.model.user.UserData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LtiPreferencesDataSourceTest {

    @Test
    fun testDefaultPreferencesLoadedCorrectly() = runTest {
        val settings = MapSettings()
        val dataSource = LtiPreferencesDataSource(settings, Dispatchers.Unconfined)

        val userData = dataSource.userData.first()
        assertEquals(UserData.DEFAULT_THEME, userData.theme)
        assertTrue(userData.effectsEnabled)
        assertFalse(userData.reducedMotion)
        assertTrue(userData.editorSettings.autoSave)
        assertTrue(userData.editorSettings.lineNumbers)
        assertTrue(userData.editorSettings.bracketMatching)
        assertEquals(14, userData.editorSettings.fontSize)
        assertEquals(4, userData.editorSettings.tabSize)
        assertTrue(userData.notificationSettings.pushEnabled)
        assertFalse(userData.notificationSettings.soundEnabled)
    }

    @Test
    fun testSetThemeUpdatesFlowAndSettings() = runTest {
        val settings = MapSettings()
        val dataSource = LtiPreferencesDataSource(settings, Dispatchers.Unconfined)

        dataSource.setTheme("dark")

        assertEquals("dark", dataSource.theme.first())
        assertEquals("dark", dataSource.userData.first().theme)
        assertEquals("dark", settings.getString(LtiPreferencesDataSource.KEY_THEME, ""))
    }

    @Test
    fun testSetEffectsAndReducedMotionUpdates() = runTest {
        val settings = MapSettings()
        val dataSource = LtiPreferencesDataSource(settings, Dispatchers.Unconfined)

        dataSource.setEffectsEnabled(false)
        dataSource.setReducedMotion(true)

        val userData = dataSource.userData.first()
        assertFalse(userData.effectsEnabled)
        assertTrue(userData.reducedMotion)
        assertFalse(settings.getBoolean(LtiPreferencesDataSource.KEY_EFFECTS_ENABLED, true))
        assertTrue(settings.getBoolean(LtiPreferencesDataSource.KEY_REDUCED_MOTION, false))
    }

    @Test
    fun testSetEditorAndNotificationSettings() = runTest {
        val settings = MapSettings()
        val dataSource = LtiPreferencesDataSource(settings, Dispatchers.Unconfined)

        dataSource.setEditorSettings(
            EditorSettings(
                autoSave = false,
                lineNumbers = false,
                bracketMatching = false,
                fontSize = 16,
                tabSize = 2,
            ),
        )
        dataSource.setNotificationSettings(
            NotificationSettings(
                pushEnabled = false,
                soundEnabled = true,
            ),
        )

        val userData = dataSource.userData.first()
        assertFalse(userData.editorSettings.autoSave)
        assertFalse(userData.editorSettings.lineNumbers)
        assertFalse(userData.editorSettings.bracketMatching)
        assertEquals(16, userData.editorSettings.fontSize)
        assertEquals(2, userData.editorSettings.tabSize)
        assertFalse(userData.notificationSettings.pushEnabled)
        assertTrue(userData.notificationSettings.soundEnabled)
    }
}
