/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.datastore

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.model.target.TargetBinding
import org.ide.lti.core.model.workspace.EnvironmentId
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspacePreferencesDataSourceMigrationTest {

    @Test
    fun testBareArrayV1BlobLoadsAsSchemaVersion1WithTargetBindingNull() = runTest {
        val settings = MapSettings()
        val bareArrayJson = """
            [
                {
                    "id": "123456",
                    "name": "Legacy Workspace",
                    "path": "/home/lti/LtiRomWorkDir/workspaces/legacy",
                    "type": "REMOTE_WSL"
                }
            ]
        """.trimIndent()
        settings.putString(WorkspacePreferencesDataSource.KEY_WORKSPACES, bareArrayJson)

        val dataSource = WorkspacePreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)
        val workspaces = dataSource.workspaces.first()

        assertEquals(1, workspaces.size)
        val ws = workspaces.first()
        assertEquals("123456", ws.id)
        assertEquals("Legacy Workspace", ws.name)
        assertNull(ws.targetBinding, "targetBinding must be null for v1 migration, no PQ84P01 inferred")
        assertNull(ws.linuxPath)
        assertNull(ws.environmentId)
        assertEquals(1, ws.layoutVersion)
        assertNull(ws.effectiveSnapshotId)
    }

    @Test
    fun testWrapperSchemaVersion2RoundTrips() = runTest {
        val settings = MapSettings()
        val dataSource = WorkspacePreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)

        val binding = TargetBinding(profileId = "PQ84P01", profileRevision = 2)
        val env = EnvironmentId(distro = "Ubuntu", user = "lti", daemonId = "daemon-1")
        val ws = Workspace(
            id = "uuid-1234",
            name = "Modern Workspace",
            path = """\\wsl.localhost\Ubuntu\home\lti\LtiRomWorkDir\workspaces\modern""",
            type = WorkspaceType.REMOTE_WSL,
            linuxPath = "/home/lti/LtiRomWorkDir/workspaces/modern",
            targetBinding = binding,
            environmentId = env,
            layoutVersion = 1,
            effectiveSnapshotId = "snap-5678",
        )

        dataSource.saveWorkspace(ws)

        val rawStored = settings.getStringOrNull(WorkspacePreferencesDataSource.KEY_WORKSPACES)
        assertNotNull(rawStored)
        assertTrue(rawStored.contains(""""schemaVersion":2""") || rawStored.contains(""""schemaVersion": 2"""))
        assertTrue(rawStored.contains(""""items""""))

        val reloaded = WorkspacePreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)
        val reloadedWorkspaces = reloaded.workspaces.first()
        assertEquals(1, reloadedWorkspaces.size)
        val reloadedWs = reloadedWorkspaces.first()
        assertEquals("uuid-1234", reloadedWs.id)
        assertEquals(binding, reloadedWs.targetBinding)
        assertEquals("/home/lti/LtiRomWorkDir/workspaces/modern", reloadedWs.linuxPath)
        assertEquals(env, reloadedWs.environmentId)
        assertEquals("snap-5678", reloadedWs.effectiveSnapshotId)
    }

    @Test
    fun testCorruptBlobQuarantinedAndEmitsWarningAndReturnsEmptyList() = runTest {
        val settings = MapSettings()
        val corruptData = "{ corrupt: true, invalid json ... "
        settings.putString(WorkspacePreferencesDataSource.KEY_WORKSPACES, corruptData)

        val dataSource = WorkspacePreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)
        val workspaces = dataSource.workspaces.first()
        assertTrue(workspaces.isEmpty(), "Workspaces must be empty on corrupt blob")

        val quarantinePrefix = "${WorkspacePreferencesDataSource.KEY_WORKSPACES}.quarantine."
        val quarantineKeys = settings.keys.filter { it.startsWith(quarantinePrefix) }
        assertEquals(1, quarantineKeys.size, "Expected exactly 1 quarantine key")
        val quarantinedValue = settings.getStringOrNull(quarantineKeys.first())
        assertEquals(corruptData, quarantinedValue)

        val warning = dataSource.decodeWarnings.first()
        assertEquals(WorkspacePreferencesDataSource.KEY_WORKSPACES, warning.key)
        assertTrue(warning.message.contains("could not be loaded"))
    }
}
