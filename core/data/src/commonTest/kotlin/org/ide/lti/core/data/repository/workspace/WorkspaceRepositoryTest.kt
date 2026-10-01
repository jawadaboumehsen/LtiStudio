/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.repository.workspace

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.ide.lti.core.common.notification.NotificationLevel
import org.ide.lti.core.common.notification.SystemNotification
import org.ide.lti.core.common.notification.SystemNotifier
import org.ide.lti.core.datastore.WorkspacePreferencesDataSource
import org.ide.lti.core.model.setup.WorkspaceReadiness
import org.ide.lti.core.model.target.TargetBinding
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceSession
import org.ide.lti.core.model.workspace.WorkspaceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceRepositoryTest {

    private class CapturingSystemNotifier : SystemNotifier {
        val notifications = mutableListOf<SystemNotification>()
        override val isSupported: Boolean = true
        override fun notify(notification: SystemNotification) {
            notifications.add(notification)
        }
    }

    private fun createRepository(
        notifier: SystemNotifier? = null,
        discovery: WorkDirDiscoveryService = WorkDirDiscoveryService(),
    ): Pair<WorkspaceRepositoryImpl, WorkspacePreferencesDataSource> {
        val settings = MapSettings()
        val ds = WorkspacePreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)
        val repo = WorkspaceRepositoryImpl(
            preferencesDataSource = ds,
            discoveryService = discovery,
            notifier = notifier,
            ioDispatcher = Dispatchers.Unconfined,
        )
        return Pair(repo, ds)
    }

    @Test
    fun testInitialWorkspacesAndRecentsAreEmpty() = runTest {
        val (repo, _) = createRepository()
        val recents = repo.getRecentProjects().first()
        val workspaces = repo.getWorkspaces().first()

        assertTrue(recents.isEmpty())
        assertTrue(workspaces.isEmpty())
    }

    @Test
    fun testDiscoverWorkspacesPopulatesCanonicalWorkspaces() = runTest {
        val fs = okio.FileSystem.SYSTEM
        val tempDir = okio.FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "lti-test-${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}"
        val wsDir = tempDir / "workspaces" / "astra_test"
        fs.createDirectories(wsDir)
        val manifestJson = """
            {
                "id": "ws-astra-1",
                "layoutVersion": 1,
                "targetBinding": {
                    "profileId": "PQ84P01",
                    "profileRevision": 1
                },
                "createdAt": "2026-09-11T00:00:00Z",
                "appVersion": "1.0.0"
            }
        """.trimIndent()
        fs.write(wsDir / "workspace.json") {
            writeUtf8(manifestJson)
        }

        try {
            val discovery = WorkDirDiscoveryService(fileSystem = fs)
            val (repo, ds) = createRepository(discovery = discovery)

            val discovered = repo.discoverWorkspacesInWorkDir(tempDir.toString())
            assertEquals(1, discovered.size)
            assertEquals("ws-astra-1", discovered.first().workspaceId)
            assertEquals("astra_test", discovered.first().name)
            assertEquals("~/LtiRomWorkDir/workspaces/astra_test", discovered.first().path)

            // DataStore should now have these recents
            val recents = ds.recentProjects.first()
            assertEquals(1, recents.size)
            assertEquals("ws-astra-1", recents.first().workspaceId)
        } finally {
            try {
                fs.deleteRecursively(tempDir)
            } catch (_: Exception) {}
        }
    }

    @Test
    fun testSystemStagingWorkDirIsPurgedFromDataStore() = runTest {
        val (_, ds) = createRepository()

        val staging = RecentProject(
            workspaceId = "ltirom_staging_workdir",
            name = "LtiRom WorkDir",
            path = "~/LtiRomWorkDir",
            lastOpened = Instant.fromEpochMilliseconds(100L),
        )
        // Attempting to add staging should be ignored
        ds.addRecentProject(staging)
        val recents = ds.recentProjects.first()
        assertTrue(recents.none { it.path == "~/LtiRomWorkDir" })
    }

    @Test
    fun testAddAndRemoveRecentProjects() = runTest {
        val (repo, _) = createRepository()

        val p1 = RecentProject(
            workspaceId = "p1",
            name = "TestROM",
            path = "/tmp/test",
            lastOpened = Instant.fromEpochMilliseconds(100L),
        )

        repo.addRecentProject(p1)
        var recents = repo.getRecentProjects().first()
        assertEquals(1, recents.size)
        assertEquals("p1", recents.first().workspaceId)

        repo.removeRecentProject("p1")
        recents = repo.getRecentProjects().first()
        assertTrue(recents.isEmpty())
    }

    @Test
    fun testSaveAndRetrieveWorkspaceAndSession() = runTest {
        val (repo, _) = createRepository()

        val ws = Workspace(
            id = "ws_test",
            name = "Custom Target",
            path = "~/LtiRomWorkDir/workspaces/ws_test",
            type = WorkspaceType.REMOTE_WSL,
        )

        repo.saveWorkspace(ws)
        val fetched = repo.getWorkspace("ws_test")
        assertNotNull(fetched)
        assertEquals("Custom Target", fetched.name)

        val session = WorkspaceSession(
            workspaceId = "ws_test",
            openFiles = listOf(org.ide.lti.core.model.workspace.OpenFile(path = "workspace.json")),
            activeFileIndex = 0,
        )
        repo.saveWorkspaceSession(session)
        val fetchedSession = repo.getWorkspaceSession("ws_test")
        assertNotNull(fetchedSession)
        assertEquals("workspace.json", fetchedSession.openFiles.first().path)

        repo.deleteWorkspace("ws_test")
        assertNull(repo.getWorkspace("ws_test"))
        assertNull(repo.getWorkspaceSession("ws_test"))
    }

    @Test
    fun testReadinessOfReturnsNeedsTargetWhenTargetBindingNull() {
        val (repo, _) = createRepository()
        val wsWithoutTarget = Workspace(
            id = "w1",
            name = "No Target",
            path = "/tmp/w1",
            targetBinding = null,
        )
        val wsWithTarget = Workspace(
            id = "w2",
            name = "With Target",
            path = "/tmp/w2",
            targetBinding = TargetBinding(profileId = "PQ84P01", profileRevision = 1),
        )

        assertEquals(WorkspaceReadiness.NEEDS_TARGET, repo.readinessOf(wsWithoutTarget))
        assertEquals(WorkspaceReadiness.READY, repo.readinessOf(wsWithTarget))
    }

    @Test
    fun testObserveDecodeWarningsSurfacesViaSystemNotifier() = runTest {
        val notifier = CapturingSystemNotifier()
        val settings = MapSettings()
        settings.putString(WorkspacePreferencesDataSource.KEY_WORKSPACES, "{ corrupt ...")
        val ds = WorkspacePreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)
        val repo = WorkspaceRepositoryImpl(
            preferencesDataSource = ds,
            notifier = notifier,
            ioDispatcher = Dispatchers.Unconfined,
        )

        val warning = repo.observeDecodeWarnings().first()
        assertNotNull(warning)
        assertEquals(1, notifier.notifications.size)
        val notification = notifier.notifications.first()
        assertEquals(NotificationLevel.Warning, notification.level)
        assertTrue(notification.message.contains("could not be loaded"))
    }
}
