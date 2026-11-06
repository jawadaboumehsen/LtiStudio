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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.ide.lti.core.domain.pipeline.PipelineEvent
import org.ide.lti.core.domain.pipeline.PipelineOrchestrator
import org.ide.lti.core.domain.pipeline.RuntimeKeys
import org.ide.lti.core.domain.pipeline.StageContext
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.ports.hasFinished
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.domain.repository.snapshot.SnapshotRepository
import org.ide.lti.core.domain.repository.target.WorkspaceDeviceRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.run.StepState
import java.util.UUID

/**
 * Reattaches to an active, non-terminal build run for a workspace. Resolves the workspace,
 * configuration snapshot, and target device, then resumes orchestrator execution.
 */
public open class ReattachRunUseCase(
    private val workspaceRepository: WorkspaceRepository,
    private val snapshotRepository: SnapshotRepository,
    private val workspaceDeviceRepository: WorkspaceDeviceRepository,
    private val runRepository: RunRepository,
    private val orchestrator: PipelineOrchestrator,
    private val scope: CoroutineScope? = null,
    private val executionPort: PipelineExecutionPort? = null,
) {
    public open suspend operator fun invoke(
        workspaceId: String,
        runId: String? = null,
        driverInstanceId: String = "driver_${UUID.randomUUID()}",
    ): Flow<PipelineEvent>? {
        val run = (if (runId != null) runRepository.getRun(runId) else runRepository.latestActive(workspaceId))
            ?: return null

        if (run.state.isTerminal || run.workspaceId != workspaceId) {
            return null
        }

        // Resuming a cancelling run only records it as cancelled, so that waits until its step has stopped.
        if (run.state == RunState.CANCELLING && !stepStopped(run)) return null

        val workspace = workspaceRepository.getWorkspace(workspaceId) ?: return null
        val snapshot = snapshotRepository.getSnapshot(run.snapshotId) ?: return null
        val target = runCatching { workspaceDeviceRepository.get(workspaceId) }.getOrNull() ?: return null
        // The run was planned for one device revision: one changed since (sizes, layout) would mix two targets.
        val admitted = run.runtimeValues[RuntimeKeys.DEVICE_REVISION]?.toIntOrNull()
        if (admitted != null && admitted != target.revision) {
            runRepository.update(run.id) {
                if (it.state.isTerminal) it else it.copy(state = RunState.FAILED, endedAt = Clock.System.now())
            }
            return null
        }

        val stageContext = StageContext(workspace, snapshot, target, run.runtimeValues)
        val eventFlow = orchestrator.resume(run, stageContext, driverInstanceId)

        if (scope != null) {
            scope.launch {
                eventFlow.collect {}
            }
        }
        return eventFlow
    }

    /**
     * Whether a cancelling [run]'s step has ended. One still running is asked to stop again. One the service does
     * not know (it answered, so it is not just unreachable) cannot be called cancelled: the run ends as interrupted,
     * as a step lost during a build does, rather than holding the workspace forever.
     */
    private suspend fun stepStopped(run: BuildRun): Boolean {
        val port = executionPort
        val step = run.steps.firstOrNull { it.state == StepState.RUNNING }?.serverRunId
        if (port == null || step == null) return true
        val status = port.status(step)
        return when {
            status == null -> false.also {
                runRepository.update(run.id) {
                    if (it.state.isTerminal) it else it.copy(state = RunState.INTERRUPTED, endedAt = Clock.System.now())
                }
            }
            status.hasFinished -> true
            else -> false.also { port.cancel(step) }
        }
    }
}
