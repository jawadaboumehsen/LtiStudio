/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.remote

import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.ports.StepEvent
import org.ide.lti.core.model.run.StepCommand
import org.ide.lti.core.model.workspace.Workspace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.ltirom.tooling.core.remote.StreamEvent as CoreStreamEvent

class StepExecutionAdapterTest {

    private class FakeRemoteTransport : RemoteTransportPort {
        var runStatusToReturn: RunStatus? = null
        val sessionEvents = mutableListOf<SequencedStreamEvent>()
        var getRunCallCount = 0
        val statusTransitions = mutableListOf<RunStatus>()
        val uploadedFiles = mutableMapOf<String, ByteArray>()

        override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse {
            if (request.toolId == "df") {
                return ToolExecutionResponse(
                    exitCode = 0,
                    stdout = "Avail\n60000000000\n",
                    stderr = "",
                    durationMs = 10L,
                )
            }
            if (request.toolId == "stat") {
                return ToolExecutionResponse(
                    exitCode = 0,
                    stdout = "123456\n",
                    stderr = "",
                    durationMs = 10L,
                )
            }
            return ToolExecutionResponse(
                exitCode = 0,
                stdout = "",
                stderr = "",
                durationMs = 10L,
            )
        }

        override fun stream(request: ToolExecutionRequest): Flow<CoreStreamEvent> = emptyFlow()
        override suspend fun checkHealth(): WslServerInfo? = null
        override suspend fun listTools(): ToolListResult = ToolListResult.Tools(emptyList())
        override suspend fun refreshTools(): ToolListResult = ToolListResult.Tools(emptyList())

        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean {
            uploadedFiles[remotePath] = content
            return true
        }

        override suspend fun downloadFile(remotePath: String): ByteArray? = uploadedFiles[remotePath]

        override suspend fun shutdown(): Boolean = true
        override suspend fun cancel(executionId: String, signal: String): Boolean = true

        override suspend fun replaySession(sessionId: String, fromSeq: Long): List<SequencedStreamEvent> {
            return sessionEvents.filter { it.seq >= fromSeq }
        }

        override suspend fun startRun(request: StartRunRequest): RunHandle {
            return RunHandle(runId = "run_123", status = RunStatusValue.RUNNING, startedAtEpochMs = 1000L)
        }

        override suspend fun getRun(runId: String): RunStatus? {
            getRunCallCount++
            return if (statusTransitions.isNotEmpty()) {
                statusTransitions.removeAt(0)
            } else {
                runStatusToReturn
            }
        }

        override suspend fun cancelRun(runId: String, signal: String): RunCancelResponse {
            return RunCancelResponse(accepted = true, status = RunStatusValue.CANCELLED)
        }
    }

    private val testWorkspace = Workspace(
        id = "ws_test",
        name = "test-ws",
        path = "/home/lti/LtiRomWorkDir/workspaces/test-ws",
        linuxPath = "/home/lti/LtiRomWorkDir/workspaces/test-ws",
    )

    @Test
    fun testObserveReplaysAndEmitsFinishedImmediatelyWhenCompleted() = runTest {
        val transport = FakeRemoteTransport().apply {
            sessionEvents.add(SequencedStreamEvent(0L, CoreStreamEvent.OutputChunk("hello\n", isError = false)))
            runStatusToReturn = RunStatus(
                runId = "run_123",
                status = RunStatusValue.COMPLETED,
                exitCode = 0,
                startedAtEpochMs = 1000L,
                lastSeq = 0L,
            )
        }
        val adapter = StepExecutionAdapter(transport)

        val events = adapter.observe("run_123", 0L).toList()

        assertEquals(2, events.size)
        assertTrue(events[0] is StepEvent.Output)
        assertEquals("hello\n", (events[0] as StepEvent.Output).text)
        assertTrue(events[1] is StepEvent.Finished)
        assertEquals(0, (events[1] as StepEvent.Finished).exitCode)
    }

    @Test
    fun testObserveWaitsAndEmitsEventsUntilRunCompletes() = runTest {
        val transport = FakeRemoteTransport().apply {
            statusTransitions.add(
                RunStatus(runId = "run_123", status = RunStatusValue.RUNNING, startedAtEpochMs = 1000L, lastSeq = 0L),
            )
            statusTransitions.add(
                RunStatus(
                    runId = "run_123",
                    status = RunStatusValue.COMPLETED,
                    exitCode = 0,
                    startedAtEpochMs = 1000L,
                    lastSeq = 1L,
                ),
            )
            sessionEvents.add(SequencedStreamEvent(0L, CoreStreamEvent.OutputChunk("processing...\n", isError = false)))
            sessionEvents.add(SequencedStreamEvent(1L, CoreStreamEvent.OutputChunk("done\n", isError = false)))
        }
        val adapter = StepExecutionAdapter(transport)

        val events = adapter.observe("run_123", 0L).toList()

        assertTrue(events.any { it is StepEvent.Output && it.text == "processing...\n" })
        assertTrue(events.any { it is StepEvent.Output && it.text == "done\n" })
        assertTrue(events.any { it is StepEvent.Heartbeat })
        val finished = events.filterIsInstance<StepEvent.Finished>().firstOrNull()
        assertTrue(finished != null && finished.exitCode == 0)
    }

    @Test
    fun testObserveEmitsFailedWhenRunFails() = runTest {
        val transport = FakeRemoteTransport().apply {
            statusTransitions.add(
                RunStatus(runId = "run_123", status = RunStatusValue.RUNNING, startedAtEpochMs = 1000L, lastSeq = 0L),
            )
            statusTransitions.add(
                RunStatus(
                    runId = "run_123",
                    status = RunStatusValue.FAILED,
                    exitCode = 2,
                    startedAtEpochMs = 1000L,
                    lastSeq = 0L,
                ),
            )
        }
        val adapter = StepExecutionAdapter(transport)

        val events = adapter.observe("run_123", 0L).toList()

        val finished = events.filterIsInstance<StepEvent.Finished>().firstOrNull()
        assertTrue(finished != null && finished.exitCode == 2)
    }

    @Test
    fun testAdapterMethodsExecuteProperly() = runTest {
        val transport = FakeRemoteTransport()
        val adapter = StepExecutionAdapter(transport)

        val handle = adapter.startStep(testWorkspace, StepCommand("test", listOf("-e")), "idemp-1")
        assertEquals("run_123", handle.runId)

        val cancelResult = adapter.cancel("run_123")
        assertTrue(cancelResult)

        val uploaded = adapter.uploadFile(testWorkspace, "foo/bar.txt", "content".toByteArray())
        assertTrue(uploaded)

        val read = adapter.readFile(testWorkspace, "foo/bar.txt")
        assertEquals("content", read)

        val stat = adapter.stat(testWorkspace, "foo/bar.txt")
        assertTrue(stat.exists)
        assertEquals(123456L, stat.sizeBytes)

        val diskSpace = adapter.checkAvailableDiskSpace(testWorkspace)
        assertEquals(60000000000L, diskSpace)
    }
}
