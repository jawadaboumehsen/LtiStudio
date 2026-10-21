/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.repository.target

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.ide.lti.core.model.target.TargetDevice

/** In-memory test double of [WorkspaceDeviceRepository]; revision rule matches the real one. */
class InMemoryWorkspaceDeviceRepository(initial: Map<String, TargetDevice> = emptyMap()) : WorkspaceDeviceRepository {
    private val devices = MutableStateFlow(initial)

    override fun observe(workspaceId: String): Flow<TargetDevice?> = devices.map { it[workspaceId] }

    override suspend fun get(workspaceId: String): TargetDevice? = devices.value[workspaceId]

    override suspend fun save(workspaceId: String, device: TargetDevice): TargetDevice {
        val stored = devices.value[workspaceId]
        val next = when {
            stored == null -> device
            stored.copy(revision = device.revision) == device -> device.copy(revision = stored.revision)
            else -> device.copy(revision = stored.revision + 1)
        }
        devices.value = devices.value + (workspaceId to next)
        return next
    }
}
