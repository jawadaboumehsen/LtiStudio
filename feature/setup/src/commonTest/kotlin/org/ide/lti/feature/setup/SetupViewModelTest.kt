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
import org.ide.lti.core.domain.ports.EnvironmentReadinessPort
import org.ide.lti.core.model.setup.EnvironmentReadiness
import org.ide.lti.core.model.setup.EnvironmentReadinessState
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.domain.setup.DiagnosticCheckItem
import org.ide.lti.core.domain.setup.SetupOutcome
import kotlinx.coroutines.flow.flowOf
import org.ide.lti.core.domain.setup.SetupPlan
import org.ide.lti.core.domain.setup.SetupPlanKind
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
class SetupViewModelTest {

    private class FakeWorkspaceRepository : WorkspaceRepository {
        val recents = MutableStateFlow<List<RecentProject>>(emptyList())
        val workspaces = mutableMapOf<String, Workspace>()

        override fun getWorkspaces(): Flow<List<Workspace>> = MutableStateFlow(workspaces.values.toList())
        override suspend fun getWorkspace(id: String): Workspace? = workspaces[id]
        override suspend fun saveWorkspace(workspace: Workspace) {
            workspaces[workspace.id] = workspace
        }
        override suspend fun deleteWorkspace(id: String) {
            workspaces.remove(id)
        }
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

    private class FakeToolchainProvisioningService(
        initialState: ToolchainSetupState = ToolchainSetupState(
            steps = ToolchainSetupState.defaultSteps().map {
                if (it.stage == SetupStepStage.SERVER_CONNECTIVITY) {
                    it.copy(status = StepStatus.SUCCESS, provenance = StepProvenance.LIVE)
                } else {
                    it.copy(status = StepStatus.PENDING, provenance = StepProvenance.LIVE)
                }
            }
        ),
    ) : ToolchainProvisioningService {
        val _state = MutableStateFlow(initialState)
        override val state: StateFlow<ToolchainSetupState> = _state.asStateFlow()

        var verifyEnvironmentCount = 0
            private set
        var runFullSetupCount = 0
            private set
        var retryCurrentStepCount = 0
            private set
        var shouldMarkReadyOnFullSetup: Boolean = true
        var autoRemediateDoctorCount = 0
            private set
        var resetSetupCacheCount = 0
            private set
        var provisionAvbKeyCount = 0
            private set
        var testToolCount = 0
            private set
        var lastTestedToolId: String? = null
            private set
        var recompileToolCount = 0
            private set
        var lastRecompiledToolId: String? = null
            private set

        fun markAllReady() {
            _state.value = _state.value.copy(
                steps = _state.value.steps.map { it.copy(status = StepStatus.SUCCESS, provenance = StepProvenance.LIVE) },
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

        override suspend fun runFullSetup() {
            runFullSetupCount++
            if (shouldMarkReadyOnFullSetup) {
                markAllReady()
            }
        }

        override suspend fun retryStep(stage: SetupStepStage) {
            retryCurrentStepCount++
        }

        override suspend fun provisionAvbKey(): Boolean {
            provisionAvbKeyCount++
            _state.value = _state.value.copy(isAvbKeyProvisioned = true)
            return true
        }

        override suspend fun autoRemediateDoctor(): Boolean {
            autoRemediateDoctorCount++
            return true
        }


        // 003 plan contract: fakes never synthesize success for behaviour the test does not drive.
        override suspend fun prepare(kind: SetupPlanKind, targetId: String?, autoDoctorEnabled: Boolean): SetupPlan =
            SetupPlan(planId = "fake-plan", revisionHash = "fake-rev", environmentKey = "fake-env", kind = kind,
                targetStageOrToolId = targetId, autoDoctorEnabled = autoDoctorEnabled)
        override suspend fun confirm(planId: String, revisionHash: String): SetupOutcome =
            SetupOutcome.Failed(stage = null, reason = "fake service does not execute plans")
        override fun observe(): Flow<SetupOutcome?> = flowOf(null)
        override suspend fun recover(attemptId: String?): SetupOutcome =
            SetupOutcome.Failed(stage = null, reason = "fake service has no journal")
        override suspend fun cancel(): SetupOutcome =
            SetupOutcome.Failed(stage = null, reason = "fake service has nothing to cancel")

        override suspend fun testTool(toolId: String): SetupOutcome {
            testToolCount++
            lastTestedToolId = toolId
            return SetupOutcome.Succeeded()
        }

        override suspend fun repairTool(toolId: String): Boolean {
            recompileToolCount++
            lastRecompiledToolId = toolId
            return true
        }

        override suspend fun resetSetupCache(): SetupOutcome {
            resetSetupCacheCount++
            _state.value = ToolchainSetupState(
                steps = ToolchainSetupState.defaultSteps().map {
                    if (it.stage == SetupStepStage.SERVER_CONNECTIVITY) {
                        it.copy(status = StepStatus.SUCCESS, provenance = StepProvenance.LIVE)
                    } else {
                        it.copy(status = StepStatus.PENDING, provenance = StepProvenance.LIVE)
                    }
                }
            )
            return SetupOutcome.Succeeded()
        }
    }

    private class FakeEnvironmentReadinessPort(
        initial: EnvironmentReadiness = EnvironmentReadiness(state = EnvironmentReadinessState.READY)
    ) : EnvironmentReadinessPort {
        val flow = MutableStateFlow(initial)
        override fun observe(): Flow<EnvironmentReadiness> = flow.asStateFlow()
        override suspend fun refresh(): EnvironmentReadiness = flow.value
        override suspend fun forWorkspace(workspace: Workspace): EnvironmentReadiness = flow.value
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

            // Init triggers environment inspection only (1) and does NOT run full setup automatically (0)
            assertEquals(1, toolchainService.verifyEnvironmentCount)
            assertEquals(0, toolchainService.runFullSetupCount)

            // runFullEnvironmentSetup should trigger runFullSetup
            viewModel.runFullEnvironmentSetup()
            testScheduler.advanceUntilIdle()
            assertEquals(1, toolchainService.runFullSetupCount)
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
    fun testSetupRunsVerificationOnInitButSkipsFullRebuildWhenAlreadyReady() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val toolchainService = FakeToolchainProvisioningService()
            toolchainService.markAllReady()

            val viewModel = SetupViewModel(manager, toolchainService)
            testScheduler.advanceUntilIdle()

            // Verification runs on every init (no bypass), but full setup is skipped because already ready
            assertEquals(1, toolchainService.verifyEnvironmentCount)
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
            toolchainService.markAllReady()

            val viewModel = SetupViewModel(manager, toolchainService)
            testScheduler.advanceUntilIdle()
            assertEquals(0, toolchainService.runFullSetupCount)

            viewModel.resetToolchainCacheAndRecheck()
            testScheduler.advanceUntilIdle()

            assertEquals(1, toolchainService.resetSetupCacheCount)
            assertEquals(0, toolchainService.runFullSetupCount)
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
            toolchainService.markAllReady()

            val viewModel = SetupViewModel(manager, toolchainService)
            testScheduler.advanceUntilIdle()
            val initialVerifyCount = toolchainService.verifyEnvironmentCount // 1 from init
            assertEquals(1, initialVerifyCount)
            assertEquals(0, toolchainService.runFullSetupCount)

            // Case 1: isChecking is true -> isBusy is true
            toolchainService._state.value = toolchainService._state.value.copy(isChecking = true)
            assertTrue(viewModel.toolchainSetupState.value.isBusy)

            viewModel.checkEnvironmentStatus()
            viewModel.reverify()
            viewModel.runFullEnvironmentSetup()
            viewModel.autoRemediateDoctorIssues()
            viewModel.retrySetupStep()
            viewModel.onProvisionAvbKey()
            testScheduler.advanceUntilIdle()

            // All counts must remain unchanged
            assertEquals(initialVerifyCount, toolchainService.verifyEnvironmentCount)
            assertEquals(0, toolchainService.runFullSetupCount)
            assertEquals(0, toolchainService.autoRemediateDoctorCount)
            assertEquals(0, toolchainService.retryCurrentStepCount)
            assertEquals(0, toolchainService.provisionAvbKeyCount)

            // Case 2: isFixing is true -> isBusy is true
            toolchainService._state.value = toolchainService._state.value.copy(isChecking = false, isFixing = true)
            assertTrue(viewModel.toolchainSetupState.value.isBusy)

            viewModel.checkEnvironmentStatus()
            viewModel.reverify()
            viewModel.runFullEnvironmentSetup()
            viewModel.autoRemediateDoctorIssues()
            testScheduler.advanceUntilIdle()

            assertEquals(initialVerifyCount, toolchainService.verifyEnvironmentCount)
            assertEquals(0, toolchainService.runFullSetupCount)
            assertEquals(0, toolchainService.autoRemediateDoctorCount)

            // Case 3: isRunning is true -> isBusy is true
            toolchainService._state.value = toolchainService._state.value.copy(isFixing = false, isRunning = true)
            assertTrue(viewModel.toolchainSetupState.value.isBusy)

            viewModel.checkEnvironmentStatus()
            viewModel.reverify()
            viewModel.runFullEnvironmentSetup()
            viewModel.autoRemediateDoctorIssues()
            testScheduler.advanceUntilIdle()

            assertEquals(initialVerifyCount, toolchainService.verifyEnvironmentCount)
            assertEquals(0, toolchainService.runFullSetupCount)
            assertEquals(0, toolchainService.autoRemediateDoctorCount)

            // Case 4: Idle -> actions execute normally
            toolchainService._state.value = toolchainService._state.value.copy(isRunning = false)
            kotlin.test.assertFalse(viewModel.toolchainSetupState.value.isBusy)

            viewModel.checkEnvironmentStatus()
            testScheduler.advanceUntilIdle()
            assertEquals(initialVerifyCount + 1, toolchainService.verifyEnvironmentCount)

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
    fun testAutoRunEnvironmentSetupExecutesDoctorRemediationAndFullSetup() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val toolchainService = FakeToolchainProvisioningService()

            // Configure initial state with failed doctor item and not ready
            toolchainService.markUnready()
            toolchainService._state.value = toolchainService._state.value.copy(
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
            // Init inspects only, no auto-mutation
            assertEquals(1, toolchainService.verifyEnvironmentCount)
            assertEquals(0, toolchainService.autoRemediateDoctorCount)
            assertEquals(0, toolchainService.runFullSetupCount)

            // Explicit execution of setup pipeline runs doctor remediation + setup
            viewModel.autoRunEnvironmentSetup(autoFix = true)
            testScheduler.advanceUntilIdle()

            assertEquals(2, toolchainService.verifyEnvironmentCount)
            assertEquals(1, toolchainService.autoRemediateDoctorCount)
            assertEquals(1, toolchainService.runFullSetupCount)
            assertTrue(viewModel.toolchainSetupState.value.isAllReady)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testAutoRunEnvironmentSetupAlwaysVerifiesAndNeverSkipsOnInit() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val toolchainService = FakeToolchainProvisioningService()
            toolchainService.markAllReady()

            val viewModel = SetupViewModel(manager, toolchainService)
            testScheduler.advanceUntilIdle()

            // Verification must ALWAYS run on startup (no 24h skip)
            assertEquals(1, toolchainService.verifyEnvironmentCount)
            // But full setup is skipped because steps are already ready
            assertEquals(0, toolchainService.runFullSetupCount)

            // Explicit reverify also re-runs verification
            viewModel.reverify()
            testScheduler.advanceUntilIdle()
            assertEquals(2, toolchainService.verifyEnvironmentCount)
            assertEquals(0, toolchainService.runFullSetupCount)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testAutoRunCallsRunFullSetupOnlyWhenToolchainOrReposPendingNeverForIdleBridge() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            // Bridge is PENDING / idle (not yet connected)
            val idleBridgeService = FakeToolchainProvisioningService(
                initialState = ToolchainSetupState(
                    steps = ToolchainSetupState.defaultSteps().map {
                        it.copy(status = StepStatus.PENDING, provenance = StepProvenance.LIVE)
                    }
                )
            )

            val viewModel = SetupViewModel(manager, idleBridgeService)
            testScheduler.advanceUntilIdle()

            // Verification ran once on init
            assertEquals(1, idleBridgeService.verifyEnvironmentCount)
            // Auto-run MUST NOT trigger runFullSetup when bridge is idle / pending!
            assertEquals(0, idleBridgeService.runFullSetupCount)
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
            toolchainService.markAllReady()

            val viewModel = SetupViewModel(manager, toolchainService)
            testScheduler.advanceUntilIdle()
            assertEquals(1, toolchainService.verifyEnvironmentCount)

            // Configure state with issues
            toolchainService.markUnready()
            toolchainService._state.value = toolchainService._state.value.copy(
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
            assertEquals(2, toolchainService.verifyEnvironmentCount)
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
            toolchainService.markAllReady()

            val viewModel = SetupViewModel(manager, toolchainService)
            testScheduler.advanceUntilIdle()
            val initialVerify = toolchainService.verifyEnvironmentCount
            assertEquals(0, toolchainService.runFullSetupCount)

            // Mark busy
            toolchainService._state.value = toolchainService._state.value.copy(isRunning = true)
            assertTrue(viewModel.toolchainSetupState.value.isBusy)

            viewModel.autoRunEnvironmentSetup(autoFix = true)
            testScheduler.advanceUntilIdle()

            // Guarded: checkStatusCount remains unchanged, no doctor fix or full setup
            assertEquals(initialVerify, toolchainService.verifyEnvironmentCount)
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
            assertTrue(viewModel.filteredToolsMatrix.value.all { it.category.displayName == "Dynamic Partitions" })

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
    fun testOpenRecentProjectAllowedOffline() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val toolchainService = FakeToolchainProvisioningService().apply {
                shouldMarkReadyOnFullSetup = false
                markUnready()
            }
            val viewModel = SetupViewModel(manager, toolchainService)
            testScheduler.advanceUntilIdle()

            val recent = RecentProject(
                workspaceId = "offline_ws",
                name = "Offline Workspace",
                path = "~/LtiRomWorkDir/workspaces/offline_ws",
                lastOpened = Clock.System.now(),
            )
            repo.addRecentProject(recent)
            repo.saveWorkspace(
                Workspace(
                    id = "offline_ws",
                    name = "Offline Workspace",
                    path = "~/LtiRomWorkDir/workspaces/offline_ws",
                )
            )

            var workspaceOpened = false
            viewModel.openRecentProject(recent) {
                workspaceOpened = true
            }
            testScheduler.advanceUntilIdle()

            assertTrue(workspaceOpened, "Opening a recent project should be allowed even when environment is offline/unverified")
            assertEquals(SetupTab.PROJECTS, viewModel.selectedTab.value)
            kotlin.test.assertNull(viewModel.environmentNotice.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testCreateAndRunGatedOnReadiness() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val readinessPort = FakeEnvironmentReadinessPort(
                initial = EnvironmentReadiness(
                    state = EnvironmentReadinessState.SERVICE_UNREACHABLE,
                    failingCheck = "Daemon unreachable",
                    remediation = "Start LtiRomServer"
                )
            )
            val viewModel = SetupViewModel(
                workspaceManager = manager,
                environmentReadinessPort = readinessPort,
            )
            testScheduler.advanceUntilIdle()

            assertEquals(EnvironmentReadinessState.SERVICE_UNREACHABLE, viewModel.environmentReadiness.value?.state)
            assertEquals("Daemon unreachable", viewModel.environmentReadiness.value?.failingCheck)

            // Transition readiness to READY
            readinessPort.flow.value = EnvironmentReadiness(state = EnvironmentReadinessState.READY)
            testScheduler.advanceUntilIdle()

            assertEquals(EnvironmentReadinessState.READY, viewModel.environmentReadiness.value?.state)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testSidebarAndStateMappingForRestoredProvenance() = runTest {
        val now = 1700000000000L
        val threeHoursAgo = now - (3L * 60 * 60 * 1000)

        // 1. Restored steps pending re-check
        val restoredState = ToolchainSetupState(
            steps = ToolchainSetupState.defaultSteps().map {
                it.copy(
                    status = StepStatus.PENDING,
                    provenance = StepProvenance.RESTORED,
                    verifiedAtEpochMs = threeHoursAgo,
                )
            }
        )
        assertEquals("Last verified 3h ago — re-checking…", computeSidebarSubtitle(restoredState, now))
        assertEquals("Checking", computeSidebarBadge(restoredState))

        // 2. Running verification
        val verifyingState = restoredState.copy(
            steps = restoredState.steps.mapIndexed { idx, s ->
                if (idx == 0) s.copy(status = StepStatus.RUNNING) else s
            }
        )
        assertEquals("Verifying…", computeSidebarSubtitle(verifyingState, now))
        assertEquals("Checking", computeSidebarBadge(verifyingState))

        // 3. Running setup pipeline
        val configuringState = restoredState.copy(isRunning = true)
        assertEquals("Configuring…", computeSidebarSubtitle(configuringState, now))
        assertEquals("Running", computeSidebarBadge(configuringState))

        // 4. Fixing doctor issues
        val fixingState = restoredState.copy(isFixing = true)
        assertEquals("Fixing packages…", computeSidebarSubtitle(fixingState, now))
        assertEquals("Fixing", computeSidebarBadge(fixingState))

        // 5. All ready (verified live)
        val readyState = ToolchainSetupState(
            steps = ToolchainSetupState.defaultSteps().map {
                it.copy(status = StepStatus.SUCCESS, provenance = StepProvenance.LIVE)
            },
            lastVerifiedTimestamp = now,
        )
        assertTrue(readyState.isAllReady)
        assertEquals("Verified just now", computeSidebarSubtitle(readyState, now))
        assertEquals("Ready", computeSidebarBadge(readyState))

        // 6. Action required (failure)
        val failedState = ToolchainSetupState(
            steps = ToolchainSetupState.defaultSteps().mapIndexed { idx, s ->
                if (idx == 1) s.copy(status = StepStatus.FAILED, provenance = StepProvenance.LIVE)
                else s.copy(status = StepStatus.PENDING, provenance = StepProvenance.LIVE)
            }
        )
        assertEquals("Action required", computeSidebarSubtitle(failedState, now))
        assertEquals("Fix needed", computeSidebarBadge(failedState))
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

    @Test
    fun testDurableRunIdsAndSudoRemediationStateExposed() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val fakeToolchain = FakeToolchainProvisioningService(
                initialState = ToolchainSetupState(
                    steps = ToolchainSetupState.defaultSteps(),
                    diagnostics = listOf(
                        DiagnosticCheckItem(
                            id = "sudo_noninteractive",
                            title = "Non-Interactive Sudo Elevation",
                            category = org.ide.lti.core.domain.setup.DiagnosticCategory.OS_UTILITIES,
                            status = StepStatus.WARNING,
                            detail = "Non-interactive sudo is not configured.",
                            copyableCommand = "echo \"\$USER ALL=(ALL) NOPASSWD: ALL\" | sudo tee /etc/sudoers.d/ltirom",
                        )
                    ),
                    stepRunIds = mapOf(
                        SetupStepStage.REPO_SYNCHRONIZATION to "setup:submodule:apktool",
                        SetupStepStage.TOOLCHAIN_COMPILATION to "setup:build:erofs-utils:cmake"
                    )
                )
            )
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val viewModel = SetupViewModel(
                workspaceManager = manager,
                toolchainService = fakeToolchain,
            )
            testScheduler.advanceUntilIdle()

            val state = viewModel.toolchainSetupState.value
            val sudoItem = state.diagnostics.find { it.id == "sudo_noninteractive" }
            assertEquals(StepStatus.WARNING, sudoItem?.status)
            assertTrue(sudoItem?.copyableCommand?.contains("sudo tee /etc/sudoers.d/ltirom") == true)
            assertEquals("setup:submodule:apktool", state.stepRunIds[SetupStepStage.REPO_SYNCHRONIZATION])
            assertEquals("setup:build:erofs-utils:cmake", state.stepRunIds[SetupStepStage.TOOLCHAIN_COMPILATION])
        } finally {
            Dispatchers.resetMain()
        }
    }
}

