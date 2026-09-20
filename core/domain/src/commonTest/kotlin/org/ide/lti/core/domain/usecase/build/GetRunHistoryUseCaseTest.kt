/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.usecase.build

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.ide.lti.core.domain.pipeline.ToolResult
import org.ide.lti.core.domain.ports.FileStat
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.ports.RunHandle
import org.ide.lti.core.domain.ports.StepEvent
import org.ide.lti.core.domain.ports.StepStatus
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.model.run.Artifact
import org.ide.lti.core.model.run.ArtifactKind
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.Presence
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.run.StageOutcome
import org.ide.lti.core.model.run.StageState
import org.ide.lti.core.model.run.StageStep
import org.ide.lti.core.model.run.StepCommand
import org.ide.lti.core.model.run.StepState
import org.ide.lti.core.model.run.VerificationState
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GetRunHistoryUseCaseTest {

    private class FakeRunRepository : RunRepository {
        val runs = MutableStateFlow<Map<String, BuildRun>>(emptyMap())
        val events = mutableMapOf<String, MutableList<String>>()

        override fun observeRuns(workspaceId: String): Flow<List<BuildRun>> =
            runs.map { map -> map.values.filter { it.workspaceId == workspaceId } }

        override suspend fun upsert(run: BuildRun) {
            runs.value = runs.value + (run.id to run)
        }

        override suspend fun appendEvents(runId: String, events: List<String>) {
            this.events.getOrPut(runId) { mutableListOf() }.addAll(events)
        }

        override suspend fun cachedEvents(runId: String, fromSeq: Long): List<String> =
            events[runId] ?: emptyList()

        override suspend fun latestActive(workspaceId: String): BuildRun? =
            runs.value.values.find { it.workspaceId == workspaceId && it.state.isActive }

        override suspend fun getRun(runId: String): BuildRun? = runs.value[runId]

        override suspend fun deleteRun(runId: String) {
            runs.value = runs.value - runId
            events.remove(runId)
        }

        override suspend fun claimDriver(runId: String, instanceId: String, now: Long): Boolean = true
        override suspend fun heartbeatDriver(runId: String, instanceId: String, now: Long) {}
    }

    private class FakeWorkspaceRepository(
        private val workspaces: Map<String, Workspace> = emptyMap(),
    ) : WorkspaceRepository {
        override fun getWorkspaces(): Flow<List<Workspace>> = flowOf(workspaces.values.toList())
        override suspend fun getWorkspace(id: String): Workspace? = workspaces[id]
        override suspend fun saveWorkspace(workspace: Workspace) {}
        override suspend fun deleteWorkspace(id: String) {}
        override fun getRecentProjects(): Flow<List<RecentProject>> = flowOf(emptyList())
        override suspend fun addRecentProject(project: RecentProject) {}
        override suspend fun removeRecentProject(workspaceId: String) {}
        override suspend fun clearRecentProjects() {}
        override suspend fun discoverWorkspacesInWorkDir(basePath: String?): List<RecentProject> = emptyList()
        override suspend fun getWorkspaceSession(workspaceId: String): WorkspaceSession? = null
        override suspend fun saveWorkspaceSession(session: WorkspaceSession) {}
    }

    private class FakePipelineExecutionPort(
        var statResult: (relPath: String) -> FileStat = { FileStat(it, exists = true, sizeBytes = 1024L) },
        var shouldThrowOnStat: Boolean = false,
    ) : PipelineExecutionPort {
        override suspend fun startStep(ws: Workspace, step: StepCommand, idempotencyKey: String): RunHandle =
            RunHandle("step_1", "RUNNING", 1000L)
        override fun observe(serverRunId: String, fromSeq: Long): Flow<StepEvent> = flowOf()
        override suspend fun status(serverRunId: String): StepStatus? = null
        override suspend fun cancel(serverRunId: String): Boolean = true
        override suspend fun uploadFile(ws: Workspace, relPath: String, bytes: ByteArray): Boolean = true
        override suspend fun readFile(ws: Workspace, relPath: String): String? = null
        override suspend fun stat(ws: Workspace, relPath: String): FileStat {
            if (shouldThrowOnStat) throw IllegalStateException("Transport disconnected")
            return statResult(relPath)
        }
        override suspend fun execute(ws: Workspace, command: StepCommand): ToolResult = ToolResult(0, "")
        override suspend fun checkAvailableDiskSpace(ws: Workspace): Long? = 50_000_000_000L
    }

    private fun sampleRun(
        id: String,
        workspaceId: String = "ws-1",
        startedEpochSec: Long,
        artifacts: List<Artifact> = emptyList(),
    ): BuildRun {
        return BuildRun(
            id = id,
            workspaceId = workspaceId,
            snapshotId = "snap-1",
            state = RunState.SUCCEEDED,
            startedAt = Instant.fromEpochSeconds(startedEpochSec),
            endedAt = Instant.fromEpochSeconds(startedEpochSec + 60),
            stages = listOf(
                StageOutcome(stageId = StageId.BUILD_FLASHABLE_ZIP, state = StageState.EXECUTED),
            ),
            steps = listOf(
                StageStep(
                    stageId = StageId.BUILD_FLASHABLE_ZIP,
                    index = 0,
                    idempotencyKey = "key-$id-0",
                    command = StepCommand(toolId = "build", args = listOf("all")),
                    state = StepState.COMPLETED,
                    exitCode = 0,
                ),
            ),
            artifacts = artifacts,
        )
    }

    private val testWorkspace = Workspace(
        id = "ws-1",
        name = "Workspace 1",
        path = "/home/lti/LtiRomWorkDir/workspaces/ws-1",
    )

    @Test
    fun testNewestFirstOrdering() = runTest {
        val runRepo = FakeRunRepository()
        val wsRepo = FakeWorkspaceRepository(mapOf(testWorkspace.id to testWorkspace))
        val execPort = FakePipelineExecutionPort()

        val run1 = sampleRun("run-old", startedEpochSec = 1000L)
        val run2 = sampleRun("run-newest", startedEpochSec = 3000L)
        val run3 = sampleRun("run-middle", startedEpochSec = 2000L)

        runRepo.upsert(run1)
        runRepo.upsert(run2)
        runRepo.upsert(run3)

        val useCase = GetRunHistoryUseCase(
            runRepository = runRepo,
            workspaceRepository = wsRepo,
            executionPort = execPort,
        )

        val history = useCase("ws-1")
        assertEquals(3, history.size)
        assertEquals("run-newest", history[0].id)
        assertEquals("run-middle", history[1].id)
        assertEquals("run-old", history[2].id)
    }

    @Test
    fun testMissingArtifactPresenceWhenStatFails() = runTest {
        val runRepo = FakeRunRepository()
        val wsRepo = FakeWorkspaceRepository(mapOf(testWorkspace.id to testWorkspace))
        val execPort = FakePipelineExecutionPort(
            statResult = { path -> FileStat(path, exists = false) },
        )

        val artifact = Artifact(
            kind = ArtifactKind.FLASHABLE_ZIP,
            linuxPath = "out/target.zip",
            presence = Presence.PRESENT,
        )
        val run = sampleRun("run-1", startedEpochSec = 1000L, artifacts = listOf(artifact))
        runRepo.upsert(run)

        val useCase = GetRunHistoryUseCase(
            runRepository = runRepo,
            workspaceRepository = wsRepo,
            executionPort = execPort,
        )

        val history = useCase("ws-1")
        assertEquals(1, history.size)
        val updatedArtifact = history[0].artifacts[0]
        assertEquals(Presence.MISSING, updatedArtifact.presence)
    }

    @Test
    fun testRecordSurvivesArtifactDeletion() = runTest {
        val runRepo = FakeRunRepository()
        val wsRepo = FakeWorkspaceRepository(mapOf(testWorkspace.id to testWorkspace))
        val execPort = FakePipelineExecutionPort(
            statResult = { path -> FileStat(path, exists = false) },
        )

        val artifact = Artifact(
            kind = ArtifactKind.FLASHABLE_ZIP,
            linuxPath = "out/target.zip",
            presence = Presence.PRESENT,
        )
        val run = sampleRun("run-1", startedEpochSec = 1000L, artifacts = listOf(artifact))
        runRepo.upsert(run)

        val useCase = GetRunHistoryUseCase(
            runRepository = runRepo,
            workspaceRepository = wsRepo,
            executionPort = execPort,
        )

        val history = useCase("ws-1")
        // The run record itself survives intact even when its artifact file is gone
        assertEquals(1, history.size)
        assertEquals("run-1", history[0].id)
        assertEquals(RunState.SUCCEEDED, history[0].state)
        assertEquals(1, history[0].steps.size)
        assertEquals(Presence.MISSING, history[0].artifacts[0].presence)

        // Verifying it was updated in repository
        val storedRun = runRepo.getRun("run-1")
        assertNotNull(storedRun)
        assertEquals(Presence.MISSING, storedRun.artifacts[0].presence)
    }

    @Test
    fun testOfflineReturnsCachedPresence() = runTest {
        val runRepo = FakeRunRepository()
        val wsRepo = FakeWorkspaceRepository(mapOf(testWorkspace.id to testWorkspace))
        val execPort = FakePipelineExecutionPort(
            shouldThrowOnStat = true, // Simulating server offline
        )

        val artifact = Artifact(
            kind = ArtifactKind.FLASHABLE_ZIP,
            linuxPath = "out/target.zip",
            presence = Presence.PRESENT,
        )
        val run = sampleRun("run-1", startedEpochSec = 1000L, artifacts = listOf(artifact))
        runRepo.upsert(run)

        val useCase = GetRunHistoryUseCase(
            runRepository = runRepo,
            workspaceRepository = wsRepo,
            executionPort = execPort,
        )

        val history = useCase("ws-1")
        assertEquals(1, history.size)
        // Presence remains PRESENT (cached value preserved)
        assertEquals(Presence.PRESENT, history[0].artifacts[0].presence)
    }

    @Test
    fun testOfflineWhenExecutionPortNull() = runTest {
        val runRepo = FakeRunRepository()

        val artifact = Artifact(
            kind = ArtifactKind.FLASHABLE_ZIP,
            linuxPath = "out/target.zip",
            presence = Presence.PRESENT,
        )
        val run = sampleRun("run-1", startedEpochSec = 1000L, artifacts = listOf(artifact))
        runRepo.upsert(run)

        val useCase = GetRunHistoryUseCase(
            runRepository = runRepo,
            workspaceRepository = null,
            executionPort = null,
        )

        val history = useCase("ws-1")
        assertEquals(1, history.size)
        assertEquals(Presence.PRESENT, history[0].artifacts[0].presence)
    }
}
