/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.ide.lti.core.domain.pipeline.stages.FirmwareAcquisitionStage
import org.ide.lti.core.domain.ports.StepEvent
import org.ide.lti.core.domain.ports.StepStatus
import org.ide.lti.core.domain.repository.run.InMemoryRunRepository
import org.ide.lti.core.domain.repository.snapshot.InMemorySnapshotRepository
import org.ide.lti.core.domain.repository.target.TargetRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.domain.usecase.build.CancelRunUseCase
import org.ide.lti.core.domain.usecase.build.ReattachRunUseCase
import org.ide.lti.core.model.run.*
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceSession
import kotlin.test.*

class PipelineResumeTest {

    private val fixture = PipelineFixture()
    private val ws = fixture.workspace.linuxPath!!

    private fun globalPort() = SimulatedExecutionPort(ws).apply {
        archiveSha256 = fixture.snapshotGlobalOta.acquisition.firmware.sha256
    }

    private fun orchestrator(port: SimulatedExecutionPort, repo: InMemoryRunRepository) =
        PipelineOrchestrator(port, repo, FakeVendoredFiles(true))

    private class FakeWorkspaceRepo(vararg initial: Workspace) : WorkspaceRepository {
        val workspaces = initial.associateBy { it.id }.toMutableMap()
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

    @Test
    fun inFlightStepReobservedFromLastSeqPlusOne() = runTest {
        val port = globalPort()
        val repo = InMemoryRunRepository()
        val ctx = fixture.globalOtaContext()
        val runId = "run-resume-1"
        val now = Clock.System.now()

        val initialRun = BuildRun(
            id = runId,
            workspaceId = ctx.workspace.id,
            snapshotId = ctx.snapshot.id,
            state = RunState.RUNNING,
            startedAt = now,
            stages = listOf(StageOutcome(stageId = StageId.FIRMWARE_ACQUISITION, state = StageState.RUNNING, startedAt = now)),
            steps = listOf(
                StageStep(
                    stageId = StageId.FIRMWARE_ACQUISITION,
                    index = 0,
                    serverRunId = "srv-inflight-1",
                    idempotencyKey = "key-1",
                    command = StepCommand("mkdir", listOf("-p", "$ws/firmware/downloaded")),
                    state = StepState.RUNNING,
                    lastSeq = 5L,
                ),
            ),
            lastSeqByStep = mapOf("srv-inflight-1" to 5L),
            driverInstanceId = "driver-1",
            driverHeartbeatEpochMs = System.currentTimeMillis(),
        )
        repo.upsert(initialRun)

        port.statuses["srv-inflight-1"] = StepStatus(
            runId = "srv-inflight-1",
            status = "RUNNING",
            lastSeq = 5L,
            pid = 1234L,
        )

        var requestedFromSeq: Long? = null
        port.observedFlows["srv-inflight-1"] = { fromSeq ->
            requestedFromSeq = fromSeq
            flowOf(
                StepEvent.Output(seq = 6L, text = "step progress chunk\n"),
                StepEvent.Finished(exitCode = 0),
            )
        }

        val events = orchestrator(port, repo).resume(initialRun, ctx, "driver-1").toList()

        assertEquals(6L, requestedFromSeq, "Re-observe must start from lastSeq + 1 (5 + 1 = 6)")
        assertTrue(events.any { it is PipelineEvent.StepOutput && it.text.contains("step progress chunk") })
        assertEquals(PipelineEvent.RunFinished(RunState.SUCCEEDED), events.last())

        val finalRun = assertNotNull(repo.getRun(runId))
        assertEquals(RunState.SUCCEEDED, finalRun.state)
        assertEquals(6L, finalRun.lastSeqByStep["srv-inflight-1"])
    }

    @Test
    fun completedWhileAbsentContinuesNextStageAndEmitsRunResumed() = runTest {
        val port = globalPort()
        val repo = InMemoryRunRepository()
        val ctx = fixture.globalOtaContext()
        val runId = "run-resume-2"
        val now = Clock.System.now()

        val archiveRel = FirmwareAcquisitionStage.archiveRelPath(ctx.snapshot)
        port.files[ctx.abs(archiveRel)] = ByteArray(100)
        port.files["${ctx.abs(archiveRel)}.partial"] = ByteArray(100)

        val pausedRun = BuildRun(
            id = runId,
            workspaceId = ctx.workspace.id,
            snapshotId = ctx.snapshot.id,
            state = RunState.PAUSED_WAITING_FOR_APP,
            startedAt = now,
            stages = listOf(
                StageOutcome(stageId = StageId.FIRMWARE_ACQUISITION, state = StageState.RUNNING, startedAt = now),
            ),
            steps = listOf(
                StageStep(
                    stageId = StageId.FIRMWARE_ACQUISITION,
                    index = 0,
                    serverRunId = "srv-mkdir-0",
                    idempotencyKey = "key-0",
                    command = StepCommand("mkdir", listOf("-p", "$ws/firmware/downloaded")),
                    state = StepState.COMPLETED,
                    lastSeq = 1L,
                ),
                StageStep(
                    stageId = StageId.FIRMWARE_ACQUISITION,
                    index = 1,
                    serverRunId = "srv-curl-1",
                    idempotencyKey = "key-1",
                    command = StepCommand("curl", listOf("-fL", "--retry", "3", "-C", "-", "-o", "$ws/$archiveRel.partial", "https://url")),
                    state = StepState.COMPLETED,
                    lastSeq = 10L,
                ),
                StageStep(
                    stageId = StageId.FIRMWARE_ACQUISITION,
                    index = 2,
                    serverRunId = "srv-file-2",
                    idempotencyKey = "key-2",
                    command = StepCommand("file", listOf("-b", "--mime-type", "$ws/$archiveRel.partial")),
                    state = StepState.COMPLETED,
                    lastSeq = 11L,
                ),
                StageStep(
                    stageId = StageId.FIRMWARE_ACQUISITION,
                    index = 3,
                    serverRunId = "srv-mv-3",
                    idempotencyKey = "key-3",
                    command = StepCommand("mv", listOf("$ws/$archiveRel.partial", "$ws/$archiveRel")),
                    state = StepState.COMPLETED,
                    lastSeq = 12L,
                ),
                StageStep(
                    stageId = StageId.FIRMWARE_ACQUISITION,
                    index = 4,
                    serverRunId = "srv-sha-4",
                    idempotencyKey = "key-4",
                    command = StepCommand("sha256sum", listOf("$ws/$archiveRel")),
                    state = StepState.COMPLETED,
                    lastSeq = 13L,
                ),
            ),
            lastSeqByStep = mapOf(
                "srv-mkdir-0" to 1L,
                "srv-curl-1" to 10L,
                "srv-file-2" to 11L,
                "srv-mv-3" to 12L,
                "srv-sha-4" to 13L,
            ),
            runtimeValues = mapOf(
                RuntimeKeys.DOWNLOAD_RESUME to "",
                RuntimeKeys.exitOf(RuntimeKeys.DOWNLOAD_RESUME) to "0",
                RuntimeKeys.ARCHIVE_MIME to "application/zip",
                RuntimeKeys.ARCHIVE_SHA256 to fixture.snapshotGlobalOta.acquisition.firmware.sha256!!,
            ),
            driverInstanceId = "driver-1",
            driverHeartbeatEpochMs = System.currentTimeMillis(),
        )
        repo.upsert(pausedRun)

        listOf("srv-mkdir-0", "srv-curl-1", "srv-file-2", "srv-mv-3", "srv-sha-4").forEach { id ->
            port.statuses[id] = StepStatus(runId = id, status = "COMPLETED", exitCode = 0)
        }

        val events = orchestrator(port, repo).resume(pausedRun, ctx, "driver-1").toList()

        // Verify RunResumed was emitted because run was PAUSED_WAITING_FOR_APP
        assertTrue(events.any { it is PipelineEvent.RunResumed && it.stageId == StageId.FIRMWARE_ACQUISITION })
        // Verify stage 0 steps were not re-executed
        assertFalse(port.durableSteps.any { it.toolId == "curl" }, "Stage 0 steps must not be re-executed")
        // Verify stage 0 wrote cache key and pipeline succeeded
        assertTrue(port.has(".cache/FIRMWARE_ACQUISITION.key"))
        assertEquals(PipelineEvent.RunFinished(RunState.SUCCEEDED), events.last())
    }

    @Test
    fun deadOrUnknownStepMapsToInterrupted() = runTest {
        val port = globalPort()
        val repo = InMemoryRunRepository()
        val ctx = fixture.globalOtaContext()
        val runId = "run-resume-3"
        val now = Clock.System.now()

        val interruptedRun = BuildRun(
            id = runId,
            workspaceId = ctx.workspace.id,
            snapshotId = ctx.snapshot.id,
            state = RunState.RUNNING,
            startedAt = now,
            stages = listOf(StageOutcome(stageId = StageId.FIRMWARE_ACQUISITION, state = StageState.RUNNING, startedAt = now)),
            steps = listOf(
                StageStep(
                    stageId = StageId.FIRMWARE_ACQUISITION,
                    index = 0,
                    serverRunId = "srv-dead-0",
                    idempotencyKey = "key-dead",
                    command = StepCommand("curl", listOf("-o", "$ws/firmware/downloaded", "https://url")),
                    state = StepState.RUNNING,
                    lastSeq = 2L,
                ),
            ),
            lastSeqByStep = mapOf("srv-dead-0" to 2L),
            driverInstanceId = "driver-1",
            driverHeartbeatEpochMs = System.currentTimeMillis(),
        )
        repo.upsert(interruptedRun)

        // Status is null (daemon lost track of run after restart)
        port.statuses.remove("srv-dead-0")

        val events = orchestrator(port, repo).resume(interruptedRun, ctx, "driver-1").toList()

        assertEquals(PipelineEvent.StageInterrupted(StageId.FIRMWARE_ACQUISITION), events.filterIsInstance<PipelineEvent.StageInterrupted>().single())
        assertEquals(PipelineEvent.RunFinished(RunState.INTERRUPTED), events.last())

        val run = assertNotNull(repo.getRun(runId))
        assertEquals(RunState.INTERRUPTED, run.state)
        assertEquals(StageState.INTERRUPTED, run.stages.first().state)
    }

    @Test
    fun staleDriverLeaseTakenOverFreshLeaseRejected() = runTest {
        val port = globalPort()
        val repo = InMemoryRunRepository()
        val ctx = fixture.globalOtaContext()
        val runId = "run-resume-4"
        val now = Clock.System.now()

        // Case 1: Fresh lease (< 45s ago) cannot be taken over
        val freshRun = BuildRun(
            id = runId,
            workspaceId = ctx.workspace.id,
            snapshotId = ctx.snapshot.id,
            state = RunState.RUNNING,
            startedAt = now,
            driverInstanceId = "driver-original",
            driverHeartbeatEpochMs = System.currentTimeMillis() - 10_000L,
        )
        repo.upsert(freshRun)

        assertFailsWith<IllegalStateException> {
            orchestrator(port, repo).resume(freshRun, ctx, "driver-new").toList()
        }

        // Case 2: Stale lease (> 45s ago) is taken over
        val staleRun = freshRun.copy(
            driverHeartbeatEpochMs = System.currentTimeMillis() - 60_000L,
        )
        repo.upsert(staleRun)

        // Resuming with new driver succeeds and updates driverInstanceId
        val events = orchestrator(port, repo).resume(staleRun, ctx, "driver-new").toList()
        assertEquals(PipelineEvent.RunFinished(RunState.SUCCEEDED), events.last())
        val finalRun = assertNotNull(repo.getRun(runId))
        assertEquals("driver-new", finalRun.driverInstanceId)
    }

    @Test
    fun cancelRunUseCaseTransitionsToCancelledAndCleansStageTempOutputs() = runTest {
        val port = globalPort()
        val repo = InMemoryRunRepository()
        val ctx = fixture.globalOtaContext()
        val runId = "run-cancel-1"
        val now = Clock.System.now()

        val stageDir = "$ws/out/package/.stage"
        port.files["$stageDir/updater-script"] = ByteArray(10)

        val run = BuildRun(
            id = runId,
            workspaceId = ctx.workspace.id,
            snapshotId = ctx.snapshot.id,
            state = RunState.RUNNING,
            startedAt = now,
            stages = listOf(
                StageOutcome(stageId = StageId.BUILD_FLASHABLE_ZIP, state = StageState.RUNNING, startedAt = now),
            ),
            steps = listOf(
                StageStep(
                    stageId = StageId.BUILD_FLASHABLE_ZIP,
                    index = 0,
                    serverRunId = "srv-to-cancel",
                    idempotencyKey = "key-cancel",
                    command = StepCommand("zip", listOf("rom.zip")),
                    state = StepState.RUNNING,
                ),
            ),
            driverInstanceId = "driver-1",
            driverHeartbeatEpochMs = System.currentTimeMillis(),
        )
        repo.upsert(run)

        val fakeWorkspaceRepo = FakeWorkspaceRepo(ctx.workspace)
        val cancelUseCase = CancelRunUseCase(repo, port, fakeWorkspaceRepo)
        val result = cancelUseCase(ctx.workspace.id, runId)
        assertTrue(result.isSuccess)

        val cancelledRun = assertNotNull(repo.getRun(runId))
        assertEquals(RunState.CANCELLED, cancelledRun.state)
        assertEquals(StageState.CANCELLED, cancelledRun.stages.first().state)
        assertEquals(StepState.CANCELLED, cancelledRun.steps.first().state)
        assertTrue(cancelledRun.artifacts.all { it.verification != VerificationState.VERIFIED })
        assertTrue(port.syncCalls.any { it.toolId == "rm" && "-rf" in it.args })
    }

    @Test
    fun reattachRunUseCaseResumesActiveRun() = runTest {
        val port = globalPort()
        val repo = InMemoryRunRepository()
        val ctx = fixture.globalOtaContext()
        val runId = "run-reattach-1"
        val now = Clock.System.now()

        val snapshotRepo = InMemorySnapshotRepository()
        snapshotRepo.saveSnapshot(ctx.snapshot)
        val targetRepo = FakeTargetRepo(fixture.targetGlobal)
        val workspaceRepo = FakeWorkspaceRepo(ctx.workspace)

        val run = BuildRun(
            id = runId,
            workspaceId = ctx.workspace.id,
            snapshotId = ctx.snapshot.id,
            state = RunState.PAUSED_WAITING_FOR_APP,
            startedAt = now,
        )
        repo.upsert(run)

        val orch = orchestrator(port, repo)
        val reattachUseCase = ReattachRunUseCase(
            workspaceRepository = workspaceRepo,
            snapshotRepository = snapshotRepo,
            targetRepository = targetRepo,
            runRepository = repo,
            orchestrator = orch,
        )

        val flow = reattachUseCase(ctx.workspace.id, runId, "driver-reattach")
        assertNotNull(flow)
        val events = flow.toList()
        val failed = events.filterIsInstance<PipelineEvent.StageFailed>().firstOrNull()
        assertNull(failed, "Stage should not fail: ${failed?.message}")
        assertEquals(PipelineEvent.RunFinished(RunState.SUCCEEDED), events.last())
    }
}
