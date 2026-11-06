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

import io.ltirom.tooling.core.RemoteCancellationUnconfirmedException
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.RunCancelResponse
import io.ltirom.tooling.core.remote.RunHandle
import io.ltirom.tooling.core.remote.RunStatus
import io.ltirom.tooling.core.remote.RunStatusValue
import io.ltirom.tooling.core.remote.SequencedStreamEvent
import io.ltirom.tooling.core.remote.StartRunRequest
import io.ltirom.tooling.core.remote.StreamEvent
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import io.ltirom.tooling.core.remote.ToolExecutionResponse
import io.ltirom.tooling.core.remote.ToolListResult
import io.ltirom.tooling.core.remote.WslServerInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RemoteCommandRunnerStreamFailureTest {
    @Test
    fun aStreamThatFailsStopsTheToolRun() = runTest {
        val port = Port(status = { FINISHED })

        assertFailsWith<IOException> { RemoteCommandRunner(port).stream("tool-command").toList() }

        assertTrue(port.cancelled, "the remote command must not outlive the read of its output")
    }

    @Test
    fun aStopThatIsNotConfirmedIsWhatTheCallerHears() = runTest {
        val failure = assertFailsWith<RemoteCancellationUnconfirmedException> {
            RemoteCommandRunner(Port(stops = false)).stream("tool-command").toList()
        }

        assertTrue(failure.suppressed.any { it is IOException }, "the stream's own failure is not lost")
    }

    @Test
    fun anAcceptedStopIsConfirmedOnlyByAFinishedRunWithNothingLeftAlive() = runTest {
        assertFailsWith<IOException> { streamWith { FINISHED } }

        val survivors = FINISHED.copy(unconfirmedSurvivors = listOf(42L))
        assertFailsWith<RemoteCancellationUnconfirmedException> { streamWith { survivors } }

        val running = FINISHED.copy(status = RunStatusValue.RUNNING)
        assertFailsWith<RemoteCancellationUnconfirmedException> { streamWith { running } }
    }

    @Test
    fun aPlainCancelCannotConfirmARunThatHasARecord() = runTest {
        val running = FINISHED.copy(status = RunStatusValue.RUNNING)
        assertFailsWith<RemoteCancellationUnconfirmedException> {
            RemoteCommandRunner(Port(status = { running })).stream("tool-command").toList()
        }
        // No answer about the run at all: the plain cancel's yes still confirms nothing.
        assertFailsWith<RemoteCancellationUnconfirmedException> {
            RemoteCommandRunner(Port()).stream("tool-command").toList()
        }
    }

    @Test
    fun aRunThatHadAlreadyFinishedIsNoFalseAlarmWhenItsStopIsRefused() = runTest {
        val port = Port(stops = false, status = { FINISHED })

        assertFailsWith<IOException> { RemoteCommandRunner(port).stream("tool-command").toList() }
    }

    @Test
    fun aPlainStreamHasOnlyItsCancelToGoBy() = runTest {
        assertFailsWith<IOException> {
            RemoteCommandRunner(Port(durable = false)).stream("tool-command").toList()
        }
        assertFailsWith<RemoteCancellationUnconfirmedException> {
            RemoteCommandRunner(Port(durable = false, stops = false)).stream("tool-command").toList()
        }
    }

    @Test
    fun aStatusThatCannotBeReadConfirmsNothing() = runTest {
        assertFailsWith<RemoteCancellationUnconfirmedException> { streamWith { throw IOException("service gone") } }
        assertFailsWith<RemoteCancellationUnconfirmedException> { streamWith { null } }
    }

    @Test
    fun aStatusReadThatFailsOnceIsReadAgain() = runTest {
        var reads = 0

        assertFailsWith<IOException> {
            streamWith { if (++reads == 1) throw IOException("hiccup") else FINISHED }
        }
    }

    /** A run whose stream fails, whose stop the service accepts, and whose status is [status]. */
    private suspend fun streamWith(status: () -> RunStatus?) {
        RemoteCommandRunner(Port(accepts = true, status = status)).stream("tool-command").toList()
    }

    private companion object {
        val FINISHED = RunStatus("remote", RunStatusValue.CANCELLED, startedAtEpochMs = 1L)
    }

    private class Port(
        private val stops: Boolean = true,
        private val accepts: Boolean = false,
        private val durable: Boolean = true,
        private val status: () -> RunStatus? = { null },
    ) : RemoteTransportPort {
        var cancelled = false

        override suspend fun execute(request: ToolExecutionRequest) = ToolExecutionResponse(0, "", "", 1L)
        override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> =
            flow { throw IOException("stream lost") }

        override suspend fun startRun(request: StartRunRequest): RunHandle = if (durable) {
            RunHandle("remote", RunStatusValue.RUNNING, 1L)
        } else {
            throw UnsupportedOperationException("no durable runs")
        }

        override fun attachRun(runId: String, fromSeq: Long): Flow<SequencedStreamEvent> =
            flow { throw IOException("stream lost") }

        override suspend fun cancelRun(runId: String, signal: String): RunCancelResponse? {
            cancelled = true
            return if (accepts) RunCancelResponse(accepted = true, status = RunStatusValue.CANCELLING) else null
        }

        override suspend fun getRun(runId: String): RunStatus? = status()

        override suspend fun cancel(executionId: String, signal: String): Boolean {
            cancelled = true
            return stops
        }

        override suspend fun checkHealth(): WslServerInfo? = null
        override suspend fun listTools(): ToolListResult = ToolListResult.Tools(emptyList())
        override suspend fun refreshTools(): ToolListResult = ToolListResult.Tools(emptyList())
        override suspend fun uploadFile(remotePath: String, content: ByteArray) = true
        override suspend fun downloadFile(remotePath: String): ByteArray? = null
        override suspend fun shutdown() = true
    }
}
