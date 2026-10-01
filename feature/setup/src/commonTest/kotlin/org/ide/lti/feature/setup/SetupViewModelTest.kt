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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import org.ide.lti.core.domain.ports.EnvironmentReadinessPort
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.domain.setup.DiagnosticCheckItem
import org.ide.lti.core.domain.setup.SetupLogEvent
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlan
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolCategory
import org.ide.lti.core.domain.setup.ToolchainProvisioningService
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.domain.usecase.target.ProvisionAvbKeyUseCase
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.setup.EnvironmentReadiness
import org.ide.lti.core.model.setup.EnvironmentReadinessState
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceSession
import org.ide.lti.feature.setup.components.computeSidebarBadge
import org.ide.lti.feature.setup.components.computeSidebarSubtitle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass")
class SetupViewModelTest {

    private class FakeWorkspaceRepository : WorkspaceRepository {
        val recents = MutableStateFlow<List<RecentProject>>(emptyList())
        val workspaces = mutableMapOf<String, Workspace>()

        var shouldThrowOnGet = false
        var throwMessage = "Disk I/O error"

        override fun getWorkspaces(): Flow<List<Workspace>> = MutableStateFlow(workspaces.values.toList())
        override suspend fun getWorkspace(id: String): Workspace? {
            if (shouldThrowOnGet) throw IllegalStateException(throwMessage)
            return workspaces[id]
        }
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
            },
        ),
    ) : ToolchainProvisioningService {
        val _state = MutableStateFlow(initialState)
        override val state: StateFlow<ToolchainSetupState> = _state.asStateFlow()

        var verifyEnvironmentCount = 0
            private set

        /** Plans that were confirmed (executed), in order. The only path that can mutate. */
        val executedPlans = mutableListOf<SetupPlan>()
        val fullSetupCount: Int get() = executedPlans.count { it.kind == SetupPlanKind.FULL_SETUP }
        val stageRetryCount: Int get() = executedPlans.count { it.kind == SetupPlanKind.STAGE_RETRY }
        val cacheResetCount: Int get() = executedPlans.count { it.kind == SetupPlanKind.CACHE_RESET }
        val remediationCount: Int
            get() = executedPlans.count { it.targetStageOrToolId == SetupStepStage.SYSTEM_DIAGNOSTICS.name }
        var shouldMarkReadyOnFullSetup: Boolean = true
        var provisionAvbKeyCount = 0
            private set
        var testToolCount = 0
            private set
        var lastTestedToolId: String? = null
            private set
        val recompileToolCount: Int
            get() = executedPlans.count { it.kind == SetupPlanKind.REPAIR_TOOL }
        val lastRecompiledToolId: String?
            get() = executedPlans.lastOrNull { it.kind == SetupPlanKind.REPAIR_TOOL }?.targetStageOrToolId

        fun markAllReady() {
            _state.value = _state.value.copy(
                steps = _state.value.steps.map {
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
                },
            )
        }

        override suspend fun verifyEnvironment(): ToolchainSetupState {
            verifyEnvironmentCount++
            return _state.value
        }

        override suspend fun provisionAvbKey(): Boolean {
            provisionAvbKeyCount++
            _state.value = _state.value.copy(isAvbKeyProvisioned = true)
            return true
        }

        var prepareCount = 0
            private set
        var lastPreparedKind: SetupPlanKind? = null
            private set
        var lastPreparedTargetId: String? = null
            private set
        var lastPreparedAutoDoctor: Boolean? = null
            private set
        var confirmCount = 0
            private set

        // 003 plan contract: fakes never synthesize success for behaviour the test does not drive.
        override suspend fun prepare(kind: SetupPlanKind, targetId: String?, autoDoctorEnabled: Boolean): SetupPlan {
            prepareCount++
            lastPreparedKind = kind
            lastPreparedTargetId = targetId
            lastPreparedAutoDoctor = autoDoctorEnabled
            return SetupPlan(
                planId = "fake-plan-$prepareCount",
                revisionHash = "fake-rev",
                environmentKey = "fake-env",
                kind = kind,
                targetStageOrToolId = targetId,
                autoDoctorEnabled = autoDoctorEnabled,
            )
                .also { lastPreparedPlan = it }
        }
        private var lastPreparedPlan: SetupPlan? = null
        override suspend fun confirm(planId: String, revisionHash: String): SetupOutcome {
            confirmCount++
            val plan = lastPreparedPlan
            if (plan == null || planId != plan.planId || revisionHash != plan.revisionHash) {
                return SetupOutcome.Failed(
                    stage = null,
                    reason = "Confirmation requires the displayed plan ID and unchanged revision hash.",
                )
            }
            // Confirmed execution is the ONLY path that mutates in this fake.
            executedPlans += plan
            when (plan.kind) {
                SetupPlanKind.CACHE_RESET -> _state.value = ToolchainSetupState(
                    steps = ToolchainSetupState.defaultSteps().map {
                        if (it.stage == SetupStepStage.SERVER_CONNECTIVITY) {
                            it.copy(status = StepStatus.SUCCESS, provenance = StepProvenance.LIVE)
                        } else {
                            it.copy(status = StepStatus.PENDING, provenance = StepProvenance.LIVE)
                        }
                    },
                )
                SetupPlanKind.FULL_SETUP,
                SetupPlanKind.STAGE_RETRY,
                SetupPlanKind.REPAIR_TOOL,
                SetupPlanKind.BOOTSTRAP_PACKAGES,
                -> if (shouldMarkReadyOnFullSetup) markAllReady()
            }
            return SetupOutcome.Succeeded()
        }
        override fun observe(): Flow<SetupOutcome?> = flowOf(null)
        override fun observeActivity(): Flow<SetupLogEvent> = emptyFlow()
        var recoverCount = 0
            private set
        var lastRecoverAttemptId: String? = null
            private set
        override suspend fun recover(attemptId: String?): SetupOutcome {
            recoverCount++
            lastRecoverAttemptId = attemptId
            return SetupOutcome.Failed(stage = null, reason = "fake service has no journal")
        }
        override suspend fun cancel(): SetupOutcome =
            SetupOutcome.Failed(stage = null, reason = "fake service has nothing to cancel")

        var resumeCount = 0
            private set
        var lastResumedPlanId: String? = null
            private set
        var resumeOutcome: SetupOutcome = SetupOutcome.Failed(
            stage = null,
            reason = "fake service does not resume plans",
        )

        override suspend fun resume(planId: String): SetupOutcome {
            resumeCount++
            lastResumedPlanId = planId
            return resumeOutcome
        }

        var testToolOutcome: SetupOutcome = SetupOutcome.Succeeded()

        override suspend fun testTool(toolId: String): SetupOutcome {
            testToolCount++
            lastTestedToolId = toolId
            return testToolOutcome
        }
    }

    @Test
    fun testToolActionThatDoesNotSucceedAlwaysSaysWhy() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val toolchainService = FakeToolchainProvisioningService()
            val viewModel = SetupViewModel(WorkspaceManager(FakeWorkspaceRepository(), this), toolchainService)
            testScheduler.advanceUntilIdle()

            // A test refused because a check is running used to end with no word at all.
            toolchainService.testToolOutcome = SetupOutcome.Busy("environment-check")
            viewModel.testTool("lpmake")
            testScheduler.advanceUntilIdle()
            assertEquals(
                "Test lpmake did not start: another operation is running (environment-check).",
                viewModel.environmentNotice.value,
            )

            toolchainService.testToolOutcome = SetupOutcome.Interrupted("connection lost")
            viewModel.testTool("lpmake")
            testScheduler.advanceUntilIdle()
            assertEquals("Test lpmake was interrupted: connection lost", viewModel.environmentNotice.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeEnvironmentReadinessPort(
        initial: EnvironmentReadiness = EnvironmentReadiness(state = EnvironmentReadinessState.READY),
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
            assertEquals(0, toolchainService.fullSetupCount)

            // Set Up opens a preview (prepare) and mutates nothing until the plan is confirmed (FR-003).
            viewModel.previewSetup()
            testScheduler.advanceUntilIdle()
            assertEquals(1, toolchainService.prepareCount)
            assertEquals(SetupPlanKind.FULL_SETUP, toolchainService.lastPreparedKind)
            assertEquals(0, toolchainService.fullSetupCount)
            assertEquals(SetupOperationState.PREVIEW, viewModel.activeOperation.value?.executionState)

            // Only the explicit confirmation of the displayed plan executes it.
            viewModel.confirmOperation()
            testScheduler.advanceUntilIdle()
            assertEquals(1, toolchainService.confirmCount)
            assertEquals(1, toolchainService.fullSetupCount)
            assertTrue(viewModel.toolchainSetupState.value.isAllReady)

            // Retry is a mutation: it previews a stage plan, it never calls the legacy direct entry point.
            viewModel.retrySetupStep()
            testScheduler.advanceUntilIdle()
            assertEquals(2, toolchainService.prepareCount)
            assertEquals(SetupPlanKind.STAGE_RETRY, toolchainService.lastPreparedKind)
            assertEquals(0, toolchainService.stageRetryCount)
            viewModel.dismissPreview()

            // Auto-Fix previews a diagnostics remediation plan; nothing runs without confirmation.
            viewModel.autoRemediateDoctorIssues()
            testScheduler.advanceUntilIdle()
            assertEquals(3, toolchainService.prepareCount)
            assertEquals(SetupStepStage.SYSTEM_DIAGNOSTICS.name, toolchainService.lastPreparedTargetId)
            assertEquals(true, toolchainService.lastPreparedAutoDoctor)
            assertEquals(0, toolchainService.remediationCount)
            assertEquals(1, toolchainService.confirmCount)
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
            assertEquals(0, toolchainService.fullSetupCount)
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
            assertEquals(0, toolchainService.fullSetupCount)
            val verifiesBefore = toolchainService.verifyEnvironmentCount

            // Reset is a mutation: it is previewed as a CACHE_RESET plan and nothing is cleared yet.
            viewModel.previewResetToolchainCache()
            testScheduler.advanceUntilIdle()
            assertEquals(SetupPlanKind.CACHE_RESET, toolchainService.lastPreparedKind)
            assertEquals(0, toolchainService.cacheResetCount, "Preview must not clear the cache")
            assertEquals(SetupOperationKind.CACHE_RESET, viewModel.activeOperation.value?.kind)
            assertTrue(viewModel.toolchainSetupState.value.isAllReady, "State untouched before confirmation")

            // Explicit confirmation clears the cache and re-checks the environment live.
            viewModel.confirmOperation()
            testScheduler.advanceUntilIdle()
            assertEquals(1, toolchainService.cacheResetCount)
            assertEquals(0, toolchainService.fullSetupCount)
            assertFalse(viewModel.toolchainSetupState.value.isAllReady, "Cleared cache is not ready")
            assertEquals(
                verifiesBefore + 1,
                toolchainService.verifyEnvironmentCount,
                "Reset is followed by a live check",
            )
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
            assertEquals(0, toolchainService.fullSetupCount)

            // Case 1: isChecking is true -> isBusy is true
            toolchainService._state.value = toolchainService._state.value.copy(isChecking = true)
            assertTrue(viewModel.toolchainSetupState.value.isBusy)

            viewModel.checkEnvironmentStatus()
            viewModel.reverify()
            viewModel.previewSetup()
            viewModel.confirmOperation()
            viewModel.autoRemediateDoctorIssues()
            viewModel.retrySetupStep()
            viewModel.onProvisionAvbKey()
            testScheduler.advanceUntilIdle()

            // All counts must remain unchanged
            assertEquals(initialVerifyCount, toolchainService.verifyEnvironmentCount)
            assertEquals(0, toolchainService.prepareCount)
            assertEquals(0, toolchainService.confirmCount)
            assertEquals(0, toolchainService.fullSetupCount)
            assertEquals(0, toolchainService.remediationCount)
            assertEquals(0, toolchainService.stageRetryCount)
            assertEquals(0, toolchainService.provisionAvbKeyCount)

            // Case 2: activeOperationId is set -> isBusy is true
            toolchainService._state.value = toolchainService._state.value.copy(
                isChecking = false,
                activeOperationId = "op-busy",
            )
            assertTrue(viewModel.toolchainSetupState.value.isBusy)

            viewModel.checkEnvironmentStatus()
            viewModel.reverify()
            viewModel.previewSetup()
            viewModel.confirmOperation()
            viewModel.autoRemediateDoctorIssues()
            testScheduler.advanceUntilIdle()

            assertEquals(initialVerifyCount, toolchainService.verifyEnvironmentCount)
            assertEquals(0, toolchainService.prepareCount)
            assertEquals(0, toolchainService.fullSetupCount)
            assertEquals(0, toolchainService.remediationCount)

            // Case 3: isRunning is true -> isBusy is true
            toolchainService._state.value = toolchainService._state.value.copy(
                activeOperationId = null,
                isRunning = true,
            )
            assertTrue(viewModel.toolchainSetupState.value.isBusy)

            viewModel.checkEnvironmentStatus()
            viewModel.reverify()
            viewModel.previewSetup()
            viewModel.confirmOperation()
            viewModel.autoRemediateDoctorIssues()
            testScheduler.advanceUntilIdle()

            assertEquals(initialVerifyCount, toolchainService.verifyEnvironmentCount)
            assertEquals(0, toolchainService.prepareCount)
            assertEquals(0, toolchainService.fullSetupCount)
            assertEquals(0, toolchainService.remediationCount)

            // Case 4: Idle -> actions execute normally
            toolchainService._state.value = toolchainService._state.value.copy(isRunning = false)
            kotlin.test.assertFalse(viewModel.toolchainSetupState.value.isBusy)

            viewModel.checkEnvironmentStatus()
            testScheduler.advanceUntilIdle()
            assertEquals(initialVerifyCount + 1, toolchainService.verifyEnvironmentCount)

            viewModel.previewSetup()
            viewModel.confirmOperation()
            testScheduler.advanceUntilIdle()
            assertEquals(1, toolchainService.prepareCount)
            assertEquals(1, toolchainService.confirmCount)
            assertEquals(1, toolchainService.fullSetupCount)
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
            assertEquals(0, toolchainService.fullSetupCount)

            // Explicit reverify also re-runs verification
            viewModel.reverify()
            testScheduler.advanceUntilIdle()
            assertEquals(2, toolchainService.verifyEnvironmentCount)
            assertEquals(0, toolchainService.fullSetupCount)
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
                    },
                ),
            )

            val viewModel = SetupViewModel(manager, idleBridgeService)
            testScheduler.advanceUntilIdle()

            // Verification ran once on init
            assertEquals(1, idleBridgeService.verifyEnvironmentCount)
            // Auto-run MUST NOT trigger runFullSetup when bridge is idle / pending!
            assertEquals(0, idleBridgeService.fullSetupCount)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testAutoDoctorDisabledPreventsAutoFixPreviewAndAnyRemediation() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val toolchainService = FakeToolchainProvisioningService()
            toolchainService.markUnready()
            toolchainService._state.value = toolchainService._state.value.copy(
                diagnostics = listOf(
                    DiagnosticCheckItem(
                        id = "python",
                        title = "Python 3",
                        status = StepStatus.FAILED,
                    ),
                ),
            )

            val viewModel = SetupViewModel(manager, toolchainService)
            testScheduler.advanceUntilIdle()
            assertEquals(1, toolchainService.verifyEnvironmentCount)

            viewModel.setAutoDoctorEnabled(false)
            assertFalse(viewModel.isAutoDoctorEnabled.value)

            // Auto-Fix with Auto Doctor disabled: no plan, no remediation, an explicit notice (FR-004).
            viewModel.autoRemediateDoctorIssues()
            testScheduler.advanceUntilIdle()
            assertEquals(0, toolchainService.prepareCount)
            assertEquals(0, toolchainService.remediationCount)
            assertEquals(0, toolchainService.fullSetupCount)
            assertNull(viewModel.activeOperation.value)
            assertEquals(
                "Auto Doctor is disabled; enable it to preview automatic remediation.",
                viewModel.environmentNotice.value,
            )

            // A full setup preview still binds the disabled policy into the plan.
            viewModel.previewSetup()
            testScheduler.advanceUntilIdle()
            assertEquals(1, toolchainService.prepareCount)
            assertEquals(false, toolchainService.lastPreparedAutoDoctor)
            assertEquals(false, viewModel.activeOperation.value?.autoDoctorEnabled)
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
            viewModel.selectToolCategory(ToolCategory.DYNAMIC_PARTITIONS)
            assertEquals(5, viewModel.filteredToolsMatrix.value.size)
            assertTrue(viewModel.filteredToolsMatrix.value.all { it.category == ToolCategory.DYNAMIC_PARTITIONS })

            // Filter by query "lpmake"
            viewModel.onToolSearchQueryChange("lpmake")
            assertEquals(1, viewModel.filteredToolsMatrix.value.size)
            assertEquals("lpmake", viewModel.filteredToolsMatrix.value.first().id)

            // Reset category to All, query "erofs" (4 tools: mkfs.erofs, dump.erofs, fsck.erofs, erofsfuse)
            viewModel.selectToolCategory(null)
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
    fun testRecompileUnsupportedToolSetsNotice() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val toolchainService = FakeToolchainProvisioningService()
            val viewModel = SetupViewModel(manager, toolchainService)

            viewModel.recompileTool("unsupported_custom_tool")
            testScheduler.advanceUntilIdle()

            assertTrue(
                viewModel.environmentNotice.value?.contains("Repair") == true ||
                    viewModel.environmentNotice.value != null,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testOpenRecentProjectGatedWhenUnready() = runTest {
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
                ),
            )

            var workspaceOpened = false
            viewModel.openRecentProject(recent) {
                workspaceOpened = true
            }
            testScheduler.advanceUntilIdle()

            kotlin.test.assertFalse(
                workspaceOpened,
                "Opening a recent project must be blocked when environment verification has not passed",
            )
            kotlin.test.assertNotNull(
                viewModel.workspaceError.value,
                "Workspace error must be reported when opening fails gate",
            )
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
                    remediation = "Start LtiRomServer",
                ),
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
            },
        )
        assertEquals("Last verified 3h ago — re-checking…", computeSidebarSubtitle(restoredState, now))
        assertEquals("Checking", computeSidebarBadge(restoredState))

        // 2. Running verification
        val verifyingState = restoredState.copy(
            steps = restoredState.steps.mapIndexed { idx, s ->
                if (idx == 0) s.copy(status = StepStatus.RUNNING) else s
            },
        )
        assertEquals("Verifying…", computeSidebarSubtitle(verifyingState, now))
        assertEquals("Checking", computeSidebarBadge(verifyingState))

        // 3. Running setup pipeline
        val configuringState = restoredState.copy(isRunning = true)
        assertEquals("Configuring…", computeSidebarSubtitle(configuringState, now))
        assertEquals("Running", computeSidebarBadge(configuringState))

        // 4. All ready (verified live)
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
                if (idx == 1) {
                    s.copy(status = StepStatus.FAILED, provenance = StepProvenance.LIVE)
                } else {
                    s.copy(status = StepStatus.PENDING, provenance = StepProvenance.LIVE)
                }
            },
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

            viewModel.previewSetup()
            assertEquals(expectedNotice, viewModel.environmentNotice.value)
            viewModel.dismissEnvironmentNotice()

            viewModel.confirmOperation()
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

            viewModel.previewResetToolchainCache()
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
                            id = "host_compilers",
                            title = "Host Compilers",
                            category = org.ide.lti.core.domain.setup.DiagnosticCategory.COMPILERS,
                            status = StepStatus.WARNING,
                            detail = "Missing cmake.",
                            copyableCommand = "sudo apt-get install -y cmake",
                        ),
                    ),
                    stepRunIds = mapOf(
                        SetupStepStage.REPO_SYNCHRONIZATION to "setup:submodule:apktool",
                        SetupStepStage.TOOLCHAIN_COMPILATION to "setup:build:erofs-utils:cmake",
                    ),
                ),
            )
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val viewModel = SetupViewModel(
                workspaceManager = manager,
                toolchainService = fakeToolchain,
            )
            testScheduler.advanceUntilIdle()

            val state = viewModel.toolchainSetupState.value
            val compilerItem = state.diagnostics.find { it.id == "host_compilers" }
            assertEquals(StepStatus.WARNING, compilerItem?.status)
            assertTrue(compilerItem?.copyableCommand?.contains("apt-get install -y cmake") == true)
            assertEquals("setup:submodule:apktool", state.stepRunIds[SetupStepStage.REPO_SYNCHRONIZATION])
            assertEquals("setup:build:erofs-utils:cmake", state.stepRunIds[SetupStepStage.TOOLCHAIN_COMPILATION])
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testReconnectPendingAttemptDelegatesToServiceOrShowsNotice() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            // Case 1: No pending attempt -> sets notice
            val fakeToolchain1 = FakeToolchainProvisioningService()
            val repo1 = FakeWorkspaceRepository()
            val manager1 = WorkspaceManager(repo1, this)
            val viewModel1 = SetupViewModel(
                workspaceManager = manager1,
                toolchainService = fakeToolchain1,
            )
            testScheduler.advanceUntilIdle()

            viewModel1.reconnectPendingAttempt()
            assertEquals("Nothing to reconnect: no journaled attempt is pending.", viewModel1.environmentNotice.value)
            assertEquals(0, fakeToolchain1.recoverCount)

            // Case 2: Pending attempt present -> invokes recover
            val fakeToolchain2 = FakeToolchainProvisioningService(
                initialState = ToolchainSetupState(
                    pendingAttemptId = "attempt_rec_42",
                    recoveryBlockReason = "Crash during stage execution",
                ),
            )
            val repo2 = FakeWorkspaceRepository()
            val manager2 = WorkspaceManager(repo2, this)
            val viewModel2 = SetupViewModel(
                workspaceManager = manager2,
                toolchainService = fakeToolchain2,
            )
            testScheduler.advanceUntilIdle()

            viewModel2.reconnectPendingAttempt()
            testScheduler.advanceUntilIdle()

            assertTrue(fakeToolchain2.recoverCount >= 1, "reconnectPendingAttempt must delegate to recover on service")
            assertEquals("attempt_rec_42", fakeToolchain2.lastRecoverAttemptId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testDismissWorkspaceErrorClearsErrorState() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            repo.shouldThrowOnGet = true
            repo.throwMessage = "Simulated disk corruption"
            val manager = WorkspaceManager(repo, this)
            val project = RecentProject(
                workspaceId = "p_err",
                name = "ErrorProject",
                path = "/home/user/error_project",
                lastOpened = Clock.System.now(),
            )
            repo.recents.value = listOf(project)

            val viewModel = SetupViewModel(workspaceManager = manager)
            testScheduler.advanceUntilIdle()

            viewModel.openRecentProject(project) {}
            testScheduler.advanceUntilIdle()

            assertTrue(viewModel.workspaceError.value != null, "Error must be recorded in workspaceError state")

            viewModel.dismissWorkspaceError()
            assertNull(viewModel.workspaceError.value, "dismissWorkspaceError must clear workspaceError state to null")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testToolVersionsStateDelegation() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val viewModel = SetupViewModel(workspaceManager = manager)
            testScheduler.advanceUntilIdle()

            val versionsState = viewModel.toolVersionsState
            assertFalse(versionsState.rows.value.isEmpty(), "toolVersionsState rows should be populated")
            assertNull(versionsState.selectedGroupId.value)

            versionsState.startEdit(org.ide.lti.core.domain.setup.ToolGroupId("erofs-utils"))
            assertEquals(
                org.ide.lti.core.domain.setup.ToolGroupId("erofs-utils"),
                versionsState.selectedGroupId.value,
                "startEdit must update selectedGroupId",
            )
            versionsState.cancelEdit()
            assertNull(versionsState.selectedGroupId.value, "cancelEdit must reset selectedGroupId")
        } finally {
            Dispatchers.resetMain()
        }
    }
}
