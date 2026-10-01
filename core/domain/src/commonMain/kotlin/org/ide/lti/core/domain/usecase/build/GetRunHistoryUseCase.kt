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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.Presence

/**
 * Retrieves the history of build runs for a workspace ordered newest-first.
 * When an execution transport is available and online, probes artifact presence via [PipelineExecutionPort.stat].
 * When offline or stat fails, falls back gracefully to cached presence values without crashing or dropping records.
 */
public open class GetRunHistoryUseCase(
    private val runRepository: RunRepository,
    private val workspaceRepository: WorkspaceRepository? = null,
    private val executionPort: PipelineExecutionPort? = null,
) {
    public open fun observe(workspaceId: String): Flow<List<BuildRun>> {
        return runRepository.observeRuns(workspaceId).map { runs ->
            runs.sortedByDescending { it.startedAt }
        }
    }

    public open suspend operator fun invoke(
        workspaceId: String,
        refreshArtifactPresence: Boolean = true,
    ): List<BuildRun> {
        val runs = runRepository.observeRuns(workspaceId).first().sortedByDescending { it.startedAt }
        if (!refreshArtifactPresence || workspaceRepository == null || executionPort == null) {
            return runs
        }

        val workspace = try {
            workspaceRepository.getWorkspace(workspaceId)
        } catch (_: Exception) {
            null
        } ?: return runs

        val updatedRuns = runs.map { run ->
            if (run.artifacts.isEmpty()) return@map run
            var runChanged = false
            val updatedArtifacts = run.artifacts.map { artifact ->
                try {
                    val stat = executionPort.stat(workspace, artifact.linuxPath)
                    val newPresence = if (stat.exists) Presence.PRESENT else Presence.MISSING
                    val newSize = if (stat.exists && stat.sizeBytes > 0L) stat.sizeBytes else artifact.sizeBytes
                    if (artifact.presence != newPresence || artifact.sizeBytes != newSize) {
                        runChanged = true
                        artifact.copy(presence = newPresence, sizeBytes = newSize)
                    } else {
                        artifact
                    }
                } catch (_: Exception) {
                    // Offline or transport unavailable: retain cached presence
                    artifact
                }
            }

            if (runChanged) {
                val updatedRun = run.copy(artifacts = updatedArtifacts)
                try {
                    runRepository.upsert(updatedRun)
                } catch (_: Exception) {
                    // Ignore persistence failure during query
                }
                updatedRun
            } else {
                run
            }
        }

        return updatedRuns
    }
}
