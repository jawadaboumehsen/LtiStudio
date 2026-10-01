package org.ide.lti.core.domain.usecase.build

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.ide.lti.core.domain.pipeline.PipelineOrchestrator
import org.ide.lti.core.domain.pipeline.ToolResult
import org.ide.lti.core.domain.ports.*
import org.ide.lti.core.domain.ports.VendoredFile
import org.ide.lti.core.domain.ports.VendoredFilePort
import org.ide.lti.core.domain.repository.run.InMemoryRunRepository
import org.ide.lti.core.domain.repository.snapshot.InMemorySnapshotRepository
import org.ide.lti.core.domain.repository.target.TargetRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.run.StepCommand
import org.ide.lti.core.model.setup.EnvironmentReadiness
import org.ide.lti.core.model.setup.EnvironmentReadinessState
import org.ide.lti.core.model.target.*
import org.ide.lti.core.model.workspace.*
import kotlin.test.*

class StartPipelineRunUseCaseTest {

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

    private class FakePipelineExecutionPort : PipelineExecutionPort {
        val uploadedFiles = mutableMapOf<String, ByteArray>()
        var availableDiskBytes: Long? = 50_000_000_000L

        override suspend fun startStep(ws: Workspace, step: StepCommand, idempotencyKey: String): RunHandle =
            RunHandle("step_1", "RUNNING", 1000L)

        override fun observe(serverRunId: String, fromSeq: Long): Flow<StepEvent> = flowOf(StepEvent.Finished(0))

        override suspend fun status(serverRunId: String): StepStatus? = null
        override suspend fun cancel(serverRunId: String): Boolean = true

        override suspend fun uploadFile(ws: Workspace, relPath: String, bytes: ByteArray): Boolean {
            uploadedFiles[relPath] = bytes
            return true
        }

        override suspend fun readFile(ws: Workspace, relPath: String): String? =
            uploadedFiles[relPath]?.decodeToString()

        override suspend fun stat(ws: Workspace, relPath: String): FileStat =
            FileStat(relPath, exists = true, sizeBytes = 1000L)

        override suspend fun execute(ws: Workspace, command: StepCommand): ToolResult = ToolResult(0, "")
        override suspend fun checkAvailableDiskSpace(ws: Workspace): Long? = availableDiskBytes
    }

    private class FakeWorkspaceRepo : WorkspaceRepository {
        val workspaces = mutableMapOf<String, Workspace>()
        override fun getWorkspaces(): Flow<List<Workspace>> = flowOf(workspaces.values.toList())
        override suspend fun getWorkspace(id: String): Workspace? = workspaces[id]
        override suspend fun saveWorkspace(workspace: Workspace) {
            workspaces[workspace.id] = workspace
        }
        override suspend fun deleteWorkspace(id: String) {
            workspaces.remove(id)
        }
        override fun getRecentProjects(): Flow<List<RecentProject>> = flowOf(emptyList())
        override suspend fun addRecentProject(project: RecentProject) {}
        override suspend fun removeRecentProject(workspaceId: String) {}
        override suspend fun clearRecentProjects() {}
        override suspend fun discoverWorkspacesInWorkDir(basePath: String?): List<RecentProject> = emptyList()
        override suspend fun getWorkspaceSession(workspaceId: String): WorkspaceSession? = null
        override suspend fun saveWorkspaceSession(session: WorkspaceSession) {}
    }

    private class FakeTargetRepo(private val target: TargetDevice) : TargetRepository {
        override fun getAvailableTargets(): Flow<List<TargetDevice>> = flowOf(listOf(target))
        override fun getSelectedTarget(): Flow<TargetDevice> = flowOf(target)
        override suspend fun selectTarget(targetId: String): Result<TargetDevice> = Result.success(target)
        override suspend fun addTarget(target: TargetDevice): Result<TargetDevice> = Result.success(target)
        override suspend fun updateTarget(target: TargetDevice): Result<TargetDevice> = Result.success(target)
        override suspend fun deleteTarget(targetId: String): Result<Unit> = Result.success(Unit)
    }

    private val testTarget = TargetDevice(
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

    private val testWorkspace = Workspace(
        id = "ws-1",
        name = "PQ84P01-Global",
        path = "/home/lti/LtiRomWorkDir/workspaces/pq84p01-global",
        linuxPath = "/home/lti/LtiRomWorkDir/workspaces/pq84p01-global",
        targetBinding = TargetBinding("PQ84P01", 2),
        effectiveSnapshotId = "snap-1",
    )

    private val testSnapshot = ConfigurationSnapshot.create(
        id = "snap-1",
        workspaceId = "ws-1",
        profileRevision = 2,
        acquisition = org.ide.lti.core.model.workspace.AcquisitionSettings(
            mode = AcquisitionMode.DOWNLOAD,
            region = TargetRegion.GLOBAL,
            firmware = TargetFirmware(
                version = "REDMAGICOS10.5.15_NP05J_GB",
                buildId = "PQ84P01:15/AQ3A.240812.002",
                androidVersion = "15",
                securityPatch = "2026-02-01",
                sourceKind = FirmwareSourceKind.OTA_ZIP,
                acquisitionUrl = "https://update.redmagic.gg/NP05J_GB.zip",
                sha256 = "c2b647f1146f8c79a29e4726bfcf1a58a7da09f193758bdf214ff94e0192e4ab",
            ),
        ),
        extraction = org.ide.lti.core.model.workspace.ExtractionSettings(),
        assembly = org.ide.lti.core.model.workspace.AssemblySettings(romVersion = "1.0.0", buildType = "userdebug"),
        debloat = org.ide.lti.core.model.workspace.DebloatSettings(),
        customization = org.ide.lti.core.model.workspace.CustomizationSettings(),
        build = org.ide.lti.core.model.workspace.BuildSettings(
            packagePolicy = testTarget.packagePolicy,
            signing = SigningPolicy(signImages = true, signPackage = true),
        ),
        release = org.ide.lti.core.model.workspace.ReleaseSettings(otaBaseUrl = "https://ota.ltirom.org/updates"),
        publish = org.ide.lti.core.model.workspace.PublishSettings(),
        createdAt = Instant.fromEpochSeconds(1726056000L),
    )

    @Test
    fun testSnapshotFrozenBeforeStartAndPersistedQueued() = runTest {
        val wsRepo = FakeWorkspaceRepo().apply { saveWorkspace(testWorkspace) }
        val snapRepo = InMemorySnapshotRepository().apply { saveSnapshot(testSnapshot) }
        val targetRepo = FakeTargetRepo(testTarget)
        val readinessPort = FakeReadinessPort()
        val executionPort = FakePipelineExecutionPort()
        val runRepo = InMemoryRunRepository()
        val orchestrator = PipelineOrchestrator(executionPort, runRepo, NoVendoredFiles)

        val useCase = StartPipelineRunUseCase(
            workspaceRepository = wsRepo,
            snapshotRepository = snapRepo,
            targetRepository = targetRepo,
            readinessPort = readinessPort,
            executionPort = executionPort,
            runRepository = runRepo,
            orchestrator = orchestrator,
        )

        val result = useCase("ws-1", "driver-1")
        assertTrue(result.isSuccess)
        val run = result.getOrThrow()
        assertEquals("ws-1", run.workspaceId)
        assertEquals(RunState.QUEUED, run.state)

        // Verify config.json frozen into runs/<runId>/config.json
        val expectedConfigPath = "runs/${run.id}/config.json"
        assertTrue(executionPort.uploadedFiles.containsKey(expectedConfigPath))
        val uploadedContent = executionPort.uploadedFiles[expectedConfigPath]!!.decodeToString()
        assertTrue(uploadedContent.contains("REDMAGICOS10.5.15_NP05J_GB"))

        // Verify run record persisted in repo
        val persisted = runRepo.getRun(run.id)
        assertNotNull(persisted)
        assertEquals(RunState.QUEUED, persisted.state)
        assertEquals("driver-1", persisted.driverInstanceId)
    }

    @Test
    fun testSecondStartInSameWorkspaceRefused() = runTest {
        val wsRepo = FakeWorkspaceRepo().apply { saveWorkspace(testWorkspace) }
        val snapRepo = InMemorySnapshotRepository().apply { saveSnapshot(testSnapshot) }
        val targetRepo = FakeTargetRepo(testTarget)
        val readinessPort = FakeReadinessPort()
        val executionPort = FakePipelineExecutionPort()
        val runRepo = InMemoryRunRepository()
        val orchestrator = PipelineOrchestrator(executionPort, runRepo, NoVendoredFiles)

        val useCase = StartPipelineRunUseCase(
            workspaceRepository = wsRepo,
            snapshotRepository = snapRepo,
            targetRepository = targetRepo,
            readinessPort = readinessPort,
            executionPort = executionPort,
            runRepository = runRepo,
            orchestrator = orchestrator,
        )

        val firstResult = useCase("ws-1")
        assertTrue(firstResult.isSuccess)

        // Attempt second run while first is active
        val secondResult = useCase("ws-1")
        assertTrue(secondResult.isFailure)
        assertTrue(secondResult.exceptionOrNull()?.message?.contains("already has an active run") == true)
    }

    @Test
    fun testReadinessGateRefusesIfNotReady() = runTest {
        val wsRepo = FakeWorkspaceRepo().apply { saveWorkspace(testWorkspace) }
        val snapRepo = InMemorySnapshotRepository().apply { saveSnapshot(testSnapshot) }
        val targetRepo = FakeTargetRepo(testTarget)
        val readinessPort = FakeReadinessPort(
            EnvironmentReadiness(state = EnvironmentReadinessState.ENV_ABSENT),
        )
        val executionPort = FakePipelineExecutionPort()
        val runRepo = InMemoryRunRepository()
        val orchestrator = PipelineOrchestrator(executionPort, runRepo, NoVendoredFiles)

        val useCase = StartPipelineRunUseCase(
            workspaceRepository = wsRepo,
            snapshotRepository = snapRepo,
            targetRepository = targetRepo,
            readinessPort = readinessPort,
            executionPort = executionPort,
            runRepository = runRepo,
            orchestrator = orchestrator,
        )

        val result = useCase("ws-1")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Environment is not ready") == true)
    }

    @Test
    fun testInsufficientDiskSpaceRefused() = runTest {
        val wsRepo = FakeWorkspaceRepo().apply { saveWorkspace(testWorkspace) }
        val snapRepo = InMemorySnapshotRepository().apply { saveSnapshot(testSnapshot) }
        val targetRepo = FakeTargetRepo(testTarget)
        val readinessPort = FakeReadinessPort()
        val executionPort = FakePipelineExecutionPort().apply {
            availableDiskBytes = 10_000_000_000L // 10 GB < 40 GB min
        }
        val runRepo = InMemoryRunRepository()
        val orchestrator = PipelineOrchestrator(executionPort, runRepo, NoVendoredFiles)

        val useCase = StartPipelineRunUseCase(
            workspaceRepository = wsRepo,
            snapshotRepository = snapRepo,
            targetRepository = targetRepo,
            readinessPort = readinessPort,
            executionPort = executionPort,
            runRepository = runRepo,
            orchestrator = orchestrator,
        )

        val result = useCase("ws-1")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Insufficient disk space") == true)
    }

    @Test
    fun testDiskPreflightWithKnownFirmwareArchiveSize() = runTest {
        val snapshotWithKnownSize = testSnapshot.copy(
            acquisition = testSnapshot.acquisition.copy(
                firmware = testSnapshot.acquisition.firmware.copy(archiveSizeBytes = 5_000_000_000L),
            ), // 5 GB -> requires 20 GB
        )
        val wsRepo = FakeWorkspaceRepo().apply { saveWorkspace(testWorkspace) }
        val snapRepo = InMemorySnapshotRepository().apply { saveSnapshot(snapshotWithKnownSize) }
        val targetRepo = FakeTargetRepo(testTarget)
        val readinessPort = FakeReadinessPort()
        val runRepo = InMemoryRunRepository()

        // 1. Available 15 GB < 20 GB -> Refused
        val executionPort1 = FakePipelineExecutionPort().apply {
            availableDiskBytes = 15_000_000_000L
        }
        val orchestrator1 = PipelineOrchestrator(executionPort1, runRepo, NoVendoredFiles)
        val useCase1 = StartPipelineRunUseCase(
            workspaceRepository = wsRepo,
            snapshotRepository = snapRepo,
            targetRepository = targetRepo,
            readinessPort = readinessPort,
            executionPort = executionPort1,
            runRepository = runRepo,
            orchestrator = orchestrator1,
        )
        val failureResult = useCase1("ws-1")
        assertTrue(failureResult.isFailure)
        assertTrue(failureResult.exceptionOrNull()?.message?.contains("Insufficient disk space") == true)

        // 2. Available 25 GB >= 20 GB -> Allowed
        val executionPort2 = FakePipelineExecutionPort().apply {
            availableDiskBytes = 25_000_000_000L
        }
        val orchestrator2 = PipelineOrchestrator(executionPort2, runRepo, NoVendoredFiles)
        val useCase2 = StartPipelineRunUseCase(
            workspaceRepository = wsRepo,
            snapshotRepository = snapRepo,
            targetRepository = targetRepo,
            readinessPort = readinessPort,
            executionPort = executionPort2,
            runRepository = runRepo,
            orchestrator = orchestrator2,
        )
        val successResult = useCase2("ws-1")
        assertTrue(successResult.isSuccess)
    }
}

private object NoVendoredFiles : VendoredFilePort {
    override fun load(resourceId: String): VendoredFile? = null
}
