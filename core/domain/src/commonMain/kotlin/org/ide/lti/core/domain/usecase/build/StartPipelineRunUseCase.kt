package org.ide.lti.core.domain.usecase.build

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import org.ide.lti.core.domain.pipeline.PipelineOrchestrator
import org.ide.lti.core.domain.pipeline.StageContext
import org.ide.lti.core.domain.pipeline.stages.FirmwareAcquisitionStage
import org.ide.lti.core.domain.ports.EnvironmentReadinessPort
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.domain.repository.snapshot.SnapshotRepository
import org.ide.lti.core.domain.repository.target.TargetRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.setup.EnvironmentReadinessState
import org.ide.lti.core.model.workspace.AcquisitionMode
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.Workspace
import java.util.UUID

public class StartPipelineRunUseCase(
    private val workspaceRepository: WorkspaceRepository,
    private val snapshotRepository: SnapshotRepository,
    private val targetRepository: TargetRepository,
    private val readinessPort: EnvironmentReadinessPort,
    private val executionPort: PipelineExecutionPort,
    private val runRepository: RunRepository,
    private val orchestrator: PipelineOrchestrator,
    private val scope: CoroutineScope? = null,
) {
    public suspend operator fun invoke(
        workspaceId: String,
        driverInstanceId: String = "driver_${UUID.randomUUID()}",
    ): Result<BuildRun> = runCatching {
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
        val targetId = requireNotNull(workspace.targetBinding?.profileId) {
            "Workspace has no target device binding"
        }
        val target = requireNotNull(
            targetRepository.getAvailableTargets().first().find { it.id == targetId },
        ) {
            "Target device profile not found: $targetId"
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
        executionPort.uploadFile(
            workspace,
            "runs/$runId/config.json",
            configJson.toByteArray(Charsets.UTF_8),
        )

        // 7. Persist run as QUEUED
        val queuedRun = BuildRun(
            id = runId,
            workspaceId = workspaceId,
            snapshotId = snapshot.id,
            state = RunState.QUEUED,
            startedAt = now,
            driverInstanceId = driverInstanceId,
            driverHeartbeatEpochMs = nowEpoch,
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

        queuedRun
    }

    /** Size of an already-imported archive (`stat -c %s`), or null when absent/unknown. */
    private suspend fun importedArchiveSize(workspace: Workspace, snapshot: ConfigurationSnapshot): Long? {
        if (snapshot.acquisition.mode != AcquisitionMode.IMPORT_ARCHIVE) return null
        val stat = executionPort.stat(workspace, FirmwareAcquisitionStage.archiveRelPath(snapshot))
        return stat.sizeBytes.takeIf { stat.exists && it > 0 }
    }
}
