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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /** A run's whole event log, or nothing: a partial list is never cached. Guarded by [eventsLock]. */
    private val events = mutableMapOf<String, MutableList<String>>()
    private val lastLogMirror = mutableMapOf<String, Long>()

    /** Where each run's current stage began in its event log, for that stage's own log file. */
    private val stageStart = mutableMapOf<String, Pair<String, Int>>()
    private val eventsLock = Mutex()

    /** Run records are written one at a time: a heartbeat or claim never interleaves with a state change. */
    private val writes = Mutex()

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
        writes.withLock { persist(run) }
        mirrorRunToRemoteWorkspace(run)
        // Step and stage boundaries always bring the mirrored log up to date.
        if (eventsLock.withLock { events[run.id] != null }) mirrorStageLogsToRemoteWorkspace(run.id)
    }

    /** Disk first: memory (what the UI and admission see) only ever shows a record that was saved. */
    private suspend fun persist(run: BuildRun) {
        runRecordDataSource.saveRun(run)
        runs.update { it + (run.id to run) }
    }

    override suspend fun appendEvents(runId: String, events: List<String>): Unit = withContext(ioDispatcher) {
        if (events.isEmpty()) return@withContext
        runRecordDataSource.appendEvents(runId, events)
        // Only a cached whole log is extended; otherwise the next read loads it all from disk.
        val mirrorNow = eventsLock.withLock {
            this@RunRepositoryImpl.events[runId]?.addAll(events)
            // The mirror uploads the whole stage log, so it runs at most every few seconds, not per output batch.
            val now = System.currentTimeMillis()
            (now - (lastLogMirror[runId] ?: 0L) >= LOG_MIRROR_INTERVAL_MS).also { if (it) lastLogMirror[runId] = now }
        }
        if (mirrorNow) mirrorStageLogsToRemoteWorkspace(runId)
    }

    /**
     * Events from [fromSeq], 1-based and inclusive as on disk (0 or 1: all). The whole log is cached on first read,
     * so memory and disk give the same answer.
     */
    override suspend fun cachedEvents(runId: String, fromSeq: Long): List<String> = withContext(ioDispatcher) {
        val all = eventsLock.withLock {
            events[runId]?.toList() ?: runRecordDataSource.getEvents(runId, 0L)
                .also { if (it.isNotEmpty()) events[runId] = it.toMutableList() }
        }
        all.drop((fromSeq - 1).coerceIn(0L, all.size.toLong()).toInt())
    }

    override suspend fun latestActive(workspaceId: String): BuildRun? = withContext(ioDispatcher) {
        runRecordDataSource.currentSummaries
            .filter { it.workspaceId == workspaceId && it.id !in runs.value }
            .forEach { summary ->
                runCatching { runRecordDataSource.getRun(summary.id) }.getOrNull()
                    ?.let { loaded -> runs.update { it + (loaded.id to loaded) } }
            }
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
        // A queued or running build keeps its record: its driver and the active-run check need it.
        // A run not loaded yet is read first, so a cold cache cannot hide that it is active.
        check(getRun(runId)?.state?.isActive != true) { "Run $runId is still active; cancel it first." }
        runs.update { it - runId }
        eventsLock.withLock {
            events.remove(runId)
            lastLogMirror.remove(runId)
            stageStart.remove(runId)
        }
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

    /** Compare-and-set under [writes]: two claimants can never both win. */
    override suspend fun claimDriver(runId: String, instanceId: String, now: Long): Boolean =
        withContext(ioDispatcher) {
            val claimed = writes.withLock {
                val run = getRun(runId) ?: return@withLock null
                val lastHeartbeat = run.driverHeartbeatEpochMs
                val leaseExpired = lastHeartbeat == null || (now - lastHeartbeat >= LEASE_MS)
                val free = run.driverInstanceId == null || run.driverInstanceId == instanceId || leaseExpired
                run.copy(driverInstanceId = instanceId, driverHeartbeatEpochMs = now).takeIf { free }
                    ?.also { persist(it) }
            }
            claimed?.let { mirrorRunToRemoteWorkspace(it) }
            claimed != null
        }

    /** Only the heartbeat time of the latest record changes: a newer state is never replaced by an older copy. */
    override suspend fun heartbeatDriver(runId: String, instanceId: String, now: Long): Unit =
        withContext(ioDispatcher) {
            writes.withLock {
                val run = getRun(runId)?.takeIf { it.driverInstanceId == instanceId } ?: return@withLock
                persist(run.copy(driverHeartbeatEpochMs = now))
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
            // A stage's file holds the events since it began, not the earlier stages' output.
            val from = eventsLock.withLock {
                stageStart[runId]?.takeIf { it.first == activeStage.name }?.second
                    ?: allLogs.size.also { stageStart[runId] = activeStage.name to it }
            }
            val logContent = allLogs.drop(from).joinToString("\n")
            port.uploadFile(workspace, "stages/${activeStage.name.lowercase()}.log", logContent.encodeToByteArray())
        } catch (_: Exception) {
            // Non-critical mirror operation
        }
    }

    private companion object {
        const val LOG_MIRROR_INTERVAL_MS = 5_000L
        const val LEASE_MS = 45_000L
    }
}
