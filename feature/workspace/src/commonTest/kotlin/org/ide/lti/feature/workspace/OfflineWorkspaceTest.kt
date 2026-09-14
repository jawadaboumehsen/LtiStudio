/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.ide.lti.core.domain.pipeline.PipelineOrchestrator
import org.ide.lti.core.domain.pipeline.ToolResult
import org.ide.lti.core.domain.ports.EnvironmentReadinessPort
import org.ide.lti.core.domain.ports.FileStat
import org.ide.lti.core.domain.ports.KeySource
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.ports.ProvisioningEvent
import org.ide.lti.core.domain.ports.ProvisioningSpec
import org.ide.lti.core.domain.ports.RunHandle
import org.ide.lti.core.domain.ports.StepEvent
import org.ide.lti.core.domain.ports.StepStatus
import org.ide.lti.core.domain.ports.VendoredFile
import org.ide.lti.core.domain.ports.VendoredFilePort
import org.ide.lti.core.domain.ports.WorkspaceProvisioningPort
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.domain.repository.snapshot.SnapshotRepository
import org.ide.lti.core.domain.repository.target.TargetRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.domain.usecase.build.ObserveRunUseCase
import org.ide.lti.core.domain.usecase.build.StartPipelineRunUseCase
import org.ide.lti.core.domain.usecase.workspace.CreateWorkspaceUseCase
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.StepCommand
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
import org.ide.lti.feature.workspace.creation.CreateWorkspaceViewModel
import org.ide.lti.feature.workspace.panels.run.BuildRunViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineWorkspaceTest {

    private val testDispatcher = StandardTestDispatcher()

    private val testTarget = TargetDevice(
        id = "PQ84P01",
        name = "REDMAGIC Astra",
        codename = "PQ84P01",
        revision = 1,
        availableRegions = listOf(TargetRegion.GLOBAL),
        availableFirmwares = mapOf(
            TargetRegion.GLOBAL to listOf(
                TargetFirmware(
                    version = "10.5.15_GL",
                    buildId = "PQ84P01:15",
                    androidVersion = "15",
                    securityPatch = "2026-02-01",
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

    private class TestWorkspaceRepository : WorkspaceRepository {
        val workspaces = mutableMapOf<String, Workspace>()
        val sessions = mutableMapOf<String, WorkspaceSession>()
        private val workspacesFlow = MutableStateFlow<List<Workspace>>(emptyList())

        override fun getWorkspaces(): Flow<List<Workspace>> = workspacesFlow.asStateFlow()
        override suspend fun getWorkspace(id: String): Workspace? = workspaces[id]
        override suspend fun saveWorkspace(workspace: Workspace) {
            workspaces[workspace.id] = workspace
            workspacesFlow.value = workspaces.values.toList()
        }
        override suspend fun deleteWorkspace(id: String) {
            workspaces.remove(id)
            workspacesFlow.value = workspaces.values.toList()
        }
        override suspend fun getWorkspaceSession(workspaceId: String): WorkspaceSession? = sessions[workspaceId]
        override suspend fun saveWorkspaceSession(session: WorkspaceSession) {
            sessions[session.workspaceId] = session
        }
        override fun getRecentProjects(): Flow<List<RecentProject>> = emptyFlow()
        override suspend fun addRecentProject(project: RecentProject) {}
        override suspend fun removeRecentProject(workspaceId: String) {}
        override suspend fun clearRecentProjects() {}
        override suspend fun discoverWorkspacesInWorkDir(basePath: String?): List<RecentProject> = emptyList()
        override fun readinessOf(workspace: Workspace): WorkspaceReadiness = WorkspaceReadiness.READY
        override fun observeDecodeWarnings(): Flow<DecodeWarning> = flowOf()
    }

    private class TestSnapshotRepository : SnapshotRepository {
        val snapshotsMap = mutableMapOf<String, ConfigurationSnapshot>()
        private val _flow = MutableStateFlow<List<ConfigurationSnapshot>>(emptyList())
        override val snapshots: Flow<List<ConfigurationSnapshot>> = _flow.asStateFlow()
        override fun snapshotsForWorkspace(workspaceId: String): Flow<List<ConfigurationSnapshot>> = _flow.asStateFlow()
        override suspend fun getSnapshot(id: String): ConfigurationSnapshot? = snapshotsMap[id]
        override suspend fun saveSnapshot(snapshot: ConfigurationSnapshot) {
            snapshotsMap[snapshot.id] = snapshot
            _flow.value = snapshotsMap.values.toList()
        }
    }

    private class TestRunRepository : RunRepository {
        val runs = mutableMapOf<String, BuildRun>()
        private val runsFlow = MutableStateFlow<List<BuildRun>>(emptyList())
        override fun observeRuns(workspaceId: String): Flow<List<BuildRun>> = runsFlow.asStateFlow()
        override suspend fun upsert(run: BuildRun) {
            runs[run.id] = run
            runsFlow.value = runs.values.toList()
        }
        override suspend fun appendEvents(runId: String, events: List<String>) {}
        override suspend fun cachedEvents(runId: String, fromSeq: Long): List<String> = emptyList()
        override suspend fun latestActive(workspaceId: String): BuildRun? =
            runs.values.firstOrNull { it.workspaceId == workspaceId && it.state.isActive }
        override suspend fun getRun(runId: String): BuildRun? = runs[runId]
        override suspend fun claimDriver(runId: String, instanceId: String, now: Long): Boolean = true
        override suspend fun heartbeatDriver(runId: String, instanceId: String, now: Long) {}
    }

    private class TestTargetRepository(val targets: List<TargetDevice>) : TargetRepository {
        private val selectedFlow = MutableStateFlow(targets.first())
        override fun getAvailableTargets(): Flow<List<TargetDevice>> = flowOf(targets)
        override fun getSelectedTarget(): Flow<TargetDevice> = selectedFlow
        override suspend fun selectTarget(targetId: String): Result<TargetDevice> = Result.success(
            targets.first {
                it.id ==
                    targetId
            },
        )
        override suspend fun addTarget(target: TargetDevice): Result<TargetDevice> = Result.success(target)
        override suspend fun updateTarget(target: TargetDevice): Result<TargetDevice> = Result.success(target)
        override suspend fun deleteTarget(targetId: String): Result<Unit> = Result.success(Unit)
    }

    private class TestReadinessPort(initialReadiness: EnvironmentReadiness) : EnvironmentReadinessPort {
        val readinessFlow = MutableStateFlow(initialReadiness)
        var forWorkspaceOverride: EnvironmentReadiness? = null

        override fun observe(): Flow<EnvironmentReadiness> = readinessFlow.asStateFlow()
        override suspend fun refresh(): EnvironmentReadiness = readinessFlow.value
        override suspend fun forWorkspace(workspace: Workspace): EnvironmentReadiness =
            forWorkspaceOverride ?: readinessFlow.value
    }

    private class TestProvisioningPort : WorkspaceProvisioningPort {
        val flow = MutableSharedFlow<ProvisioningEvent>(replay = 10)
        override fun provision(spec: ProvisioningSpec): Flow<ProvisioningEvent> = flow
        override suspend fun cancel(id: String) {}
        override suspend fun writeConfig(workspace: Workspace, snapshot: ConfigurationSnapshot) {}
    }

    private class TestPipelineExecutionPort : PipelineExecutionPort {
        override suspend fun startStep(ws: Workspace, step: StepCommand, idempotencyKey: String): RunHandle =
            RunHandle("step_1", "RUNNING", 1000L)
        override fun observe(serverRunId: String, fromSeq: Long): Flow<StepEvent> = flowOf(StepEvent.Finished(0))
        override suspend fun status(serverRunId: String): StepStatus? = null
        override suspend fun cancel(serverRunId: String): Boolean = true
        override suspend fun uploadFile(ws: Workspace, relPath: String, bytes: ByteArray): Boolean = true
        override suspend fun readFile(ws: Workspace, relPath: String): String? = null
        override suspend fun stat(ws: Workspace, relPath: String): FileStat =
            FileStat(relPath, exists = true, sizeBytes = 1000L)
        override suspend fun execute(ws: Workspace, command: StepCommand): ToolResult = ToolResult(0, "")
        override suspend fun checkAvailableDiskSpace(ws: Workspace): Long? = 50_000_000_000L
    }

    private object NoVendoredFiles : VendoredFilePort {
        override fun load(resourceId: String): VendoredFile? = null
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testServiceUnreachableReadOnlyOpenAndCanStartFalseWithRemediation() = runTest {
        val workspaceRepo = TestWorkspaceRepository()
        val snapshotRepo = TestSnapshotRepository()
        val runRepo = TestRunRepository()
        val targetRepo = TestTargetRepository(listOf(testTarget))
        val executionPort = TestPipelineExecutionPort()

        val unreachableReadiness = EnvironmentReadiness(
            state = EnvironmentReadinessState.SERVICE_UNREACHABLE,
            failingCheck = "Execution service unreachable on WSL port 8080",
            remediation = "Start the WSL daemon using: service ltirom start",
        )
        val readinessPort = TestReadinessPort(unreachableReadiness)

        val orchestrator = PipelineOrchestrator(
            executionPort = executionPort,
            runRepository = runRepo,
            vendoredFiles = NoVendoredFiles,
        )

        val startUseCase = StartPipelineRunUseCase(
            workspaceRepository = workspaceRepo,
            snapshotRepository = snapshotRepo,
            targetRepository = targetRepo,
            readinessPort = readinessPort,
            executionPort = executionPort,
            runRepository = runRepo,
            orchestrator = orchestrator,
            scope = CoroutineScope(testDispatcher),
        )
        val observeUseCase = ObserveRunUseCase(runRepo)
        val workspaceManager = WorkspaceManager(workspaceRepo, CoroutineScope(testDispatcher))

        val snapshot = ConfigurationSnapshot.create(
            id = "snap-1",
            workspaceId = "ws-offline-1",
            profileRevision = 1,
            acquisition = org.ide.lti.core.model.workspace.AcquisitionSettings(
                mode = AcquisitionMode.DOWNLOAD,
                region = TargetRegion.GLOBAL,
                firmware = testTarget.availableFirmwares[TargetRegion.GLOBAL]!!.first(),
            ),
            build = org.ide.lti.core.model.workspace.BuildSettings(
                packagePolicy = testTarget.packagePolicy,
                signing = org.ide.lti.core.model.workspace.SigningPolicy(),
            ),
            createdAt = Instant.fromEpochMilliseconds(1000L),
            release = org.ide.lti.core.model.workspace.ReleaseSettings(otaBaseUrl = "https://example.com/updates"),
        )
        snapshotRepo.saveSnapshot(snapshot)

        val ws = Workspace(
            id = "ws-offline-1",
            name = "Offline Workspace",
            path = "C:\\projects\\ws-offline-1",
            linuxPath = "/home/lti/LtiRomWorkDir/workspaces/ws-offline-1",
            targetBinding = TargetBinding(profileId = testTarget.id, profileRevision = 1),
            effectiveSnapshotId = snapshot.id,
        )
        workspaceRepo.saveWorkspace(ws)

        val viewModel = BuildRunViewModel(
            workspaceManager = workspaceManager,
            startPipelineRunUseCase = startUseCase,
            observeRunUseCase = observeUseCase,
            runRepository = runRepo,
            readinessPort = readinessPort,
        )

        val job = launch(testDispatcher) { viewModel.uiState.collect() }
        workspaceManager.openWorkspace(ws.id)
        advanceUntilIdle()

        // 1. Workspace is open read-only
        assertEquals(ws, viewModel.uiState.value.currentWorkspace)

        // 2. Start is disabled with failing check & remediation
        assertFalse(viewModel.uiState.value.canStartRun)
        assertTrue(viewModel.uiState.value.isRunDisabled)
        assertEquals("Execution service unreachable on WSL port 8080", viewModel.uiState.value.failingCheck)
        assertEquals("Start the WSL daemon using: service ltirom start", viewModel.uiState.value.remediation)
        assertEquals(EnvironmentReadinessState.SERVICE_UNREACHABLE, viewModel.uiState.value.environmentReadiness?.state)

        // 3. Triggering start does nothing
        viewModel.startBuild()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isRunning)
        assertEquals(0, runRepo.runs.size)

        job.cancel()
    }

    @Test
    fun testDraftPreservedAcrossReadinessTransitionsInCreateWorkspace() = runTest {
        val workspaceRepo = TestWorkspaceRepository()
        val snapshotRepo = TestSnapshotRepository()
        val targetRepo = TestTargetRepository(listOf(testTarget))
        val provisioningPort = TestProvisioningPort()

        val initialUnreachable = EnvironmentReadiness(
            state = EnvironmentReadinessState.SERVICE_UNREACHABLE,
            failingCheck = "Execution service unreachable",
            remediation = "Start daemon or verify WSL distro",
        )
        val readinessPort = TestReadinessPort(initialUnreachable)

        val createUseCase = CreateWorkspaceUseCase(
            readinessPort = readinessPort,
            provisioningPort = provisioningPort,
            workspaceRepository = workspaceRepo,
            snapshotRepository = snapshotRepo,
        )

        val viewModel = CreateWorkspaceViewModel(
            createWorkspaceUseCase = createUseCase,
            targetRepository = targetRepo,
            workspaceRepository = workspaceRepo,
            readinessPort = readinessPort,
        )

        val job = launch(testDispatcher) { viewModel.uiState.collect() }
        advanceUntilIdle()

        // User enters draft while environment is unreachable
        viewModel.setName("my-custom-rom-draft")
        viewModel.selectTarget(testTarget)
        viewModel.setKeySource(KeySource.COPY_GLOBAL)
        advanceUntilIdle()

        // Verify draft is set and creation is gated
        assertFalse(viewModel.uiState.value.canCreate)
        assertEquals("my-custom-rom-draft", viewModel.uiState.value.name)
        assertEquals(testTarget, viewModel.uiState.value.selectedTarget)
        assertEquals(KeySource.COPY_GLOBAL, viewModel.uiState.value.keySource)
        assertEquals("Execution service unreachable", viewModel.uiState.value.failingCheck)
        assertEquals("Start daemon or verify WSL distro", viewModel.uiState.value.remediation)

        // Attempting to create while not ready is rejected
        viewModel.startCreation()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isProvisioning)

        // Now daemon comes online: readiness transitions to READY
        readinessPort.readinessFlow.value = EnvironmentReadiness(
            state = EnvironmentReadinessState.READY,
            workDirLinuxPath = "/home/lti/LtiRomWorkDir",
        )
        advanceUntilIdle()

        // Verify that draft inputs are strictly preserved across readiness transition
        assertTrue(viewModel.uiState.value.canCreate)
        assertNull(viewModel.uiState.value.failingCheck)
        assertNull(viewModel.uiState.value.remediation)
        assertEquals("my-custom-rom-draft", viewModel.uiState.value.name)
        assertEquals(testTarget, viewModel.uiState.value.selectedTarget)
        assertEquals(KeySource.COPY_GLOBAL, viewModel.uiState.value.keySource)

        job.cancel()
    }

    @Test
    fun testToolsMissingBlocksStartWithRemediation() = runTest {
        val workspaceRepo = TestWorkspaceRepository()
        val snapshotRepo = TestSnapshotRepository()
        val runRepo = TestRunRepository()
        val targetRepo = TestTargetRepository(listOf(testTarget))
        val executionPort = TestPipelineExecutionPort()

        val toolsMissingReadiness = EnvironmentReadiness(
            state = EnvironmentReadinessState.TOOLS_MISSING,
            failingCheck = "Required tool missing: mkfs.erofs",
            remediation = "Install erofs-utils via: apt-get install -y erofs-utils",
            missingToolIds = setOf("mkfs.erofs"),
        )
        val readinessPort = TestReadinessPort(toolsMissingReadiness)

        val orchestrator = PipelineOrchestrator(
            executionPort = executionPort,
            runRepository = runRepo,
            vendoredFiles = NoVendoredFiles,
        )

        val startUseCase = StartPipelineRunUseCase(
            workspaceRepository = workspaceRepo,
            snapshotRepository = snapshotRepo,
            targetRepository = targetRepo,
            readinessPort = readinessPort,
            executionPort = executionPort,
            runRepository = runRepo,
            orchestrator = orchestrator,
            scope = CoroutineScope(testDispatcher),
        )
        val observeUseCase = ObserveRunUseCase(runRepo)
        val workspaceManager = WorkspaceManager(workspaceRepo, CoroutineScope(testDispatcher))

        val snapshot = ConfigurationSnapshot.create(
            id = "snap-2",
            workspaceId = "ws-tools-missing",
            profileRevision = 1,
            acquisition = org.ide.lti.core.model.workspace.AcquisitionSettings(
                mode = AcquisitionMode.DOWNLOAD,
                region = TargetRegion.GLOBAL,
                firmware = testTarget.availableFirmwares[TargetRegion.GLOBAL]!!.first(),
            ),
            build = org.ide.lti.core.model.workspace.BuildSettings(
                packagePolicy = testTarget.packagePolicy,
                signing = org.ide.lti.core.model.workspace.SigningPolicy(),
            ),
            createdAt = Instant.fromEpochMilliseconds(2000L),
            release = org.ide.lti.core.model.workspace.ReleaseSettings(otaBaseUrl = "https://example.com/updates"),
        )
        snapshotRepo.saveSnapshot(snapshot)

        val ws = Workspace(
            id = "ws-tools-missing",
            name = "Tools Missing Workspace",
            path = "C:\\projects\\ws-tools-missing",
            linuxPath = "/home/lti/LtiRomWorkDir/workspaces/ws-tools-missing",
            targetBinding = TargetBinding(profileId = testTarget.id, profileRevision = 1),
            effectiveSnapshotId = snapshot.id,
        )
        workspaceRepo.saveWorkspace(ws)

        val viewModel = BuildRunViewModel(
            workspaceManager = workspaceManager,
            startPipelineRunUseCase = startUseCase,
            observeRunUseCase = observeUseCase,
            runRepository = runRepo,
            readinessPort = readinessPort,
        )

        val job = launch(testDispatcher) { viewModel.uiState.collect() }
        workspaceManager.openWorkspace(ws.id)
        advanceUntilIdle()

        // Verify TOOLS_MISSING blocks start with proper remediation and missingToolIds
        assertFalse(viewModel.uiState.value.canStartRun)
        assertTrue(viewModel.uiState.value.isRunDisabled)
        assertEquals("Required tool missing: mkfs.erofs", viewModel.uiState.value.failingCheck)
        assertEquals("Install erofs-utils via: apt-get install -y erofs-utils", viewModel.uiState.value.remediation)
        assertEquals(setOf("mkfs.erofs"), viewModel.uiState.value.environmentReadiness?.missingToolIds)

        // Triggering build does nothing
        viewModel.startBuild()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isRunning)
        assertEquals(0, runRepo.runs.size)

        job.cancel()
    }
}
