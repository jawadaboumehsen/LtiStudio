/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.panels.target

import io.github.vinceglb.filekit.core.PlatformFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import org.ide.lti.core.domain.ports.AdmissionResult
import org.ide.lti.core.domain.ports.EnvironmentReadinessPort
import org.ide.lti.core.domain.ports.LeaseReconciliation
import org.ide.lti.core.domain.ports.MutationKind
import org.ide.lti.core.domain.ports.ProvisioningEvent
import org.ide.lti.core.domain.ports.ProvisioningSpec
import org.ide.lti.core.domain.ports.RemoteFileUploaderPort
import org.ide.lti.core.domain.ports.SnapshotMaterializationIntent
import org.ide.lti.core.domain.ports.SnapshotMaterializerPort
import org.ide.lti.core.domain.ports.WorkspaceAdmissionPort
import org.ide.lti.core.domain.ports.WorkspaceProvisioningPort
import org.ide.lti.core.domain.ports.WorkspaceWriteLease
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.domain.repository.snapshot.SnapshotRepository
import org.ide.lti.core.domain.repository.target.TargetRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.domain.usecase.workspace.SaveConfigurationSnapshotUseCase
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.setup.EnvironmentReadiness
import org.ide.lti.core.model.setup.EnvironmentReadinessState
import org.ide.lti.core.model.setup.WorkspaceReadiness
import org.ide.lti.core.model.target.PackagePolicy
import org.ide.lti.core.model.target.TargetBinding
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetFirmware
import org.ide.lti.core.model.target.TargetRegion
import org.ide.lti.core.model.target.TargetStatus
import org.ide.lti.core.model.workspace.AcquisitionMode
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.DecodeWarning
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceSession
import org.ide.lti.core.model.workspace.WorkspaceType
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceConfigurationViewModelTest {

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
                ),
            ),
            TargetRegion.CHINA to listOf(
                TargetFirmware(
                    version = "10.5.15_CN",
                    buildId = "PQ84P01:15",
                    androidVersion = "15",
                    securityPatch = "2026-02-01",
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
        val selectedTargetFlow = MutableStateFlow(targets.first())

        override fun getAvailableTargets(): Flow<List<TargetDevice>> = targetsFlow.asStateFlow()
        override fun getSelectedTarget(): Flow<TargetDevice> = selectedTargetFlow.asStateFlow()
        override suspend fun selectTarget(targetId: String): Result<TargetDevice> {
            val found = targetsFlow.value.find { it.id == targetId }
            return if (found != null) {
                selectedTargetFlow.value = found
                Result.success(found)
            } else {
                Result.failure(IllegalArgumentException("Target not found: $targetId"))
            }
        }
        override suspend fun addTarget(target: TargetDevice): Result<TargetDevice> {
            val list = targetsFlow.value.toMutableList()
            list.removeAll { it.id == target.id }
            list.add(target)
            targetsFlow.value = list
            return Result.success(target)
        }
        override suspend fun updateTarget(target: TargetDevice): Result<TargetDevice> = addTarget(target)
        override suspend fun deleteTarget(targetId: String): Result<Unit> {
            val list = targetsFlow.value.toMutableList()
            list.removeAll { it.id == targetId }
            targetsFlow.value = list
            return Result.success(Unit)
        }
    }

    private class FakeSnapshotRepository : SnapshotRepository {
        val snapshotsMap = mutableMapOf<String, ConfigurationSnapshot>()
        private val snapshotsFlow = MutableStateFlow<List<ConfigurationSnapshot>>(emptyList())
        override val snapshots: Flow<List<ConfigurationSnapshot>> = snapshotsFlow.asStateFlow()

        override fun snapshotsForWorkspace(workspaceId: String): Flow<List<ConfigurationSnapshot>> =
            snapshotsFlow.map { list -> list.filter { it.workspaceId == workspaceId } }

        override suspend fun getSnapshot(id: String): ConfigurationSnapshot? = snapshotsMap[id]

        override suspend fun saveSnapshot(snapshot: ConfigurationSnapshot) {
            snapshotsMap[snapshot.id] = snapshot
            snapshotsFlow.value = snapshotsMap.values.toList()
        }
    }

    private class FakeRunRepository : RunRepository {
        val runs = mutableMapOf<String, BuildRun>()
        var activeRun: BuildRun? = null

        override fun observeRuns(workspaceId: String): Flow<List<BuildRun>> =
            flowOf(runs.values.filter { it.workspaceId == workspaceId })
        override suspend fun upsert(run: BuildRun) {
            runs[run.id] = run
        }
        override suspend fun appendEvents(runId: String, events: List<String>) {}
        override suspend fun cachedEvents(runId: String, fromSeq: Long): List<String> = emptyList()
        override suspend fun latestActive(workspaceId: String): BuildRun? = activeRun
        override suspend fun getRun(runId: String): BuildRun? = runs[runId]
        override suspend fun claimDriver(runId: String, instanceId: String, now: Long): Boolean = true
        override suspend fun heartbeatDriver(runId: String, instanceId: String, now: Long) {}
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

    private class FakeProvisioningPort : WorkspaceProvisioningPort {
        val uploadedConfigs = mutableListOf<Pair<Workspace, ConfigurationSnapshot>>()
        override fun provision(spec: ProvisioningSpec): Flow<ProvisioningEvent> = emptyFlow()
        override suspend fun cancel(id: String) {}
        override suspend fun writeConfig(workspace: Workspace, snapshot: ConfigurationSnapshot) {
            uploadedConfigs.add(workspace to snapshot)
        }
    }

    private class FakeWorkspaceAdmissionPort : WorkspaceAdmissionPort {
        override suspend fun tryAcquire(
            workspaceId: String,
            kind: MutationKind,
            operationId: String,
            expectedRevision: Int?,
            currentRevision: Int?,
        ): AdmissionResult = AdmissionResult.Admitted(
            WorkspaceWriteLease(
                workspaceId = workspaceId,
                ownerKind = kind,
                operationId = operationId,
                expectedRevision = expectedRevision,
                acquiredAtEpochMs = 0L,
                durable = false,
            ),
        )
        override suspend fun release(lease: WorkspaceWriteLease) {}
        override suspend fun currentOwner(workspaceId: String): WorkspaceWriteLease? = null
        override suspend fun unreconciledLeases(): List<WorkspaceWriteLease> = emptyList()
        override suspend fun reconcile(lease: WorkspaceWriteLease, outcome: LeaseReconciliation) {}
    }

    private class FakeSnapshotMaterializerPort : SnapshotMaterializerPort {
        override suspend fun markMaterializing(workspaceId: String, snapshotId: String) {}
        override suspend fun clearIntent(workspaceId: String, snapshotId: String) {}
        override suspend fun findIncompleteMaterializations(): List<SnapshotMaterializationIntent> = emptyList()
    }

    private class FakeTransport : RemoteFileUploaderPort {
        val uploadedFiles = mutableMapOf<String, ByteArray>()
        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean {
            uploadedFiles[remotePath] = content
            return true
        }
    }

    private class DynamicReadinessPort : EnvironmentReadinessPort {
        val flow = MutableSharedFlow<EnvironmentReadiness>(replay = 1)
        override fun observe(): Flow<EnvironmentReadiness> = flow
        override suspend fun refresh() = EnvironmentReadiness(state = EnvironmentReadinessState.READY)
        override suspend fun forWorkspace(workspace: Workspace) =
            EnvironmentReadiness(state = EnvironmentReadinessState.READY)
    }

    @Test
    fun draftEditsAreLocalUntilSaveAndSaveCallsUseCaseOnce() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val workspaceRepo = FakeWorkspaceRepo()
            val snapshotRepo = FakeSnapshotRepository()
            val runRepo = FakeRunRepository()
            val provisioningPort = FakeProvisioningPort()
            val targetRepo = FakeTargetRepository(listOf(target))
            val saveUseCase = SaveConfigurationSnapshotUseCase(
                workspaceRepo,
                snapshotRepo,
                runRepo,
                provisioningPort,
                FakeWorkspaceAdmissionPort(),
                FakeSnapshotMaterializerPort(),
            )
            val workspaceManager = WorkspaceManager(workspaceRepo, CoroutineScope(Dispatchers.Unconfined))

            val initialSnapshot = ConfigurationSnapshot.create(
                id = "snap-1",
                workspaceId = "ws-1",
                profileRevision = 1,
                acquisition = org.ide.lti.core.model.workspace.AcquisitionSettings(
                    mode = AcquisitionMode.DOWNLOAD,
                    region = TargetRegion.GLOBAL,
                    firmware = target.availableFirmwares[TargetRegion.GLOBAL]!!.first(),
                ),
                build = org.ide.lti.core.model.workspace.BuildSettings(
                    packagePolicy = target.packagePolicy!!,
                    signing = org.ide.lti.core.model.workspace.SigningPolicy(),
                ),
                assembly = org.ide.lti.core.model.workspace.AssemblySettings(buildType = "user", romVersion = "1.0"),
                release = org.ide.lti.core.model.workspace.ReleaseSettings(otaBaseUrl = "https://example.com"),
                createdAt = Clock.System.now(),
            )
            snapshotRepo.saveSnapshot(initialSnapshot)

            val ws = Workspace(
                id = "ws-1",
                name = "Test Workspace",
                path = "/home/lti/LtiRomWorkDir/workspaces/test-ws",
                linuxPath = "/home/lti/LtiRomWorkDir/workspaces/test-ws",
                type = WorkspaceType.REMOTE_WSL,
                targetBinding = TargetBinding("PQ84P01", 1),
                layoutVersion = 1,
                effectiveSnapshotId = "snap-1",
            )
            workspaceRepo.saveWorkspace(ws)
            workspaceManager.setCurrentWorkspace(ws)

            val viewModel = WorkspaceConfigurationViewModel(
                workspaceManager = workspaceManager,
                snapshotRepository = snapshotRepo,
                targetRepository = targetRepo,
                saveConfigurationSnapshotUseCase = saveUseCase,
            )

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNotNull(state.draft)
            assertEquals(TargetRegion.GLOBAL, state.draft?.region)
            assertFalse(state.isDirty)

            // Local edit: switch to CN
            viewModel.updateDraftRegion(TargetRegion.CHINA)
            assertTrue(viewModel.uiState.value.isDirty)
            assertEquals(TargetRegion.CHINA, viewModel.uiState.value.draft?.region)
            // initial snapshot is still GLOBAL
            assertEquals(TargetRegion.GLOBAL, viewModel.uiState.value.initialSnapshot?.acquisition?.region)

            // Save
            viewModel.saveConfiguration()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isDirty)
            assertEquals(TargetRegion.CHINA, viewModel.uiState.value.initialSnapshot?.acquisition?.region)
            assertEquals(1, provisioningPort.uploadedConfigs.size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun importArchiveModeUploadsFileViaTransport() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val workspaceRepo = FakeWorkspaceRepo()
            val snapshotRepo = FakeSnapshotRepository()
            val runRepo = FakeRunRepository()
            val provisioningPort = FakeProvisioningPort()
            val targetRepo = FakeTargetRepository(listOf(target))
            val transport = FakeTransport()
            val saveUseCase = SaveConfigurationSnapshotUseCase(
                workspaceRepo,
                snapshotRepo,
                runRepo,
                provisioningPort,
                FakeWorkspaceAdmissionPort(),
                FakeSnapshotMaterializerPort(),
            )
            val workspaceManager = WorkspaceManager(workspaceRepo, CoroutineScope(Dispatchers.Unconfined))

            val ws = Workspace(
                id = "ws-import",
                name = "Import WS",
                path = "/home/lti/LtiRomWorkDir/workspaces/import-ws",
                linuxPath = "/home/lti/LtiRomWorkDir/workspaces/import-ws",
                targetBinding = TargetBinding("PQ84P01", 1),
            )
            workspaceRepo.saveWorkspace(ws)
            workspaceManager.setCurrentWorkspace(ws)

            val tempFile = File.createTempFile("test_firmware", ".zip").apply {
                writeBytes("firmware content".toByteArray())
                deleteOnExit()
            }
            val platformFile = PlatformFile(tempFile)

            val viewModel = WorkspaceConfigurationViewModel(
                workspaceManager = workspaceManager,
                snapshotRepository = snapshotRepo,
                targetRepository = targetRepo,
                saveConfigurationSnapshotUseCase = saveUseCase,
                transport = transport,
                fileReader = { tempFile.readBytes() },
            )

            advanceUntilIdle()

            viewModel.pickAndUploadArchive(platformFile)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isDirty)
            assertEquals(AcquisitionMode.IMPORT_ARCHIVE, viewModel.uiState.value.draft?.acquisitionMode)
            assertEquals(platformFile.name, viewModel.uiState.value.draft?.importedArchiveName)

            val expectedRemotePath =
                "/home/lti/LtiRomWorkDir/workspaces/import-ws/firmware/downloaded/${platformFile.name}"
            assertTrue(transport.uploadedFiles.containsKey(expectedRemotePath))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun draftSurvivesReadinessChanges() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val workspaceRepo = FakeWorkspaceRepo()
            val snapshotRepo = FakeSnapshotRepository()
            val runRepo = FakeRunRepository()
            val provisioningPort = FakeProvisioningPort()
            val targetRepo = FakeTargetRepository(listOf(target))
            val readinessPort = DynamicReadinessPort()
            val saveUseCase = SaveConfigurationSnapshotUseCase(
                workspaceRepo,
                snapshotRepo,
                runRepo,
                provisioningPort,
                FakeWorkspaceAdmissionPort(),
                FakeSnapshotMaterializerPort(),
            )
            val workspaceManager = WorkspaceManager(workspaceRepo, CoroutineScope(Dispatchers.Unconfined))

            readinessPort.flow.emit(EnvironmentReadiness(state = EnvironmentReadinessState.READY))

            val ws = Workspace(
                id = "ws-readiness",
                name = "Readiness WS",
                path = "/path",
                linuxPath = "/path",
                targetBinding = TargetBinding("PQ84P01", 1),
            )
            workspaceRepo.saveWorkspace(ws)
            workspaceManager.setCurrentWorkspace(ws)

            val viewModel = WorkspaceConfigurationViewModel(
                workspaceManager = workspaceManager,
                snapshotRepository = snapshotRepo,
                targetRepository = targetRepo,
                saveConfigurationSnapshotUseCase = saveUseCase,
                readinessPort = readinessPort,
            )

            advanceUntilIdle()

            // Edit rom version
            viewModel.setRomVersion("2.5.0")
            assertTrue(viewModel.uiState.value.isDirty)
            assertEquals("2.5.0", viewModel.uiState.value.draft?.romVersion)

            // Environment readiness drops to SERVICE_UNREACHABLE
            readinessPort.flow.emit(EnvironmentReadiness(state = EnvironmentReadinessState.SERVICE_UNREACHABLE))
            advanceUntilIdle()

            assertEquals(EnvironmentReadinessState.SERVICE_UNREACHABLE, viewModel.uiState.value.environmentReadiness)
            // Draft edit is NOT lost (FR-029)
            assertTrue(viewModel.uiState.value.isDirty)
            assertEquals("2.5.0", viewModel.uiState.value.draft?.romVersion)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun invalidOtaBaseUrlBlocksSaveWithMessage() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val workspaceRepo = FakeWorkspaceRepo()
            val snapshotRepo = FakeSnapshotRepository()
            val runRepo = FakeRunRepository()
            val provisioningPort = FakeProvisioningPort()
            val targetRepo = FakeTargetRepository(listOf(target))
            val saveUseCase = SaveConfigurationSnapshotUseCase(
                workspaceRepo,
                snapshotRepo,
                runRepo,
                provisioningPort,
                FakeWorkspaceAdmissionPort(),
                FakeSnapshotMaterializerPort(),
            )
            val workspaceManager = WorkspaceManager(workspaceRepo, CoroutineScope(Dispatchers.Unconfined))

            val ws = Workspace(
                id = "ws-invalid",
                name = "Invalid URL WS",
                path = "/path",
                linuxPath = "/path",
                targetBinding = TargetBinding("PQ84P01", 1),
            )
            workspaceRepo.saveWorkspace(ws)
            workspaceManager.setCurrentWorkspace(ws)

            val viewModel = WorkspaceConfigurationViewModel(
                workspaceManager = workspaceManager,
                snapshotRepository = snapshotRepo,
                targetRepository = targetRepo,
                saveConfigurationSnapshotUseCase = saveUseCase,
            )

            advanceUntilIdle()

            // Insecure HTTP URL
            viewModel.setOtaBaseUrl("http://insecure.com/updates")
            viewModel.saveConfiguration()
            advanceUntilIdle()

            assertNotNull(viewModel.uiState.value.errorMessage)
            assertEquals(0, provisioningPort.uploadedConfigs.size)

            // Trailing slash URL
            viewModel.setOtaBaseUrl("https://secure.com/updates/")
            viewModel.saveConfiguration()
            advanceUntilIdle()

            assertNotNull(viewModel.uiState.value.errorMessage)
            assertEquals(0, provisioningPort.uploadedConfigs.size)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
