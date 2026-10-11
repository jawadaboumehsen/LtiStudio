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
import kotlinx.coroutines.CancellationException
import org.ide.lti.core.domain.setup.ToolGroupId
import java.util.concurrent.ConcurrentHashMap

internal sealed interface Recheck {
    /** Nothing was blocking the group. */
    data object Clear : Recheck

    /** The service confirmed the process is gone; the block is removed. */
    data object Lifted : Recheck

    data class Blocked(val executionId: String) : Recheck
}

/**
 * Groups whose remote process could not be confirmed stopped. A block is not permanent: every new attempt asks the
 * service again, and the block lifts as soon as the service no longer tracks the process (it ended, or the
 * service restarted and no longer knows it). Until then the group stays blocked, because building on top of a
 * process that might still write into the same folders is worse than waiting.
 */
internal class UnconfirmedCancellations(private val transport: RemoteTransportPort) {
    private val pending = ConcurrentHashMap<ToolGroupId, String>()

    fun record(group: ToolGroupId, executionId: String) {
        pending[group] = executionId
    }

    fun isBlocking(group: ToolGroupId): Boolean = pending.containsKey(group)

    suspend fun recheck(group: ToolGroupId): Recheck {
        val executionId = pending[group] ?: return Recheck.Clear
        return if (isGone(executionId)) {
            pending.remove(group, executionId)
            Recheck.Lifted
        } else {
            Recheck.Blocked(executionId)
        }
    }

    private suspend fun isGone(executionId: String): Boolean = try {
        // Unknown (no answer, or a service that does not report its work) is never "gone".
        val tracked = transport.checkHealth()?.activeWork
        val stillTracked = tracked?.any { it == executionId || it.substringAfter(':') == executionId }
        if (stillTracked == true) {
            // Ask again to stop it; the next attempt sees whether that worked.
            runCatching { transport.cancelRun(executionId) ?: transport.cancel(executionId) }
        }
        stillTracked == false
    } catch (ce: CancellationException) {
        throw ce
    } catch (_: Exception) {
        false
    }
}
