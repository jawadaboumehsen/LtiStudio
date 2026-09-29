/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.repository.run

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ide.lti.core.datastore.RunRecordDataSource
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.model.run.Artifact
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.Presence
import org.ide.lti.core.model.run.StageState

/**
 * Durable, settings- and file-backed implementation of [RunRepository].
 *
 * Syncs an in-memory [StateFlow] cache with [RunRecordDataSource] (index in Settings,
 * full JSON + event logs on local filesystem), and mirrors runs and stage logs into
 * the remote workspace when [PipelineExecutionPort] is available and connected.
 */
public class RunRepositoryImpl(
    private val runRecordDataSource: RunRecordDataSource,
    private val workspaceRepository: WorkspaceRepository? = null,
    private val executionPort: PipelineExecutionPort? = null,
    private val json: Json = RunRecordDataSource.defaultJson(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
) : RunRepository {

    private val runs = MutableStateFlow<Map<String, BuildRun>>(emptyMap())
    private val events = mutableMapOf<String, MutableList<String>>()

    init {
        scope.launch(ioDispatcher) {
            for (summary in runRecordDataSource.currentSummaries) {
                try {
                    val loaded = runRecordDataSource.getRun(summary.id)
                    if (loaded != null) {
                        runs.update { it + (loaded.id to loaded) }
                    }
                } catch (_: Exception) {
                    // Ignore decoding failures during eager warmup
                }
            }
        }
    }

    override fun observeRuns(workspaceId: String): Flow<List<BuildRun>> = flow {
        withContext(ioDispatcher) {
            val summaries = runRecordDataSource.currentSummaries.filter { it.workspaceId == workspaceId }
            val missing = summaries.filter { it.id !in runs.value }
            for (summary in missing) {
                try {
                    val loaded = runRecordDataSource.getRun(summary.id)
                    if (loaded != null) {
                        runs.update { it + (loaded.id to loaded) }
                    }
                } catch (_: Exception) {
                    // Ignore
                }
            }
        }
        emitAll(
            runs.map { byId ->
                byId.values.filter { it.workspaceId == workspaceId }
            },
        )
    }

    override suspend fun upsert(run: BuildRun): Unit = withContext(ioDispatcher) {
        runs.update { it + (run.id to run) }
        runRecordDataSource.saveRun(run)
        mirrorRunToRemoteWorkspace(run)
    }

    override suspend fun appendEvents(runId: String, events: List<String>): Unit = withContext(ioDispatcher) {
        if (events.isEmpty()) return@withContext
        this@RunRepositoryImpl.events.getOrPut(runId) { mutableListOf() }.addAll(events)
        runRecordDataSource.appendEvents(runId, events)
        mirrorStageLogsToRemoteWorkspace(runId)
    }

    override suspend fun cachedEvents(runId: String, fromSeq: Long): List<String> = withContext(ioDispatcher) {
        val inMemory = events[runId]
        if (!inMemory.isNullOrEmpty()) {
            return@withContext inMemory.drop(fromSeq.toInt().coerceIn(0, inMemory.size))
        }
        val fromDisk = runRecordDataSource.getEvents(runId, fromSeq)
        if (fromDisk.isNotEmpty()) {
            events.getOrPut(runId) { mutableListOf() }.addAll(fromDisk)
        }
        fromDisk
    }

    override suspend fun latestActive(workspaceId: String): BuildRun? = withContext(ioDispatcher) {
        runs.value.values
            .filter { it.workspaceId == workspaceId && it.state.isActive }
            .maxByOrNull { it.startedAt }
    }

    override suspend fun getRun(runId: String): BuildRun? = withContext(ioDispatcher) {
        runs.value[runId]?.let { return@withContext it }
        val loaded = runRecordDataSource.getRun(runId) ?: return@withContext null
        runs.update { it + (loaded.id to loaded) }
        loaded
    }

    override suspend fun deleteRun(runId: String): Unit = withContext(ioDispatcher) {
        runs.update { it - runId }
        events.remove(runId)
        runRecordDataSource.deleteRun(runId)
    }

    override suspend fun refreshPresence(workspaceId: String, checker: suspend (Artifact) -> Presence): Unit =
        withContext(ioDispatcher) {
            val currentRuns = observeRuns(workspaceId).first()
            for (run in currentRuns) {
                if (run.artifacts.isNotEmpty()) {
                    var changed = false
                    val updatedArtifacts = run.artifacts.map { artifact ->
                        val newPresence = checker(artifact)
                        if (newPresence != artifact.presence) {
                            changed = true
                            artifact.copy(presence = newPresence)
                        } else {
                            artifact
                        }
                    }
                    if (changed) {
                        upsert(run.copy(artifacts = updatedArtifacts))
                    }
                }
            }
        }

    override suspend fun pruneArtifacts(runId: String): Unit = withContext(ioDispatcher) {
        val run = getRun(runId) ?: return@withContext
        val pruned = run.artifacts.filter { it.presence == Presence.PRESENT }
        if (pruned.size != run.artifacts.size) {
            upsert(run.copy(artifacts = pruned))
        }
    }

    override suspend fun claimDriver(runId: String, instanceId: String, now: Long): Boolean =
        withContext(ioDispatcher) {
            val run = getRun(runId) ?: return@withContext false
            val currentDriver = run.driverInstanceId
            val lastHeartbeat = run.driverHeartbeatEpochMs
            val leaseExpired = lastHeartbeat == null || (now - lastHeartbeat >= 45_000L)
            if (currentDriver == null || currentDriver == instanceId || leaseExpired) {
                upsert(run.copy(driverInstanceId = instanceId, driverHeartbeatEpochMs = now))
                return@withContext true
            }
            false
        }

    override suspend fun heartbeatDriver(runId: String, instanceId: String, now: Long): Unit =
        withContext(ioDispatcher) {
            val run = getRun(runId) ?: return@withContext
            if (run.driverInstanceId == instanceId) {
                upsert(run.copy(driverHeartbeatEpochMs = now))
            }
        }

    private suspend fun mirrorRunToRemoteWorkspace(run: BuildRun) {
        val port = executionPort ?: return
        val wsRepo = workspaceRepository ?: return
        try {
            val workspace = wsRepo.getWorkspace(run.workspaceId) ?: return
            val runJson = json.encodeToString(run)
            port.uploadFile(workspace, "runs/${run.id}/run.json", runJson.encodeToByteArray())
        } catch (_: Exception) {
            // Non-critical mirror operation
        }
    }

    private suspend fun mirrorStageLogsToRemoteWorkspace(runId: String) {
        val port = executionPort ?: return
        val wsRepo = workspaceRepository ?: return
        try {
            val run = getRun(runId) ?: return
            val workspace = wsRepo.getWorkspace(run.workspaceId) ?: return
            val activeStage = run.stages.firstOrNull { it.state == StageState.RUNNING }?.stageId
                ?: run.stages.lastOrNull()?.stageId ?: return
            val allLogs = cachedEvents(runId)
            val logContent = allLogs.joinToString("\n")
            port.uploadFile(workspace, "stages/${activeStage.name.lowercase()}.log", logContent.encodeToByteArray())
        } catch (_: Exception) {
            // Non-critical mirror operation
        }
    }
}
