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
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.run.StageOutcome
import org.ide.lti.core.model.run.StageState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RunEstimateTest {
    private fun run(at: Long, vararg stages: Pair<StageId, Long?>) = BuildRun(
        id = "r$at",
        workspaceId = "w",
        snapshotId = "s",
        startedAt = Instant.fromEpochSeconds(at),
        stages = stages.map { (id, ms) ->
            StageOutcome(id, if (ms == null) StageState.SKIPPED else StageState.EXECUTED, durationMs = ms)
        },
    )

    private val build = StageId.BUILD_FLASHABLE_ZIP
    private val meta = StageId.GENERATE_OTA_MANIFEST
    private val debloat = StageId.DEBLOAT

    @Test
    fun theStagesThatRunAreEstimatedFromTheirRealRunsAndTheOthersAreNamed() {
        val history = listOf(
            run(1, build to 600_000L, meta to 1_000L),
            run(2, build to 300_000L, meta to null),
            run(3, build to 400_000L, meta to 3_000L),
            run(4, debloat to null),
        )
        val preview = listOf(
            StagePreview(StageId.WORK_TREE_ASSEMBLY, null),
            StagePreview(debloat, "x"),
            StagePreview(build, "y"),
            StagePreview(meta, "z"),
        )

        val estimate = RunEstimate.of(preview, history)!!

        // Build: 300k, 400k, 600k -> median 400k; metadata: 1k, 3k -> median taken at index 1 = 3k. Cache hits ignored.
        assertEquals(403_000L, estimate.typicalMs)
        assertEquals(301_000L, estimate.shortestMs)
        assertEquals(603_000L, estimate.longestMs)
        assertEquals(2, estimate.samples)
        assertEquals(listOf(debloat), estimate.unknown)
    }

    @Test
    fun onlyTheNewestRunsCountAndNothingRunningGivesNoEstimate() {
        val history = (1L..8L).map { run(it, build to it * 1000) }
        val estimate = RunEstimate.of(listOf(StagePreview(build, "y")), history)!!
        assertEquals(4_000L, estimate.shortestMs) // runs 8, 7, 6, 5, 4
        assertEquals(RunEstimate.SAMPLES, estimate.samples)
        assertNull(RunEstimate.of(listOf(StagePreview(build, null)), history))
    }
}
