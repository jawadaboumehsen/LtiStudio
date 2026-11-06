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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.pipeline.ToolResult
import org.ide.lti.core.model.run.StepCommand
import org.ide.lti.core.model.workspace.Workspace
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class WorkspaceLinksTest {
    private val ws = Workspace("ws", "ws", "/ws", linuxPath = "/ws")

    @Test
    fun aScanThatFailedAuthorizesNothing() = runTest {
        val port = Port(ToolResult(1, "", "find: permission denied"))
        val failure = assertFailsWith<IllegalStateException> { port.requireNoLinkInWorkspace(ws) }
        assertContains(failure.message.orEmpty(), "permission denied")
    }

    @Test
    fun aCleanScanFindsNothingAndALinkIsNamed() = runTest {
        assertNull(Port(ToolResult(0, "", "")).linkInWorkspace(ws))
        assertEquals("/ws/work", Port(ToolResult(0, "/ws/work\n", "")).linkInWorkspace(ws))
    }

    private class Port(private val scan: ToolResult) : PipelineExecutionPort {
        override suspend fun execute(ws: Workspace, command: StepCommand) = scan
        override suspend fun startStep(ws: Workspace, step: StepCommand, idempotencyKey: String): RunHandle =
            error("unused")

        override fun observe(serverRunId: String, fromSeq: Long): Flow<StepEvent> = emptyFlow()
        override suspend fun cancel(serverRunId: String) = false
        override suspend fun status(serverRunId: String): StepStatus? = null
        override suspend fun uploadFile(ws: Workspace, relPath: String, bytes: ByteArray) = false
        override suspend fun readFile(ws: Workspace, relPath: String): String? = null
        override suspend fun stat(ws: Workspace, relPath: String) = FileStat(relPath, false)
        override suspend fun checkAvailableDiskSpace(ws: Workspace): Long? = null
    }
}
