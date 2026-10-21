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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
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
import org.ide.lti.core.domain.ports.SnapshotMaterializationIntent
import org.ide.lti.core.domain.ports.SnapshotMaterializerPort
import org.ide.lti.core.domain.ports.WorkspaceAdmissionPort
import org.ide.lti.core.domain.ports.WorkspaceProvisioningPort
import org.ide.lti.core.domain.ports.WorkspaceWriteLease
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.domain.repository.snapshot.SnapshotRepository
import org.ide.lti.core.domain.repository.target.WorkspaceDeviceRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.domain.usecase.build.ObserveRunUseCase
import org.ide.lti.core.domain.usecase.workspace.SaveConfigurationSnapshotUseCase
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    /** One device for any workspace id; saves are kept so a test can read them back. */
    private class FakeWorkspaceDevices(private val device: TargetDevice? = null) : WorkspaceDeviceRepository {
        private val saved = MutableStateFlow<Map<String, TargetDevice>>(emptyMap())

        override fun observe(workspaceId: String): Flow<TargetDevice?> = saved.map { it[workspaceId] ?: device }

        override suspend fun get(workspaceId: String): TargetDevice? = saved.value[workspaceId] ?: device

        override suspend fun save(workspaceId: String, device: TargetDevice): TargetDevice {
            saved.value = saved.value + (workspaceId to device)
            return device
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
            val targetRepo = FakeWorkspaceDevices(target)
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
                workspaceDeviceRepository = targetRepo,
                saveConfigurationSnapshotUseCase = saveUseCase,
            )

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNotNull(state.draft)
            assertEquals(TargetRegion.GLOBAL, state.draft?.region)
            assertFalse(state.isDirty)

            // Local edit of something this panel owns
            viewModel.setBuildType("userdebug")
            assertTrue(viewModel.uiState.value.isDirty)
            assertEquals("user", viewModel.uiState.value.initialSnapshot?.assembly?.buildType)

            // Save
            viewModel.saveConfiguration()
            advanceUntilIdle()

            val saved = viewModel.uiState.value.initialSnapshot
            assertFalse(viewModel.uiState.value.isDirty)
            assertEquals("userdebug", saved?.assembly?.buildType)
            // What the stages own is kept as they saved it, not rewritten from this panel's draft
            assertEquals(TargetRegion.GLOBAL, saved?.acquisition?.region)
            assertEquals("https://example.com", saved?.release?.otaBaseUrl)
            assertEquals(1, provisioningPort.uploadedConfigs.size)
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
            val targetRepo = FakeWorkspaceDevices(target)
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
                workspaceDeviceRepository = targetRepo,
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

    private val deviceSnapshots = FakeSnapshotRepository()

    private suspend fun deviceFixture(
        runs: FakeRunRepository = FakeRunRepository(),
        withDevice: Boolean = true,
    ): Triple<WorkspaceDeviceViewModel, FakeWorkspaceRepo, FakeWorkspaceDevices> {
        val workspaceRepo = FakeWorkspaceRepo()
        val snapshotRepo = deviceSnapshots
        val firmware = target.availableFirmwares[TargetRegion.GLOBAL]!!.first().copy(
            acquisitionUrl = "https://example.com/fw.zip",
            sha256 = "a".repeat(64),
        )
        snapshotRepo.saveSnapshot(
            ConfigurationSnapshot.create(
                id = "snap-1",
                workspaceId = "ws-1",
                profileRevision = 1,
                acquisition = org.ide.lti.core.model.workspace.AcquisitionSettings(
                    region = TargetRegion.GLOBAL,
                    firmware = firmware,
                ),
                assembly = org.ide.lti.core.model.workspace.AssemblySettings(buildType = "user", romVersion = "1.0"),
                release = org.ide.lti.core.model.workspace.ReleaseSettings(otaBaseUrl = "https://example.com"),
                createdAt = Clock.System.now(),
            ),
        )
        val ws = Workspace(
            id = "ws-1",
            name = "Test Workspace",
            path = "/w",
            linuxPath = "/w",
            type = WorkspaceType.REMOTE_WSL,
            targetBinding = TargetBinding("ws-1", 1),
            effectiveSnapshotId = "snap-1",
        )
        workspaceRepo.saveWorkspace(ws)
        val manager = WorkspaceManager(workspaceRepo, CoroutineScope(Dispatchers.Unconfined))
        manager.setCurrentWorkspace(ws)
        val devices = FakeWorkspaceDevices(if (withDevice) target else null)
        val viewModel = WorkspaceDeviceViewModel(manager, devices, snapshotRepo, ObserveRunUseCase(runs))
        return Triple(viewModel, workspaceRepo, devices)
    }

    @Test
    fun deviceEditsShowTheirProblemAndSaveStoresThemForThisWorkspace() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val (viewModel, workspaceRepo, devices) = deviceFixture()
            advanceUntilIdle()
            assertEquals(emptyList(), viewModel.uiState.value.issues, "the starting device is ready")

            viewModel.update { it.copy(superGroupBytes = it.superPartitionBytes + 1) }
            assertEquals(listOf("superGroupBytes"), viewModel.uiState.value.issues.map { it.fieldPath })
            assertTrue(viewModel.uiState.value.isDirty)

            viewModel.update { it.copy(superGroupBytes = it.superPartitionBytes, fastbootProduct = "sun2") }
            viewModel.save()
            advanceUntilIdle()

            assertEquals("sun2", devices.get("ws-1")?.fastbootProduct)
            assertEquals(false, viewModel.uiState.value.isDirty)
            assertEquals("ws-1", workspaceRepo.workspaces["ws-1"]?.targetBinding?.profileId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun theDeviceIsReadOnlyWhileARunIsActive() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val runs = FakeRunRepository()
            runs.upsert(
                BuildRun(
                    id = "run-1",
                    workspaceId = "ws-1",
                    snapshotId = "snap-1",
                    state = RunState.RUNNING,
                    startedAt = Clock.System.now(),
                ),
            )
            val (viewModel, _, devices) = deviceFixture(runs)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.locked)

            viewModel.update { it.copy(name = "changed while running") }
            viewModel.save()
            advanceUntilIdle()

            assertEquals(target.name, viewModel.uiState.value.draft?.name, "edits are ignored")
            assertNull(devices.get("ws-1")?.takeIf { it.name == "changed while running" })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun discardReturnsToTheSavedDeviceAndResyncsTheFields() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val (viewModel, _, _) = deviceFixture()
            advanceUntilIdle()
            val before = viewModel.uiState.value.resetCount

            viewModel.update { it.copy(name = "draft") }
            viewModel.discard()

            assertEquals(target.name, viewModel.uiState.value.draft?.name)
            assertEquals(false, viewModel.uiState.value.isDirty)
            assertTrue(viewModel.uiState.value.resetCount > before)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun aWorkspaceWithoutADeviceStartsFromACopyOfATemplate() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val (viewModel, workspaceRepo, devices) = deviceFixture(withDevice = false)
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.draft, "no device yet")

            viewModel.startFromTemplate(target)
            advanceUntilIdle()

            assertEquals(target, devices.get("ws-1"))
            assertEquals(target, viewModel.uiState.value.draft)
            assertEquals("ws-1", workspaceRepo.workspaces["ws-1"]?.targetBinding?.profileId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun aNewlySavedConfigurationIsCheckedWithoutReopeningTheWorkspace() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val (viewModel, workspaceRepo, _) = deviceFixture()
            advanceUntilIdle()
            assertEquals(emptyList(), viewModel.uiState.value.issues)

            val saved = deviceSnapshots.getSnapshot("snap-1")!!
            val noUrl = saved.acquisition.firmware.copy(acquisitionUrl = null)
            val broken = saved.copy(id = "snap-2", acquisition = saved.acquisition.copy(firmware = noUrl))
            deviceSnapshots.saveSnapshot(broken)
            workspaceRepo.saveWorkspace(workspaceRepo.workspaces.getValue("ws-1").copy(effectiveSnapshotId = "snap-2"))
            advanceUntilIdle()

            assertEquals(listOf("firmware.acquisitionUrl"), viewModel.uiState.value.issues.map { it.fieldPath })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun anUnreadableDeviceFileIsReportedAndNeverReplacedByATemplate() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val workspaceRepo = FakeWorkspaceRepo()
            val ws = Workspace(id = "ws-1", name = "W", path = "/w", linuxPath = "/w", type = WorkspaceType.REMOTE_WSL)
            workspaceRepo.saveWorkspace(ws)
            val manager = WorkspaceManager(workspaceRepo, CoroutineScope(Dispatchers.Unconfined))
            manager.setCurrentWorkspace(ws)
            var writes = 0
            val unreadable = object : WorkspaceDeviceRepository {
                override fun observe(workspaceId: String): Flow<TargetDevice?> = flow { error("service down") }
                override suspend fun get(workspaceId: String): TargetDevice? = error("service down")
                override suspend fun save(workspaceId: String, device: TargetDevice): TargetDevice {
                    writes++
                    return device
                }
            }
            val viewModel = WorkspaceDeviceViewModel(manager, unreadable, FakeSnapshotRepository())
            advanceUntilIdle()

            assertEquals("service down", viewModel.uiState.value.loadError)
            viewModel.startFromTemplate(target)
            advanceUntilIdle()
            assertEquals(0, writes, "an existing but unreadable device.json is never overwritten")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun theTargetCardFollowsADeviceCreatedFromATemplate() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val workspaceRepo = FakeWorkspaceRepo()
            val snapshotRepo = FakeSnapshotRepository()
            val ws = Workspace(id = "ws-1", name = "W", path = "/w", linuxPath = "/w", type = WorkspaceType.REMOTE_WSL)
            workspaceRepo.saveWorkspace(ws)
            val manager = WorkspaceManager(workspaceRepo, CoroutineScope(Dispatchers.Unconfined))
            manager.setCurrentWorkspace(ws)
            val devices = FakeWorkspaceDevices(null)
            val saveUseCase = SaveConfigurationSnapshotUseCase(
                workspaceRepo,
                snapshotRepo,
                FakeRunRepository(),
                FakeProvisioningPort(),
                FakeWorkspaceAdmissionPort(),
                FakeSnapshotMaterializerPort(),
            )
            val viewModel = WorkspaceConfigurationViewModel(manager, snapshotRepo, devices, saveUseCase)
            advanceUntilIdle()
            assertEquals(emptyMap(), viewModel.uiState.value.availableFirmwares, "no device, nothing to offer")

            devices.save("ws-1", target)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(target, state.currentTarget)
            assertEquals(target.availableFirmwares, state.availableFirmwares)
            assertFalse(state.isDirty, "firmware is chosen in the Acquire stage, never changed here")
        } finally {
            Dispatchers.resetMain()
        }
    }
}
