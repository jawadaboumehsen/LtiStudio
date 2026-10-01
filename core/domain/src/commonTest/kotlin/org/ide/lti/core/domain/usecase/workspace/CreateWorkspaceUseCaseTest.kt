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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.ports.DestinationExistsException
import org.ide.lti.core.domain.ports.EnvironmentNotReadyException
import org.ide.lti.core.domain.ports.EnvironmentReadinessPort
import org.ide.lti.core.domain.ports.KeySource
import org.ide.lti.core.domain.ports.ProvisioningEvent
import org.ide.lti.core.domain.ports.ProvisioningSpec
import org.ide.lti.core.domain.ports.WorkspaceProvisioningPort
import org.ide.lti.core.domain.repository.snapshot.InMemorySnapshotRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.model.setup.EnvironmentReadiness
import org.ide.lti.core.model.setup.EnvironmentReadinessState
import org.ide.lti.core.model.setup.WorkspaceReadiness
import org.ide.lti.core.model.target.PackagePolicy
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetFirmware
import org.ide.lti.core.model.target.TargetRegion
import org.ide.lti.core.model.target.TargetStatus
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.DecodeWarning
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceKeys
import org.ide.lti.core.model.workspace.WorkspaceSession
import org.ide.lti.core.model.workspace.WorkspaceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class CreateWorkspaceUseCaseTest {

    private val target = TargetDevice(
        id = "PQ84P01",
        name = "REDMAGIC Astra Gaming Tablet",
        codename = "PQ84P01",
        revision = 2,
        availableRegions = listOf(TargetRegion.GLOBAL),
        availableFirmwares = mapOf(
            TargetRegion.GLOBAL to listOf(
                TargetFirmware(
                    version = "REDMAGICOS10.5.15_NP05J_GB",
                    buildId = "PQ84P01:15/AQ3A.240812.002",
                    androidVersion = "15",
                    securityPatch = "2026-02-01",
                    isRecommended = true,
                ),
            ),
        ),
        packagePolicy = PackagePolicy(
            flashablePartitions = listOf("system", "vendor"),
            flashableBootPartitions = listOf("boot"),
        ),
        socPlatform = "Snapdragon 8 Elite",
        filesystemType = "erofs",
        superPartitionBytes = 17179869184L,
        dynamicPartitions = listOf("system", "vendor"),
        bootPartitions = listOf("boot"),
        status = TargetStatus.QUALIFIED,
        description = "Official baseline",
    )

    private class FakeReadinessPort(
        var readiness: EnvironmentReadiness = EnvironmentReadiness(
            state = EnvironmentReadinessState.READY,
            workDirLinuxPath = "/home/lti/LtiRomWorkDir",
        ),
    ) : EnvironmentReadinessPort {
        override fun observe(): Flow<EnvironmentReadiness> = flowOf(readiness)
        override suspend fun refresh(): EnvironmentReadiness = readiness
        override suspend fun forWorkspace(workspace: Workspace): EnvironmentReadiness = readiness
    }

    private class FakeProvisioningPort : WorkspaceProvisioningPort {
        var shouldFail: Boolean = false
        var failureStep: String = "layout"
        var failureMessage: String = "Permission denied"
        var shouldThrowCollision: Boolean = false
        val provisionedSpecs = mutableListOf<ProvisioningSpec>()
        val writtenConfigs = mutableListOf<Pair<Workspace, ConfigurationSnapshot>>()

        override fun provision(spec: ProvisioningSpec): Flow<ProvisioningEvent> = flow {
            provisionedSpecs.add(spec)
            if (shouldThrowCollision) {
                emit(ProvisioningEvent.Failed("check_destination", "Destination already exists"))
                throw DestinationExistsException("Destination already exists: ${spec.destinationLinuxPath}")
            }
            if (shouldFail) {
                emit(ProvisioningEvent.Step(failureStep, "Starting $failureStep"))
                emit(ProvisioningEvent.Failed(failureStep, failureMessage))
                return@flow
            }
            emit(ProvisioningEvent.Step("layout", "Creating workspace directory layout"))
            emit(ProvisioningEvent.Step("config", "Writing workspace configuration"))
            emit(ProvisioningEvent.Step("key", "Generating keys"))
            emit(ProvisioningEvent.Step("validate", "Validating"))
            emit(ProvisioningEvent.Step("promote", "Promoting"))
            emit(
                ProvisioningEvent.Completed(
                    WorkspaceKeys(
                        avbPublicKeySha1 = "sha1:avb123",
                        platformCertSha1 = "sha1:cert123",
                    ),
                ),
            )
        }

        override suspend fun cancel(id: String) {}

        override suspend fun writeConfig(workspace: Workspace, snapshot: ConfigurationSnapshot) {
            writtenConfigs.add(workspace to snapshot)
        }
    }

    private class FakeWorkspaceRepo : WorkspaceRepository {
        val savedWorkspaces = mutableListOf<Workspace>()
        val savedRecents = mutableListOf<RecentProject>()
        private val _workspacesFlow = MutableStateFlow<List<Workspace>>(emptyList())

        override fun getWorkspaces(): Flow<List<Workspace>> = _workspacesFlow.asStateFlow()

        override suspend fun getWorkspace(id: String): Workspace? =
            savedWorkspaces.firstOrNull { it.id == id }

        override suspend fun saveWorkspace(workspace: Workspace) {
            savedWorkspaces.removeAll { it.id == workspace.id }
            savedWorkspaces.add(workspace)
            _workspacesFlow.value = savedWorkspaces.toList()
        }

        override suspend fun deleteWorkspace(id: String) {
            savedWorkspaces.removeAll { it.id == id }
            _workspacesFlow.value = savedWorkspaces.toList()
        }

        override suspend fun getWorkspaceSession(workspaceId: String): WorkspaceSession? = null
        override suspend fun saveWorkspaceSession(session: WorkspaceSession) {}

        override fun getRecentProjects(): Flow<List<RecentProject>> = flowOf(savedRecents)
        override suspend fun addRecentProject(project: RecentProject) {
            savedRecents.removeAll { it.workspaceId == project.workspaceId }
            savedRecents.add(project)
        }
        override suspend fun removeRecentProject(workspaceId: String) {
            savedRecents.removeAll { it.workspaceId == workspaceId }
        }
        override suspend fun clearRecentProjects() {
            savedRecents.clear()
        }
        override suspend fun discoverWorkspacesInWorkDir(basePath: String?): List<RecentProject> = emptyList()
        override fun readinessOf(workspace: Workspace): WorkspaceReadiness = WorkspaceReadiness.READY
        override fun observeDecodeWarnings(): Flow<DecodeWarning> = flowOf()
    }

    @Test
    fun happyPathRegistersWorkspaceSnapshotAndRecentProject() = runTest {
        val readiness = FakeReadinessPort()
        val provisioning = FakeProvisioningPort()
        val repo = FakeWorkspaceRepo()
        val snapshotRepo = InMemorySnapshotRepository()
        val useCase = CreateWorkspaceUseCase(readiness, provisioning, repo, snapshotRepo)

        val params = CreateWorkspaceParams(
            name = "Astra Test Workspace",
            target = target,
            keySource = KeySource.GENERATE,
        )

        val events = useCase(params).toList()

        val completed = events.filterIsInstance<ProvisioningEvent.Completed>().firstOrNull()
        assertNotNull(completed)
        assertEquals("sha1:avb123", completed.keys.avbPublicKeySha1)

        // Verify Workspace registered
        assertEquals(1, repo.savedWorkspaces.size)
        val ws = repo.savedWorkspaces.first()
        assertEquals("Astra Test Workspace", ws.name)
        assertEquals("/home/lti/LtiRomWorkDir/workspaces/astra-test-workspace", ws.linuxPath)
        assertEquals(WorkspaceType.REMOTE_WSL, ws.type)
        assertEquals(1, ws.layoutVersion)
        assertNotNull(ws.targetBinding)
        assertEquals("PQ84P01", ws.targetBinding?.profileId)
        assertEquals(2, ws.targetBinding?.profileRevision)
        assertNotNull(ws.effectiveSnapshotId)

        // Verify Snapshot registered
        val snapshot = snapshotRepo.getSnapshot(ws.effectiveSnapshotId!!)
        assertNotNull(snapshot)
        assertEquals(ws.id, snapshot.workspaceId)
        assertEquals(2, snapshot.profileRevision)
        assertEquals(TargetRegion.GLOBAL, snapshot.acquisition.region)

        // Verify RecentProject registered
        assertEquals(1, repo.savedRecents.size)
        val recent = repo.savedRecents.first()
        assertEquals(ws.id, recent.workspaceId)
        assertEquals("Astra Test Workspace", recent.name)
        assertEquals("REDMAGIC Astra Gaming Tablet", recent.targetDisplayName)
        assertEquals(ws.targetBinding, recent.targetBinding)
    }

    @Test
    fun collisionRefusesCreationWithDestinationExists() = runTest {
        val readiness = FakeReadinessPort()
        val provisioning = FakeProvisioningPort().apply { shouldThrowCollision = true }
        val repo = FakeWorkspaceRepo()
        val snapshotRepo = InMemorySnapshotRepository()
        val useCase = CreateWorkspaceUseCase(readiness, provisioning, repo, snapshotRepo)

        val params = CreateWorkspaceParams(
            name = "Existing Workspace",
            target = target,
        )

        assertFailsWith<DestinationExistsException> {
            useCase(params).toList()
        }

        assertEquals(0, repo.savedWorkspaces.size)
        assertEquals(0, repo.savedRecents.size)
    }

    @Test
    fun provisioningFailureRegistersNothing() = runTest {
        val readiness = FakeReadinessPort()
        val provisioning = FakeProvisioningPort().apply {
            shouldFail = true
            failureStep = "validate"
            failureMessage = "Validation failed"
        }
        val repo = FakeWorkspaceRepo()
        val snapshotRepo = InMemorySnapshotRepository()
        val useCase = CreateWorkspaceUseCase(readiness, provisioning, repo, snapshotRepo)

        val params = CreateWorkspaceParams(
            name = "Failing Workspace",
            target = target,
        )

        val events = useCase(params).toList()
        val failed = events.filterIsInstance<ProvisioningEvent.Failed>().firstOrNull()
        assertNotNull(failed)
        assertEquals("validate", failed.step)

        assertEquals(0, repo.savedWorkspaces.size)
        assertEquals(0, repo.savedRecents.size)
    }

    @Test
    fun environmentNotReadyRefusesCreation() = runTest {
        val readiness = FakeReadinessPort(
            readiness = EnvironmentReadiness(
                state = EnvironmentReadinessState.SERVICE_UNREACHABLE,
                failingCheck = "LtiRomServer ping failed",
            ),
        )
        val provisioning = FakeProvisioningPort()
        val repo = FakeWorkspaceRepo()
        val snapshotRepo = InMemorySnapshotRepository()
        val useCase = CreateWorkspaceUseCase(readiness, provisioning, repo, snapshotRepo)

        val params = CreateWorkspaceParams(
            name = "Unready Workspace",
            target = target,
        )

        assertFailsWith<EnvironmentNotReadyException> {
            useCase(params).toList()
        }

        assertEquals(0, repo.savedWorkspaces.size)
        assertEquals(0, provisioning.provisionedSpecs.size)
    }

    @Test
    fun testReadyWithoutWorkDirIsEnvironmentNotReady() = runTest {
        val readiness = FakeReadinessPort(EnvironmentReadiness(state = EnvironmentReadinessState.READY))
        val provisioning = FakeProvisioningPort()
        val repo = FakeWorkspaceRepo()
        val snapshotRepo = InMemorySnapshotRepository()
        val useCase = CreateWorkspaceUseCase(readiness, provisioning, repo, snapshotRepo)

        val params = CreateWorkspaceParams(name = "No WorkDir", target = target)

        assertFailsWith<EnvironmentNotReadyException> { useCase(params).toList() }
        assertEquals(0, repo.savedWorkspaces.size)
        assertEquals(0, provisioning.provisionedSpecs.size)
    }
}
