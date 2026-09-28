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
import org.ide.lti.core.domain.usecase.build.ObserveRunUseCase
import org.ide.lti.core.domain.usecase.build.StartPipelineRunUseCase
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.run.Artifact
import org.ide.lti.core.model.run.ArtifactKind
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.run.StageOutcome
import org.ide.lti.core.model.run.StageState
import org.ide.lti.core.model.run.StepCommand
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
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceSession
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BuildRunViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

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

    private class FakeReadinessPort(var readinessState: EnvironmentReadinessState = EnvironmentReadinessState.READY) :
        EnvironmentReadinessPort {
        override fun observe(): Flow<EnvironmentReadiness> = flowOf(
            EnvironmentReadiness(
                state = readinessState,
                workDirLinuxPath = "/home/lti/LtiRomWorkDir",
            ),
        )
        override suspend fun refresh(): EnvironmentReadiness = EnvironmentReadiness(
            state = readinessState,
            workDirLinuxPath = "/home/lti/LtiRomWorkDir",
        )
        override suspend fun forWorkspace(workspace: Workspace): EnvironmentReadiness = EnvironmentReadiness(
            state = readinessState,
            workDirLinuxPath = "/home/lti/LtiRomWorkDir",
        )
    }

    private class FakePipelineExecutionPort : PipelineExecutionPort {
        var availableDiskBytes: Long? = 50_000_000_000L

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
        override suspend fun checkAvailableDiskSpace(ws: Workspace): Long? = availableDiskBytes
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createFixture(
        readinessState: EnvironmentReadinessState = EnvironmentReadinessState.READY,
        availableDiskBytes: Long = 50_000_000_000L,
    ): Fixture {
        val workspaceRepo = FakeWorkspaceRepository()
        val snapshotRepo = FakeSnapshotRepository()
        val runRepo = FakeRunRepository()
        val readinessPort = FakeReadinessPort(readinessState)
        val executionPort = FakePipelineExecutionPort().apply {
            this.availableDiskBytes = availableDiskBytes
        }

        val target = TargetDevice(
            id = "PQ84P01",
            name = "Test Target",
            codename = "PQ84P01",
            availableRegions = listOf(TargetRegion.GLOBAL),
            availableFirmwares = mapOf(
                TargetRegion.GLOBAL to listOf(
                    TargetFirmware("V1.0", "test.build", isOfficial = true, isRecommended = true),
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
        val targetRepo = FakeTargetRepository(listOf(target))

        val orchestrator =
            PipelineOrchestrator(
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

        val viewModel = BuildRunViewModel(
            workspaceManager = workspaceManager,
            startPipelineRunUseCase = startUseCase,
            observeRunUseCase = observeUseCase,
            runRepository = runRepo,
        )

        return Fixture(
            viewModel = viewModel,
            workspaceManager = workspaceManager,
            workspaceRepo = workspaceRepo,
            snapshotRepo = snapshotRepo,
            runRepo = runRepo,
            target = target,
            readinessPort = readinessPort,
        )
    }

    private data class Fixture(
        val viewModel: BuildRunViewModel,
        val workspaceManager: WorkspaceManager,
        val workspaceRepo: FakeWorkspaceRepository,
        val snapshotRepo: FakeSnapshotRepository,
        val runRepo: FakeRunRepository,
        val target: TargetDevice,
        val readinessPort: FakeReadinessPort,
    )

    private suspend fun Fixture.createAndOpenWorkspace(id: String = "ws-1"): Pair<Workspace, ConfigurationSnapshot> {
        val snapshot = ConfigurationSnapshot.create(
            id = "snap-1",
            workspaceId = id,
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
            assembly = org.ide.lti.core.model.workspace.AssemblySettings(
                buildType = "user",
                romVersion = "1.0",
            ),
            release = org.ide.lti.core.model.workspace.ReleaseSettings(
                otaBaseUrl = "https://ota.example.com",
            ),
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        snapshotRepo.saveSnapshot(snapshot)

        val workspace = Workspace(
            id = id,
            name = "Workspace 1",
            path = "C:\\projects\\$id",
            linuxPath = "/home/lti/LtiRomWorkDir/workspaces/$id",
            targetBinding = TargetBinding(profileId = target.id, profileRevision = 1),
            effectiveSnapshotId = snapshot.id,
        )
        workspaceRepo.saveWorkspace(workspace)
        workspaceManager.openWorkspace(workspace.id)
        return workspace to snapshot
    }

    @Test
    fun initial_state_has_no_workspace_and_run_is_disabled() = runTest {
        val fixture = createFixture()
        val state = fixture.viewModel.uiState.value

        assertNull(state.currentWorkspace)
        assertNull(state.latestRun)
        assertFalse(state.isRunning)
        assertTrue(state.isRunDisabled)
    }

    @Test
    fun opening_workspace_enables_run() = runTest {
        val fixture = createFixture()
        fixture.createAndOpenWorkspace()
        advanceUntilIdle()

        val state = fixture.viewModel.uiState.value
        assertNotNull(state.currentWorkspace)
        assertNull(state.latestRun)
        assertFalse(state.isRunning)
        assertFalse(state.isRunDisabled)
        assertTrue(state.canStartRun)
    }

    @Test
    fun run_disabled_while_active_and_indeterminate_progress() = runTest {
        val fixture = createFixture()
        val (ws, _) = fixture.createAndOpenWorkspace()
        advanceUntilIdle()

        // Persist an active run in the repo
        val activeRun = BuildRun(
            id = "run-active",
            workspaceId = ws.id,
            snapshotId = "snap-1",
            state = RunState.RUNNING,
            startedAt = Instant.fromEpochMilliseconds(2000L),
        )
        fixture.runRepo.upsert(activeRun)
        advanceUntilIdle()

        val state = fixture.viewModel.uiState.value
        assertEquals("run-active", state.latestRun?.id)
        assertTrue(state.isRunning)
        assertTrue(state.isRunDisabled)
        assertFalse(state.canStartRun)

        // Attempting startBuild while running is a no-op
        fixture.viewModel.startBuild()
        advanceUntilIdle()
        assertEquals("run-active", fixture.viewModel.uiState.value.latestRun?.id)
    }

    @Test
    fun failure_highlighting_and_dismissal() = runTest {
        // Environment not ready triggers failure
        val fixture = createFixture(readinessState = EnvironmentReadinessState.TOOLS_MISSING)
        fixture.createAndOpenWorkspace()
        advanceUntilIdle()

        fixture.viewModel.startBuild()
        advanceUntilIdle()

        val state = fixture.viewModel.uiState.value
        assertFalse(state.isRunning)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("Environment is not ready"))

        // Dismiss error
        fixture.viewModel.clearError()
        assertNull(fixture.viewModel.uiState.value.errorMessage)
    }

    @Test
    fun stage_stepper_states_mapping() = runTest {
        val fixture = createFixture()
        val (ws, _) = fixture.createAndOpenWorkspace()
        advanceUntilIdle()

        val stages = listOf(
            StageOutcome(
                stageId = StageId.FIRMWARE_ACQUISITION,
                state = StageState.EXECUTED,
                durationMs = 5000L,
            ),
            StageOutcome(
                stageId = StageId.FIRMWARE_EXTRACTION,
                state = StageState.SKIPPED,
            ),
            StageOutcome(
                stageId = StageId.WORK_TREE_ASSEMBLY,
                state = StageState.RUNNING,
            ),
            StageOutcome(
                stageId = StageId.MODULE_APPLICATION,
                state = StageState.PENDING,
            ),
            StageOutcome(
                stageId = StageId.BUILD_FLASHABLE_ZIP,
                state = StageState.FAILED,
                message = "AVB signing failed",
            ),
            StageOutcome(
                stageId = StageId.GENERATE_OTA_MANIFEST,
                state = StageState.CANCELLED,
            ),
        )

        val run = BuildRun(
            id = "run-stages",
            workspaceId = ws.id,
            snapshotId = "snap-1",
            state = RunState.FAILED,
            stages = stages,
            startedAt = Instant.fromEpochMilliseconds(3000L),
        )
        fixture.runRepo.upsert(run)
        advanceUntilIdle()

        val state = fixture.viewModel.uiState.value
        assertEquals("run-stages", state.latestRun?.id)
        val loadedStages = state.latestRun!!.stages
        assertEquals(6, loadedStages.size)
        assertEquals(StageState.EXECUTED, loadedStages[0].state)
        assertEquals(StageState.SKIPPED, loadedStages[1].state)
        assertEquals(StageState.RUNNING, loadedStages[2].state)
        assertEquals(StageState.PENDING, loadedStages[3].state)
        assertEquals(StageState.FAILED, loadedStages[4].state)
        assertEquals("AVB signing failed", loadedStages[4].message)
        assertEquals(StageState.CANCELLED, loadedStages[5].state)
    }

    @Test
    fun artifacts_with_verification_display() = runTest {
        val fixture = createFixture()
        val (ws, _) = fixture.createAndOpenWorkspace()
        advanceUntilIdle()

        val artifacts = listOf(
            Artifact(
                kind = ArtifactKind.FLASHABLE_ZIP,
                linuxPath = "${ws.linuxPath}/out/package/rom.zip",
                sizeBytes = 1_500_000_000L,
                sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                verification = VerificationState.VERIFIED,
            ),
            Artifact(
                kind = ArtifactKind.CHECKSUM,
                linuxPath = "${ws.linuxPath}/out/package/rom.zip.sha256",
                sizeBytes = 64L,
                sha256 = null,
                verification = VerificationState.VERIFIED,
            ),
            Artifact(
                kind = ArtifactKind.OTA_MANIFEST,
                linuxPath = "${ws.linuxPath}/out/package/manifest.json",
                sizeBytes = 512L,
                sha256 = null,
                verification = VerificationState.VERIFIED,
            ),
        )

        val run = BuildRun(
            id = "run-artifacts",
            workspaceId = ws.id,
            snapshotId = "snap-1",
            state = RunState.SUCCEEDED,
            artifacts = artifacts,
            startedAt = Instant.fromEpochMilliseconds(4000L),
        )
        fixture.runRepo.upsert(run)
        advanceUntilIdle()

        val state = fixture.viewModel.uiState.value
        assertEquals("run-artifacts", state.latestRun?.id)
        assertEquals(3, state.latestRun!!.artifacts.size)

        val zip = state.latestRun!!.artifacts[0]
        assertEquals(ArtifactKind.FLASHABLE_ZIP, zip.kind)
        assertEquals(VerificationState.VERIFIED, zip.verification)
        assertEquals(1_500_000_000L, zip.sizeBytes)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", zip.sha256)

        val checksum = state.latestRun!!.artifacts[1]
        assertEquals(ArtifactKind.CHECKSUM, checksum.kind)
        assertEquals(VerificationState.VERIFIED, checksum.verification)

        val manifest = state.latestRun!!.artifacts[2]
        assertEquals(ArtifactKind.OTA_MANIFEST, manifest.kind)
        assertEquals(VerificationState.VERIFIED, manifest.verification)
    }
}

private object NoVendoredFiles : VendoredFilePort {
    override fun load(resourceId: String): VendoredFile? = null
}
