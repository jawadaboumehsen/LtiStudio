/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.creation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.ide.lti.core.domain.ports.EnvironmentReadinessPort
import org.ide.lti.core.domain.ports.KeySource
import org.ide.lti.core.domain.ports.ProvisioningEvent
import org.ide.lti.core.domain.ports.ProvisioningSpec
import org.ide.lti.core.domain.ports.WorkspaceProvisioningPort
import org.ide.lti.core.domain.repository.snapshot.SnapshotRepository
import org.ide.lti.core.domain.repository.target.TargetRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.domain.usecase.workspace.CreateWorkspaceUseCase
import org.ide.lti.core.domain.workspace.WorkspaceManager
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CreateWorkspaceViewModelTest {

    private val target = TargetDevice(
        id = "PQ84P01",
        name = "REDMAGIC Astra",
        codename = "PQ84P01",
        revision = 1,
        availableRegions = listOf(TargetRegion.GLOBAL, TargetRegion.CHINA),
        availableFirmwares = mapOf(
            TargetRegion.GLOBAL to listOf(
                TargetFirmware(
                    version = "10.5.15_GL",
                    buildId = "PQ84P01:15",
                    androidVersion = "15",
                    securityPatch = "2026-02-01",
                    otaUrl = "https://example.com/update.zip",
                    isOfficial = true,
                    isRecommended = true,
                ),
            ),
        ),
        packagePolicy = PackagePolicy(listOf("system"), listOf("boot")),
        socPlatform = "Snapdragon 8 Elite",
        filesystemType = "erofs",
        superPartitionBytes = 17179869184L,
        dynamicPartitions = listOf("system"),
        bootPartitions = listOf("boot"),
        status = TargetStatus.QUALIFIED,
        description = "Official baseline",
    )

    private class FakeTargetRepository(val targets: List<TargetDevice>) : TargetRepository {
        val targetsFlow = MutableStateFlow(targets)
        val selectedTargetFlow = MutableStateFlow(targets.firstOrNull())

        override fun getAvailableTargets(): Flow<List<TargetDevice>> = targetsFlow.asStateFlow()
        override fun getSelectedTarget(): Flow<TargetDevice> =
            flowOf(selectedTargetFlow.value ?: throw NoSuchElementException("No target"))

        override suspend fun selectTarget(targetId: String): Result<TargetDevice> {
            val found = targetsFlow.value.find { it.id == targetId }
            return if (found != null) {
                selectedTargetFlow.value = found
                Result.success(found)
            } else {
                Result.failure(IllegalArgumentException("Target not found: $targetId"))
            }
        }

        override suspend fun addTarget(target: TargetDevice): Result<TargetDevice> = Result.success(target)
        override suspend fun updateTarget(target: TargetDevice): Result<TargetDevice> = Result.success(target)
        override suspend fun deleteTarget(targetId: String): Result<Unit> = Result.success(Unit)
    }

    private class FakeWorkspaceRepo : WorkspaceRepository {
        val workspaces = mutableMapOf<String, Workspace>()
        private val workspacesFlow = MutableStateFlow<List<Workspace>>(emptyList())
        override fun getWorkspaces(): Flow<List<Workspace>> = workspacesFlow.asStateFlow()
        override suspend fun getWorkspace(id: String): Workspace? = workspaces[id]
        override suspend fun saveWorkspace(workspace: Workspace) {
            workspaces[workspace.id] = workspace
            workspacesFlow.value = workspaces.values.toList()
        }
        override suspend fun deleteWorkspace(id: String) {}
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

    private class FakeSnapshotRepository : SnapshotRepository {
        val snapshotsMap = mutableMapOf<String, ConfigurationSnapshot>()
        private val _snapshots = MutableStateFlow<List<ConfigurationSnapshot>>(emptyList())
        override val snapshots: Flow<List<ConfigurationSnapshot>> = _snapshots.asStateFlow()
        override fun snapshotsForWorkspace(workspaceId: String): Flow<List<ConfigurationSnapshot>> =
            _snapshots.map { list -> list.filter { it.workspaceId == workspaceId } }
        override suspend fun getSnapshot(id: String): ConfigurationSnapshot? = snapshotsMap[id]
        override suspend fun saveSnapshot(snapshot: ConfigurationSnapshot) {
            snapshotsMap[snapshot.id] = snapshot
            _snapshots.value = snapshotsMap.values.toList()
        }
    }

    private class FakeProvisioningPort : WorkspaceProvisioningPort {
        val flow = MutableSharedFlow<ProvisioningEvent>(replay = 10)
        var cancelledId: String? = null
        var lastSpec: ProvisioningSpec? = null

        override fun provision(spec: ProvisioningSpec): Flow<ProvisioningEvent> {
            lastSpec = spec
            return flow
        }

        override suspend fun cancel(id: String) {
            cancelledId = id
        }

        override suspend fun writeConfig(workspace: Workspace, snapshot: ConfigurationSnapshot) {}
    }

    private class FakeReadinessPort(var ready: Boolean = true) : EnvironmentReadinessPort {
        override fun observe(): Flow<EnvironmentReadiness> = flowOf(readiness())
        override suspend fun refresh(): EnvironmentReadiness = readiness()
        override suspend fun forWorkspace(workspace: Workspace): EnvironmentReadiness = refresh()

        private fun readiness(): EnvironmentReadiness = if (ready) {
            EnvironmentReadiness(state = EnvironmentReadinessState.READY, workDirLinuxPath = "/home/lti/LtiRomWorkDir")
        } else {
            EnvironmentReadiness(state = EnvironmentReadinessState.SERVICE_UNREACHABLE)
        }
    }

    @Test
    fun blankNameValidationRejectsCreation() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val targetRepo = FakeTargetRepository(listOf(target))
            val workspaceRepo = FakeWorkspaceRepo()
            val snapshotRepo = FakeSnapshotRepository()
            val provisioningPort = FakeProvisioningPort()
            val readinessPort = FakeReadinessPort(ready = true)
            val useCase = CreateWorkspaceUseCase(readinessPort, provisioningPort, workspaceRepo, snapshotRepo)

            val viewModel = CreateWorkspaceViewModel(useCase, targetRepo, workspaceRepo)
            advanceUntilIdle()

            viewModel.setName("   ")
            viewModel.startCreation()
            advanceUntilIdle()

            assertNotNull(viewModel.uiState.value.nameError)
            assertFalse(viewModel.uiState.value.isProvisioning)
            assertNull(provisioningPort.lastSpec)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun targetRequiredRejectsCreationWhenNoTargetSelected() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val targetRepo = FakeTargetRepository(emptyList())
            val workspaceRepo = FakeWorkspaceRepo()
            val snapshotRepo = FakeSnapshotRepository()
            val provisioningPort = FakeProvisioningPort()
            val readinessPort = FakeReadinessPort(ready = true)
            val useCase = CreateWorkspaceUseCase(readinessPort, provisioningPort, workspaceRepo, snapshotRepo)

            val viewModel = CreateWorkspaceViewModel(useCase, targetRepo, workspaceRepo)
            advanceUntilIdle()

            viewModel.setName("MyWorkspace")
            viewModel.startCreation()
            advanceUntilIdle()

            assertNotNull(viewModel.uiState.value.errorMessage)
            assertFalse(viewModel.uiState.value.isProvisioning)
            assertNull(provisioningPort.lastSpec)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun keyChoiceForwardedToUseCase() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val targetRepo = FakeTargetRepository(listOf(target))
            val workspaceRepo = FakeWorkspaceRepo()
            val snapshotRepo = FakeSnapshotRepository()
            val provisioningPort = FakeProvisioningPort()
            val readinessPort = FakeReadinessPort(ready = true)
            val useCase = CreateWorkspaceUseCase(readinessPort, provisioningPort, workspaceRepo, snapshotRepo)

            val viewModel = CreateWorkspaceViewModel(useCase, targetRepo, workspaceRepo)
            advanceUntilIdle()

            viewModel.setName("GlobalKeyWorkspace")
            viewModel.setKeySource(KeySource.COPY_GLOBAL)
            viewModel.startCreation()
            advanceUntilIdle()

            assertNotNull(provisioningPort.lastSpec)
            assertEquals(KeySource.COPY_GLOBAL, provisioningPort.lastSpec?.keySource)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun stepperAdvancesThroughAllStagesAndSuccessNavigatesToWorkspace() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val targetRepo = FakeTargetRepository(listOf(target))
            val workspaceRepo = FakeWorkspaceRepo()
            val snapshotRepo = FakeSnapshotRepository()
            val provisioningPort = FakeProvisioningPort()
            val readinessPort = FakeReadinessPort(ready = true)
            val workspaceManager = WorkspaceManager(workspaceRepo, CoroutineScope(Dispatchers.Unconfined))
            val useCase = CreateWorkspaceUseCase(readinessPort, provisioningPort, workspaceRepo, snapshotRepo)

            val viewModel = CreateWorkspaceViewModel(useCase, targetRepo, workspaceRepo, workspaceManager)
            advanceUntilIdle()

            viewModel.setName("StepWorkspace")
            viewModel.startCreation()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isProvisioning)

            // Step: layout
            provisioningPort.flow.emit(ProvisioningEvent.Step("layout", "Creating directory layout"))
            advanceUntilIdle()
            assertEquals(CreationStage.LAYOUT, viewModel.uiState.value.currentStage)

            // Step: config
            provisioningPort.flow.emit(ProvisioningEvent.Step("config", "Writing configuration"))
            advanceUntilIdle()
            assertEquals(CreationStage.CONFIG, viewModel.uiState.value.currentStage)

            // Step: key
            provisioningPort.flow.emit(ProvisioningEvent.Step("key", "Provisioning keys"))
            advanceUntilIdle()
            assertEquals(CreationStage.KEY, viewModel.uiState.value.currentStage)

            // Step: validate
            provisioningPort.flow.emit(ProvisioningEvent.Step("validate", "Validating workspace"))
            advanceUntilIdle()
            assertEquals(CreationStage.VALIDATE, viewModel.uiState.value.currentStage)

            // Step: promote
            provisioningPort.flow.emit(ProvisioningEvent.Step("promote", "Promoting workspace"))
            advanceUntilIdle()
            assertEquals(CreationStage.PROMOTE, viewModel.uiState.value.currentStage)

            // Completed
            provisioningPort.flow.emit(ProvisioningEvent.Completed(WorkspaceKeys("a", "b", "c", "d")))
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isProvisioning)
            assertNotNull(viewModel.uiState.value.createdWorkspace)
            assertEquals("StepWorkspace", viewModel.uiState.value.createdWorkspace?.name)
            assertEquals("StepWorkspace", workspaceManager.currentWorkspace.value?.name)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun cancelInvokesUseCaseCancel() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val targetRepo = FakeTargetRepository(listOf(target))
            val workspaceRepo = FakeWorkspaceRepo()
            val snapshotRepo = FakeSnapshotRepository()
            val provisioningPort = FakeProvisioningPort()
            val readinessPort = FakeReadinessPort(ready = true)
            val useCase = CreateWorkspaceUseCase(readinessPort, provisioningPort, workspaceRepo, snapshotRepo)

            val viewModel = CreateWorkspaceViewModel(useCase, targetRepo, workspaceRepo)
            advanceUntilIdle()

            viewModel.setName("CancelWorkspace")
            viewModel.startCreation()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isProvisioning)

            viewModel.cancel()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isProvisioning)
            assertNotNull(provisioningPort.cancelledId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun errorLeavesDraftIntact() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val targetRepo = FakeTargetRepository(listOf(target))
            val workspaceRepo = FakeWorkspaceRepo()
            val snapshotRepo = FakeSnapshotRepository()
            val provisioningPort = FakeProvisioningPort()
            val readinessPort = FakeReadinessPort(ready = true)
            val useCase = CreateWorkspaceUseCase(readinessPort, provisioningPort, workspaceRepo, snapshotRepo)

            val viewModel = CreateWorkspaceViewModel(useCase, targetRepo, workspaceRepo)
            advanceUntilIdle()

            viewModel.setName("ErrorWorkspace")
            viewModel.setKeySource(KeySource.COPY_GLOBAL)
            viewModel.startCreation()
            advanceUntilIdle()

            // Fail event emitted
            provisioningPort.flow.emit(ProvisioningEvent.Failed("key", "Failed to generate signing keys"))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isProvisioning)
            assertEquals("Failed to generate signing keys", state.errorMessage)
            // Draft details remain intact
            assertEquals("ErrorWorkspace", state.name)
            assertEquals(target.id, state.selectedTarget?.id)
            assertEquals(KeySource.COPY_GLOBAL, state.keySource)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
