/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.versions

import kotlin.test.Test
import kotlin.test.assertEquals

class ToolVersionsPresentationMapperTest {

    @Test
    fun `maps to up to date when desired equals installed and healthy`() {
        val row = ToolVersionsPresentationMapper.mapGroupRow(
            groupId = "android-tools",
            installedVersion = "v1.2.3",
            desiredVersion = "v1.2.3",
            isHealthy = true,
        )

        assertEquals("v1.2.3", row.installedLabel)
        assertEquals("v1.2.3", row.selectedLabel)
        assertEquals(ToolGroupStatus.UP_TO_DATE, row.status)
        assertEquals("Up to date", row.status.label)
        assertEquals("Edit version", row.status.primaryAction)
    }

    @Test
    fun `maps to change pending when desired differs from installed`() {
        val row = ToolVersionsPresentationMapper.mapGroupRow(
            groupId = "android-tools",
            installedVersion = "v1.2.3",
            desiredVersion = "v2.0.0",
            isHealthy = true,
        )

        assertEquals("v1.2.3", row.installedLabel)
        assertEquals("v2.0.0", row.selectedLabel)
        assertEquals(ToolGroupStatus.CHANGE_PENDING, row.status)
        assertEquals("Change pending", row.status.label)
        assertEquals("Review & build", row.status.primaryAction)
    }

    @Test
    fun `maps to needs recovery when toolchain is not healthy`() {
        val row = ToolVersionsPresentationMapper.mapGroupRow(
            groupId = "android-tools",
            installedVersion = "v1.2.3",
            desiredVersion = "v1.2.3",
            isHealthy = false,
        )

        assertEquals(ToolGroupStatus.NEEDS_RECOVERY, row.status)
        assertEquals("Needs recovery", row.status.label)
        assertEquals("Recover", row.status.primaryAction)
    }
}
