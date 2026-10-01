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
import org.ide.lti.core.domain.debloat.DebloatDecision
import org.ide.lti.core.domain.debloat.DebloatResolution
import org.ide.lti.core.domain.debloat.DebloatResolvedRemoval
import org.ide.lti.core.domain.debloat.RemovalReason
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.domain.pipeline.stages.DebloatStage
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.target.TargetFirmware
import org.ide.lti.core.model.target.TargetRegion
import org.ide.lti.core.model.workspace.AcquisitionSettings
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.DebloatSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
    fun `default enabled plan is one Check that returns a non-null failure naming inventory unavailable`() {
        val stage = DebloatStage()
        val ctx = createContext(enabled = true)
        val plan = stage.plan(ctx)

        assertEquals(1, plan.size)
        val step = plan.first() as PipelineStep.Check
        val error = step.verify(emptyMap())
        assertNotNull(error)
        assertTrue(error.contains("DEBLOAT_INVENTORY_UNAVAILABLE"))
    }

    @Test
    fun `enabled with a Resolved decision plans the selection json and Check`() {
        val resolvedDecision = DebloatDecision(
            removals = listOf(
                DebloatResolvedRemoval(
                    partition = "system",
                    relativePath = "app/YouTube/YouTube.apk",
                    sizeBytes = 1000L,
                    packageId = "com.google.android.youtube",
                    reason = RemovalReason.EXPLICIT_SELECTOR,
                    sidecarEntries = emptyList(),
                ),
            ),
            kept = emptyList(),
            logicalBytesFreed = 1000L,
        )
        val stage = DebloatStage(
            debloatPlanner = { DebloatResolution.Resolved(resolvedDecision) },
        )
        val ctx = createContext(enabled = true)
        val plan = stage.plan(ctx)

        assertEquals(2, plan.size)
        val writeStep = plan[0] as PipelineStep.WriteFile
        assertEquals("work/debloat/selection.json", writeStep.relPath)
        assertTrue(writeStep.content.decodeToString().contains("com.google.android.youtube"))

        val checkStep = plan[1] as PipelineStep.Check
        assertEquals("debloat applied", checkStep.label)

        // Fails if runtime value is missing or not ok
        val failMsg = checkStep.verify(emptyMap())
        assertNotNull(failMsg)

        // Succeeds if runtime value is ok
        val okMsg = checkStep.verify(mapOf("debloat.apply.result" to "ok"))
        assertNull(okMsg)

        val outputs = stage.outputs(ctx)
        assertEquals(listOf("work/debloat/selection.json"), outputs)
    }

    @Test
    fun `enabled with a Rejected resolution plans a failing Check naming the code`() {
        val rejectionCode = "PROTECTED_ENTRY"
        val stage = DebloatStage(
            debloatPlanner = {
                DebloatResolution.Rejected(
                    ValidationReport(
                        errors = listOf(
                            ValidationError(
                                stageId = StageId.DEBLOAT,
                                objectId = null,
                                fieldPath = "system:etc/hosts",
                                code = rejectionCode,
                                severity = Severity.ERROR,
                                message = "Protected entry cannot be removed",
                            ),
                        ),
                    ),
                )
            },
        )
        val ctx = createContext(enabled = true)
        val plan = stage.plan(ctx)

        assertEquals(1, plan.size)
        val checkStep = plan.first() as PipelineStep.Check
        val error = checkStep.verify(emptyMap())
        assertNotNull(error)
        assertTrue(error.contains(rejectionCode))
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

    @Test
    fun `cache key changes with the decision`() {
        val decision1 = DebloatDecision(
            removals = listOf(
                DebloatResolvedRemoval("system", "app/A.apk", 100L, null, RemovalReason.PRESET, emptyList()),
            ),
            kept = emptyList(),
            logicalBytesFreed = 100L,
        )
        val decision2 = DebloatDecision(
            removals = listOf(
                DebloatResolvedRemoval("system", "app/B.apk", 200L, null, RemovalReason.PRESET, emptyList()),
            ),
            kept = emptyList(),
            logicalBytesFreed = 200L,
        )

        val stage1 = DebloatStage(debloatPlanner = { DebloatResolution.Resolved(decision1) })
        val stage2 = DebloatStage(debloatPlanner = { DebloatResolution.Resolved(decision2) })

        val ctx = createContext(enabled = true)
        val key1 = stage1.computeCacheKey(ctx, "prev")
        val key2 = stage2.computeCacheKey(ctx, "prev")

        assertNotEquals(key1, key2)
    }
}
