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
import org.ide.lti.core.model.run.StepState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunTimelineAnalysisTest {
    private val t0 = Instant.fromEpochSeconds(5_000)

    private fun timedStep(stageId: StageId, index: Int, label: String, offsetSec: Long, durSec: Long) = StageStep(
        stageId = stageId,
        index = index,
        idempotencyKey = "${stageId.name}_$index",
        command = StepCommand("tool-$index", listOf("--flag")),
        state = StepState.COMPLETED,
        label = label,
        startedAt = Instant.fromEpochSeconds(5_000 + offsetSec),
        endedAt = Instant.fromEpochSeconds(5_000 + offsetSec + durSec),
    )

    @Test
    fun analyzeProducesDagColumnsCacheCountsSharesAndBottleneck() {
        val run = BuildRun(
            id = "run_dag",
            workspaceId = "ws1",
            snapshotId = "snap1",
            startedAt = t0,
            endedAt = Instant.fromEpochSeconds(5_100),
            stages = listOf(
                StageOutcome(StageId.FIRMWARE_ACQUISITION, StageState.SKIPPED),
                StageOutcome(StageId.FIRMWARE_EXTRACTION, StageState.SKIPPED),
                StageOutcome(StageId.WORK_TREE_ASSEMBLY, StageState.SKIPPED),
                StageOutcome(
                    StageId.DEBLOAT,
                    StageState.EXECUTED,
                    startedAt = t0,
                    durationMs = 20_000,
                    whyRan = "debloat changed",
                ),
                StageOutcome(
                    StageId.MODULE_APPLICATION,
                    StageState.EXECUTED,
                    startedAt = Instant.fromEpochSeconds(5_020),
                    durationMs = 20_000,
                ),
                StageOutcome(
                    StageId.BUILD_FLASHABLE_ZIP,
                    StageState.EXECUTED,
                    startedAt = Instant.fromEpochSeconds(5_040),
                    durationMs = 60_000,
                ),
            ),
            steps = listOf(
                timedStep(StageId.DEBLOAT, 0, "remove bloat apks", 0, 20),
                timedStep(StageId.BUILD_FLASHABLE_ZIP, 0, "mkfs.erofs system", 40, 45),
                timedStep(StageId.BUILD_FLASHABLE_ZIP, 1, "sign ota zip", 85, 15),
            ),
        )

        val analysis = RunTimeline.analyze(run)
        assertEquals(3, analysis.executedCount)
        assertEquals(3, analysis.cachedCount)
        assertEquals(0, analysis.failedCount)
        assertEquals(100_000L, analysis.totalExecutedDurationMs)
        assertEquals(StageId.BUILD_FLASHABLE_ZIP, analysis.bottleneckStage)

        val buildNode = analysis.stageNode(StageId.BUILD_FLASHABLE_ZIP)!!
        assertEquals(TimelineNodeStatus.EXECUTED, buildNode.status)
        assertEquals(60, buildNode.sharePercent)
        assertTrue(buildNode.isBottleneck)
        // Build packs what Patches left, which applied to what Debloat left: a chain, not parallel branches.
        assertEquals(listOf(StageId.MODULE_APPLICATION), buildNode.upstream)
        val debloatNode = analysis.stageNode(StageId.DEBLOAT)!!
        val modNode = analysis.stageNode(StageId.MODULE_APPLICATION)!!
        assertEquals(listOf(StageId.DEBLOAT), modNode.upstream)
        assertEquals(debloatNode.column + 1, modNode.column)

        assertEquals("mkfs.erofs system", analysis.slowestSteps.first().label)
        assertEquals(45_000L, analysis.slowestSteps.first().durationMs)
    }

    @Test
    fun aStageShowsItsOwnLinesAndARunWithoutStartsSaysSo() {
        val logs = listOf("removing YouTube.apk", "mkfs.erofs system", "lpmake super.img", "signapk out.zip")
        val run = BuildRun(
            id = "r",
            workspaceId = "ws1",
            snapshotId = "snap1",
            startedAt = t0,
            stages = listOf(
                StageOutcome(StageId.WORK_TREE_ASSEMBLY, StageState.SKIPPED),
                StageOutcome(StageId.DEBLOAT, StageState.EXECUTED, logStart = 0),
                StageOutcome(StageId.BUILD_FLASHABLE_ZIP, StageState.EXECUTED, logStart = 1),
            ),
        )
        assertEquals(listOf("removing YouTube.apk"), TimelineLogFilter.filter(logs, run, StageId.DEBLOAT).lines)
        val built = TimelineLogFilter.filter(logs, run, StageId.BUILD_FLASHABLE_ZIP)
        assertEquals(logs.drop(1), built.lines)
        assertEquals(null, built.note)

        val step = TimelineStepNode(
            stageId = StageId.BUILD_FLASHABLE_ZIP,
            index = 0,
            label = "lpmake",
            commandDisplay = "lpmake --metadata-size 65536",
            durationMs = 1_000L,
            sharePercent = 50,
            startOffsetMs = 0L,
            state = StepState.COMPLETED,
            exitCode = 0,
        )
        val build = StageId.BUILD_FLASHABLE_ZIP
        assertEquals(listOf("lpmake super.img"), TimelineLogFilter.filter(logs, run, build, step).lines)
        val other = step.copy(label = "zipalign", commandDisplay = "zipalign")
        val unnamed = TimelineLogFilter.filter(logs, run, build, other)
        assertEquals(logs.drop(1), unnamed.lines)
        assertTrue(unnamed.note != null)

        // A run from before stages recorded where their output began: no lines, and why.
        val unrecorded = run.copy(stages = run.stages.map { it.copy(logStart = null) })
        val old = TimelineLogFilter.filter(logs, unrecorded, StageId.DEBLOAT)
        assertEquals(emptyList(), old.lines)
        assertTrue(old.note != null)
        assertEquals(logs, TimelineLogFilter.filter(logs, run, null).lines)
    }
}
