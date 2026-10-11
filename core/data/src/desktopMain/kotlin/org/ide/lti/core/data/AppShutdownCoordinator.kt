/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data

import io.ltirom.tooling.client.resilience.DaemonHeartbeatMonitor
import io.ltirom.tooling.core.ports.RemoteTransportPort
import kotlinx.coroutines.withTimeoutOrNull
import org.ide.lti.core.domain.tools.AppShutdownPort
import org.ide.lti.core.domain.tools.ShutdownReport
import org.ide.lti.core.domain.tools.ToolManager
import org.ide.lti.core.domain.tools.ToolStorePort
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

public class AppShutdownCoordinator(
    private val transport: RemoteTransportPort,
    private val heartbeatMonitor: DaemonHeartbeatMonitor? = null,
    private val toolManager: ToolManager? = null,
    private val toolStore: ToolStorePort? = null,
) : AppShutdownPort {
    override suspend fun shutdown(): ShutdownReport = shutdown(10.seconds)

    public suspend fun shutdown(timeout: Duration): ShutdownReport = withTimeoutOrNull(timeout) {
        // The tools' cleanup runs uninterruptibly, so it gets its own share of the deadline, not all of it.
        val toolBudgetMs = timeout.inWholeMilliseconds * TOOL_SHARE_PERCENT / PERCENT
        if (toolManager != null) {
            toolManager.cancelAll(toolBudgetMs)
        } else {
            toolStore?.cancelAll(toolBudgetMs)
        }

        // Quiesce and join all background heartbeat and handshake jobs BEFORE stopping client
        heartbeatMonitor?.shutdown()

        val result = transport.stopClient()

        if (result.stopped && result.unconfirmedExecutionIds.isEmpty()) {
            ShutdownReport.Stopped
        } else {
            heartbeatMonitor?.markDisconnected("Shutdown unconfirmed")
            ShutdownReport.Unconfirmed(result.unconfirmedExecutionIds)
        }
    } ?: run {
        // Out of time: nothing here may wait again (the orderly shutdown can be stuck behind the monitor's lock).
        heartbeatMonitor?.abort()
        heartbeatMonitor?.markDisconnected("Shutdown timed out")
        ShutdownReport.Unconfirmed(emptyList())
    }

    private companion object {
        const val TOOL_SHARE_PERCENT = 60L
        const val PERCENT = 100L
        const val FORCE_SHUTDOWN_MS = 2_000L
    }

    override suspend fun forceShutdown() {
        if (withTimeoutOrNull(FORCE_SHUTDOWN_MS) { heartbeatMonitor?.shutdown() } == null && heartbeatMonitor != null) {
            heartbeatMonitor.abort()
        }
    }
}
