package org.ide.lti.feature.setup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.domain.setup.DiagnosticCheckItem
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainProvisioningService
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.feature.setup.components.computeSidebarBadge
import org.ide.lti.feature.setup.components.computeSidebarSubtitle
import org.ide.lti.core.domain.usecase.target.ProvisionAvbKeyUseCase
import org.ide.lti.core.domain.usecase.workspace.DiscoverWorkdirWorkspacesUseCase
import org.ide.lti.core.domain.usecase.workspace.GetRecentWorkspacesUseCase
import org.ide.lti.core.domain.usecase.workspace.OpenWorkspaceUseCase
import org.ide.lti.core.domain.usecase.workspace.RemoveRecentWorkspaceUseCase
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelWorkspaceTest {

    private class FakeWorkspaceRepository : WorkspaceRepository {
        val recents = MutableStateFlow<List<RecentProject>>(emptyList())

        override fun getWorkspaces(): Flow<List<Workspace>> = MutableStateFlow(emptyList())
        override suspend fun getWorkspace(id: String): Workspace? = null
        override suspend fun saveWorkspace(workspace: Workspace) {}
        override suspend fun deleteWorkspace(id: String) {}
        override suspend fun getWorkspaceSession(workspaceId: String): WorkspaceSession? = null
        override suspend fun saveWorkspaceSession(session: WorkspaceSession) {}
        override fun getRecentProjects(): Flow<List<RecentProject>> = recents.asStateFlow()
        override suspend fun addRecentProject(project: RecentProject) {
            recents.value = listOf(project) + recents.value.filter { it.workspaceId != project.workspaceId }
        }
        override suspend fun removeRecentProject(workspaceId: String) {
            recents.value = recents.value.filter { it.workspaceId != workspaceId }
        }
        override suspend fun clearRecentProjects() {
            recents.value = emptyList()
        }
        override suspend fun discoverWorkspacesInWorkDir(basePath: String?): List<RecentProject> {
            val defaults = listOf(
                RecentProject(
                    workspaceId = "ltirom_staging_workdir",
                    name = "LtiRom Staging WorkDir",
                    path = "~/LtiRomWorkDir",
                    lastOpened = Clock.System.now(),
                ),
                RecentProject(
                    workspaceId = "target_pq84p01",
                    name = "REDMAGIC Astra (PQ84P01)",
                    path = "~/LtiRomWorkDir/workspaces/target_pq84p01",
                    lastOpened = Clock.System.now(),
                ),
            )
            val current = recents.value
            val combined = (current + defaults).distinctBy { it.workspaceId }
            recents.value = combined
            return combined
        }
    }

    @Test
    fun testSearchFilteringByProjectNameAndPath() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val project1 = RecentProject(
                workspaceId = "p1",
                name = "LtiRomProject",
                path = "/Users/dev/LtiRomProject",
                lastOpened = Clock.System.now(),
            )
            val project2 = RecentProject(
                workspaceId = "p2",
                name = "KmpTemplate",
                path = "/Users/dev/KmpTemplate",
                lastOpened = Clock.System.now(),
            )
            repo.recents.value = listOf(project1, project2)

            val viewModel = SetupViewModel(manager)
            val collectJob = backgroundScope.launch(testDispatcher) {
                viewModel.filteredRecentProjects.collect()
            }

            // When query is empty, all projects returned
            assertEquals(2, viewModel.filteredRecentProjects.value.size)

            // Filter by name "Lti"
            viewModel.onSearchQueryChange("Lti")
            assertEquals(1, viewModel.filteredRecentProjects.value.size)
            assertEquals("LtiRomProject", viewModel.filteredRecentProjects.value.first().name)

            // Filter by path "Template"
            viewModel.onSearchQueryChange("Template")
            assertEquals(1, viewModel.filteredRecentProjects.value.size)
            assertEquals("KmpTemplate", viewModel.filteredRecentProjects.value.first().name)

            // Filter with no matches
            viewModel.onSearchQueryChange("NonExistent")
            assertEquals(0, viewModel.filteredRecentProjects.value.size)

            // Clear query
            viewModel.onSearchQueryChange("")
            assertEquals(2, viewModel.filteredRecentProjects.value.size)

            collectJob.cancel()
            testScheduler.advanceUntilIdle()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testRemoveRecentProject() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val project = RecentProject(
                workspaceId = "p1",
                name = "LtiRomProject",
                path = "/Users/dev/LtiRomProject",
                lastOpened = Clock.System.now(),
            )
            repo.recents.value = listOf(project)

            val viewModel = SetupViewModel(manager)
            assertEquals(1, repo.recents.value.size)

            viewModel.removeRecentProject("p1")
            testScheduler.advanceUntilIdle()

            assertEquals(0, repo.recents.value.size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testWorkdirWorkspacesDiscoveryAndUseCases() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val getRecents = GetRecentWorkspacesUseCase(repo)
            val discoverWorkdir = DiscoverWorkdirWorkspacesUseCase(repo)
            val openWorkspace = OpenWorkspaceUseCase(repo)
            val removeRecent = RemoveRecentWorkspaceUseCase(repo)

            val viewModel = SetupViewModel(
                workspaceManager = manager,
                getRecentWorkspacesUseCase = getRecents,
                discoverWorkdirWorkspacesUseCase = discoverWorkdir,
                openWorkspaceUseCase = openWorkspace,
                removeRecentWorkspaceUseCase = removeRecent,
            )
            val collectJob = backgroundScope.launch(testDispatcher) {
                viewModel.recentProjects.collect()
            }
            testScheduler.advanceUntilIdle()

            // On init, recents is clean and empty (no auto-seeding without user action)
            assertEquals(0, viewModel.recentProjects.value.size)

            // Explicit discovery populates canonical workspaces
            viewModel.discoverWorkspaces()
            testScheduler.advanceUntilIdle()

            assertEquals(2, viewModel.recentProjects.value.size)
            assertTrue(viewModel.recentProjects.value.any { it.path == "~/LtiRomWorkDir" })
            assertTrue(viewModel.recentProjects.value.any { it.name == "REDMAGIC Astra (PQ84P01)" })

            // Test removal via use case
            viewModel.removeRecentProject("target_pq84p01")
            testScheduler.advanceUntilIdle()

            assertEquals(1, viewModel.recentProjects.value.size)
            assertEquals("ltirom_staging_workdir", viewModel.recentProjects.value.first().workspaceId)

            collectJob.cancel()
            testScheduler.advanceUntilIdle()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeToolchainProvisioningService(
        initialState: ToolchainSetupState = ToolchainSetupState(),
    ) : ToolchainProvisioningService {
        val _state = MutableStateFlow(initialState)
        override val state: StateFlow<ToolchainSetupState> = _state.asStateFlow()

        var checkStatusCount = 0
        var verifyEnvironmentCount = 0
        var runFullSetupCount = 0
        var retryCurrentStepCount = 0
        var shouldMarkReadyOnFullSetup: Boolean = true

        fun markAllReady() {
            _state.value = _state.value.copy(
                steps = ToolchainSetupState.defaultSteps().map {
                    it.copy(status = StepStatus.SUCCESS, provenance = StepProvenance.LIVE)
                },
                lastVerifiedTimestamp = Clock.System.now().toEpochMilliseconds(),
            )
        }

        fun markUnready(pendingStage: SetupStepStage = SetupStepStage.TOOLCHAIN_COMPILATION) {
            _state.value = _state.value.copy(
                steps = ToolchainSetupState.defaultSteps().map {
                    if (it.stage == pendingStage) {
                        it.copy(status = StepStatus.PENDING, provenance = StepProvenance.LIVE)
                    } else {
                        it.copy(status = StepStatus.SUCCESS, provenance = StepProvenance.LIVE)
                    }
                }
            )
        }

        override suspend fun verifyEnvironment(): ToolchainSetupState {
            verifyEnvironmentCount++
            return _state.value
        }

        override suspend fun checkStatus(): ToolchainSetupState {
            checkStatusCount++
            return verifyEnvironment()
        }

        override suspend fun runFullSetup() {
            runFullSetupCount++
            if (shouldMarkReadyOnFullSetup) {
                markAllReady()
            }
        }

        var provisionAvbKeyCount = 0

        override suspend fun retryCurrentStep() {
            retryCurrentStepCount++
        }

        override suspend fun provisionAvbKey(): Boolean {
            provisionAvbKeyCount++
            _state.value = _state.value.copy(isAvbKeyProvisioned = true)
            return true
        }

        var autoRemediateDoctorCount = 0

        override suspend fun autoRemediateDoctor(): Boolean {
            autoRemediateDoctorCount++
            return true
        }

        var testToolCount = 0
        var lastTestedToolId: String? = null

        override suspend fun testIndividualTool(toolId: String): StepStatus {
            testToolCount++
            lastTestedToolId = toolId
            return StepStatus.SUCCESS
        }

        var recompileToolCount = 0
        var lastRecompiledToolId: String? = null

        override suspend fun recompileTool(toolId: String): Boolean {
            recompileToolCount++
            lastRecompiledToolId = toolId
            return true
        }

        var resetSetupCacheCount = 0
        override suspend fun resetSetupCache() {
            resetSetupCacheCount++
            markUnready()
        }
    }

}
