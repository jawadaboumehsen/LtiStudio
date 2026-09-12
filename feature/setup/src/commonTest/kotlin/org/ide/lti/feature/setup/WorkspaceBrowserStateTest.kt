package org.ide.lti.feature.setup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import org.ide.lti.core.domain.ports.EnvironmentReadinessPort
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.domain.usecase.workspace.GetRecentWorkspacesUseCase
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
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

    private class FakeWorkspaceRepository : WorkspaceRepository {
        val recents = MutableStateFlow<List<RecentProject>>(emptyList())
        val workspaces = mutableMapOf<String, Workspace>()
        var deleteWorkspaceCallCount = 0
        var removeRecentProjectCallCount = 0

        override fun getWorkspaces(): Flow<List<Workspace>> = flowOf(workspaces.values.toList())
        override suspend fun getWorkspace(id: String): Workspace? = workspaces[id]
        override suspend fun saveWorkspace(workspace: Workspace) {
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
        )
    ) : EnvironmentReadinessPort {
        val flow = MutableStateFlow(readiness)

        override fun observe(): Flow<EnvironmentReadiness> = flow.asStateFlow()
        override suspend fun refresh(): EnvironmentReadiness = readiness
        override suspend fun forWorkspace(workspace: Workspace): EnvironmentReadiness = readiness
    }

    @Test
    fun testCachedOpenWorksWithoutExecutionReadiness() = runTest(testDispatcher) {
        val repo = FakeWorkspaceRepository()
        val offlinePort = FakeEnvironmentReadinessPort(
            EnvironmentReadiness(
                state = EnvironmentReadinessState.ENV_ABSENT,
                failingCheck = "WSL2 execution environment absent",
            )
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
            )
        )

        val state = WorkspaceBrowserState(
            scope = backgroundScope,
            getRecentWorkspacesUseCase = GetRecentWorkspacesUseCase(repo),
            openWorkspaceUseCase = OpenWorkspaceUseCase(repo),
            removeRecentWorkspaceUseCase = RemoveRecentWorkspaceUseCase(repo),
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

        assertNotNull(openedWorkspace, "Cached workspace must open even without live execution readiness (FR-002, FR-003)")
        assertEquals("ws_offline_1", openedWorkspace.id)
        collectJob.cancel()
    }

    @Test
    fun testCreationReportsPrerequisitesWhenOffline() = runTest(testDispatcher) {
        val repo = FakeWorkspaceRepository()
        val offlinePort = FakeEnvironmentReadinessPort(
            EnvironmentReadiness(
                state = EnvironmentReadinessState.ENV_ABSENT,
                failingCheck = "WSL2 distribution not found. Remote workspace creation requires WSL2.",
            )
        )
        val state = WorkspaceBrowserState(
            scope = backgroundScope,
            getRecentWorkspacesUseCase = GetRecentWorkspacesUseCase(repo),
            openWorkspaceUseCase = OpenWorkspaceUseCase(repo),
            removeRecentWorkspaceUseCase = RemoveRecentWorkspaceUseCase(repo),
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
}
