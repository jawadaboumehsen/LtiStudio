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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.domain.usecase.build.GetRunHistoryUseCase
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.run.Artifact
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.run.StageOutcome
import org.ide.lti.core.model.run.StageState
import org.ide.lti.core.model.run.StageStep
import org.ide.lti.core.model.run.StepCommand
import org.ide.lti.core.model.run.StepState
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceSession
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RunHistoryViewModelTest {

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

        override suspend fun cachedEvents(runId: String, fromSeq: Long): List<String> = events[runId] ?: emptyList()

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

    private fun sampleRun(
        id: String,
        workspaceId: String = "ws-1",
        startedEpochSec: Long,
        stages: List<StageOutcome> = emptyList(),
        artifacts: List<Artifact> = emptyList(),
    ): BuildRun = BuildRun(
        id = id,
        workspaceId = workspaceId,
        snapshotId = "snap-1",
        state = RunState.SUCCEEDED,
        startedAt = Instant.fromEpochSeconds(startedEpochSec),
        endedAt = Instant.fromEpochSeconds(startedEpochSec + 100),
        stages = stages,
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

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testListRunsForWorkspace() = runTest(testDispatcher) {
        val wsRepo = FakeWorkspaceRepository()
        val wsManager = WorkspaceManager(wsRepo, CoroutineScope(testDispatcher))
        val runRepo = FakeRunRepository()
        val getRunHistoryUseCase = GetRunHistoryUseCase(runRepo, wsRepo)

        val ws1 = Workspace(id = "ws-1", name = "Test WS", path = "/tmp/ws1")
        wsRepo.saveWorkspace(ws1)

        val run1 = sampleRun("run-1", workspaceId = "ws-1", startedEpochSec = 1000L)
        val run2 = sampleRun("run-2", workspaceId = "ws-1", startedEpochSec = 2000L)
        runRepo.upsert(run1)
        runRepo.upsert(run2)

        val viewModel = RunHistoryViewModel(
            workspaceManager = wsManager,
            getRunHistoryUseCase = getRunHistoryUseCase,
            runRepository = runRepo,
        )

        wsManager.openWorkspace(ws1.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.runs.size)
        // Ordered newest first
        assertEquals("run-2", state.runs[0].id)
        assertEquals("run-1", state.runs[1].id)
        assertEquals("run-2", state.selectedRun?.id)
    }

    @Test
    fun testSelectRunAndStageTimeline() = runTest(testDispatcher) {
        val wsRepo = FakeWorkspaceRepository()
        val wsManager = WorkspaceManager(wsRepo, CoroutineScope(testDispatcher))
        val runRepo = FakeRunRepository()
        val getRunHistoryUseCase = GetRunHistoryUseCase(runRepo, wsRepo)

        val ws1 = Workspace(id = "ws-1", name = "Test WS", path = "/tmp/ws1")
        wsRepo.saveWorkspace(ws1)

        val stages1 = listOf(
            StageOutcome(stageId = StageId.FIRMWARE_ACQUISITION, state = StageState.EXECUTED, durationMs = 2000L),
            StageOutcome(stageId = StageId.BUILD_FLASHABLE_ZIP, state = StageState.EXECUTED, durationMs = 8000L),
        )
        val run1 = sampleRun("run-1", workspaceId = "ws-1", startedEpochSec = 1000L, stages = stages1)

        val stages2 = listOf(
            StageOutcome(stageId = StageId.FIRMWARE_ACQUISITION, state = StageState.FAILED, message = "Network error"),
        )
        val run2 = sampleRun("run-2", workspaceId = "ws-1", startedEpochSec = 2000L, stages = stages2)

        runRepo.upsert(run1)
        runRepo.upsert(run2)

        val viewModel = RunHistoryViewModel(
            workspaceManager = wsManager,
            getRunHistoryUseCase = getRunHistoryUseCase,
            runRepository = runRepo,
        )

        wsManager.openWorkspace(ws1.id)
        advanceUntilIdle()

        // By default, latest run (run-2) is selected
        assertEquals("run-2", viewModel.uiState.value.selectedRun?.id)
        assertEquals(1, viewModel.uiState.value.selectedRun?.stages?.size)
        assertEquals(StageState.FAILED, viewModel.uiState.value.selectedRun?.stages?.first()?.state)

        // Select run-1
        viewModel.selectRun(run1)
        advanceUntilIdle()

        val selected = viewModel.uiState.value.selectedRun
        assertNotNull(selected)
        assertEquals("run-1", selected.id)
        assertEquals(2, selected.stages.size)
        assertEquals(StageId.BUILD_FLASHABLE_ZIP, selected.stages[1].stageId)
    }

    @Test
    fun testCachedLogRetrieval() = runTest(testDispatcher) {
        val wsRepo = FakeWorkspaceRepository()
        val wsManager = WorkspaceManager(wsRepo, CoroutineScope(testDispatcher))
        val runRepo = FakeRunRepository()
        val getRunHistoryUseCase = GetRunHistoryUseCase(runRepo, wsRepo)

        val ws1 = Workspace(id = "ws-1", name = "Test WS", path = "/tmp/ws1")
        wsRepo.saveWorkspace(ws1)

        val run1 = sampleRun("run-1", workspaceId = "ws-1", startedEpochSec = 1000L)
        runRepo.upsert(run1)
        runRepo.appendEvents("run-1", listOf("Log line 1", "Log line 2", "Build completed"))

        val viewModel = RunHistoryViewModel(
            workspaceManager = wsManager,
            getRunHistoryUseCase = getRunHistoryUseCase,
            runRepository = runRepo,
        )

        wsManager.openWorkspace(ws1.id)
        advanceUntilIdle()

        val logs = viewModel.uiState.value.selectedRunLogs
        assertEquals(3, logs.size)
        assertEquals("Log line 1", logs[0])
        assertEquals("Build completed", logs[2])
    }

    @Test
    fun testLogNotCachedFallback() = runTest(testDispatcher) {
        val wsRepo = FakeWorkspaceRepository()
        val wsManager = WorkspaceManager(wsRepo, CoroutineScope(testDispatcher))
        val runRepo = FakeRunRepository()
        val getRunHistoryUseCase = GetRunHistoryUseCase(runRepo, wsRepo)

        val ws1 = Workspace(id = "ws-1", name = "Test WS", path = "/tmp/ws1")
        wsRepo.saveWorkspace(ws1)

        // Run has no cached events
        val run1 = sampleRun("run-no-logs", workspaceId = "ws-1", startedEpochSec = 1000L)
        runRepo.upsert(run1)

        val viewModel = RunHistoryViewModel(
            workspaceManager = wsManager,
            getRunHistoryUseCase = getRunHistoryUseCase,
            runRepository = runRepo,
        )

        wsManager.openWorkspace(ws1.id)
        advanceUntilIdle()

        // When no logs are cached in repository, selectedRunLogs is empty
        val logs = viewModel.uiState.value.selectedRunLogs
        assertTrue(logs.isEmpty())
    }

    @Test
    fun testDeleteRunUpdatesHistoryAndSelection() = runTest(testDispatcher) {
        val wsRepo = FakeWorkspaceRepository()
        val wsManager = WorkspaceManager(wsRepo, CoroutineScope(testDispatcher))
        val runRepo = FakeRunRepository()
        val getRunHistoryUseCase = GetRunHistoryUseCase(runRepo, wsRepo)

        val ws1 = Workspace(id = "ws-1", name = "Test WS", path = "/tmp/ws1")
        wsRepo.saveWorkspace(ws1)

        val run1 = sampleRun("run-1", workspaceId = "ws-1", startedEpochSec = 1000L)
        val run2 = sampleRun("run-2", workspaceId = "ws-1", startedEpochSec = 2000L)
        runRepo.upsert(run1)
        runRepo.upsert(run2)

        val viewModel = RunHistoryViewModel(
            workspaceManager = wsManager,
            getRunHistoryUseCase = getRunHistoryUseCase,
            runRepository = runRepo,
        )

        wsManager.openWorkspace(ws1.id)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.runs.size)
        assertEquals("run-2", viewModel.uiState.value.selectedRun?.id)

        // Delete currently selected run-2
        viewModel.deleteRun("run-2")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.runs.size)
        assertEquals("run-1", state.runs[0].id)
        assertEquals("run-1", state.selectedRun?.id)
        assertNull(runRepo.getRun("run-2"))
    }
}
