/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.usecase.workspace

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.pipeline.ToolResult
import org.ide.lti.core.domain.ports.EnvironmentReadinessPort
import org.ide.lti.core.domain.ports.FileStat
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.ports.RunHandle
import org.ide.lti.core.domain.ports.StepEvent
import org.ide.lti.core.domain.ports.StepStatus
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.model.run.StepCommand
import org.ide.lti.core.model.setup.EnvironmentReadiness
import org.ide.lti.core.model.setup.EnvironmentReadinessState
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceSession
import kotlin.test.Test
import kotlin.test.assertEquals

class DiscoverWorkdirWorkspacesUseCaseTest {
    private class Folders(val files: Map<String, String>) : PipelineExecutionPort {
        override suspend fun execute(ws: Workspace, command: StepCommand): ToolResult {
            val root = command.args.first()
            val found = files.keys.filter { it.startsWith("$root/") && it.endsWith("/workspace.json") }
            return if (found.isEmpty()) ToolResult(1, "", "No such file") else ToolResult(0, found.joinToString("\n"))
        }
        override suspend fun readFile(ws: Workspace, relPath: String): String? = files["${ws.linuxPath}/$relPath"]
        override suspend fun uploadFile(ws: Workspace, relPath: String, bytes: ByteArray): Boolean = false
        override suspend fun stat(ws: Workspace, relPath: String) = FileStat(relPath, exists = false)
        override suspend fun startStep(ws: Workspace, step: StepCommand, idempotencyKey: String): RunHandle =
            error("unused")
        override fun observe(serverRunId: String, fromSeq: Long): Flow<StepEvent> = emptyFlow()
        override suspend fun status(serverRunId: String): StepStatus? = null
        override suspend fun cancel(serverRunId: String): Boolean = false
        override suspend fun checkAvailableDiskSpace(ws: Workspace): Long? = null
    }

    /** The app's own records: empty, as after a new install. */
    private class AppRecords : WorkspaceRepository {
        val workspaces = mutableMapOf<String, Workspace>()
        val recents = MutableStateFlow<List<RecentProject>>(emptyList())
        override fun getWorkspaces(): Flow<List<Workspace>> = flowOf(workspaces.values.toList())
        override suspend fun getWorkspace(id: String): Workspace? = workspaces[id]
        override suspend fun saveWorkspace(workspace: Workspace) {
            workspaces[workspace.id] = workspace
        }
        override suspend fun deleteWorkspace(id: String) {}
        override fun getRecentProjects(): Flow<List<RecentProject>> = recents
        override suspend fun addRecentProject(project: RecentProject) {
            recents.value = recents.value + project
        }
        override suspend fun removeRecentProject(workspaceId: String) {}
        override suspend fun clearRecentProjects() {}
        override suspend fun discoverWorkspacesInWorkDir(basePath: String?): List<RecentProject> = emptyList()
        override suspend fun getWorkspaceSession(workspaceId: String): WorkspaceSession? = null
        override suspend fun saveWorkspaceSession(session: WorkspaceSession) {}
    }

    private object Ready : EnvironmentReadinessPort {
        private val ready =
            EnvironmentReadiness(EnvironmentReadinessState.READY, workDirLinuxPath = "/h/LtiRomWorkDir")
        override fun observe(): Flow<EnvironmentReadiness> = flowOf(ready)
        override suspend fun refresh(): EnvironmentReadiness = ready
        override suspend fun forWorkspace(workspace: Workspace): EnvironmentReadiness = ready
    }

    private val ws = "/h/LtiRomWorkDir/workspaces"
    private val manifest =
        """{"id":"id-a","layoutVersion":1,"targetBinding":{"profileId":"id-a","profileRevision":3},""" +
            """"createdAt":"2026-10-17T16:21:47Z","appVersion":"1.0.0"}"""

    @Test
    fun `a workspace folder the app has no record of becomes openable with its saved configuration`() = runTest {
        val app = AppRecords()
        val folders = Folders(
            mapOf(
                "$ws/a/workspace.json" to manifest,
                "$ws/a/config.json" to """{"id":"snap-9","workspaceId":"id-a"}""",
                "$ws/a/device.json" to """{"name":"REDMAGIC Astra","codename":"PQ84P01","revision":5}""",
                "$ws/broken/workspace.json" to "{ not json",
            ),
        )

        val found = DiscoverWorkdirWorkspacesUseCase(app, Ready, folders)()

        assertEquals(listOf("id-a"), found.map { it.id }, "an unreadable folder is skipped, not fatal")
        val record = app.workspaces.getValue("id-a")
        assertEquals("$ws/a", record.linuxPath)
        assertEquals("snap-9", record.effectiveSnapshotId, "points at the folder's config.json")
        assertEquals(5, record.targetBinding?.profileRevision, "the device's current revision, not the one at creation")
        assertEquals("REDMAGIC Astra", app.recents.value.single().targetDisplayName)
    }

    @Test
    fun `a known workspace keeps its record and is not listed twice`() = runTest {
        val app = AppRecords()
        val mine = Workspace(
            id = "id-a",
            name = "mine",
            path = "$ws/a",
            linuxPath = "$ws/a",
            effectiveSnapshotId = "snap-10",
        )
        app.saveWorkspace(mine)
        val folders = Folders(mapOf("$ws/a/workspace.json" to manifest, "$ws/a/config.json" to """{"id":"snap-9"}"""))

        DiscoverWorkdirWorkspacesUseCase(app, Ready, folders)()
        DiscoverWorkdirWorkspacesUseCase(app, Ready, folders)()

        assertEquals(mine, app.workspaces.getValue("id-a"))
        assertEquals(1, app.recents.value.size)
    }

    @Test
    fun `no workspaces folder yet finds nothing`() = runTest {
        assertEquals(emptyList(), DiscoverWorkdirWorkspacesUseCase(AppRecords(), Ready, Folders(emptyMap()))())
    }
}
