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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class WorkspaceForPathTest {
    private class Repo(val saved: MutableList<Workspace> = mutableListOf()) : WorkspaceRepository {
        override fun getWorkspaces(): Flow<List<Workspace>> = flowOf(saved.toList())
        override suspend fun getWorkspace(id: String): Workspace? = saved.firstOrNull { it.id == id }
        override suspend fun saveWorkspace(workspace: Workspace) {
            saved.removeAll { it.id == workspace.id }
            saved += workspace
        }
        override suspend fun deleteWorkspace(id: String) {}
        override fun getRecentProjects(): Flow<List<RecentProject>> = flowOf(emptyList())
        override suspend fun addRecentProject(project: RecentProject) {}
        override suspend fun removeRecentProject(workspaceId: String) {}
        override suspend fun clearRecentProjects() {}
        override suspend fun discoverWorkspacesInWorkDir(basePath: String?): List<RecentProject> = emptyList()
        override suspend fun getWorkspaceSession(workspaceId: String): WorkspaceSession? = null
        override suspend fun saveWorkspaceSession(session: WorkspaceSession) {}
    }

    @Test
    fun pathsWithCollidingHashCodesGetDistinctIds() = runTest {
        // "Aa".hashCode() == "BB".hashCode(): the old id scheme gave both folders one workspace.
        assertEquals("C:/rom/Aa".hashCode(), "C:/rom/BB".hashCode())
        val repo = Repo()
        val a = WorkspaceManager.workspaceForPath(repo, "C:/rom/Aa")
        val b = WorkspaceManager.workspaceForPath(repo, "C:/rom/BB")
        assertNotEquals(a.id, b.id)
    }

    @Test
    fun reopeningAFolderKeepsItsRecordWhateverTheSpelling() = runTest {
        val bound = Workspace(id = "uuid-1", name = "rom", path = "C:\\Work\\rom", linuxPath = "/home/u/rom")
        val repo = Repo(mutableListOf(bound))
        for (spelling in listOf("C:\\Work\\rom\\", "c:/work/rom", "C:/Work//rom/")) {
            val found = WorkspaceManager.workspaceForPath(repo, spelling)
            assertEquals("uuid-1", found.id, spelling)
            assertEquals("/home/u/rom", found.linuxPath, spelling)
        }
        // A Linux path is case-sensitive.
        assertNotEquals(
            WorkspaceManager.canonicalPath("/home/u/Rom"),
            WorkspaceManager.canonicalPath("/home/u/rom"),
        )
    }

    @Test
    fun uncRootsAndDriveRootsKeepTheirMeaning() = runTest {
        // A UNC path stays a UNC path (case-insensitive), never the Linux path "/server/share".
        assertEquals(
            WorkspaceManager.canonicalPath("\\\\wsl.localhost\\Ubuntu\\home"),
            WorkspaceManager.canonicalPath("//WSL.localhost/ubuntu/home/"),
        )
        assertNotEquals(
            WorkspaceManager.canonicalPath("//server/share"),
            WorkspaceManager.canonicalPath("/server/share"),
        )
        // "C:" alone is the drive's current folder: the root keeps its separator.
        assertEquals("C:\\", WorkspaceManager.workspaceForPath(Repo(), "C:\\").path)
    }
}
