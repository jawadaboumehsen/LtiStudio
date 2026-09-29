/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.api

import org.ide.lti.core.model.run.StageId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StageDescriptorTest {

    @Test
    fun studioSubobjectIdRejectsBlank() {
        assertFailsWith<IllegalArgumentException> {
            StudioSubobjectId("")
        }
        assertFailsWith<IllegalArgumentException> {
            StudioSubobjectId("  ")
        }
    }

    @Test
    fun stageDescriptorRejectsBlankTitles() {
        assertFailsWith<IllegalArgumentException> {
            StageDescriptor(
                stageId = StageId.FIRMWARE_ACQUISITION,
                title = "   ",
                iconKey = StudioIconKey.ACQUIRE,
                subobjects = emptyList(),
            )
        }
    }

    @Test
    fun stageDescriptorRejectsDuplicateSubobjectIds() {
        assertFailsWith<IllegalArgumentException> {
            StageDescriptor(
                stageId = StageId.FIRMWARE_ACQUISITION,
                title = "Acquire",
                iconKey = StudioIconKey.ACQUIRE,
                subobjects = listOf(
                    StudioSubobject(StudioSubobjectId("source"), "Source 1"),
                    StudioSubobject(StudioSubobjectId("source"), "Source 2"),
                ),
            )
        }
    }

    @Test
    fun romStudioRouteBuildsValidQuery() {
        val route = RomStudioRoute.buildRoute(
            workspaceId = "ws-test-1",
            stageId = StageId.DEBLOAT,
            subobjectId = StudioSubobjectId("presets"),
        )
        assertEquals("studio/ws-test-1?stageId=DEBLOAT&subobjectId=presets", route)
    }
}
