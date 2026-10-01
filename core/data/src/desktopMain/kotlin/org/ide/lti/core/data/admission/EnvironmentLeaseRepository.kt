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
import org.ide.lti.core.domain.ports.EnvironmentAdmission
import org.ide.lti.core.domain.ports.EnvironmentLease
import org.ide.lti.core.domain.ports.EnvironmentLeasePort
import org.ide.lti.core.domain.ports.EnvironmentUse
import java.nio.file.Files
import java.nio.file.Path

public class EnvironmentLeaseRepository(storageDir: Path? = null) : EnvironmentLeasePort {

    private val mutex = Mutex()
    private val leasesByEnv = mutableMapOf<String, MutableList<EnvironmentLease>>()
    private val leaseFile: Path? = storageDir?.also(Files::createDirectories)?.resolve("environment_leases.json")

    init {
        readIfPresent(leaseFile)?.let { content ->
            Json.decodeFromString<List<EnvironmentLease>>(content).forEach { lease ->
                leasesByEnv.getOrPut(lease.environmentKey) { mutableListOf() }.add(lease)
            }
        }
    }

    override suspend fun tryAcquire(
        environmentKey: String,
        use: EnvironmentUse,
        operationId: String,
    ): EnvironmentAdmission = mutex.withLock {
        val current = leasesByEnv[environmentKey].orEmpty()
        // MAINTENANCE is exclusive against everything; BUILD_USE is shared but excluded by MAINTENANCE.
        val blockers = when (use) {
            EnvironmentUse.MAINTENANCE -> current
            EnvironmentUse.BUILD_USE -> current.filter { it.use == EnvironmentUse.MAINTENANCE }
        }
        if (blockers.isNotEmpty()) return EnvironmentAdmission.Busy(owners = blockers.toList())

        val lease = EnvironmentLease(
            environmentKey = environmentKey,
            use = use,
            operationId = operationId,
            acquiredAtEpochMs = System.currentTimeMillis(),
        )
        leasesByEnv.getOrPut(environmentKey) { mutableListOf() }.add(lease)
        persistLocked()
        EnvironmentAdmission.Admitted(lease)
    }

    override suspend fun release(lease: EnvironmentLease): Unit = mutex.withLock {
        val list = leasesByEnv[lease.environmentKey] ?: return
        list.removeAll { it.operationId == lease.operationId && it.use == lease.use }
        if (list.isEmpty()) leasesByEnv.remove(lease.environmentKey)
        persistLocked()
    }

    override suspend fun activeLeases(environmentKey: String): List<EnvironmentLease> = mutex.withLock {
        leasesByEnv[environmentKey]?.toList().orEmpty()
    }

    private fun persistLocked() {
        val target = leaseFile ?: return
        atomicWrite(target, Json.encodeToString(leasesByEnv.values.flatten()))
    }
}
