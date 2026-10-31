/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.studio

import kotlinx.datetime.Instant
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.run.StageOutcome
import org.ide.lti.core.model.run.StageState
import kotlin.test.Test
import kotlin.test.assertEquals

class RunActivityTest {

    private val titles = mapOf(StageId.BUILD_FLASHABLE_ZIP to "Build & Sign")

    private fun run(state: RunState, vararg stages: Pair<StageId, StageState>) = BuildRun(
        id = "r",
        workspaceId = "w",
        snapshotId = "s",
        startedAt = Instant.fromEpochMilliseconds(0),
        state = state,
        stages = stages.map { (id, s) -> StageOutcome(id, s) },
    )

    @Test
    fun theStatusBarNamesTheRunningStageAndHowFarTheBuildIs() {
        val building = run(
            RunState.RUNNING,
            StageId.FIRMWARE_ACQUISITION to StageState.SKIPPED,
            StageId.WORK_TREE_ASSEMBLY to StageState.EXECUTED,
            StageId.BUILD_FLASHABLE_ZIP to StageState.RUNNING,
            StageId.GENERATE_OTA_MANIFEST to StageState.PENDING,
        )
        assertEquals(ActivityUiState.Active("Build & Sign (stage 3 of 4)", 0.5f), runActivity(building, titles::get))
        assertEquals(ActivityUiState.Idle, runActivity(null, titles::get))
        assertEquals(
            "build queued",
            (runActivity(run(RunState.QUEUED), titles::get) as ActivityUiState.Active).description,
        )
    }
}
