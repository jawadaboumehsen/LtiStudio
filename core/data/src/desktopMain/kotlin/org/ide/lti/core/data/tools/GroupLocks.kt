/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.tools

import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.ToolListResult
import kotlinx.coroutines.sync.Mutex
import org.ide.lti.core.domain.setup.ToolGroup
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.tools.GroupLinks
import org.ide.lti.core.domain.tools.ToolError
import org.ide.lti.core.domain.tools.ToolErrorException
import java.util.concurrent.ConcurrentHashMap

/** One lock per tool group: `locks(group).withLock { ... }`. */
internal class GroupLocks {
    private val locks = ConcurrentHashMap<ToolGroupId, Mutex>()

    operator fun invoke(group: ToolGroupId): Mutex = locks.computeIfAbsent(group) { Mutex() }
}

/** The result of a switch to [versionId]: success only once the service offers the group's required tools. */
internal suspend fun RemoteTransportPort.registrationOutcome(group: ToolGroup?, versionId: String): Result<GroupLinks> {
    val problem = unregisteredTools(group)
    return if (problem == null) {
        Result.success(GroupLinks(current = versionId, selection = null))
    } else {
        Result.failure(ToolErrorException(ToolError.RegistrationFailed(problem)))
    }
}

/**
 * Asks the service to reload its tools and says why a required tool of [group] is still not offered; null when
 * every one is. A switch that only moved a link is not a finished switch.
 */
private suspend fun RemoteTransportPort.unregisteredTools(group: ToolGroup?): String? =
    when (val listing = refreshTools()) {
        is ToolListResult.ServiceError -> "the service could not reload its tools (${listing.reason})"
        is ToolListResult.Unreachable -> "the service did not answer (${listing.reason})"
        is ToolListResult.Tools -> {
            val offered = listing.list.filter { it.installed }.map { it.tool }.toSet()
            val missing = group?.outputs.orEmpty().filter { it.requiredForProduct && it.toolId !in offered }
            if (missing.isEmpty()) null else "missing ${missing.joinToString { it.toolId }}"
        }
    }
