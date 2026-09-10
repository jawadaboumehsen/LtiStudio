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
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainProvisioningService
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.domain.usecase.target.ProvisionAvbKeyUseCase
import org.ide.lti.core.domain.usecase.workspace.DiscoverWorkdirWorkspacesUseCase
import org.ide.lti.core.domain.usecase.workspace.GetRecentWorkspacesUseCase
import org.ide.lti.core.domain.usecase.workspace.OpenWorkspaceUseCase
import org.ide.lti.core.domain.usecase.workspace.RemoveRecentWorkspaceUseCase
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetProperties
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {

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
                    path = "~/LtiRomWorkDir/target/PQ84P01",
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

    private class FakeToolchainProvisioningService : ToolchainProvisioningService {
        val _state = MutableStateFlow(ToolchainSetupState())
        override val state: StateFlow<ToolchainSetupState> = _state.asStateFlow()

        var checkStatusCount = 0
        var runFullSetupCount = 0
        var retryCurrentStepCount = 0
        var shouldMarkReadyOnFullSetup: Boolean = true

        override suspend fun checkStatus(): ToolchainSetupState {
            checkStatusCount++
            return _state.value
        }

        override suspend fun runFullSetup() {
            runFullSetupCount++
            if (shouldMarkReadyOnFullSetup) {
                _state.value = _state.value.copy(isAllReady = true)
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

        var provisionTargetWorkspaceCount = 0
        var lastProvisionedTarget: TargetDevice? = null

        override suspend fun provisionTargetWorkspace(target: TargetDevice, customProperties: TargetProperties?): Boolean {
            provisionTargetWorkspaceCount++
            lastProvisionedTarget = target
            _state.value = _state.value.copy(targetWorkspacePath = "~/LtiRomWorkDir/target/${target.targetKey}")
            return true
        }

        override suspend fun readTargetProperties(targetCodename: String): String? = ""

        override suspend fun saveTargetProperties(targetCodename: String, content: String): Boolean = true

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
            _state.value = _state.value.copy(isAllReady = false)
        }
    }

    @Test
    fun testToolchainStatusCheckOnInitAndActionDelegation() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val toolchainService = FakeToolchainProvisioningService()

            val viewModel = SetupViewModel(manager, toolchainService)
            testScheduler.advanceUntilIdle()

            // Init triggers autoRunEnvironmentSetup which checks status (1) and runs full setup (1)
            assertEquals(1, toolchainService.checkStatusCount)
            assertEquals(1, toolchainService.runFullSetupCount)
            assertTrue(viewModel.toolchainSetupState.value.isAllReady)

            // runFullEnvironmentSetup should trigger runFullSetup again
            viewModel.runFullEnvironmentSetup()
            testScheduler.advanceUntilIdle()
            assertEquals(2, toolchainService.runFullSetupCount)
            assertTrue(viewModel.toolchainSetupState.value.isAllReady)

            // retrySetupStep should trigger retryCurrentStep
            viewModel.retrySetupStep()
            testScheduler.advanceUntilIdle()
            assertEquals(1, toolchainService.retryCurrentStepCount)

            // autoRemediateDoctorIssues should trigger autoRemediateDoctor
            viewModel.autoRemediateDoctorIssues()
            testScheduler.advanceUntilIdle()
            assertEquals(1, toolchainService.autoRemediateDoctorCount)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testSetupSkipsFullRebuildWhenAlreadyReadyFromPersistentState() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val toolchainService = FakeToolchainProvisioningService()
            // Simulate restored state from persistent DataStore
            toolchainService._state.value = toolchainService._state.value.copy(isAllReady = true)

            val viewModel = SetupViewModel(manager, toolchainService)
            testScheduler.advanceUntilIdle()

            // When state is already ready on startup, neither checkStatus nor runFullSetup should run (sub-second zero-start)
            assertEquals(0, toolchainService.checkStatusCount)
            assertEquals(0, toolchainService.runFullSetupCount)
            assertTrue(viewModel.toolchainSetupState.value.isAllReady)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testResetToolchainCacheAndRecheckTriggersRebuild() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val toolchainService = FakeToolchainProvisioningService()
            toolchainService._state.value = toolchainService._state.value.copy(isAllReady = true)

            val viewModel = SetupViewModel(manager, toolchainService)
            testScheduler.advanceUntilIdle()
            assertEquals(0, toolchainService.runFullSetupCount)

            viewModel.resetToolchainCacheAndRecheck()
            testScheduler.advanceUntilIdle()

            assertEquals(1, toolchainService.resetSetupCacheCount)
            assertEquals(1, toolchainService.runFullSetupCount)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testBusyStateGuardsPreventDuplicateActions() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val toolchainService = FakeToolchainProvisioningService()
            toolchainService._state.value = toolchainService._state.value.copy(isAllReady = true)

            val viewModel = SetupViewModel(manager, toolchainService)
            testScheduler.advanceUntilIdle()
            assertEquals(0, toolchainService.checkStatusCount)
            assertEquals(0, toolchainService.runFullSetupCount)

            // Case 1: isChecking is true -> isBusy is true
            toolchainService._state.value = toolchainService._state.value.copy(isChecking = true)
            assertTrue(viewModel.toolchainSetupState.value.isBusy)

            viewModel.checkEnvironmentStatus()
            viewModel.runFullEnvironmentSetup()
            viewModel.autoRemediateDoctorIssues()
            viewModel.retrySetupStep()
            viewModel.onProvisionAvbKey()
            testScheduler.advanceUntilIdle()

            // All counts must remain unchanged
            assertEquals(0, toolchainService.checkStatusCount)
            assertEquals(0, toolchainService.runFullSetupCount)
            assertEquals(0, toolchainService.autoRemediateDoctorCount)
            assertEquals(0, toolchainService.retryCurrentStepCount)
            assertEquals(0, toolchainService.provisionAvbKeyCount)

            // Case 2: isFixing is true -> isBusy is true
            toolchainService._state.value = toolchainService._state.value.copy(isChecking = false, isFixing = true)
            assertTrue(viewModel.toolchainSetupState.value.isBusy)

            viewModel.checkEnvironmentStatus()
            viewModel.runFullEnvironmentSetup()
            viewModel.autoRemediateDoctorIssues()
            testScheduler.advanceUntilIdle()

            assertEquals(0, toolchainService.checkStatusCount)
            assertEquals(0, toolchainService.runFullSetupCount)
            assertEquals(0, toolchainService.autoRemediateDoctorCount)

            // Case 3: isRunning is true -> isBusy is true
            toolchainService._state.value = toolchainService._state.value.copy(isFixing = false, isRunning = true)
            assertTrue(viewModel.toolchainSetupState.value.isBusy)

            viewModel.checkEnvironmentStatus()
            viewModel.runFullEnvironmentSetup()
            viewModel.autoRemediateDoctorIssues()
            testScheduler.advanceUntilIdle()

            assertEquals(0, toolchainService.checkStatusCount)
            assertEquals(0, toolchainService.runFullSetupCount)
            assertEquals(0, toolchainService.autoRemediateDoctorCount)

            // Case 4: Idle -> actions execute normally
            toolchainService._state.value = toolchainService._state.value.copy(isRunning = false)
            kotlin.test.assertFalse(viewModel.toolchainSetupState.value.isBusy)

            viewModel.checkEnvironmentStatus()
            testScheduler.advanceUntilIdle()
            assertEquals(1, toolchainService.checkStatusCount)

            viewModel.runFullEnvironmentSetup()
            testScheduler.advanceUntilIdle()
            assertEquals(1, toolchainService.runFullSetupCount)
        } finally {
            Dispatchers.resetMain()
        }
    }


    @Test
    fun testAvbKeyProvisioning() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val toolchainService = FakeToolchainProvisioningService()
            val avbUseCase = ProvisionAvbKeyUseCase(toolchainService)

            val viewModel = SetupViewModel(
                workspaceManager = manager,
                toolchainService = toolchainService,
                provisionAvbKeyUseCase = avbUseCase,
            )
            testScheduler.advanceUntilIdle()

            viewModel.onProvisionAvbKey()
            testScheduler.advanceUntilIdle()
            assertEquals(1, toolchainService.provisionAvbKeyCount)
            assertTrue(viewModel.toolchainSetupState.value.isAvbKeyProvisioned)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testOpenDefaultWorkspaceOpensBaseWorkDir() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val toolchainService = FakeToolchainProvisioningService().apply {
                _state.value = _state.value.copy(isAllReady = true)
            }

            val viewModel = SetupViewModel(manager, toolchainService)
            var openedWorkspace: Workspace? = null
            viewModel.openDefaultWorkspace { ws ->
                openedWorkspace = ws
            }
            testScheduler.advanceUntilIdle()

            assertEquals("~/LtiRomWorkDir/target/PQ84P01_GLOBAL_A15", openedWorkspace?.path)
            assertEquals("REDMAGIC Astra (PQ84P01 • Global • Android 15)", openedWorkspace?.name)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testDefaultOfficialTargetsAvailable() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)

            val viewModel = SetupViewModel(manager)
            val collectJob = backgroundScope.launch(testDispatcher) {
                viewModel.availableTargets.collect()
            }
            val targetJob = backgroundScope.launch(testDispatcher) {
                viewModel.selectedTarget.collect()
            }

            assertEquals(1, viewModel.availableTargets.value.size)
            assertEquals("PQ84P01", viewModel.availableTargets.value[0].codename)
            assertEquals("REDMAGIC Astra Gaming Tablet", viewModel.availableTargets.value[0].name)
            assertEquals("PQ84P01", viewModel.selectedTarget.value?.codename)

            collectJob.cancel()
            targetJob.cancel()
            testScheduler.advanceUntilIdle()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testTargetSelectionUpdatesSelectedTarget() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)

            val viewModel = SetupViewModel(manager)
            val targetJob = backgroundScope.launch(testDispatcher) {
                viewModel.selectedTarget.collect()
            }

            val targetAstra = viewModel.availableTargets.value.first { it.codename == "PQ84P01" }
            viewModel.selectTarget(targetAstra)
            testScheduler.advanceUntilIdle()

            assertEquals("PQ84P01", viewModel.selectedTarget.value?.codename)

            targetJob.cancel()
            testScheduler.advanceUntilIdle()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testSelectTargetAndLaunchSelectsTargetAndOpensWorkspace() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val toolchainService = FakeToolchainProvisioningService().apply {
                _state.value = _state.value.copy(isAllReady = true)
            }

            val viewModel = SetupViewModel(manager, toolchainService)
            val targetJob = backgroundScope.launch(testDispatcher) {
                viewModel.selectedTarget.collect()
            }

            val targetAstra = viewModel.availableTargets.value.first { it.codename == "PQ84P01" }
            var launchedWorkspace: Workspace? = null

            viewModel.selectTargetAndLaunch(targetAstra) { ws ->
                launchedWorkspace = ws
            }
            testScheduler.advanceUntilIdle()

            assertEquals("PQ84P01", viewModel.selectedTarget.value?.codename)
            assertEquals("~/LtiRomWorkDir/target/PQ84P01_GLOBAL_A15", launchedWorkspace?.path)

            targetJob.cancel()
            testScheduler.advanceUntilIdle()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testAutoRunEnvironmentSetupExecutesDoctorRemediationAndFullSetup() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val toolchainService = FakeToolchainProvisioningService()

            // Configure initial state with failed doctor item and not ready
            toolchainService._state.value = toolchainService._state.value.copy(
                isAllReady = false,
                diagnostics = listOf(
                    DiagnosticCheckItem(
                        id = "gcc",
                        title = "GCC Compiler",
                        status = StepStatus.FAILED,
                    )
                )
            )

            val viewModel = SetupViewModel(manager, toolchainService)
            testScheduler.advanceUntilIdle()
            assertEquals(1, toolchainService.checkStatusCount) // from init
            assertEquals(1, toolchainService.autoRemediateDoctorCount)
            assertEquals(1, toolchainService.runFullSetupCount)
            assertTrue(viewModel.toolchainSetupState.value.isAllReady)

            viewModel.autoRunEnvironmentSetup(autoFix = true)
            testScheduler.advanceUntilIdle()

            // Since it is now ready, recheck is bypassed (checkStatusCount remains 1)
            assertEquals(1, toolchainService.checkStatusCount)
            assertEquals(1, toolchainService.autoRemediateDoctorCount)
            assertEquals(1, toolchainService.runFullSetupCount)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testAutoRunEnvironmentSetupSkipsWhenAlreadyReady() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val toolchainService = FakeToolchainProvisioningService()

            toolchainService._state.value = toolchainService._state.value.copy(
                isAllReady = true,
            )

            val viewModel = SetupViewModel(manager, toolchainService)
            testScheduler.advanceUntilIdle()
            assertEquals(0, toolchainService.checkStatusCount)

            viewModel.autoRunEnvironmentSetup(autoFix = true)
            testScheduler.advanceUntilIdle()

            // Since it's already ready, no doctor remediation or full setup should run
            assertEquals(0, toolchainService.checkStatusCount)
            assertEquals(0, toolchainService.autoRemediateDoctorCount)
            assertEquals(0, toolchainService.runFullSetupCount)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testAutoRunEnvironmentSetupRechecksWhenPersistedVerificationIsStale() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val toolchainService = FakeToolchainProvisioningService()
            val staleTimestamp = System.currentTimeMillis() - (25L * 60 * 60 * 1000) // 25h ago, past the 24h TTL
            toolchainService._state.value = toolchainService._state.value.copy(
                isAllReady = true,
                lastVerifiedTimestamp = staleTimestamp,
            )

            val viewModel = SetupViewModel(manager, toolchainService)
            testScheduler.advanceUntilIdle()

            // Persisted state says ready, but the timestamp is stale -> must force a live recheck
            // instead of trusting the flag forever.
            assertEquals(1, toolchainService.checkStatusCount)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testAutoRunEnvironmentSetupSkipsRecheckWhenPersistedVerificationIsFresh() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val toolchainService = FakeToolchainProvisioningService()
            toolchainService._state.value = toolchainService._state.value.copy(
                isAllReady = true,
                lastVerifiedTimestamp = System.currentTimeMillis(),
            )

            val viewModel = SetupViewModel(manager, toolchainService)
            testScheduler.advanceUntilIdle()

            assertEquals(0, toolchainService.checkStatusCount)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testAutoRunEnvironmentSetupRespectsAutoDoctorDisabledToggle() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val toolchainService = FakeToolchainProvisioningService()
            toolchainService._state.value = toolchainService._state.value.copy(isAllReady = true)

            val viewModel = SetupViewModel(manager, toolchainService)
            testScheduler.advanceUntilIdle()
            assertEquals(0, toolchainService.checkStatusCount)

            // Configure state with issues
            toolchainService._state.value = toolchainService._state.value.copy(
                isAllReady = false,
                diagnostics = listOf(
                    DiagnosticCheckItem(
                        id = "python",
                        title = "Python 3",
                        status = StepStatus.FAILED,
                    )
                )
            )

            // Disable auto doctor
            viewModel.setAutoDoctorEnabled(false)
            assertFalse(viewModel.isAutoDoctorEnabled.value)

            viewModel.autoRunEnvironmentSetup(autoFix = true)
            testScheduler.advanceUntilIdle()

            // Doctor auto-fix should not be called, but full setup pipeline runs
            assertEquals(1, toolchainService.checkStatusCount)
            assertEquals(0, toolchainService.autoRemediateDoctorCount)
            assertEquals(1, toolchainService.runFullSetupCount)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testAutoRunEnvironmentSetupGuardedWhenBusy() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val toolchainService = FakeToolchainProvisioningService()
            toolchainService._state.value = toolchainService._state.value.copy(isAllReady = true)

            val viewModel = SetupViewModel(manager, toolchainService)
            testScheduler.advanceUntilIdle()
            assertEquals(0, toolchainService.checkStatusCount)
            assertEquals(0, toolchainService.runFullSetupCount)

            // Mark busy
            toolchainService._state.value = toolchainService._state.value.copy(isRunning = true)
            assertTrue(viewModel.toolchainSetupState.value.isBusy)

            viewModel.autoRunEnvironmentSetup(autoFix = true)
            testScheduler.advanceUntilIdle()

            // Guarded: checkStatusCount remains 0, no doctor fix or full setup
            assertEquals(0, toolchainService.checkStatusCount)
            assertEquals(0, toolchainService.autoRemediateDoctorCount)
            assertEquals(0, toolchainService.runFullSetupCount)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testCockpitTabSelection() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val viewModel = SetupViewModel(manager)

            assertEquals("doctor", viewModel.selectedCockpitTab.value)

            viewModel.selectCockpitTab("tools")
            assertEquals("tools", viewModel.selectedCockpitTab.value)

            viewModel.selectCockpitTab("pipeline")
            assertEquals("pipeline", viewModel.selectedCockpitTab.value)

            viewModel.selectCockpitTab("console")
            assertEquals("console", viewModel.selectedCockpitTab.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testToolMatrixFilteringAndSearch() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val toolchainService = FakeToolchainProvisioningService()
            val viewModel = SetupViewModel(manager, toolchainService)

            val collectJob = backgroundScope.launch(testDispatcher) {
                viewModel.filteredToolsMatrix.collect()
            }

            // Initially All categories (35 tools)
            assertEquals(35, viewModel.filteredToolsMatrix.value.size)

            // Filter by Dynamic Partitions category (5 tools)
            viewModel.selectToolCategory("Dynamic Partitions")
            assertEquals(5, viewModel.filteredToolsMatrix.value.size)
            assertTrue(viewModel.filteredToolsMatrix.value.all { it.category == "Dynamic Partitions" })

            // Filter by query "lpmake"
            viewModel.onToolSearchQueryChange("lpmake")
            assertEquals(1, viewModel.filteredToolsMatrix.value.size)
            assertEquals("lpmake", viewModel.filteredToolsMatrix.value.first().id)

            // Reset category to All, query "erofs" (4 tools: mkfs.erofs, dump.erofs, fsck.erofs, erofsfuse)
            viewModel.selectToolCategory("All")
            viewModel.onToolSearchQueryChange("erofs")
            assertEquals(4, viewModel.filteredToolsMatrix.value.size)

            // Clear query
            viewModel.onToolSearchQueryChange("")
            assertEquals(35, viewModel.filteredToolsMatrix.value.size)

            collectJob.cancel()
            testScheduler.advanceUntilIdle()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testToolActionDelegation() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val toolchainService = FakeToolchainProvisioningService()
            val viewModel = SetupViewModel(manager, toolchainService)

            viewModel.testTool("mkfs.erofs")
            testScheduler.advanceUntilIdle()

            assertEquals(1, toolchainService.testToolCount)
            assertEquals("mkfs.erofs", toolchainService.lastTestedToolId)

            viewModel.recompileTool("adb")
            testScheduler.advanceUntilIdle()

            assertEquals(1, toolchainService.recompileToolCount)
            assertEquals("adb", toolchainService.lastRecompiledToolId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testCloneGitRepositoryWithUrlResolvesProjectWorkspace() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val toolchainService = FakeToolchainProvisioningService().apply {
                _state.value = _state.value.copy(isAllReady = true)
            }
            val viewModel = SetupViewModel(manager, toolchainService)

            var openedWorkspace: Workspace? = null
            viewModel.cloneGitRepository("https://github.com/nubia/android_device_PQ84P01.git") { ws ->
                openedWorkspace = ws
            }
            testScheduler.advanceUntilIdle()

            assertEquals("~/LtiRomWorkDir/projects/android_device_PQ84P01", openedWorkspace?.path)
            assertEquals("android_device_PQ84P01", openedWorkspace?.name)
            assertEquals(openedWorkspace?.id, manager.currentWorkspace.value?.id)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testOpenDefaultWorkspaceSynchronizesActiveWorkspaceSession() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val toolchainService = FakeToolchainProvisioningService().apply {
                _state.value = _state.value.copy(isAllReady = true)
            }
            val viewModel = SetupViewModel(manager, toolchainService)

            var openedWorkspace: Workspace? = null
            viewModel.openDefaultWorkspace { ws ->
                openedWorkspace = ws
            }
            testScheduler.advanceUntilIdle()

            assertEquals("~/LtiRomWorkDir/target/PQ84P01_GLOBAL_A15", openedWorkspace?.path)
            assertEquals("REDMAGIC Astra (PQ84P01 • Global • Android 15)", openedWorkspace?.name)
            assertEquals(openedWorkspace?.id, manager.currentWorkspace.value?.id)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testWorkspaceEntryBlockedWhenEnvironmentUnverified() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val toolchainService = FakeToolchainProvisioningService().apply {
                shouldMarkReadyOnFullSetup = false
                _state.value = ToolchainSetupState(isAllReady = false)
            }
            val viewModel = SetupViewModel(manager, toolchainService)
            testScheduler.advanceUntilIdle()

            assertFalse(viewModel.toolchainSetupState.value.canLaunchWorkspace)
            assertEquals(SetupTab.PROJECTS, viewModel.selectedTab.value)

            var workspaceOpened = false

            // 1. openFolder must be blocked
            viewModel.openFolder { workspaceOpened = true }
            testScheduler.advanceUntilIdle()
            assertFalse(workspaceOpened)
            assertEquals(SetupTab.ENVIRONMENT, viewModel.selectedTab.value)
            kotlin.test.assertNotNull(viewModel.environmentNotice.value)

            // Dismiss notice & reset tab
            viewModel.dismissEnvironmentNotice()
            kotlin.test.assertNull(viewModel.environmentNotice.value)
            viewModel.selectTab(SetupTab.PROJECTS)

            // 2. cloneGitRepository must be blocked
            viewModel.cloneGitRepository("https://github.com/nubia/test.git") { workspaceOpened = true }
            testScheduler.advanceUntilIdle()
            assertFalse(workspaceOpened)
            assertEquals(SetupTab.ENVIRONMENT, viewModel.selectedTab.value)
            kotlin.test.assertNotNull(viewModel.environmentNotice.value)

            // Dismiss & reset
            viewModel.dismissEnvironmentNotice()
            viewModel.selectTab(SetupTab.PROJECTS)

            // 3. openDefaultWorkspace must be blocked
            viewModel.openDefaultWorkspace { workspaceOpened = true }
            testScheduler.advanceUntilIdle()
            assertFalse(workspaceOpened)
            assertEquals(SetupTab.ENVIRONMENT, viewModel.selectedTab.value)
            kotlin.test.assertNotNull(viewModel.environmentNotice.value)

            // Dismiss & reset
            viewModel.dismissEnvironmentNotice()
            viewModel.selectTab(SetupTab.PROJECTS)

            // 4. openRecentProject must be blocked
            val recent = RecentProject("p1", "Test", "/path", Clock.System.now())
            viewModel.openRecentProject(recent) { workspaceOpened = true }
            testScheduler.advanceUntilIdle()
            assertFalse(workspaceOpened)
            assertEquals(SetupTab.ENVIRONMENT, viewModel.selectedTab.value)
            kotlin.test.assertNotNull(viewModel.environmentNotice.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testWorkspaceEntryAllowedWhenServerConnectedAndToolsVerified() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val toolchainService = FakeToolchainProvisioningService()
            toolchainService._state.value = ToolchainSetupState(
                isAllReady = false,
                steps = ToolchainSetupState.defaultSteps().map { step ->
                    if (step.stage == org.ide.lti.core.domain.setup.SetupStepStage.SERVER_CONNECTIVITY ||
                        step.stage == org.ide.lti.core.domain.setup.SetupStepStage.TOOLCHAIN_COMPILATION
                    ) {
                        step.copy(status = StepStatus.SUCCESS)
                    } else step
                }
            )

            val viewModel = SetupViewModel(manager, toolchainService)
            testScheduler.advanceUntilIdle()

            assertTrue(viewModel.toolchainSetupState.value.isServerConnected)
            assertTrue(viewModel.toolchainSetupState.value.isToolsVerified)
            assertTrue(viewModel.toolchainSetupState.value.canLaunchWorkspace)

            var openedWorkspace: Workspace? = null
            viewModel.openDefaultWorkspace { ws ->
                openedWorkspace = ws
            }
            testScheduler.advanceUntilIdle()

            assertEquals("~/LtiRomWorkDir/target/PQ84P01_GLOBAL_A15", openedWorkspace?.path)
            assertEquals(openedWorkspace?.id, manager.currentWorkspace.value?.id)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testZeroStartDataStoreFastPathBypassesRecheck() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val toolchainService = FakeToolchainProvisioningService()
            toolchainService._state.value = ToolchainSetupState(isAllReady = true)

            val viewModel = SetupViewModel(manager, toolchainService)
            testScheduler.advanceUntilIdle()

            assertEquals(0, toolchainService.checkStatusCount)
            assertEquals(0, toolchainService.runFullSetupCount)
            assertTrue(viewModel.toolchainSetupState.value.canLaunchWorkspace)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testToolchainActionsWhenServiceUnavailableShowNotice() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val viewModel = SetupViewModel(manager)
            testScheduler.advanceUntilIdle()

            val expectedNotice = "Toolchain service is not available in this session."

            viewModel.checkEnvironmentStatus()
            assertEquals(expectedNotice, viewModel.environmentNotice.value)
            viewModel.dismissEnvironmentNotice()

            viewModel.runFullEnvironmentSetup()
            assertEquals(expectedNotice, viewModel.environmentNotice.value)
            viewModel.dismissEnvironmentNotice()

            viewModel.retrySetupStep()
            assertEquals(expectedNotice, viewModel.environmentNotice.value)
            viewModel.dismissEnvironmentNotice()

            viewModel.autoRemediateDoctorIssues()
            assertEquals(expectedNotice, viewModel.environmentNotice.value)
            viewModel.dismissEnvironmentNotice()

            viewModel.testTool("gcc")
            assertEquals(expectedNotice, viewModel.environmentNotice.value)
            viewModel.dismissEnvironmentNotice()

            viewModel.recompileTool("gcc")
            assertEquals(expectedNotice, viewModel.environmentNotice.value)
            viewModel.dismissEnvironmentNotice()

            viewModel.resetToolchainCacheAndRecheck()
            assertEquals(expectedNotice, viewModel.environmentNotice.value)
            viewModel.dismissEnvironmentNotice()
        } finally {
            Dispatchers.resetMain()
        }
    }
}

