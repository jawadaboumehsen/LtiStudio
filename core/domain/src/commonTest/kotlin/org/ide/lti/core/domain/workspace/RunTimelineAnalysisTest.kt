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
        assertEquals(listOf(StageId.DEBLOAT, StageId.MODULE_APPLICATION), buildNode.upstream)

        val debloatNode = analysis.stageNode(StageId.DEBLOAT)!!
        val modNode = analysis.stageNode(StageId.MODULE_APPLICATION)!!
        assertEquals(debloatNode.column, modNode.column)
        assertEquals(0, debloatNode.lane)
        assertEquals(1, modNode.lane)

        assertEquals("mkfs.erofs system", analysis.slowestSteps.first().label)
        assertEquals(45_000L, analysis.slowestSteps.first().durationMs)
    }

    @Test
    fun timelineLogFilterScopesToStageAndStepWithSafeFallback() {
        val logs = listOf(
            "[DEBLOAT] removing YouTube.apk",
            "[BUILD_FLASHABLE_ZIP] running mkfs.erofs on system",
            "[BUILD_FLASHABLE_ZIP] running lpmake super.img",
            "[BUILD_FLASHABLE_ZIP] running signapk out.zip",
        )
        val stageOnly = TimelineLogFilter.filter(logs, StageId.BUILD_FLASHABLE_ZIP)
        assertEquals(3, stageOnly.size)

        val stepNode = TimelineStepNode(
            stageId = StageId.BUILD_FLASHABLE_ZIP,
            index = 0,
            label = "mkfs.erofs",
            commandDisplay = "mkfs.erofs system.img",
            durationMs = 1_000L,
            sharePercent = 50,
            startOffsetMs = 0L,
            state = StepState.COMPLETED,
            exitCode = 0,
        )
        val stepFiltered = TimelineLogFilter.filter(logs, StageId.BUILD_FLASHABLE_ZIP, stepNode)
        assertEquals(listOf("[BUILD_FLASHABLE_ZIP] running mkfs.erofs on system"), stepFiltered)
    }
}
