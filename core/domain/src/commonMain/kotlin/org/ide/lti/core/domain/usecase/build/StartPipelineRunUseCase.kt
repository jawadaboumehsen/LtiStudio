package org.ide.lti.core.domain.usecase.build

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import org.ide.lti.core.domain.pipeline.PipelineOrchestrator
import org.ide.lti.core.domain.pipeline.StageContext
import org.ide.lti.core.domain.pipeline.configuration.DeviceConfigReadiness
import org.ide.lti.core.domain.pipeline.stages.FirmwareAcquisitionStage
import org.ide.lti.core.domain.pipeline.toolkit.JobLock
import org.ide.lti.core.domain.pipeline.toolkit.LockResult
import org.ide.lti.core.domain.ports.EnvironmentReadinessPort
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.domain.repository.snapshot.SnapshotRepository
import org.ide.lti.core.domain.repository.target.WorkspaceDeviceRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.domain.workspace.WorkspaceSettingsStore
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.setup.EnvironmentReadinessState
import org.ide.lti.core.model.workspace.AcquisitionMode
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.Workspace
import java.util.UUID

public class StartPipelineRunUseCase(
    private val workspaceRepository: WorkspaceRepository,
    private val snapshotRepository: SnapshotRepository,
    private val workspaceDeviceRepository: WorkspaceDeviceRepository,
    private val readinessPort: EnvironmentReadinessPort,
    private val executionPort: PipelineExecutionPort,
    private val runRepository: RunRepository,
    private val orchestrator: PipelineOrchestrator,
    private val scope: CoroutineScope? = null,
    private val settingsStore: WorkspaceSettingsStore? = null,
    /** The workbench job lock: a build never starts while an interactive edit is changing the workspace. */
    private val jobLock: JobLock? = null,
) {
    public suspend operator fun invoke(
        workspaceId: String,
        driverInstanceId: String = "driver_${UUID.randomUUID()}",
        throughStage: StageId? = null,
    ): Result<BuildRun> = runCatching {
        // One start at a time: the active-run check and the QUEUED record must not interleave with another start.
        admission.withLock { start(workspaceId, driverInstanceId, throughStage) }
    }

    /**
     * Holds the workspace's job lock while the run is checked and queued: a workbench edit then either holds the
     * lock (and the build is refused) or finds the queued run (and the edit is refused). Never both running.
     */
    private suspend fun start(workspaceId: String, driverInstanceId: String, throughStage: StageId?): BuildRun {
        val workspace = requireNotNull(workspaceRepository.getWorkspace(workspaceId)) {
            "Workspace not found: $workspaceId"
        }
        val lease = when (val lock = jobLock?.acquire(workspace, BUILD_HOLDER)) {
            null -> null

            is LockResult.Locked ->
                error("Cannot start build: \"${lock.holderName}\" is changing this workspace; wait for it to finish.")

            is LockResult.Acquired -> lock.handle
        }
        return try {
            queue(workspaceId, driverInstanceId, throughStage)
        } finally {
            lease?.let { withContext(NonCancellable) { it.release() } }
        }
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private suspend fun queue(workspaceId: String, driverInstanceId: String, throughStage: StageId?): BuildRun {
        settingsStore?.flush(workspaceId)?.getOrThrow()
        // 1. Fetch workspace
        val workspace = requireNotNull(workspaceRepository.getWorkspace(workspaceId)) {
            "Workspace not found: $workspaceId"
        }

        // 2. Readiness gate
        val readiness = readinessPort.forWorkspace(workspace)
        check(readiness.state == EnvironmentReadinessState.READY) {
            "Cannot start build: Environment is not ready (${readiness.state})"
        }

        // 3. Active run check (FR-014)
        val activeRun = runRepository.latestActive(workspaceId)
        check(activeRun == null || !activeRun.state.isActive) {
            "Cannot start build: Workspace already has an active run (${activeRun?.id} in state ${activeRun?.state})"
        }

        // 4. Fetch snapshot and target
        val snapshotId = requireNotNull(workspace.effectiveSnapshotId) {
            "Workspace has no effective configuration snapshot"
        }
        val snapshot = requireNotNull(snapshotRepository.getSnapshot(snapshotId)) {
            "Configuration snapshot not found: $snapshotId"
        }
        val target = requireNotNull(workspaceDeviceRepository.get(workspaceId)) {
            "Workspace has no device configuration"
        }
        val issues = DeviceConfigReadiness.check(target, snapshot)
        check(issues.isEmpty()) {
            "Cannot start build: device configuration is incomplete (${issues.first().message})"
        }

        // 5. Disk pre-flight check (4x firmware archive size, or 40 GiB fallback)
        val availableBytes = requireNotNull(executionPort.checkAvailableDiskSpace(workspace)) {
            "Cannot start build: free space under ${workspace.linuxPath} could not be determined (df failed)"
        }
        val archiveSizeBytes =
            importedArchiveSize(workspace, snapshot) ?: snapshot.acquisition.firmware.archiveSizeBytes ?: 0L
        val minRequiredBytes = if (archiveSizeBytes > 0L) {
            4L * archiveSizeBytes
        } else {
            40L * 1024L * 1024L * 1024L
        }
        check(availableBytes >= minRequiredBytes) {
            val reqGiB = (minRequiredBytes + (1024L * 1024L * 1024L - 1L)) / (1024L * 1024L * 1024L)
            val availGiB = availableBytes / (1024L * 1024L * 1024L)
            "Insufficient disk space on target: required at least $reqGiB GiB, but available is $availGiB GiB. " +
                "Please free up space under ${workspace.linuxPath}."
        }

        // 6. Freeze snapshot & allocate runId (FR-015)
        val runId = UUID.randomUUID().toString()
        val now = Clock.System.now()
        val nowEpoch = System.currentTimeMillis()

        // Copy config.json to runs/<runId>/config.json
        val configJson = ConfigurationSnapshot.canonicalJson.encodeToString(
            ConfigurationSnapshot.serializer(),
            snapshot,
        )
        check(executionPort.uploadFile(workspace, "runs/$runId/config.json", configJson.toByteArray(Charsets.UTF_8))) {
            "Cannot start build: runs/$runId/config.json could not be written to the workspace"
        }

        // 7. Persist run as QUEUED
        val queuedRun = BuildRun(
            id = runId,
            workspaceId = workspaceId,
            snapshotId = snapshot.id,
            state = RunState.QUEUED,
            startedAt = now,
            driverInstanceId = driverInstanceId,
            driverHeartbeatEpochMs = nowEpoch,
            throughStage = throughStage,
        )
        runRepository.upsert(queuedRun)

        // 8. Claim driver lease
        val claimed = runRepository.claimDriver(runId, driverInstanceId, nowEpoch)
        check(claimed) {
            "Failed to claim driver lease for run $runId"
        }

        val stageContext = StageContext(workspace, snapshot, target)

        // 9. Launch orchestrator asynchronously if scope provided, or return queued run
        if (scope != null) {
            scope.launch {
                orchestrator.drive(runId, stageContext, driverInstanceId).collect {}
            }
        }

        return queuedRun
    }

    private companion object {
        val admission = Mutex()
        const val BUILD_HOLDER = "Starting a build"
    }

    /** Size of an already-imported archive (`stat -c %s`), or null when absent/unknown. */
    private suspend fun importedArchiveSize(workspace: Workspace, snapshot: ConfigurationSnapshot): Long? {
        if (snapshot.acquisition.mode != AcquisitionMode.IMPORT_ARCHIVE) return null
        val stat = executionPort.stat(workspace, FirmwareAcquisitionStage.archiveRelPath(snapshot))
        return stat.sizeBytes.takeIf { stat.exists && it > 0 }
    }
}
