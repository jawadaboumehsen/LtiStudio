package org.ide.lti.core.domain.plugin

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.pipeline.PipelineFixture
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.domain.workspace.WorkspaceSettingsStore
import org.ide.lti.core.model.plugin.DependencyRef
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.PluginLockfile
import org.ide.lti.core.model.workspace.PluginLockfileEntry
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceSession
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginWorkspaceUsageTest {
    private val fixture = PipelineFixture()

    private fun record(id: String, version: String, deps: List<String> = emptyList()) = InstalledRecord(
        identity = PackageIdentity("p", id, version, "sha-$id-$version"),
        trust = TrustDecision.TRUSTED_UNVERIFIED,
        enabledInWorkspaces = emptyList(),
        revoked = false,
        archived = false,
        dependencies = deps.map { DependencyRef("p", it, version) },
        conflicts = emptyList(),
    )

    private var snapshot: ConfigurationSnapshot = fixture.snapshotGlobalOta.let { s ->
        s.copy(
            customization = s.customization.copy(
                enabledPackages = listOf("p:keybox", "p:pif"),
                lockfile = PluginLockfile(
                    entries = listOf(
                        PluginLockfileEntry("p", "keybox", "1.0.0", "sha-keybox-1.0.0"),
                        PluginLockfileEntry("p", "pif", "1.0.0", "sha-pif-1.0.0"),
                    ),
                ),
            ),
        )
    }

    private val usage = PluginWorkspaceUsage(
        workspaces = OneWorkspace(fixture.workspace),
        store = object : WorkspaceSettingsStore {
            override suspend fun current(workspaceId: String) = snapshot
            override suspend fun update(
                workspaceId: String,
                change: (ConfigurationSnapshot) -> ConfigurationSnapshot,
            ) = Result.success(change(snapshot).also { snapshot = it })
            override suspend fun device(workspaceId: String) = fixture.targetGlobal
        },
    )

    @Test
    fun usersAreTheWorkspacesWhoseLockfileHasThatVersion() = runTest {
        val users = usage.usersOf()
        assertEquals(listOf(fixture.workspace.name), users["p:keybox@1.0.0"])
        assertEquals(null, users["p:keybox@1.1.0"])
    }

    @Test
    fun anUpdateMovesTheWorkspaceToTheNewestVersions() = runTest {
        val installed = listOf(
            record("keybox", "1.0.0"),
            record("keybox", "1.1.0"),
            record("pif", "1.0.0", listOf("keybox")),
            record("pif", "1.1.0", listOf("keybox")),
        )

        assertEquals(listOf(fixture.workspace.name), usage.moveToNewest(installed))
        assertEquals(setOf("1.1.0"), snapshot.customization.lockfile.entries.map { it.version }.toSet())
        assertEquals(emptyList(), usage.moveToNewest(installed), "already on the newest: nothing to move")
    }

    private class OneWorkspace(private val ws: Workspace) : WorkspaceRepository {
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
}
