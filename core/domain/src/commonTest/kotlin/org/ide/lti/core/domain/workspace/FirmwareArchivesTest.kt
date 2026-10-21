/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.workspace

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.pipeline.ToolResult
import org.ide.lti.core.domain.ports.FileStat
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.ports.RunHandle
import org.ide.lti.core.domain.ports.StepEvent
import org.ide.lti.core.domain.ports.StepStatus
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.model.run.StepCommand
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FirmwareArchivesTest {
    /** A workspace folder in WSL: files by absolute path, changed only through the commands the service runs. */
    private class Folder : PipelineExecutionPort {
        val files = mutableMapOf<String, Long>()
        val commands = mutableListOf<StepCommand>()
        var failCopy = false

        override suspend fun execute(ws: Workspace, command: StepCommand): ToolResult {
            commands += command
            val a = command.args
            return when (command.toolId) {
                "mkdir" -> ToolResult(0, "")
                "cp" -> if (failCopy || a[2] !in files) {
                    ToolResult(1, "", "cp: cannot stat '${a[2]}'")
                } else {
                    files[a[3]] = files.getValue(a[2])
                    ToolResult(0, "")
                }
                "mv" -> ToolResult(0, "").also { files[a[3]] = files.remove(a[2])!! }
                "find" -> ToolResult(
                    0,
                    files.filterKeys { it.startsWith(a[0] + "/") && !it.endsWith(".partial") }
                        .map { (path, size) -> "$size\t${path.substringAfterLast('/')}" }
                        .joinToString("\n", postfix = "\n"),
                )
                else -> error("unexpected ${command.toolId}")
            }
        }

        override suspend fun stat(ws: Workspace, relPath: String): FileStat {
            val path = "${ws.linuxPath}/$relPath"
            return FileStat(path, exists = path in files, sizeBytes = files[path] ?: 0L)
        }

        override suspend fun startStep(ws: Workspace, step: StepCommand, idempotencyKey: String): RunHandle =
            error("unused")
        override fun observe(serverRunId: String, fromSeq: Long): Flow<StepEvent> = emptyFlow()
        override suspend fun status(serverRunId: String): StepStatus? = null
        override suspend fun cancel(serverRunId: String): Boolean = false
        override suspend fun uploadFile(ws: Workspace, relPath: String, bytes: ByteArray): Boolean = false
        override suspend fun readFile(ws: Workspace, relPath: String): String? = null
        override suspend fun checkAvailableDiskSpace(ws: Workspace): Long? = null
    }

    private class Workspaces(private val ws: Workspace) : WorkspaceRepository {
        override fun getWorkspaces(): Flow<List<Workspace>> = flowOf(listOf(ws))
        override suspend fun getWorkspace(id: String): Workspace? = ws.takeIf { it.id == id }
        override suspend fun saveWorkspace(workspace: Workspace) {}
        override suspend fun deleteWorkspace(id: String) {}
        override fun getRecentProjects(): Flow<List<RecentProject>> = flowOf(emptyList())
        override suspend fun addRecentProject(project: RecentProject) {}
        override suspend fun removeRecentProject(workspaceId: String) {}
        override suspend fun clearRecentProjects() {}
        override suspend fun discoverWorkspacesInWorkDir(basePath: String?): List<RecentProject> = emptyList()
        override suspend fun getWorkspaceSession(workspaceId: String): WorkspaceSession? = null
        override suspend fun saveWorkspaceSession(session: WorkspaceSession) {}
    }

    private val folder = Folder()
    private val ws = Workspace(id = "ws", name = "ws", path = "/w", linuxPath = "/w")
    private val archives = FirmwareArchives(Workspaces(ws), folder) { it.replace("C:\\", "/mnt/c/").replace('\\', '/') }

    @Test
    fun `an imported archive is copied inside WSL to where the Acquire stage looks for it`() = runTest {
        folder.files["/mnt/c/fw/PQ84P01.zip"] = 3_000_000_000L

        val imported = archives.import("ws", "C:\\fw\\PQ84P01.zip")

        assertEquals("firmware/downloaded/PQ84P01.zip", imported.ref)
        assertEquals(3_000_000_000L, imported.sizeBytes)
        assertEquals(3_000_000_000L, folder.files["/w/firmware/downloaded/PQ84P01.zip"])
        assertTrue(folder.files.keys.none { it.endsWith(".partial") }, "the side copy is renamed into place")
        assertEquals(listOf(imported), archives.list("ws"))
    }

    @Test
    fun `a failed copy is reported and leaves no archive behind`() = runTest {
        folder.files["/mnt/c/fw/a.zip"] = 10L
        folder.failCopy = true

        val error = assertFailsWith<IllegalStateException> { archives.import("ws", "C:\\fw\\a.zip") }

        assertTrue("cannot stat" in error.message.orEmpty())
        assertEquals(emptyList(), archives.list("ws"))
    }

    @Test
    fun `listing skips half-copied archives`() = runTest {
        folder.files["/w/firmware/downloaded/b.zip"] = 2L
        folder.files["/w/firmware/downloaded/a.zip.partial"] = 1L

        assertEquals(listOf("b.zip"), archives.list("ws").map { it.name })
    }
}
