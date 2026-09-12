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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.model.run.BuildRun

/**
 * Process-scoped [RunRepository] used until the durable, settings-backed implementation lands
 * with build history (feature 001, T086/T090). It exists so the active-run guard in
 * `SaveConfigurationSnapshotUseCase` is enforced from the first run started in this session.
 */
public class InMemoryRunRepository : RunRepository {

    private val runs = MutableStateFlow<Map<String, BuildRun>>(emptyMap())
    private val events = mutableMapOf<String, MutableList<String>>()

    override fun observeRuns(workspaceId: String): Flow<List<BuildRun>> =
        runs.map { byId -> byId.values.filter { it.workspaceId == workspaceId } }

    override suspend fun upsert(run: BuildRun) {
        runs.update { it + (run.id to run) }
    }

    override suspend fun appendEvents(runId: String, events: List<String>) {
        this.events.getOrPut(runId) { mutableListOf() }.addAll(events)
    }

    override suspend fun cachedEvents(runId: String, fromSeq: Long): List<String> {
        val list = events[runId] ?: return emptyList()
        if (fromSeq <= 0L) return list
        // fromSeq is 1-based and inclusive: list index 0 corresponds to seq 1.
        return list.drop((fromSeq - 1).coerceIn(0L, list.size.toLong()).toInt())
    }

    override suspend fun latestActive(workspaceId: String): BuildRun? =
        runs.value.values
            .filter { it.workspaceId == workspaceId && it.state.isActive }
            .maxByOrNull { it.startedAt }

    override suspend fun getRun(runId: String): BuildRun? = runs.value[runId]

    override suspend fun claimDriver(runId: String, instanceId: String, now: Long): Boolean {
        val run = getRun(runId) ?: return false
        val currentDriver = run.driverInstanceId
        val lastHeartbeat = run.driverHeartbeatEpochMs
        val leaseExpired = lastHeartbeat == null || (now - lastHeartbeat >= 45_000L)
        if (currentDriver == null || currentDriver == instanceId || leaseExpired) {
            upsert(run.copy(driverInstanceId = instanceId, driverHeartbeatEpochMs = now))
            return true
        }
        return false
    }

    override suspend fun heartbeatDriver(runId: String, instanceId: String, now: Long) {
        val run = getRun(runId) ?: return
        if (run.driverInstanceId == instanceId) {
            upsert(run.copy(driverHeartbeatEpochMs = now))
        }
    }
}
