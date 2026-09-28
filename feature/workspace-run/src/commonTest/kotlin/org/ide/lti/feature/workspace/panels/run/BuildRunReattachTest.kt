/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.panels.run

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.ide.lti.core.domain.pipeline.PipelineEvent
import org.ide.lti.core.domain.pipeline.PipelineOrchestrator
import org.ide.lti.core.domain.pipeline.ToolResult
import org.ide.lti.core.domain.ports.EnvironmentReadinessPort
import org.ide.lti.core.domain.ports.FileStat
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.ports.RunHandle
import org.ide.lti.core.domain.ports.StepEvent
import org.ide.lti.core.domain.ports.StepStatus
import org.ide.lti.core.domain.ports.VendoredFile
import org.ide.lti.core.domain.ports.VendoredFilePort
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.domain.repository.snapshot.SnapshotRepository
import org.ide.lti.core.domain.repository.target.TargetRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.domain.usecase.build.CancelRunUseCase
import org.ide.lti.core.domain.usecase.build.ObserveRunUseCase
import org.ide.lti.core.domain.usecase.build.ReattachRunUseCase
import org.ide.lti.core.domain.usecase.build.StartPipelineRunUseCase
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.run.Artifact
import org.ide.lti.core.model.run.ArtifactKind
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.run.StageStep
import org.ide.lti.core.model.run.StepCommand
import org.ide.lti.core.model.run.StepState
import org.ide.lti.core.model.run.VerificationState
import org.ide.lti.core.model.setup.EnvironmentReadiness
import org.ide.lti.core.model.setup.EnvironmentReadinessState
import org.ide.lti.core.model.target.PackagePolicy
import org.ide.lti.core.model.target.TargetBinding
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetFirmware
import org.ide.lti.core.model.target.TargetRegion
import org.ide.lti.core.model.target.TargetStatus
import org.ide.lti.core.model.workspace.AcquisitionMode
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.SigningPolicy
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceSession
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BuildRunReattachTest {

    private val testDispatcher = StandardTestDispatcher()

    private object NoVendoredFiles : VendoredFilePort {
        override fun load(resourceId: String): VendoredFile? = null
    }

    private class FakeWorkspaceRepository : WorkspaceRepository {
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
    }

    private class FakeRunRepository : RunRepository {
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

    private class FakeSnapshotRepository : SnapshotRepository {
        val snapshotsMap = mutableMapOf<String, ConfigurationSnapshot>()
        private val snapshotsFlow = MutableStateFlow<List<ConfigurationSnapshot>>(emptyList())
        override val snapshots: Flow<List<ConfigurationSnapshot>> = snapshotsFlow.asStateFlow()
        override fun snapshotsForWorkspace(workspaceId: String): Flow<List<ConfigurationSnapshot>> =
            snapshotsFlow.asStateFlow()
        override suspend fun getSnapshot(id: String): ConfigurationSnapshot? = snapshotsMap[id]
        override suspend fun saveSnapshot(snapshot: ConfigurationSnapshot) {
            snapshotsMap[snapshot.id] = snapshot
            snapshotsFlow.value = snapshotsMap.values.toList()
        }
    }

    private class FakeTargetRepository(val targets: List<TargetDevice>) : TargetRepository {
        private val selectedFlow = MutableStateFlow(targets.first())
        override fun getAvailableTargets(): Flow<List<TargetDevice>> = flowOf(targets)
        override fun getSelectedTarget(): Flow<TargetDevice> = selectedFlow
        override suspend fun selectTarget(targetId: String): Result<TargetDevice> =
            Result.success(targets.first { it.id == targetId })
        override suspend fun addTarget(target: TargetDevice): Result<TargetDevice> = Result.success(target)
        override suspend fun updateTarget(target: TargetDevice): Result<TargetDevice> = Result.success(target)
        override suspend fun deleteTarget(targetId: String): Result<Unit> = Result.success(Unit)
    }

    private class FakeReadinessPort : EnvironmentReadinessPort {
        override fun observe(): Flow<EnvironmentReadiness> = flowOf(
            EnvironmentReadiness(
                state = EnvironmentReadinessState.READY,
                workDirLinuxPath = "/home/lti/LtiRomWorkDir",
            ),
        )
        override suspend fun refresh(): EnvironmentReadiness = EnvironmentReadiness(
            state = EnvironmentReadinessState.READY,
            workDirLinuxPath = "/home/lti/LtiRomWorkDir",
        )
        override suspend fun forWorkspace(workspace: Workspace): EnvironmentReadiness = EnvironmentReadiness(
            state = EnvironmentReadinessState.READY,
            workDirLinuxPath = "/home/lti/LtiRomWorkDir",
        )
    }

    private class FakePipelineExecutionPort : PipelineExecutionPort {
        val cancelledRuns = mutableListOf<String>()
        override suspend fun startStep(ws: Workspace, step: StepCommand, idempotencyKey: String): RunHandle =
            RunHandle("step_1", "RUNNING", 1000L)
        override fun observe(serverRunId: String, fromSeq: Long): Flow<StepEvent> = flowOf(StepEvent.Finished(0))
        override suspend fun status(serverRunId: String): StepStatus? = null
        override suspend fun cancel(serverRunId: String): Boolean {
            cancelledRuns.add(serverRunId)
            return true
        }
        override suspend fun uploadFile(ws: Workspace, relPath: String, bytes: ByteArray): Boolean = true
        override suspend fun readFile(ws: Workspace, relPath: String): String? = null
        override suspend fun stat(ws: Workspace, relPath: String): FileStat =
            FileStat(relPath, exists = true, sizeBytes = 100L)
        override suspend fun execute(ws: Workspace, command: StepCommand): ToolResult = ToolResult(0, "")
        override suspend fun checkAvailableDiskSpace(ws: Workspace): Long? = 50_000_000_000L
    }

    private val testTarget = TargetDevice(
        id = "PQ84P01",
        name = "REDMAGIC Astra",
        codename = "PQ84P01",
        revision = 2,
        availableRegions = listOf(TargetRegion.GLOBAL),
        availableFirmwares = mapOf(
            TargetRegion.GLOBAL to listOf(
                TargetFirmware(
                    version = "10.5",
                    buildId = "PQ84P01:15",
                    androidVersion = "15",
                    securityPatch = "2026-02-01",
                    otaUrl = "https://ota.zip",
                    isOfficial = true,
                    isRecommended = true,
                ),
            ),
        ),
        packagePolicy = PackagePolicy(
            flashablePartitions = listOf("system"),
            flashableBootPartitions = listOf("boot"),
            bootSlots = listOf("a"),
            recoverySystemMountPoint = "/system_root",
        ),
        socPlatform = "Snapdragon 8 Elite",
        filesystemType = "erofs",
        superPartitionBytes = 1000L,
        superGroupBytes = 1000L,
        dynamicPartitions = listOf("system"),
        bootPartitions = listOf("boot"),
        status = TargetStatus.QUALIFIED,
        description = "Test Target",
    )

    private val testWorkspace = Workspace(
        id = "ws_test",
        name = "Test Workspace",
        path = "C:\\test\\ws_test",
        linuxPath = "/home/lti/LtiRomWorkDir/workspaces/ws_test",
        targetBinding = TargetBinding("PQ84P01", 2),
        effectiveSnapshotId = "snap_1",
    )

    private val testSnapshot = ConfigurationSnapshot.create(
        id = "snap_1",
        workspaceId = "ws_test",
        profileRevision = 2,
        acquisition = org.ide.lti.core.model.workspace.AcquisitionSettings(
            mode = AcquisitionMode.DOWNLOAD,
            region = TargetRegion.GLOBAL,
            firmware = TargetFirmware(
                version = "10.5",
                buildId = "PQ84P01:15",
                androidVersion = "15",
                securityPatch = "2026-02-01",
                otaUrl = "https://ota.zip",
                isOfficial = true,
                isRecommended = true,
            ),
        ),
        build = org.ide.lti.core.model.workspace.BuildSettings(
            packagePolicy = testTarget.packagePolicy,
            signing = SigningPolicy(signImages = true, signPackage = true),
        ),
        assembly = org.ide.lti.core.model.workspace.AssemblySettings(
            buildType = "userdebug",
            romVersion = "1.0.0",
        ),
        release = org.ide.lti.core.model.workspace.ReleaseSettings(
            otaBaseUrl = "https://example.com/updates",
        ),
        createdAt = Instant.fromEpochMilliseconds(1000L),

    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testAutoReattachOnWorkspaceOpen() = runTest(testDispatcher) {
        val workspaceRepo = FakeWorkspaceRepository().apply { saveWorkspace(testWorkspace) }
        val snapshotRepo = FakeSnapshotRepository().apply { saveSnapshot(testSnapshot) }
        val targetRepo = FakeTargetRepository(listOf(testTarget))
        val runRepo = FakeRunRepository()
        val executionPort = FakePipelineExecutionPort()

        val activeRun = BuildRun(
            id = "run_active_1",
            workspaceId = testWorkspace.id,
            snapshotId = testSnapshot.id,
            state = RunState.RUNNING,
            startedAt = Instant.fromEpochMilliseconds(1000L),
        )
        runRepo.upsert(activeRun)

        val workspaceManager = WorkspaceManager(workspaceRepo, CoroutineScope(testDispatcher))
        val observeUseCase = ObserveRunUseCase(runRepo)
        val orchestrator = PipelineOrchestrator(executionPort, runRepo, NoVendoredFiles)
        val startUseCase = StartPipelineRunUseCase(
            workspaceRepo,
            snapshotRepo,
            targetRepo,
            FakeReadinessPort(),
            executionPort,
            runRepo,
            orchestrator,
            CoroutineScope(testDispatcher),
        )

        val reattachedRuns = mutableListOf<String>()
        val spiedReattachUseCase = object : ReattachRunUseCase(
            workspaceRepo,
            snapshotRepo,
            targetRepo,
            runRepo,
            orchestrator,
            CoroutineScope(testDispatcher),
        ) {
            override suspend fun invoke(
                workspaceId: String,
                runId: String?,
                driverInstanceId: String,
            ): Flow<PipelineEvent>? {
                reattachedRuns.add(runId ?: "")
                return flowOf(PipelineEvent.RunFinished(RunState.SUCCEEDED))
            }
        }

        val viewModel = BuildRunViewModel(
            workspaceManager = workspaceManager,
            startPipelineRunUseCase = startUseCase,
            observeRunUseCase = observeUseCase,
            cancelRunUseCase = null,
            reattachRunUseCase = spiedReattachUseCase,
            runRepository = runRepo,
        )

        workspaceManager.openWorkspace(testWorkspace.id)
        advanceUntilIdle()

        assertEquals(
            listOf("run_active_1"),
            reattachedRuns,
            "Active run must be auto-reattached when workspace opens",
        )
    }

    @Test
    fun testCancelTransitionsCancellingToCancelled() = runTest(testDispatcher) {
        val workspaceRepo = FakeWorkspaceRepository().apply { saveWorkspace(testWorkspace) }
        val snapshotRepo = FakeSnapshotRepository().apply { saveSnapshot(testSnapshot) }
        val targetRepo = FakeTargetRepository(listOf(testTarget))
        val runRepo = FakeRunRepository()
        val executionPort = FakePipelineExecutionPort()

        val activeRun = BuildRun(
            id = "run_to_cancel",
            workspaceId = testWorkspace.id,
            snapshotId = testSnapshot.id,
            state = RunState.RUNNING,
            startedAt = Instant.fromEpochMilliseconds(1000L),
            steps = listOf(
                StageStep(
                    stageId = StageId.FIRMWARE_ACQUISITION,
                    index = 0,
                    serverRunId = "srv_step_cancel",
                    idempotencyKey = "key_cancel",
                    command = StepCommand("curl", listOf("https://url")),
                    state = StepState.RUNNING,
                ),
            ),
        )
        runRepo.upsert(activeRun)

        val workspaceManager = WorkspaceManager(workspaceRepo, CoroutineScope(testDispatcher))
        val observeUseCase = ObserveRunUseCase(runRepo)
        val orchestrator = PipelineOrchestrator(executionPort, runRepo, NoVendoredFiles)
        val startUseCase = StartPipelineRunUseCase(
            workspaceRepo,
            snapshotRepo,
            targetRepo,
            FakeReadinessPort(),
            executionPort,
            runRepo,
            orchestrator,
            CoroutineScope(testDispatcher),
        )
        val cancelUseCase = CancelRunUseCase(runRepo, executionPort, workspaceRepo)

        val viewModel = BuildRunViewModel(
            workspaceManager = workspaceManager,
            startPipelineRunUseCase = startUseCase,
            observeRunUseCase = observeUseCase,
            cancelRunUseCase = cancelUseCase,
            runRepository = runRepo,
        )

        workspaceManager.openWorkspace(testWorkspace.id)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.canCancelRun)
        viewModel.cancelBuild()
        advanceUntilIdle()

        assertEquals(listOf("srv_step_cancel"), executionPort.cancelledRuns)
        val finalRun = runRepo.getRun("run_to_cancel")
        assertNotNull(finalRun)
        assertEquals(RunState.CANCELLED, finalRun.state)
        assertEquals(RunState.CANCELLED, viewModel.uiState.value.latestRun?.state)
    }

    @Test
    fun testInterruptedRunDisplaysOutcomeUnknownAndCannotBeStartedDirectly() = runTest(
        testDispatcher,
    ) {
        val workspaceRepo = FakeWorkspaceRepository().apply { saveWorkspace(testWorkspace) }
        val snapshotRepo = FakeSnapshotRepository().apply { saveSnapshot(testSnapshot) }
        val targetRepo = FakeTargetRepository(listOf(testTarget))
        val runRepo = FakeRunRepository()
        val executionPort = FakePipelineExecutionPort()

        val interruptedRun = BuildRun(
            id = "run_interrupted",
            workspaceId = testWorkspace.id,
            snapshotId = testSnapshot.id,
            state = RunState.INTERRUPTED,
            startedAt = Instant.fromEpochMilliseconds(1000L),
        )
        runRepo.upsert(interruptedRun)

        val workspaceManager = WorkspaceManager(workspaceRepo, CoroutineScope(testDispatcher))
        val observeUseCase = ObserveRunUseCase(runRepo)
        val orchestrator = PipelineOrchestrator(executionPort, runRepo, NoVendoredFiles)
        val startUseCase = StartPipelineRunUseCase(
            workspaceRepo,
            snapshotRepo,
            targetRepo,
            FakeReadinessPort(),
            executionPort,
            runRepo,
            orchestrator,
            CoroutineScope(testDispatcher),
        )

        val viewModel = BuildRunViewModel(
            workspaceManager = workspaceManager,
            startPipelineRunUseCase = startUseCase,
            observeRunUseCase = observeUseCase,
            runRepository = runRepo,
        )

        workspaceManager.openWorkspace(testWorkspace.id)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isInterrupted)
        assertFalse(viewModel.uiState.value.isRunning)
    }

    @Test
    fun testCancelledAndInterruptedArtifactsAreNeverVerified() = runTest(testDispatcher) {
        val artifact1 = Artifact(
            kind = ArtifactKind.FLASHABLE_ZIP,
            linuxPath = "/path/to/rom.zip",
            sizeBytes = 1000L,
            verification = VerificationState.NOT_VERIFIED,
        )
        val runRepo = FakeRunRepository()
        val cancelledRun = BuildRun(
            id = "run_cancelled_artifacts",
            workspaceId = testWorkspace.id,
            snapshotId = testSnapshot.id,
            state = RunState.CANCELLED,
            startedAt = Instant.fromEpochMilliseconds(1000L),
            artifacts = listOf(artifact1),
        )
        runRepo.upsert(cancelledRun)

        val workspaceRepo = FakeWorkspaceRepository().apply { saveWorkspace(testWorkspace) }
        val workspaceManager = WorkspaceManager(workspaceRepo, CoroutineScope(testDispatcher))
        val observeUseCase = ObserveRunUseCase(runRepo)
        val executionPort = FakePipelineExecutionPort()
        val orchestrator = PipelineOrchestrator(executionPort, runRepo, NoVendoredFiles)
        val startUseCase = StartPipelineRunUseCase(
            workspaceRepo,
            FakeSnapshotRepository(),
            FakeTargetRepository(listOf(testTarget)),
            FakeReadinessPort(),
            executionPort,
            runRepo,
            orchestrator,
            CoroutineScope(testDispatcher),
        )

        val viewModel = BuildRunViewModel(
            workspaceManager = workspaceManager,
            startPipelineRunUseCase = startUseCase,
            observeRunUseCase = observeUseCase,
            runRepository = runRepo,
        )

        workspaceManager.openWorkspace(testWorkspace.id)
        advanceUntilIdle()

        val artifacts = viewModel.uiState.value.latestRun?.artifacts ?: emptyList()
        assertEquals(1, artifacts.size)
        assertTrue(artifacts.all { it.verification != VerificationState.VERIFIED })
    }
}
