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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.pipeline.PipelineEvent
import org.ide.lti.core.domain.pipeline.PipelineOrchestrator
import org.ide.lti.core.domain.pipeline.StageContext
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.domain.repository.snapshot.SnapshotRepository
import org.ide.lti.core.domain.repository.target.TargetRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import java.util.UUID

/**
 * Reattaches to an active, non-terminal build run for a workspace. Resolves the workspace,
 * configuration snapshot, and target device, then resumes orchestrator execution.
 */
public open class ReattachRunUseCase(
    private val workspaceRepository: WorkspaceRepository,
    private val snapshotRepository: SnapshotRepository,
    private val targetRepository: TargetRepository,
    private val runRepository: RunRepository,
    private val orchestrator: PipelineOrchestrator,
    private val scope: CoroutineScope? = null,
) {
    public open suspend operator fun invoke(
        workspaceId: String,
        runId: String? = null,
        driverInstanceId: String = "driver_${UUID.randomUUID()}",
    ): Flow<PipelineEvent>? {
        val run = (if (runId != null) runRepository.getRun(runId) else runRepository.latestActive(workspaceId))
            ?: return null

        if (run.state.isTerminal) {
            return null
        }

        val workspace = workspaceRepository.getWorkspace(workspaceId) ?: return null
        val snapshot = snapshotRepository.getSnapshot(run.snapshotId) ?: return null
        val targetId = workspace.targetBinding?.profileId ?: return null
        val target = targetRepository.getAvailableTargets().first().find { it.id == targetId } ?: return null

        val stageContext = StageContext(workspace, snapshot, target, run.runtimeValues)
        val eventFlow = orchestrator.resume(run, stageContext, driverInstanceId)

        if (scope != null) {
            scope.launch {
                eventFlow.collect {}
            }
        }
        return eventFlow
    }
}
