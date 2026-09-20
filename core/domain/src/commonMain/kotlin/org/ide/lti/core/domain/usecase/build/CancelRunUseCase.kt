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

import kotlinx.datetime.Clock
import org.ide.lti.core.domain.ports.PipelineExecutionPort
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
) {
    public suspend operator fun invoke(
        workspaceId: String,
        runId: String? = null,
    ): Result<BuildRun> = runCatching {
        val run = (if (runId != null) runRepository.getRun(runId) else runRepository.latestActive(workspaceId))
            ?: error("No active run found for workspace $workspaceId")

        if (run.state.isTerminal) {
            return@runCatching run
        }

        val now = clock.now()
        // 1. Transition to CANCELLING
        var current = run.copy(state = RunState.CANCELLING)
        runRepository.upsert(current)

        // 2. Cancel in-flight step
        val runningStep = run.steps.firstOrNull { it.state == StepState.RUNNING }
        val serverRunId = runningStep?.serverRunId
        if (serverRunId != null) {
            executionPort.cancel(serverRunId)
        }

        // 3. Clean stage temporary outputs (e.g. out/package/.stage)
        val ws = workspaceRepository?.getWorkspace(workspaceId)
        if (ws?.linuxPath != null) {
            executionPort.execute(
                ws,
                StepCommand("rm", listOf("-rf", "${ws.linuxPath}/out/package/.stage"), ws.linuxPath),
            )
        }

        // 4. Update steps and stages
        val updatedSteps = current.steps.map { step ->
            if (step.state == StepState.RUNNING) {
                step.copy(state = StepState.CANCELLED)
            } else step
        }
        val updatedStages = current.stages.map { stage ->
            if (stage.state == StageState.RUNNING || stage.state == StageState.PENDING) {
                if (stage.state == StageState.RUNNING) {
                    stage.copy(state = StageState.CANCELLED, endedAt = now, message = "Run cancelled by user")
                } else {
                    stage.copy(state = StageState.NOT_RUN)
                }
            } else stage
        }

        // 5. Ensure artifacts are never verified
        val updatedArtifacts = current.artifacts.map {
            it.copy(verification = VerificationState.NOT_VERIFIED)
        }

        // 6. Transition to CANCELLED
        val cancelledRun = current.copy(
            state = RunState.CANCELLED,
            endedAt = now,
            steps = updatedSteps,
            stages = updatedStages,
            artifacts = updatedArtifacts,
        )
        runRepository.upsert(cancelledRun)
        cancelledRun
    }
}
