/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.repository.run

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.ide.lti.core.model.run.BuildRun

/**
 * In-memory test double implementation of [RunRepository].
 */
class InMemoryRunRepository : RunRepository {

    private val _runs = MutableStateFlow<Map<String, BuildRun>>(emptyMap())
    private val _events = mutableMapOf<String, MutableList<String>>()

    override fun observeRuns(workspaceId: String): Flow<List<BuildRun>> {
        return _runs.map { map ->
            map.values.filter { it.workspaceId == workspaceId }
        }
    }

    override suspend fun upsert(run: BuildRun) {
        val current = _runs.value.toMutableMap()
        current[run.id] = run
        _runs.value = current
    }

    override suspend fun appendEvents(runId: String, events: List<String>) {
        val list = _events.getOrPut(runId) { mutableListOf() }
        list.addAll(events)
    }

    override suspend fun cachedEvents(runId: String, fromSeq: Long): List<String> {
        val list = _events[runId] ?: return emptyList()
        val index = fromSeq.toInt().coerceIn(0, list.size)
        return list.drop(index)
    }

    override suspend fun latestActive(workspaceId: String): BuildRun? {
        return _runs.value.values
            .filter { it.workspaceId == workspaceId && it.state.isActive }
            .maxByOrNull { it.startedAt }
    }

    override suspend fun getRun(runId: String): BuildRun? {
        return _runs.value[runId]
    }

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

    fun clear() {
        _runs.value = emptyMap()
        _events.clear()
    }
}
