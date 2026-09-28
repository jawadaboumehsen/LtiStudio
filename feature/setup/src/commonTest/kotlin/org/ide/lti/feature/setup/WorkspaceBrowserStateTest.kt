/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import org.ide.lti.core.domain.ports.EnvironmentReadinessPort
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.domain.usecase.workspace.GetRecentWorkspacesUseCase
import org.ide.lti.core.domain.usecase.workspace.OpenWorkspaceByPathUseCase
import org.ide.lti.core.domain.usecase.workspace.OpenWorkspaceUseCase
import org.ide.lti.core.domain.usecase.workspace.RemoveRecentWorkspaceUseCase
import org.ide.lti.core.model.setup.EnvironmentReadiness
import org.ide.lti.core.model.setup.EnvironmentReadinessState
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceSession
import org.ide.lti.core.model.workspace.WorkspaceType
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass")
class WorkspaceBrowserStateTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private open class FakeWorkspaceRepository : WorkspaceRepository {
        val recents = MutableStateFlow<List<RecentProject>>(emptyList())
        val workspaces = mutableMapOf<String, Workspace>()
        var deleteWorkspaceCallCount = 0
        var removeRecentProjectCallCount = 0

        override fun getWorkspaces(): Flow<List<Workspace>> = flowOf(workspaces.values.toList())
        override suspend fun getWorkspace(id: String): Workspace? = workspaces[id]
        open override suspend fun saveWorkspace(workspace: Workspace) {
            workspaces[workspace.id] = workspace
        }
        override suspend fun deleteWorkspace(id: String) {
            deleteWorkspaceCallCount++
            workspaces.remove(id)
        }
        override suspend fun getWorkspaceSession(workspaceId: String): WorkspaceSession? = null
        override suspend fun saveWorkspaceSession(session: WorkspaceSession) {}
        override fun getRecentProjects(): Flow<List<RecentProject>> = recents.asStateFlow()
        override suspend fun addRecentProject(project: RecentProject) {
            recents.value = listOf(project) + recents.value.filter { it.workspaceId != project.workspaceId }
        }
        override suspend fun removeRecentProject(workspaceId: String) {
            removeRecentProjectCallCount++
            recents.value = recents.value.filter { it.workspaceId != workspaceId }
        }
        override suspend fun clearRecentProjects() {
            recents.value = emptyList()
        }
        override suspend fun discoverWorkspacesInWorkDir(basePath: String?): List<RecentProject> = emptyList()
    }

    private class FakeEnvironmentReadinessPort(
        var readiness: EnvironmentReadiness = EnvironmentReadiness(
            state = EnvironmentReadinessState.ENV_ABSENT,
            failingCheck = "WSL2 distribution not detected.",
        ),
    ) : EnvironmentReadinessPort {
        val flow = MutableStateFlow(readiness)

        override fun observe(): Flow<EnvironmentReadiness> = flow.asStateFlow()
        override suspend fun refresh(): EnvironmentReadiness = readiness
        override suspend fun forWorkspace(workspace: Workspace): EnvironmentReadiness = readiness
    }

    private fun defaultToolchainFlow(
        steps: List<SetupStepDetail> = SetupStepStage.entries.map {
            SetupStepDetail(
                stage = it,
                title = it.displayName,
                description = "OK",
                status = StepStatus.SUCCESS,
                provenance = StepProvenance.LIVE,
            )
        },
        isChecking: Boolean = false,
    ): StateFlow<ToolchainSetupState> = MutableStateFlow(
        ToolchainSetupState(
            isChecking = isChecking,
            steps = steps,
        ),
    )

    @Test
    fun testCachedOpenWorksWithoutExecutionReadiness() = runTest(testDispatcher) {
        val repo = FakeWorkspaceRepository()
        val offlinePort = FakeEnvironmentReadinessPort(
            EnvironmentReadiness(
                state = EnvironmentReadinessState.ENV_ABSENT,
                failingCheck = "WSL2 execution environment absent",
            ),
        )
        val now = Clock.System.now()
        val cachedWorkspace = Workspace(
            id = "ws_offline_1",
            name = "Offline ROM Project",
            path = "C:/Projects/OfflineRom",
            type = WorkspaceType.LOCAL,
            lastOpened = now,
        )
        repo.saveWorkspace(cachedWorkspace)
        repo.addRecentProject(
            RecentProject(
                workspaceId = cachedWorkspace.id,
                name = cachedWorkspace.name,
                path = cachedWorkspace.path,
                lastOpened = now,
            ),
        )

        val offlineToolchain = MutableStateFlow(
            ToolchainSetupState(
                isChecking = false,
                steps = listOf(
                    SetupStepDetail(
                        stage = SetupStepStage.WSL_DETECTION,
                        title = "WSL",
                        description = "Offline",
                        status = StepStatus.FAILED,
                        error = "WSL2 execution environment absent",
                    ),
                ),
            ),
        )
        val state = WorkspaceBrowserState(
            scope = backgroundScope,
            getRecentWorkspacesUseCase = GetRecentWorkspacesUseCase(repo),
            openWorkspaceUseCase = OpenWorkspaceUseCase(repo),
            removeRecentWorkspaceUseCase = RemoveRecentWorkspaceUseCase(repo),
            toolchainSetupState = offlineToolchain,
            environmentReadinessPort = offlinePort,
        )

        val collectJob = backgroundScope.launch(testDispatcher) {
            state.filteredProjects.collect()
        }

        assertEquals(1, state.recentProjects.value.size)
        var openedWorkspace: Workspace? = null
        state.openRecentProject(state.recentProjects.value.first()) { opened ->
            openedWorkspace = opened
        }

        assertNull(openedWorkspace, "Workspace must fail closed when environment is offline")
        assertNotNull(state.workspaceError.value, "WorkspaceError must be reported when environment is offline")
        assertTrue(state.workspaceError.value?.reason?.contains("WSL2") == true)
        collectJob.cancel()
    }

    @Test
    fun testCreationReportsPrerequisitesWhenOffline() = runTest(testDispatcher) {
        val repo = FakeWorkspaceRepository()
        val offlinePort = FakeEnvironmentReadinessPort(
            EnvironmentReadiness(
                state = EnvironmentReadinessState.ENV_ABSENT,
                failingCheck = "WSL2 distribution not found. Remote workspace creation requires WSL2.",
            ),
        )
        val state = WorkspaceBrowserState(
            scope = backgroundScope,
            getRecentWorkspacesUseCase = GetRecentWorkspacesUseCase(repo),
            openWorkspaceUseCase = OpenWorkspaceUseCase(repo),
            removeRecentWorkspaceUseCase = RemoveRecentWorkspaceUseCase(repo),
            toolchainSetupState = defaultToolchainFlow(),
            environmentReadinessPort = offlinePort,
        )

        val offlineCheck = state.checkCreationPrerequisites()
        assertTrue(offlineCheck.isFailure, "Creation must fail and report prerequisite when execution service is absent")
        val errorMsg = offlineCheck.exceptionOrNull()?.message.orEmpty()
        assertTrue(errorMsg.contains("WSL2") || errorMsg.contains("ENV_ABSENT"), "Error must report prerequisite detail: $errorMsg")

        // Now test when environment is ready
        offlinePort.readiness = EnvironmentReadiness(
            state = EnvironmentReadinessState.READY,
            workDirLinuxPath = "/home/lti/LtiRomWorkDir",
        )
        val readyCheck = state.checkCreationPrerequisites()
        assertTrue(readyCheck.isSuccess, "Creation must succeed when execution environment is READY")
    }

    @Test
    fun testSearchFilterMatchesNameOrPathCaseInsensitively() = runTest(testDispatcher) {
        val repo = FakeWorkspaceRepository()
        val p1 = RecentProject("id1", "RedMagicKernel", "/mnt/c/android/kernel", Clock.System.now())
        val p2 = RecentProject("id2", "AOSP_Build", "/mnt/d/builds/aosp", Clock.System.now())
        val p3 = RecentProject("id3", "CustomRecovery", "/home/lti/twrp", Clock.System.now())
        repo.recents.value = listOf(p1, p2, p3)

        val state = WorkspaceBrowserState(
            scope = backgroundScope,
            getRecentWorkspacesUseCase = GetRecentWorkspacesUseCase(repo),
            openWorkspaceUseCase = OpenWorkspaceUseCase(repo),
            removeRecentWorkspaceUseCase = RemoveRecentWorkspaceUseCase(repo),
            toolchainSetupState = defaultToolchainFlow(),
        )

        val collectJob = backgroundScope.launch(testDispatcher) {
            state.filteredProjects.collect()
        }

        // Initially matches all
        assertEquals(3, state.filteredProjects.value.size)

        // Case-insensitive name match
        state.onSearchQueryChange("redmagic")
        assertEquals(1, state.filteredProjects.value.size)
        assertEquals("RedMagicKernel", state.filteredProjects.value.first().name)

        // Case-insensitive path match
        state.onSearchQueryChange("twrp")
        assertEquals(1, state.filteredProjects.value.size)
        assertEquals("CustomRecovery", state.filteredProjects.value.first().name)

        // Non-matching query
        state.onSearchQueryChange("nonexistent_pattern")
        assertEquals(0, state.filteredProjects.value.size)

        // Empty query restores all
        state.onSearchQueryChange("   ")
        assertEquals(3, state.filteredProjects.value.size)

        collectJob.cancel()
    }

    @Test
    fun testRemovalOnlyDeletesRecentRecordNotWorkspace() = runTest(testDispatcher) {
        val repo = FakeWorkspaceRepository()
        val ws = Workspace("w1", "KeepThisWorkspace", "/path/to/keep", WorkspaceType.LOCAL, Clock.System.now())
        repo.saveWorkspace(ws)
        val recent = RecentProject("w1", "KeepThisWorkspace", "/path/to/keep", Clock.System.now())
        repo.recents.value = listOf(recent)

        val state = WorkspaceBrowserState(
            scope = backgroundScope,
            getRecentWorkspacesUseCase = GetRecentWorkspacesUseCase(repo),
            openWorkspaceUseCase = OpenWorkspaceUseCase(repo),
            removeRecentWorkspaceUseCase = RemoveRecentWorkspaceUseCase(repo),
            toolchainSetupState = defaultToolchainFlow(),
        )

        val collectJob = backgroundScope.launch(testDispatcher) {
            state.filteredProjects.collect()
        }

        assertEquals(1, state.filteredProjects.value.size)
        state.removeRecentProject("w1")

        assertEquals(0, repo.recents.value.size, "Recent record must be removed")
        assertEquals(1, repo.removeRecentProjectCallCount)
        assertEquals(0, repo.deleteWorkspaceCallCount, "deleteWorkspace must NOT be called; workspace itself is preserved (FR-002)")
        assertNotNull(repo.getWorkspace("w1"), "Workspace entity in repository must remain intact")

        collectJob.cancel()
    }

    @Test
    fun testSelectedRecentRemovalSelectsNearestRemainingRow() = runTest(testDispatcher) {
        val repo = FakeWorkspaceRepository()
        val p1 = RecentProject("w1", "Workspace1", "/path1", Clock.System.now())
        val p2 = RecentProject("w2", "Workspace2", "/path2", Clock.System.now())
        val p3 = RecentProject("w3", "Workspace3", "/path3", Clock.System.now())
        repo.recents.value = listOf(p1, p2, p3)

        val state = WorkspaceBrowserState(
            scope = backgroundScope,
            getRecentWorkspacesUseCase = GetRecentWorkspacesUseCase(repo),
            openWorkspaceUseCase = OpenWorkspaceUseCase(repo),
            removeRecentWorkspaceUseCase = RemoveRecentWorkspaceUseCase(repo),
            toolchainSetupState = defaultToolchainFlow(),
        )

        val collectJob = backgroundScope.launch(testDispatcher) {
            state.filteredProjects.collect()
        }

        // Case 1: Select middle item (w2 at index 1) and remove it -> nearest remaining is w3 (now at index 1)
        state.selectWorkspace("w2")
        assertEquals("w2", state.selectedWorkspaceId.value)
        state.removeRecentProject("w2")
        assertEquals("w3", state.selectedWorkspaceId.value, "Nearest remaining row w3 should be selected after w2 removal")

        // Case 2: Select last item (w3 at index 1) and remove it -> nearest remaining is w1 (index 0)
        state.selectWorkspace("w3")
        state.removeRecentProject("w3")
        assertEquals("w1", state.selectedWorkspaceId.value, "Previous remaining row w1 should be selected when last item is removed")

        // Case 3: Remove the only remaining item -> selection becomes null
        state.removeRecentProject("w1")
        assertNull(state.selectedWorkspaceId.value, "Selection must become null when all items are removed")

        collectJob.cancel()
    }

    @Test
    fun testPreservesSessionSearchSelectionAndListPosition() = runTest(testDispatcher) {
        val repo = FakeWorkspaceRepository()
        val p1 = RecentProject("w1", "ROM1", "/path1", Clock.System.now())
        val p2 = RecentProject("w2", "ROM2", "/path2", Clock.System.now())
        repo.recents.value = listOf(p1, p2)

        val state = WorkspaceBrowserState(
            scope = backgroundScope,
            getRecentWorkspacesUseCase = GetRecentWorkspacesUseCase(repo),
            openWorkspaceUseCase = OpenWorkspaceUseCase(repo),
            removeRecentWorkspaceUseCase = RemoveRecentWorkspaceUseCase(repo),
            toolchainSetupState = defaultToolchainFlow(),
        )

        state.onSearchQueryChange("ROM1")
        state.selectWorkspace("w1")
        state.setListPosition(4, 16)

        // Session state is preserved on state holder across destination changes (FR-004)
        assertEquals("ROM1", state.searchQuery.value)
        assertEquals("w1", state.selectedWorkspaceId.value)
        assertEquals(Pair(4, 16), state.getListPosition())
    }

    @Test
    fun test100RecentsSearchableWithinSlo() = runTest(testDispatcher) {
        val repo = FakeWorkspaceRepository()
        val hundredProjects = (1..100).map { i ->
            RecentProject(
                workspaceId = "ws_$i",
                name = "Project_$i",
                path = "/home/dev/workspaces/project_$i",
                lastOpened = Clock.System.now(),
            )
        }
        repo.recents.value = hundredProjects

        val state = WorkspaceBrowserState(
            scope = backgroundScope,
            getRecentWorkspacesUseCase = GetRecentWorkspacesUseCase(repo),
            openWorkspaceUseCase = OpenWorkspaceUseCase(repo),
            removeRecentWorkspaceUseCase = RemoveRecentWorkspaceUseCase(repo),
            toolchainSetupState = defaultToolchainFlow(),
        )

        val collectJob = backgroundScope.launch(testDispatcher) {
            state.filteredProjects.collect()
        }

        assertEquals(100, state.filteredProjects.value.size)

        val start = Clock.System.now().toEpochMilliseconds()
        state.onSearchQueryChange("Project_42")
        assertEquals(1, state.filteredProjects.value.size)
        assertEquals("Project_42", state.filteredProjects.value.first().name)

        val elapsed = Clock.System.now().toEpochMilliseconds() - start
        assertTrue(elapsed < 1000, "100 recents search must complete in well under 1s (SC-001)")

        collectJob.cancel()
    }

    @Test
    fun testOpenRecentQueuesWhileCheckingAndResolvesWhenPassing() = runTest(testDispatcher) {
        val repo = FakeWorkspaceRepository()
        val now = Clock.System.now()
        val ws = Workspace(id = "ws_test", name = "Test", path = "C:/ws", type = WorkspaceType.LOCAL, lastOpened = now)
        repo.saveWorkspace(ws)
        val project = RecentProject("ws_test", "Test", "C:/ws", now)

        val toolchainFlow = MutableStateFlow(
            ToolchainSetupState(
                isChecking = true,
                steps = listOf(
                    SetupStepDetail(
                        stage = SetupStepStage.WSL_DETECTION,
                        title = "WSL",
                        description = "Checking",
                        status = StepStatus.RUNNING,
                    ),
                ),
            ),
        )

        val state = WorkspaceBrowserState(
            scope = backgroundScope,
            getRecentWorkspacesUseCase = GetRecentWorkspacesUseCase(repo),
            openWorkspaceUseCase = OpenWorkspaceUseCase(repo),
            removeRecentWorkspaceUseCase = RemoveRecentWorkspaceUseCase(repo),
            toolchainSetupState = toolchainFlow,
        )

        var openedWorkspace: Workspace? = null
        state.openRecentProject(project) { opened ->
            openedWorkspace = opened
        }

        // Must be queued, not opened yet
        assertEquals(PendingWorkspaceRequest.OpenRecent(project), state.pendingWorkspaceRequest.value)
        assertNull(openedWorkspace)

        // Now checks complete and pass
        toolchainFlow.value = ToolchainSetupState(
            isChecking = false,
            steps = SetupStepStage.entries.map {
                SetupStepDetail(
                    stage = it,
                    title = it.displayName,
                    description = "OK",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.LIVE,
                )
            },
        )

        // Queue resolved
        assertNull(state.pendingWorkspaceRequest.value)
        assertNotNull(openedWorkspace)
        assertEquals("ws_test", openedWorkspace?.id)
    }

    @Test
    fun testOpenRecentBlockedWhenChecksFail() = runTest(testDispatcher) {
        val repo = FakeWorkspaceRepository()
        val now = Clock.System.now()
        val ws = Workspace(id = "ws_test", name = "Test", path = "C:/ws", type = WorkspaceType.LOCAL, lastOpened = now)
        repo.saveWorkspace(ws)
        val project = RecentProject("ws_test", "Test", "C:/ws", now)

        val toolchainFlow = MutableStateFlow(
            ToolchainSetupState(
                isChecking = false,
                steps = listOf(
                    SetupStepDetail(
                        stage = SetupStepStage.WSL_DETECTION,
                        title = "WSL",
                        description = "WSL 2 not found",
                        status = StepStatus.FAILED,
                        error = "WSL 2 is not installed",
                    ),
                ),
            ),
        )

        val state = WorkspaceBrowserState(
            scope = backgroundScope,
            getRecentWorkspacesUseCase = GetRecentWorkspacesUseCase(repo),
            openWorkspaceUseCase = OpenWorkspaceUseCase(repo),
            removeRecentWorkspaceUseCase = RemoveRecentWorkspaceUseCase(repo),
            toolchainSetupState = toolchainFlow,
        )

        var openedWorkspace: Workspace? = null
        state.openRecentProject(project) { opened ->
            openedWorkspace = opened
        }

        assertNull(state.pendingWorkspaceRequest.value, "Must not queue when checks have failed")
        assertNull(openedWorkspace, "Workspace must not be opened")
        assertNotNull(state.workspaceError.value)
        assertTrue(state.workspaceError.value?.reason?.contains("WSL 2") == true)
    }

    @Test
    fun testFolderOpenInterceptionWhileChecking() = runTest(testDispatcher) {
        val repo = FakeWorkspaceRepository()
        val now = Clock.System.now()
        val ws = Workspace(
            id = "ws_folder",
            name = "folder",
            path = "C:/my_folder",
            type = WorkspaceType.LOCAL,
            lastOpened = now,
        )
        repo.saveWorkspace(ws)

        val toolchainFlow = MutableStateFlow(
            ToolchainSetupState(
                isChecking = true,
                steps = listOf(
                    SetupStepDetail(
                        stage = SetupStepStage.WSL_DETECTION,
                        title = "WSL",
                        description = "Checking",
                        status = StepStatus.RUNNING,
                    ),
                ),
            ),
        )

        val openByPathUseCase = OpenWorkspaceByPathUseCase(repo)
        val state = WorkspaceBrowserState(
            scope = backgroundScope,
            getRecentWorkspacesUseCase = GetRecentWorkspacesUseCase(repo),
            openWorkspaceUseCase = OpenWorkspaceUseCase(repo),
            removeRecentWorkspaceUseCase = RemoveRecentWorkspaceUseCase(repo),
            openWorkspaceByPathUseCase = openByPathUseCase,
            toolchainSetupState = toolchainFlow,
            selectDirectory = { "C:/my_folder" },
        )

        var openedWorkspace: Workspace? = null
        state.openFolder { opened ->
            openedWorkspace = opened
        }

        // While checking, must queue OpenPath without invoking open use case
        assertEquals(PendingWorkspaceRequest.OpenPath("C:/my_folder"), state.pendingWorkspaceRequest.value)
        assertNull(openedWorkspace)

        // Now checks complete successfully
        toolchainFlow.value = ToolchainSetupState(
            isChecking = false,
            steps = SetupStepStage.entries.map {
                SetupStepDetail(
                    stage = it,
                    title = it.displayName,
                    description = "OK",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.LIVE,
                )
            },
        )

        // Queue resolved and folder opened
        assertNull(state.pendingWorkspaceRequest.value)
        assertNotNull(openedWorkspace)
        assertEquals("C:/my_folder", openedWorkspace?.path)
    }

    @Test
    fun testCancelPendingWorkspaceRequest() = runTest(testDispatcher) {
        val repo = FakeWorkspaceRepository()
        val now = Clock.System.now()
        val ws = Workspace(id = "ws_test", name = "Test", path = "C:/ws", type = WorkspaceType.LOCAL, lastOpened = now)
        repo.saveWorkspace(ws)
        val project = RecentProject("ws_test", "Test", "C:/ws", now)

        val toolchainFlow = MutableStateFlow(
            ToolchainSetupState(
                isChecking = true,
                steps = listOf(
                    SetupStepDetail(
                        stage = SetupStepStage.WSL_DETECTION,
                        title = "WSL",
                        description = "Checking",
                        status = StepStatus.RUNNING,
                    ),
                ),
            ),
        )

        val state = WorkspaceBrowserState(
            scope = backgroundScope,
            getRecentWorkspacesUseCase = GetRecentWorkspacesUseCase(repo),
            openWorkspaceUseCase = OpenWorkspaceUseCase(repo),
            removeRecentWorkspaceUseCase = RemoveRecentWorkspaceUseCase(repo),
            toolchainSetupState = toolchainFlow,
        )

        var openedWorkspace: Workspace? = null
        state.openRecentProject(project) { opened ->
            openedWorkspace = opened
        }

        assertEquals(PendingWorkspaceRequest.OpenRecent(project), state.pendingWorkspaceRequest.value)

        state.cancelPendingWorkspaceRequest()
        assertNull(state.pendingWorkspaceRequest.value, "Pending workspace request must be cleared on cancel")

        // Now checks complete, but canceled request should NOT open
        toolchainFlow.value = ToolchainSetupState(
            isChecking = false,
            steps = SetupStepStage.entries.map {
                SetupStepDetail(
                    stage = it,
                    title = it.displayName,
                    description = "OK",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.LIVE,
                )
            },
        )

        assertNull(openedWorkspace, "Workspace must not be opened after being canceled")
    }

    @Test
    fun testRetryQueuedOpenPathWhenReadinessBlockedDoesNotBypassGate() = runTest {
        val repo = object : FakeWorkspaceRepository() {
            var saveCount = 0
            override suspend fun saveWorkspace(workspace: Workspace) {
                saveCount++
                super.saveWorkspace(workspace)
            }
        }

        val toolchainFlow = MutableStateFlow(
            ToolchainSetupState(
                isChecking = true,
                steps = listOf(
                    SetupStepDetail(
                        stage = SetupStepStage.WSL_DETECTION,
                        title = "WSL",
                        description = "Checking",
                        status = StepStatus.RUNNING,
                    ),
                ),
            ),
        )

        val state = WorkspaceBrowserState(
            scope = backgroundScope,
            getRecentWorkspacesUseCase = GetRecentWorkspacesUseCase(repo),
            openWorkspaceUseCase = OpenWorkspaceUseCase(repo),
            openWorkspaceByPathUseCase = OpenWorkspaceByPathUseCase(repo),
            removeRecentWorkspaceUseCase = RemoveRecentWorkspaceUseCase(repo),
            toolchainSetupState = toolchainFlow,
        )

        var openedWorkspace: Workspace? = null
        state.openPath("/home/user/project") { opened ->
            openedWorkspace = opened
        }

        assertEquals(PendingWorkspaceRequest.OpenPath("/home/user/project"), state.pendingWorkspaceRequest.value)
        assertEquals(0, repo.saveCount, "Open by path must not save/open workspace while checking")

        // Check finishes with failure
        toolchainFlow.value = ToolchainSetupState(
            isChecking = false,
            steps = listOf(
                SetupStepDetail(
                    stage = SetupStepStage.WSL_DETECTION,
                    title = "WSL 2 Runtime",
                    description = "WSL not found",
                    status = StepStatus.FAILED,
                    error = "WSL 2 is not installed",
                ),
            ),
        )
        testScheduler.runCurrent()

        assertNull(state.pendingWorkspaceRequest.value, "Pending request must be cleared")
        assertNull(openedWorkspace, "Workspace must not open on failure")
        val error = state.workspaceError.value
        assertNotNull(error, "Workspace error must be set on failure")

        // User clicks "Retry" action from error card while environment is still failing
        val retry = error.retryAction
        assertNotNull(retry, "Retry action must be present")
        retry.invoke()
        testScheduler.runCurrent()

        // Verify it checked canLaunchWorkspace and did NOT execute use-case
        assertEquals(0, repo.saveCount, "Retry action must not execute openPath while environment gate is closed")
        assertNull(openedWorkspace, "Workspace must not be opened while gate is closed")
        assertNotNull(state.workspaceError.value, "Workspace error must remain set")
    }

    @Test
    fun testRetryPathOpenErrorWhenReadinessBecomesBlockedDoesNotBypassGate() = runTest {
        var callCount = 0
        var shouldFail = true
        val repo = object : FakeWorkspaceRepository() {
            override suspend fun saveWorkspace(workspace: Workspace) {
                callCount++
                if (shouldFail) {
                    error("Simulated disk error")
                }
                super.saveWorkspace(workspace)
            }
        }

        val toolchainFlow = MutableStateFlow(
            ToolchainSetupState(
                isChecking = false,
                steps = SetupStepStage.entries.map {
                    SetupStepDetail(
                        stage = it,
                        title = it.displayName,
                        description = "OK",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    )
                },
            ),
        )

        val state = WorkspaceBrowserState(
            scope = backgroundScope,
            getRecentWorkspacesUseCase = GetRecentWorkspacesUseCase(repo),
            openWorkspaceUseCase = OpenWorkspaceUseCase(repo),
            openWorkspaceByPathUseCase = OpenWorkspaceByPathUseCase(repo),
            removeRecentWorkspaceUseCase = RemoveRecentWorkspaceUseCase(repo),
            toolchainSetupState = toolchainFlow,
        )

        // Open path initially while ready, which fails with simulated disk error
        state.openPath("/home/user/project")
        testScheduler.runCurrent()
        assertEquals(1, callCount)
        val initialError = state.workspaceError.value
        assertNotNull(initialError)
        assertEquals("Simulated disk error", initialError.reason)

        // Now environment becomes blocked (e.g. server bridge lost)
        toolchainFlow.value = ToolchainSetupState(
            isChecking = false,
            steps = listOf(
                SetupStepDetail(
                    stage = SetupStepStage.SERVER_CONNECTIVITY,
                    title = "Server Bridge",
                    description = "Connection refused",
                    status = StepStatus.FAILED,
                    error = "Daemon crashed",
                ),
            ),
        )
        testScheduler.runCurrent()

        // User retries
        initialError.retryAction?.invoke()
        testScheduler.runCurrent()

        // Call count must NOT increase because gate is now closed
        assertEquals(1, callCount, "Retry must not bypass the closed environment readiness gate")
        assertEquals("Daemon crashed", state.workspaceError.value?.reason)
    }

    @Test
    fun testOpenRecentProjectReadinessClosesBetweenDispatchAndExecutionBlocksWorkspaceMutation() = runTest {
        var openCallCount = 0
        val repo = object : FakeWorkspaceRepository() {
            override suspend fun getWorkspace(id: String): Workspace? {
                openCallCount++
                return super.getWorkspace(id)
            }
        }
        val now = Clock.System.now()
        val ws = Workspace(
            id = "ws_race",
            name = "Race Project",
            path = "C:/ws_race",
            type = WorkspaceType.LOCAL,
            lastOpened = now,
        )
        repo.saveWorkspace(ws)
        val project = RecentProject("ws_race", "Race Project", "C:/ws_race", now)

        val toolchainFlow = MutableStateFlow(
            ToolchainSetupState(
                isChecking = false,
                steps = SetupStepStage.entries.map {
                    SetupStepDetail(
                        stage = it,
                        title = it.displayName,
                        description = "OK",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    )
                },
            ),
        )

        val standardDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = CoroutineScope(standardDispatcher)

        val state = WorkspaceBrowserState(
            scope = testScope,
            getRecentWorkspacesUseCase = GetRecentWorkspacesUseCase(repo),
            openWorkspaceUseCase = OpenWorkspaceUseCase(repo),
            removeRecentWorkspaceUseCase = RemoveRecentWorkspaceUseCase(repo),
            toolchainSetupState = toolchainFlow,
        )

        var openedWorkspace: Workspace? = null
        state.openRecentProject(project) { opened ->
            openedWorkspace = opened
        }

        // Before coroutine executes, readiness closes
        toolchainFlow.value = ToolchainSetupState(
            isChecking = false,
            steps = listOf(
                SetupStepDetail(
                    stage = SetupStepStage.SERVER_CONNECTIVITY,
                    title = "Server Bridge",
                    description = "Connection dropped",
                    status = StepStatus.FAILED,
                    error = "Daemon crashed during launch",
                ),
            ),
        )

        // Execute scheduled coroutine
        testScheduler.runCurrent()

        assertEquals(0, openCallCount, "Use case must NOT be invoked when readiness closes before execution")
        assertNull(openedWorkspace, "Workspace must not be emitted to callback")
        assertNotNull(state.workspaceError.value, "Workspace error must be reported")
        assertEquals("Daemon crashed during launch", state.workspaceError.value?.reason)
    }

    @Test
    fun testOpenPathReadinessClosesBetweenDispatchAndExecutionBlocksWorkspaceMutation() = runTest {
        var openCallCount = 0
        val repo = object : FakeWorkspaceRepository() {
            override suspend fun saveWorkspace(workspace: Workspace) {
                openCallCount++
                super.saveWorkspace(workspace)
            }
        }

        val toolchainFlow = MutableStateFlow(
            ToolchainSetupState(
                isChecking = false,
                steps = SetupStepStage.entries.map {
                    SetupStepDetail(
                        stage = it,
                        title = it.displayName,
                        description = "OK",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    )
                },
            ),
        )

        val standardDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = CoroutineScope(standardDispatcher)

        val state = WorkspaceBrowserState(
            scope = testScope,
            getRecentWorkspacesUseCase = GetRecentWorkspacesUseCase(repo),
            openWorkspaceUseCase = OpenWorkspaceUseCase(repo),
            openWorkspaceByPathUseCase = OpenWorkspaceByPathUseCase(repo),
            removeRecentWorkspaceUseCase = RemoveRecentWorkspaceUseCase(repo),
            toolchainSetupState = toolchainFlow,
        )

        var openedWorkspace: Workspace? = null
        state.openPath("/home/dev/race_folder") { opened ->
            openedWorkspace = opened
        }

        // Before coroutine executes, readiness closes
        toolchainFlow.value = ToolchainSetupState(
            isChecking = false,
            steps = listOf(
                SetupStepDetail(
                    stage = SetupStepStage.WSL_DETECTION,
                    title = "WSL 2 Runtime",
                    description = "WSL service stopped",
                    status = StepStatus.FAILED,
                    error = "WSL 2 service stopped unexpectedly",
                ),
            ),
        )

        // Execute scheduled coroutine
        testScheduler.runCurrent()

        assertEquals(0, openCallCount, "OpenPath use case must NOT be invoked when readiness closes before execution")
        assertNull(openedWorkspace, "Workspace must not be emitted to callback")
        assertNotNull(state.workspaceError.value, "Workspace error must be reported")
        assertEquals("WSL 2 service stopped unexpectedly", state.workspaceError.value?.reason)
    }
}
