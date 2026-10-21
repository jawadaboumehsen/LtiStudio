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

import com.russhwolf.settings.PropertiesSettings
import java.nio.file.Files
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AppSettingsFileTest {
    @Test
    fun `legacy preferences migrate once including non string values and removed keys stay removed`() {
        val legacy = PropertiesSettings(Properties()).apply {
            putString("lti_workspaces_v1", "workspace records")
            putBoolean("pref_effects_enabled", false)
            putInt("pref_editor_font_size", 19)
        }
        val file = Files.createTempDirectory("lti-migration").resolve("settings.properties").toFile()
        fileSettings(file).putInt("pref_editor_font_size", 21)
        val migrated = fileSettings(file, legacy)
        assertEquals("workspace records", migrated.getStringOrNull("lti_workspaces_v1"))
        assertFalse(migrated.getBoolean("pref_effects_enabled", true))
        assertEquals(21, migrated.getInt("pref_editor_font_size", 0), "newer file settings win")
        migrated.remove("lti_workspaces_v1")
        migrated.putInt("pref_editor_font_size", 23)
        val reopened = fileSettings(file, legacy)
        assertNull(reopened.getStringOrNull("lti_workspaces_v1"))
        assertEquals(23, reopened.getInt("pref_editor_font_size", 0))
        assertEquals("workspace records", legacy.getStringOrNull("lti_workspaces_v1"), "legacy store stays intact")
    }

    @Test
    fun `values far over the 8 KB registry limit are kept across restarts`() {
        val file = Files.createTempDirectory("lti-settings").resolve("settings.properties").toFile()
        val big = "x".repeat(200_000) + "é\n=:#"

        fileSettings(file).putString("lti_snapshots_v1", big)

        assertEquals(big, fileSettings(file).getStringOrNull("lti_snapshots_v1"), "read back by a fresh instance")
        assertFalse(file.resolveSibling("settings.properties.tmp").exists(), "the temporary copy is renamed away")
    }

    @Test
    fun `a removed key stays removed`() {
        val file = Files.createTempDirectory("lti-settings").resolve("settings.properties").toFile()
        fileSettings(file).putString("k", "v")
        fileSettings(file).remove("k")
        assertNull(fileSettings(file).getStringOrNull("k"))
    }
}
