/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.admission

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ide.lti.core.domain.ports.AdmissionResult
import org.ide.lti.core.domain.ports.LeaseReconciliation
import org.ide.lti.core.domain.ports.MutationKind
import org.ide.lti.core.domain.ports.WorkspaceAdmissionPort
import org.ide.lti.core.domain.ports.WorkspaceWriteLease
import java.nio.file.Files
import java.nio.file.Path

public class WorkspaceAdmissionRepository(storageDir: Path? = null) : WorkspaceAdmissionPort {

    private val mutex = Mutex()
    private val activeLeases = mutableMapOf<String, WorkspaceWriteLease>()
    private val unreconciled = mutableMapOf<String, WorkspaceWriteLease>()
    private val leaseFile: Path? = storageDir?.also(Files::createDirectories)?.resolve("workspace_leases.json")

    init {
        readIfPresent(leaseFile)?.let { content ->
            Json.decodeFromString<List<WorkspaceWriteLease>>(content).associateByTo(unreconciled) { it.workspaceId }
        }
    }

    override suspend fun tryAcquire(
        workspaceId: String,
        kind: MutationKind,
        operationId: String,
        expectedRevision: Int?,
        currentRevision: Int?,
    ): AdmissionResult = mutex.withLock {
        if (expectedRevision != null && currentRevision != null && expectedRevision != currentRevision) {
            return AdmissionResult.RevisionMismatch(expected = expectedRevision, actual = currentRevision)
        }
        (unreconciled[workspaceId] ?: activeLeases[workspaceId])?.let { return AdmissionResult.Busy(owner = it) }

        val lease = WorkspaceWriteLease(
            workspaceId = workspaceId,
            ownerKind = kind,
            operationId = operationId,
            expectedRevision = expectedRevision,
            acquiredAtEpochMs = System.currentTimeMillis(),
            durable = leaseFile != null,
        )
        activeLeases[workspaceId] = lease
        persistLocked()
        AdmissionResult.Admitted(lease)
    }

    override suspend fun release(lease: WorkspaceWriteLease): Unit = mutex.withLock {
        if (activeLeases[lease.workspaceId]?.operationId == lease.operationId) {
            activeLeases.remove(lease.workspaceId)
            persistLocked()
        }
    }

    override suspend fun currentOwner(workspaceId: String): WorkspaceWriteLease? = mutex.withLock {
        activeLeases[workspaceId] ?: unreconciled[workspaceId]
    }

    override suspend fun unreconciledLeases(): List<WorkspaceWriteLease> = mutex.withLock {
        unreconciled.values.toList()
    }

    override suspend fun reconcile(lease: WorkspaceWriteLease, outcome: LeaseReconciliation): Unit = mutex.withLock {
        val removed = unreconciled.remove(lease.workspaceId) != null || activeLeases.remove(lease.workspaceId) != null
        if (removed) persistLocked()
    }

    private fun persistLocked() {
        val target = leaseFile ?: return
        atomicWrite(target, Json.encodeToString(activeLeases.values + unreconciled.values))
    }
}
