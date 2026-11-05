/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.ports

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.pipeline.ToolResult
import org.ide.lti.core.model.run.StepCommand
import org.ide.lti.core.model.workspace.Workspace
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RunToEndTest {
    private val ws = Workspace("ws", "ws", "/tmp/ws", linuxPath = "/tmp/ws")

    @Test
    fun aReadThatFailsStopsTheCommand() = runTest {
        val port = Port(flow { throw IllegalStateException("connection lost") })
        assertFailsWith<IllegalStateException> { port.runToEnd(ws, "sh", listOf("-c", "work")) }
        assertTrue(port.cancelled, "the job lock is released next: the command must not go on behind it")
    }

    @Test
    fun outputPastTheCapStopsTheCommandInsteadOfFillingMemory() = runTest {
        val line = "x".repeat(1024 * 1024)
        val port = Port(
            flow {
                repeat(100) { emit(StepEvent.Output(it + 1L, line)) }
                emit(StepEvent.Finished(0))
            },
        )
        val failure = assertFailsWith<IllegalStateException> { port.runToEnd(ws, "find", listOf("/")) }
        assertContains(failure.message.orEmpty(), "not kept")
        assertTrue(port.cancelled)
    }

    private class Port(private val events: Flow<StepEvent>) : PipelineExecutionPort {
        var cancelled = false

        override suspend fun startStep(ws: Workspace, step: StepCommand, idempotencyKey: String) =
            RunHandle("remote", "RUNNING", 0)

        override fun observe(serverRunId: String, fromSeq: Long): Flow<StepEvent> = events

        override suspend fun cancel(serverRunId: String): Boolean {
            cancelled = true
            return true
        }

        override suspend fun status(serverRunId: String) = StepStatus(serverRunId, "RUNNING")
        override suspend fun uploadFile(ws: Workspace, relPath: String, bytes: ByteArray) = false
        override suspend fun readFile(ws: Workspace, relPath: String): String? = null
        override suspend fun stat(ws: Workspace, relPath: String) = FileStat(relPath, false)
        override suspend fun execute(ws: Workspace, command: StepCommand) = ToolResult(0, "", "")
        override suspend fun checkAvailableDiskSpace(ws: Workspace): Long? = null
    }
}
