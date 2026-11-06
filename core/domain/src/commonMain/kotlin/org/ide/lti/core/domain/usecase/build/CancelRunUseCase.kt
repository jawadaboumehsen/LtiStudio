/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.usecase.build

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.ports.hasFinished
import org.ide.lti.core.domain.ports.linkInWorkspace
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.run.StageState
import org.ide.lti.core.model.run.StepCommand
import org.ide.lti.core.model.run.StepState
import org.ide.lti.core.model.run.VerificationState

/**
 * Cancels an active build run. Transitions state RUNNING -> CANCELLING -> CANCELLED, cancels
 * in-flight step processes via executionPort, cleans stage temporary outputs, and guarantees
 * partial artifacts are never marked VERIFIED and no cache keys are written.
 */
public class CancelRunUseCase(
    private val runRepository: RunRepository,
    private val executionPort: PipelineExecutionPort,
    private val workspaceRepository: WorkspaceRepository? = null,
    private val clock: Clock = Clock.System,
    /** Whether this app drives the run now: its driver then stops the step (a host step too) and finishes it. */
    private val isDriving: (String) -> Boolean = { false },
    private val driverStopTimeoutMs: Long = DRIVER_STOP_TIMEOUT_MS,
) {
    public suspend operator fun invoke(workspaceId: String, runId: String? = null): Result<BuildRun> = runCatching {
        val run = (if (runId != null) runRepository.getRun(runId) else runRepository.latestActive(workspaceId))
            ?: error("No active run found for workspace $workspaceId")
        require(run.workspaceId == workspaceId) { "Run ${run.id} belongs to another workspace." }

        if (run.state.isTerminal) {
            return@runCatching run
        }

        // 1. Transition to CANCELLING (in one step: a driver writing meanwhile keeps it)
        val cancelling = runRepository.update(run.id) {
            if (it.state.isTerminal) it else it.copy(state = RunState.CANCELLING)
        } ?: run.copy(state = RunState.CANCELLING).also { runRepository.upsert(it) }
        if (cancelling.state.isTerminal) return@runCatching cancelling

        // 2. Cancel in-flight step
        val stopped = stopRunningStep(cancelling)

        // 3. A driver in this app stops its work and records the cancel itself; the run stays active (no new build
        // or edit is admitted) until it has. One that does not stop in time leaves the run cancelling.
        if (isDriving(run.id)) return@runCatching awaitDriver(workspaceId, cancelling)

        // 4. No driver here. A refused stop of a step not known to have ended leaves the run cancelling: nothing new
        // is admitted over a step that may be writing, and ReattachRunUseCase follows it from there.
        if (!stopped) return@runCatching cancelling

        // 5. Clean the stage temporary outputs and finish the run
        cleanStaging(workspaceId)
        val now = clock.now()
        runRepository.update(run.id) { current -> if (current.state.isTerminal) current else cancelled(current, now) }
            ?: cancelled(cancelling, now).also { runRepository.upsert(it) }
    }

    /**
     * The run once this app's driver has stopped and recorded it; still cancelling if not. Staging is not cleaned
     * here: once the run is terminal a new build may already be using it, and Build & Sign empties it on start.
     */
    private suspend fun awaitDriver(workspaceId: String, cancelling: BuildRun): BuildRun {
        val stopped = withTimeoutOrNull(driverStopTimeoutMs) {
            runRepository.observeRuns(workspaceId)
                .map { runs -> runs.firstOrNull { it.id == cancelling.id } }
                .first { it == null || it.state.isTerminal }
        }
        return stopped ?: runRepository.getRun(cancelling.id) ?: cancelling
    }

    /** False when the service refused to stop a step it does not report as finished. */
    private suspend fun stopRunningStep(run: BuildRun): Boolean {
        val step = run.steps.firstOrNull { it.state == StepState.RUNNING }?.serverRunId ?: return true
        return executionPort.cancel(step) || executionPort.status(step)?.hasFinished == true
    }

    private suspend fun cleanStaging(workspaceId: String) {
        val ws = workspaceRepository?.getWorkspace(workspaceId)
        // A link, or a scan that failed: the cancel still finishes, this cleanup just waits for the next build's.
        if (ws?.linuxPath != null && runCatching { executionPort.linkInWorkspace(ws) == null }.getOrDefault(false)) {
            executionPort.execute(
                ws,
                StepCommand("rm", listOf("-rf", "${ws.linuxPath}/out/package/.stage"), ws.linuxPath),
            )
        }
    }

    /** [run] cancelled at [now]: running steps and stages cancelled, pending ones not run, no artifact verified. */
    private fun cancelled(run: BuildRun, now: Instant): BuildRun = run.copy(
        state = RunState.CANCELLED,
        endedAt = now,
        steps = run.steps.map { step ->
            if (step.state == StepState.RUNNING) step.copy(state = StepState.CANCELLED) else step
        },
        stages = run.stages.map { stage ->
            when (stage.state) {
                StageState.RUNNING ->
                    stage.copy(state = StageState.CANCELLED, endedAt = now, message = "Run cancelled by user")

                StageState.PENDING -> stage.copy(state = StageState.NOT_RUN)

                else -> stage
            }
        },
        artifacts = run.artifacts.map { it.copy(verification = VerificationState.NOT_VERIFIED) },
    )

    private companion object {
        const val DRIVER_STOP_TIMEOUT_MS = 60_000L
    }
}
