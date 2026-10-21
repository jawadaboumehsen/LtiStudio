/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.repository.target

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.repository.workspace.WorkspaceRepositoryImpl
import org.ide.lti.core.datastore.WorkspacePreferencesDataSource
import org.ide.lti.core.domain.pipeline.ToolResult
import org.ide.lti.core.domain.ports.FileStat
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.ports.RunHandle
import org.ide.lti.core.domain.ports.StepEvent
import org.ide.lti.core.domain.ports.StepStatus
import org.ide.lti.core.domain.repository.target.WorkspaceDeviceRepository.Companion.FILE_NAME
import org.ide.lti.core.model.run.StepCommand
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.workspace.Workspace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceFolderDeviceRepositoryTest {
    /** The workspace folders on the WSL side, keyed by full path. */
    private class FakeFolders : PipelineExecutionPort {
        val files = mutableMapOf<String, String>()
        var reachable = true
        var unreadable = false
        var writable = true

        private fun path(ws: Workspace, rel: String) = "${ws.linuxPath ?: ws.path}/$rel"

        override suspend fun uploadFile(ws: Workspace, relPath: String, bytes: ByteArray): Boolean {
            if (writable) files[path(ws, relPath)] = bytes.decodeToString()
            return writable
        }

        override suspend fun readFile(ws: Workspace, relPath: String): String? =
            if (unreadable) null else files[path(ws, relPath)]

        override suspend fun stat(ws: Workspace, relPath: String): FileStat {
            check(reachable) { "build service unavailable" }
            return FileStat(path(ws, relPath), exists = path(ws, relPath) in files)
        }

        override suspend fun startStep(ws: Workspace, step: StepCommand, idempotencyKey: String): RunHandle =
            error("unused")
        override fun observe(serverRunId: String, fromSeq: Long): Flow<StepEvent> = emptyFlow()
        override suspend fun status(serverRunId: String): StepStatus? = null
        override suspend fun cancel(serverRunId: String): Boolean = false
        override suspend fun execute(ws: Workspace, command: StepCommand): ToolResult = ToolResult(0, "")
        override suspend fun checkAvailableDiskSpace(ws: Workspace): Long? = null
    }

    private val folders = FakeFolders()
    private val template = DefaultTargetCatalog.PQ84P01_DEFAULT
    private val workspace = Workspace(id = "ws-1", name = "One", path = "/w/one", linuxPath = "/w/one")

    private suspend fun repo(): WorkspaceFolderDeviceRepository {
        val workspaces = WorkspaceRepositoryImpl(
            preferencesDataSource = WorkspacePreferencesDataSource(
                MapSettings(),
                ioDispatcher = Dispatchers.Unconfined,
            ),
            discoveryService = org.ide.lti.core.data.repository.workspace.WorkDirDiscoveryService(),
            ioDispatcher = Dispatchers.Unconfined,
        )
        workspaces.saveWorkspace(workspace)
        folders.files["/w/one/workspace.json"] = "{}"
        folders.files["/w/two/workspace.json"] = "{}"
        workspaces.saveWorkspace(workspace.copy(id = "ws-2", path = "/w/two", linuxPath = "/w/two"))
        return WorkspaceFolderDeviceRepository(workspaces, folders)
    }

    @Test
    fun `the device is written into the workspace folder and read back from there, not from the app`() = runTest {
        repo().save("ws-1", template)

        assertTrue("/w/one/$FILE_NAME" in folders.files, "device.json sits in the workspace folder")
        assertEquals(template, repo().get("ws-1"), "a fresh repository (no app state) reads the same device")
        assertNull(repo().get("ws-2"), "another workspace folder has its own (here: none)")
    }

    @Test
    fun `a folder without device json has no device, an unreadable one is an error`() = runTest {
        val repo = repo()
        assertNull(repo.get("ws-1"))

        repo.save("ws-1", template)
        folders.unreadable = true
        assertFailsWith<IllegalStateException> { repo.get("ws-1") }

        folders.unreadable = false
        folders.files["/w/one/$FILE_NAME"] = "{ not json"
        assertFailsWith<IllegalArgumentException> { repo.get("ws-1") }

        folders.reachable = false
        assertFailsWith<IllegalStateException> { repo.get("ws-1") }
    }

    @Test
    fun `the revision moves only when the content changes and a failed write is reported`() = runTest {
        val repo = repo()
        val first = repo.save("ws-1", template)
        assertEquals(first.revision, repo.save("ws-1", first).revision)
        assertEquals(first.revision + 1, repo.save("ws-1", first.copy(fastbootProduct = "x")).revision)

        folders.writable = false
        assertFailsWith<IllegalStateException> { repo.save("ws-1", first.copy(fastbootProduct = "y")) }
        assertEquals("x", repo.get("ws-1")?.fastbootProduct, "the folder keeps what was last written")
    }

    @Test
    fun `observers see the stored device and every later save`() = runTest {
        val repo = repo()
        repo.save("ws-1", template)
        val seen = mutableListOf<String?>()
        val job = launch { repo.observe("ws-1").take(2).toList().forEach { seen += it?.fastbootProduct } }
        runCurrent()
        repo.save("ws-1", template.copy(fastbootProduct = "new"))
        job.join()

        assertEquals<List<String?>>(listOf(template.fastbootProduct, "new"), seen)
    }

    @Test
    fun `an unreachable workspace folder is an error, not a missing device`() = runTest {
        val repo = repo()
        folders.files.remove("/w/one/workspace.json")
        assertFailsWith<IllegalStateException> { repo.get("ws-1") }
    }
}
