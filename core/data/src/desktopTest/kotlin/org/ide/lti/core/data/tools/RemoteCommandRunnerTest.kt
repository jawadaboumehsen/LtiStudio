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

import io.ltirom.tooling.core.ToolingException
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.RunHandle
import io.ltirom.tooling.core.remote.RunStatusValue
import io.ltirom.tooling.core.remote.SequencedStreamEvent
import io.ltirom.tooling.core.remote.StartRunRequest
import io.ltirom.tooling.core.remote.StreamEvent
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import io.ltirom.tooling.core.remote.ToolExecutionResponse
import io.ltirom.tooling.core.remote.ToolListResult
import io.ltirom.tooling.core.remote.WslServerInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.tools.ToolError
import org.ide.lti.core.domain.tools.ToolErrorException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RemoteCommandRunnerTest {
    private class FinishingTransport(private val finished: StreamEvent.ExecutionFinished) : RemoteTransportPort {
        override suspend fun execute(request: ToolExecutionRequest) = ToolExecutionResponse(0, "", "", 1L)

        var streams = 0

        override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> {
            streams++
            return flowOf(StreamEvent.OutputChunk("Cloning into bare repository..."), finished)
        }

        override suspend fun checkHealth(): WslServerInfo? = null

        override suspend fun listTools(): ToolListResult = ToolListResult.Tools(emptyList())

        override suspend fun refreshTools(): ToolListResult = ToolListResult.Tools(emptyList())

        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean = true

        override suspend fun downloadFile(remotePath: String): ByteArray? = null

        override suspend fun shutdown(): Boolean = true
    }

    @Test
    fun `a failed run's reason reaches the log instead of a bare exit code`() = runTest {
        val reason = "Tool 'sh' timed out after 1200000 ms"
        var exit = 0
        val lines =
            RemoteCommandRunner(FinishingTransport(StreamEvent.ExecutionFinished(-1, 5L, reason)))
                .stream("git clone --mirror x y", onFinished = { exit = it })
                .toList()

        assertEquals(listOf("Cloning into bare repository...", "[run] exit -1: $reason"), lines)
        assertEquals(-1, exit)
    }

    @Test
    fun `a successful run adds no reason line`() = runTest {
        val lines =
            RemoteCommandRunner(FinishingTransport(StreamEvent.ExecutionFinished(0, 5L, "done")))
                .stream("true")
                .toList()

        assertEquals(listOf("Cloning into bare repository..."), lines)
    }

    @Test
    fun `a timed-out run is reported as a timeout and not retried`() = runTest {
        val transport = FinishingTransport(StreamEvent.ExecutionFinished(-1, 5L, "Tool 'sh' timed out after 60000 ms"))
        val result = RemoteCommandRunner(transport, networkBackoffMs = listOf(0L, 0L)).streamCollect(
            "git clone --mirror x y",
            category = CommandCategory.NETWORK,
        )

        val error = (result.exceptionOrNull() as ToolErrorException).toolError
        assertIs<ToolError.Timeout>(error)
        assertEquals(1, transport.streams, "a timeout must not be retried")
    }

    @Test
    fun `a run that ends without a result is a failure, not a success`() = runTest {
        val transport = object : RemoteTransportPort by FinishingTransport(StreamEvent.ExecutionFinished(0, 1L)) {
            override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> =
                flowOf(StreamEvent.OutputChunk("[ 40%] Building C object"))
        }
        var exit = 0
        val lines = RemoteCommandRunner(transport).stream("cmake --build out", onFinished = { exit = it }).toList()

        assertEquals(-1, exit)
        assertTrue(lines.last().startsWith("[run] exit -1: the run ended without a result"), lines.last())
    }

    @Test
    fun `a git retry removes the old checkout before pruning, so the worktree can be added again`() {
        val script = KotlinStoreLayout.buildGitPrepScript("/h/LtiRomTools", "g", "https://x/y.git", "abc", "/h/w/src")

        assertTrue(script.indexOf("rm -rf '/h/w/src'") < script.indexOf("worktree prune"), script)
        assertTrue("GIT_HTTP_LOW_SPEED_TIME" in script, "a stalled clone must fail and retry, not hang for the hour")
    }

    @Test
    fun `a failed start is retried with the same key and never reruns the script as a plain stream`() = runTest {
        val keys = mutableListOf<String?>()
        val transport = object : RemoteTransportPort by FinishingTransport(StreamEvent.ExecutionFinished(0, 1L)) {
            override suspend fun startRun(request: StartRunRequest): RunHandle {
                keys += request.idempotencyKey
                if (keys.size == 1) throw ToolingException("response lost")
                return RunHandle("run-1", RunStatusValue.RUNNING, 1L)
            }

            override fun attachRun(runId: String, fromSeq: Long): Flow<SequencedStreamEvent> =
                flowOf(SequencedStreamEvent(1L, StreamEvent.ExecutionFinished(0, 1L)))

            override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> = error("must not run twice")
        }
        var exit = -1
        RemoteCommandRunner(transport).stream("git fetch", onFinished = { exit = it }).toList()

        assertEquals(0, exit)
        assertEquals(2, keys.size)
        assertEquals(keys[0], keys[1], "the retry must reuse the key so the service dedupes it")
    }

    @Test
    fun `very long output lines are cut for the log`() = runTest {
        val longLine = "x".repeat(RemoteCommandRunner.MAX_LOG_LINE_CHARS + 500)
        val transport = object : RemoteTransportPort by FinishingTransport(StreamEvent.ExecutionFinished(0, 1L)) {
            override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> =
                flowOf(StreamEvent.OutputChunk(longLine), StreamEvent.ExecutionFinished(0, 1L))
        }
        val line = RemoteCommandRunner(transport).stream("cmake --build out").toList().single()

        assertTrue(line.length < longLine.length && line.endsWith("(500 more chars)"), line.takeLast(40))
    }
}
