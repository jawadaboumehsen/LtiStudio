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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.ide.lti.core.domain.ports.ProvisioningEvent
import org.ide.lti.core.domain.ports.ProvisioningSpec
import org.ide.lti.core.domain.ports.WorkspaceProvisioningPort
import org.ide.lti.core.domain.repository.run.InMemoryRunRepository
import org.ide.lti.core.domain.repository.snapshot.InMemorySnapshotRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.setup.WorkspaceReadiness
import org.ide.lti.core.model.target.PackagePolicy
import org.ide.lti.core.model.target.TargetBinding
import org.ide.lti.core.model.target.TargetFirmware
import org.ide.lti.core.model.target.TargetRegion
import org.ide.lti.core.model.workspace.AcquisitionMode
import org.ide.lti.core.model.workspace.ConfigurationDraft
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.DecodeWarning
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceSession
import org.ide.lti.core.model.workspace.WorkspaceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SaveConfigurationSnapshotUseCaseTest {

    private class FakeProvisioningPort : WorkspaceProvisioningPort {
        val writtenConfigs = mutableListOf<Pair<Workspace, ConfigurationSnapshot>>()
        override fun provision(spec: ProvisioningSpec): Flow<ProvisioningEvent> = emptyFlow()
        override suspend fun cancel(id: String) {}
        override suspend fun writeConfig(workspace: Workspace, snapshot: ConfigurationSnapshot) {
            writtenConfigs.add(workspace to snapshot)
        }
    }

    private class FakeWorkspaceRepo : WorkspaceRepository {
        val workspaces = mutableMapOf<String, Workspace>()
        private val _flow = MutableStateFlow<List<Workspace>>(emptyList())

        override fun getWorkspaces(): Flow<List<Workspace>> = _flow.asStateFlow()
        override suspend fun getWorkspace(id: String): Workspace? = workspaces[id]
        override suspend fun saveWorkspace(workspace: Workspace) {
            workspaces[workspace.id] = workspace
            _flow.value = workspaces.values.toList()
        }
        override suspend fun deleteWorkspace(id: String) {
            workspaces.remove(id)
            _flow.value = workspaces.values.toList()
        }
        override suspend fun getWorkspaceSession(workspaceId: String): WorkspaceSession? = null
        override suspend fun saveWorkspaceSession(session: WorkspaceSession) {}
        override fun getRecentProjects(): Flow<List<RecentProject>> = flowOf(emptyList())
        override suspend fun addRecentProject(project: RecentProject) {}
        override suspend fun removeRecentProject(workspaceId: String) {}
        override suspend fun clearRecentProjects() {}
        override suspend fun discoverWorkspacesInWorkDir(basePath: String?): List<RecentProject> = emptyList()
        override fun readinessOf(workspace: Workspace): WorkspaceReadiness = WorkspaceReadiness.READY
        override fun observeDecodeWarnings(): Flow<DecodeWarning> = flowOf()
    }

    private val firmwareGlobal = TargetFirmware(
        version = "10.5.15_GL",
        buildId = "PQ84P01:15",
        androidVersion = "15",
        securityPatch = "2026-02-01",
    )

    private val firmwareCn = TargetFirmware(
        version = "10.5.15_CN",
        buildId = "PQ84P01:15",
        androidVersion = "15",
        securityPatch = "2026-02-01",
    )

    @Test
    fun savingCreatesNewImmutableSnapshotAndUpdatesEffectiveId() = runTest {
        val workspaceRepo = FakeWorkspaceRepo()
        val snapshotRepo = InMemorySnapshotRepository()
        val runRepo = InMemoryRunRepository()
        val provisioningPort = FakeProvisioningPort()
        val useCase = SaveConfigurationSnapshotUseCase(workspaceRepo, snapshotRepo, runRepo, provisioningPort)

        // Initial snapshot
        val initialSnapshot = ConfigurationSnapshot.create(
            id = "snap-initial",
            workspaceId = "ws-1",
            profileRevision = 1,
            acquisition = org.ide.lti.core.model.workspace.AcquisitionSettings(
                region = TargetRegion.GLOBAL,
                firmware = firmwareGlobal,
            ),
            build = org.ide.lti.core.model.workspace.BuildSettings(
                packagePolicy = PackagePolicy(listOf("system"), listOf("boot")),
            ),
            createdAt = Clock.System.now(),
        )
        snapshotRepo.saveSnapshot(initialSnapshot)

        // Workspace 1
        val ws1 = Workspace(
            id = "ws-1",
            name = "Workspace 1",
            path = "/home/lti/LtiRomWorkDir/workspaces/ws-1",
            linuxPath = "/home/lti/LtiRomWorkDir/workspaces/ws-1",
            type = WorkspaceType.REMOTE_WSL,
            targetBinding = TargetBinding("PQ84P01", 1),
            layoutVersion = 1,
            effectiveSnapshotId = "snap-initial",
        )
        workspaceRepo.saveWorkspace(ws1)

        // Workspace 2 bound to same target
        val ws2 = Workspace(
            id = "ws-2",
            name = "Workspace 2",
            path = "/home/lti/LtiRomWorkDir/workspaces/ws-2",
            linuxPath = "/home/lti/LtiRomWorkDir/workspaces/ws-2",
            type = WorkspaceType.REMOTE_WSL,
            targetBinding = TargetBinding("PQ84P01", 1),
            layoutVersion = 1,
            effectiveSnapshotId = "snap-initial",
        )
        workspaceRepo.saveWorkspace(ws2)

        // Draft for Workspace 1: switch to CN
        val draft = ConfigurationDraft(
            region = TargetRegion.CHINA,
            firmware = firmwareCn,
            acquisitionMode = AcquisitionMode.DOWNLOAD,
            buildType = "userdebug",
            romVersion = "1.1.0",
            otaBaseUrl = "https://ota.nubia.com/astra",
        )

        val result = useCase("ws-1", draft)
        assertTrue(result.isSuccess)
        val newSnapshot = result.getOrThrow()

        // 1. New snapshot created with new id and new digest
        assertNotEquals("snap-initial", newSnapshot.id)
        assertNotEquals(initialSnapshot.digest, newSnapshot.digest)
        assertEquals(TargetRegion.CHINA, newSnapshot.acquisition.region)
        assertEquals("1.1.0", newSnapshot.assembly.romVersion)

        // 2. Previous snapshot is untouched
        val retrievedInitial = snapshotRepo.getSnapshot("snap-initial")
        assertNotNull(retrievedInitial)
        assertEquals(TargetRegion.GLOBAL, retrievedInitial.acquisition.region)

        // 3. Workspace 1 effectiveSnapshotId updated
        val updatedWs1 = workspaceRepo.getWorkspace("ws-1")
        assertEquals(newSnapshot.id, updatedWs1?.effectiveSnapshotId)

        // 4. Workspace 2 is unaffected
        val updatedWs2 = workspaceRepo.getWorkspace("ws-2")
        assertEquals("snap-initial", updatedWs2?.effectiveSnapshotId)

        // 5. writeConfig was called once for ws-1
        assertEquals(1, provisioningPort.writtenConfigs.size)
        assertEquals(newSnapshot.id, provisioningPort.writtenConfigs.first().second.id)
    }

    @Test
    fun savingIsRefusedWhileRunIsActiveInWorkspace() = runTest {
        val workspaceRepo = FakeWorkspaceRepo()
        val snapshotRepo = InMemorySnapshotRepository()
        val runRepo = InMemoryRunRepository()
        val provisioningPort = FakeProvisioningPort()
        val useCase = SaveConfigurationSnapshotUseCase(workspaceRepo, snapshotRepo, runRepo, provisioningPort)

        val ws = Workspace(
            id = "ws-active",
            name = "Active Workspace",
            path = "/path",
            linuxPath = "/path",
            effectiveSnapshotId = "snap-1",
        )
        workspaceRepo.saveWorkspace(ws)

        // Active build run in ws-active
        val activeRun = BuildRun(
            id = "run-1",
            workspaceId = "ws-active",
            snapshotId = "snap-1",
            state = RunState.RUNNING,
            startedAt = Instant.fromEpochMilliseconds(1000L),
        )
        runRepo.upsert(activeRun)

        val draft = ConfigurationDraft(
            region = TargetRegion.GLOBAL,
            firmware = firmwareGlobal,
        )

        val result = useCase("ws-active", draft)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(0, provisioningPort.writtenConfigs.size)
    }
}
