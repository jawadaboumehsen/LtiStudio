/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup

import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.model.setup.ToolchainPersistenceState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SetupStateUpdatesTest {
    private val fresh = ToolchainSetupState(steps = ToolchainSetupState.defaultSteps())

    @Test
    fun aFailedStagePutsEveryLaterStageBackToPendingAndStopsTheCheck() {
        val failed = SetupStepStage.SERVER_CONNECTIVITY
        val state = fresh
            .withStepStatus(SetupStepStage.WSL_DETECTION, StepStatus.SUCCESS)
            .withStepStatus(failed, StepStatus.FAILED, error = "no service")
            .withRemainingPending(failed, "Waiting for the build service", now = 7L)
        val byStage = state.steps.associateBy { it.stage }
        assertEquals(StepStatus.SUCCESS, byStage.getValue(SetupStepStage.WSL_DETECTION).status)
        assertEquals(StepStatus.FAILED, byStage.getValue(failed).status)
        val after = state.steps.dropWhile { it.stage != failed }.drop(1)
        assertTrue(after.isNotEmpty())
        assertTrue(after.all { it.status == StepStatus.PENDING && it.description == "Waiting for the build service" })
        assertEquals(null, state.currentStage)
        assertEquals(7L, state.checkedAt)
    }

    @Test
    fun aRunningStepIsTheCurrentStageAndKeepsItsFirstStartTime() {
        val stage = SetupStepStage.SYSTEM_PACKAGES
        val running = fresh.withStepRunning(stage, now = 100L).withStepRunning(stage, now = 200L)
        assertEquals(stage, running.currentStage)
        assertEquals(100L, running.steps.first { it.stage == stage }.startedAt)
        // A finished step is no longer the current one; its description stays unless a new one is given.
        val done = running.withStepProgress(stage, "lz4", 2, 5, now = 300L).withStepStatus(stage, StepStatus.SUCCESS)
        assertEquals(null, done.currentStage)
        assertEquals("lz4 (2 of 5)", done.steps.first { it.stage == stage }.description)
    }

    @Test
    fun theLogKeepsItsTailAndTheNewestLineIsActive() {
        val state = (1..5).fold(fresh) { s, i -> s.withLogLine("line $i", tail = 3) }
        assertEquals(listOf("line 3", "line 4", "line 5"), state.logs)
        assertEquals("line 5", state.activeLogLine)
    }

    @Test
    fun aRestoredStageIsEvidenceToCheckAgainNeverASuccess() {
        val minute = 60_000L
        val persisted = ToolchainPersistenceState(
            completedStages = setOf(SetupStepStage.WSL_DETECTION.name),
            lastVerifiedTimestamp = 1_000L,
        )
        val state = restoredSetupState(persisted, now = 1_000L + 5 * minute)
        val wsl = state.steps.first { it.stage == SetupStepStage.WSL_DETECTION }
        assertEquals(StepStatus.PENDING, wsl.status)
        assertEquals(StepProvenance.RESTORED, wsl.provenance)
        assertEquals("Last verified 5 min ago — re-checking…", wsl.description)
        // Nothing journaled: the plain starting state.
        assertEquals(ToolchainSetupState(), restoredSetupState(null))
        assertEquals("just now", formatAgo(1_000L, now = 1_000L + 59_000L))
        assertEquals("2 d ago", formatAgo(1L, now = 1L + 48 * 60 * minute))
        assertEquals("previously", formatAgo(null, now = 0L))
    }
}
