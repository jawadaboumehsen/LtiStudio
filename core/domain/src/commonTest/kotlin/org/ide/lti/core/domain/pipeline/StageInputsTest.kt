/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline

import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.workspace.AcquisitionSettings
import org.ide.lti.core.model.workspace.BuildSettings
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StageInputsTest {
    @Test
    fun aChangedSettingIsNamedWithItsOldAndNewValue() {
        val json = Json { encodeDefaults = true }
        val snapshot = ConfigurationSnapshot.create(
            id = "snap",
            workspaceId = "ws",
            profileRevision = 1,
            acquisition = AcquisitionSettings(
                region = DefaultTargetCatalog.PQ84P01_DEFAULT.availableRegions.first(),
                firmware = DefaultTargetCatalog.PQ84P01_GLOBAL_FIRMWARES.first(),
            ),
            createdAt = Clock.System.now(),
        )
        val before = StageInputs.settings(StageId.BUILD_FLASHABLE_ZIP, snapshot, json)
        val after = StageInputs.settings(
            StageId.BUILD_FLASHABLE_ZIP,
            snapshot.copy(build = snapshot.build.copy(brotliLevel = 9, keepStock = setOf("odm"))),
            json,
        )
        assertTrue(before.keys.all { it.startsWith("build.") })
        val changes = StageInputs.changes(before + ("tool mkfs.erofs" to "/a"), after + ("tool mkfs.erofs" to "/b"))
        assertEquals(
            listOf(
                "build.brotliLevel: ${BuildSettings().brotliLevel} → 9",
                "build.keepStock: [] → [\"odm\"]",
                "tool mkfs.erofs: /a → /b",
            ),
            changes,
        )
    }
}
