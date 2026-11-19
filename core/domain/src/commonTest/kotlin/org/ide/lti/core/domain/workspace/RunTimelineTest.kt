/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.workspace

import kotlinx.datetime.Instant
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.run.StageOutcome
import org.ide.lti.core.model.run.StageState
import org.ide.lti.core.model.run.StageStep
import org.ide.lti.core.model.run.StepCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunTimelineTest {
    private val t0 = Instant.fromEpochSeconds(1_000)

    private fun step(index: Int, label: String?, seconds: Long) = StageStep(
        stageId = StageId.BUILD_FLASHABLE_ZIP,
        index = index,
        idempotencyKey = "k$index",
        command = StepCommand("sh"),
        label = label,
        startedAt = t0,
        endedAt = Instant.fromEpochSeconds(1_000 + seconds),
    )

    @Test
    fun eachStageShowsItsShareWhyItRanAndItsSlowestSteps() {
        val run = BuildRun(
            id = "r",
            workspaceId = "w",
            snapshotId = "s",
            startedAt = t0,
            stages = listOf(
                StageOutcome(StageId.WORK_TREE_ASSEMBLY, StageState.SKIPPED),
                StageOutcome(
                    StageId.DEBLOAT,
                    StageState.EXECUTED,
                    startedAt = t0,
                    durationMs = 30_000,
                    whyRan = "debloat.removeSelectors",
                ),
                StageOutcome(
                    StageId.BUILD_FLASHABLE_ZIP,
                    StageState.EXECUTED,
                    startedAt = Instant.fromEpochSeconds(1_030),
                    durationMs = 90_000,
                ),
            ),
            steps = listOf(
                step(0, "mkfs system", 60),
                step(1, "zip", 20),
                step(2, null, 5),
                step(3, "sign", 2),
            ),
        )
        val text = RunTimeline.of(run)
        assertTrue("Stages that ran took 2 min 0 s in all." in text, text)
        assertTrue("- Assemble: from cache" in text, text)
        assertTrue("- Debloat: 30 s (25%), from +0 ms; ran because debloat.removeSelectors" in text, text)
        assertTrue("- Build & sign: 1 min 30 s (75%), from +30 s" in text, text)
        assertTrue("  - mkfs system: 1 min 0 s\n  - zip: 20 s\n  - sh: 5 s\n" in text, text)
        assertTrue("sign: 2 s" !in text, text)
    }

    @Test
    fun durationsReadNaturally() {
        assertEquals("850 ms", RunTimeline.duration(850))
        assertEquals("12 min 5 s", RunTimeline.duration(725_000))
        assertEquals("1 h 3 min", RunTimeline.duration(3_780_000))
    }
}
