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

import kotlinx.datetime.Instant
import org.ide.lti.core.domain.pipeline.stages.DebloatStage
import org.ide.lti.core.model.target.TargetFirmware
import org.ide.lti.core.model.target.TargetRegion
import org.ide.lti.core.model.workspace.AcquisitionSettings
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.DebloatSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DebloatStageTest {

    private fun createSnapshot(enabled: Boolean): ConfigurationSnapshot = ConfigurationSnapshot.create(
        id = "id",
        workspaceId = "ws",
        profileRevision = 1,
        acquisition = AcquisitionSettings(
            region = TargetRegion.GLOBAL,
            firmware = TargetFirmware("v1", "test-url", "test-sha", "2023-01"),
        ),
        debloat = DebloatSettings(enabled = enabled),
        createdAt = Instant.parse("2023-01-01T00:00:00Z"),
    )

    private fun createContext(enabled: Boolean): StageContext = StageContext(
        workspace = org.ide.lti.core.model.workspace.Workspace("ws", "name", "dir"),
        target = org.ide.lti.core.model.target.TargetDevice.EMPTY,
        snapshot = createSnapshot(enabled),
        runtimeValues = emptyMap(),
    )

    @Test
    fun `disabled plan is exactly one WriteFile of the pass-through JSON and outputs lists the result path`() {
        val stage = DebloatStage()
        val ctx = createContext(enabled = false)
        val plan = stage.plan(ctx)

        assertEquals(1, plan.size)
        val step = plan.first() as PipelineStep.WriteFile
        assertEquals("work/debloat/result.json", step.relPath)
        assertEquals("""{"enabled":false,"removed":[]}""", step.content.decodeToString())

        val outputs = stage.outputs(ctx)
        assertEquals(listOf("work/debloat/result.json"), outputs)
    }

    @Test
    fun `enabled plan is one Check that returns a non-null failure`() {
        val stage = DebloatStage()
        val ctx = createContext(enabled = true)
        val plan = stage.plan(ctx)

        assertEquals(1, plan.size)
        val step = plan.first() as PipelineStep.Check
        val error = step.verify(emptyMap())
        assertNotNull(error)
        assertTrue(error.contains("Debloat removals are not implemented yet"))
    }

    @Test
    fun `cache key changes when debloat settings change`() {
        val stage = DebloatStage()
        val ctxDisabled = createContext(enabled = false)
        val ctxEnabled = createContext(enabled = true)

        val keyDisabled = stage.computeCacheKey(ctxDisabled, "prev")
        val keyEnabled = stage.computeCacheKey(ctxEnabled, "prev")

        assertNotEquals(keyDisabled, keyEnabled)
    }
}
