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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.ide.lti.core.domain.setup.DiagnosticCategory
import org.ide.lti.core.domain.setup.DiagnosticCheckItem
import org.ide.lti.core.domain.setup.SetupOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.ide.lti.core.domain.setup.SetupPlan
import org.ide.lti.core.domain.setup.SetupPlanAction
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainProvisioningService
import org.ide.lti.core.domain.setup.ToolchainSetupState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EnvironmentSetupStateTest {

    private fun runEnvironmentTest(
        testBody: suspend TestScope.(environmentScope: CoroutineScope) -> Unit
    ) = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        val envScope = CoroutineScope(testDispatcher + SupervisorJob())
        try {
            testBody(envScope)
        } finally {
            envScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private class SpyToolchainProvisioningService(
        initialState: ToolchainSetupState = ToolchainSetupState(
            steps = ToolchainSetupState.defaultSteps().map {
                it.copy(status = StepStatus.PENDING, provenance = StepProvenance.LIVE)
            }
        )
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
            get() = executedPlans.count { plan -> plan.orderedActions.any { it is SetupPlanAction.InstallPackages } }
        var testToolCount = 0
            private set


        override suspend fun verifyEnvironment(): ToolchainSetupState {
            verifyEnvironmentCount++
            return _state.value
        }

        override suspend fun checkStatus(): ToolchainSetupState {
            verifyEnvironmentCount++
            return _state.value
        }

        override suspend fun provisionAvbKey(): Boolean = true


        var prepareCount = 0
            private set
        var confirmCount = 0
            private set
        var lastConfirmedPlanId: String? = null
            private set
        var lastConfirmedRevisionHash: String? = null
            private set
        var confirmOutcome: SetupOutcome = SetupOutcome.Failed(
            stage = null,
            reason = "fake service does not execute plans",
        )

        private val preparedPlans = mutableMapOf<String, SetupPlan>()

        // 003 plan contract: fakes never synthesize success for behaviour the test does not drive.
        var lastPreparedKind: SetupPlanKind? = null
            private set

        override suspend fun prepare(kind: SetupPlanKind, targetId: String?, autoDoctorEnabled: Boolean): SetupPlan {
            prepareCount++
            lastPreparedKind = kind
            // Actions are derived from the fake's own state the way the real factory does, so the
            // preview the state holder shows is exactly what this fake would execute.
            val snapshot = _state.value
            val actions = buildList {
                val hasFailedDiagnostics = snapshot.diagnostics.any { it.status == StepStatus.FAILED }
                if (autoDoctorEnabled && hasFailedDiagnostics) {
                    add(SetupPlanAction.InstallPackages(listOf("build-essential", "cmake")))
                }
                val repoPending = snapshot.steps.any {
                    it.stage == SetupStepStage.REPO_SYNCHRONIZATION && it.status != StepStatus.SUCCESS
                }
                if (repoPending) add(SetupPlanAction.SyncSources(listOf("android-tools")))
            }
            return SetupPlan(
                planId = "fake-plan-$prepareCount",
                revisionHash = "fake-rev-${snapshot.hashCode()}-$autoDoctorEnabled",
                environmentKey = snapshot.activeDistro ?: "fake-env",
                kind = kind,
                targetStageOrToolId = targetId,
                autoDoctorEnabled = autoDoctorEnabled,
                orderedActions = if (kind == SetupPlanKind.CACHE_RESET) emptyList() else actions,
            ).also { preparedPlans[it.planId] = it }
        }

        override suspend fun confirm(planId: String, revisionHash: String): SetupOutcome {
            confirmCount++
            lastConfirmedPlanId = planId
            lastConfirmedRevisionHash = revisionHash
            // Like the real adapter: a plan that was never prepared, or whose hash changed, does not run.
            val plan = preparedPlans[planId]
            if (plan == null || plan.revisionHash != revisionHash) {
                return SetupOutcome.Failed(
                    stage = null,
                    reason = "Confirmation requires the displayed plan ID and unchanged revision hash.",
                )
            }
            executedPlans += plan
            if (plan.kind == SetupPlanKind.CACHE_RESET && confirmOutcome is SetupOutcome.Succeeded) {
                _state.value = ToolchainSetupState()
            }
            return confirmOutcome
        }

        val observedOutcomes = MutableStateFlow<SetupOutcome?>(null)
        override fun observe(): Flow<SetupOutcome?> = observedOutcomes.asStateFlow()

        var recoverOutcome: SetupOutcome? = null
        var recoverCount = 0
            private set
        var lastRecoverAttemptId: String? = null
            private set
        val recoverDeferred = CompletableDeferred<SetupOutcome>()

        override suspend fun recover(attemptId: String?): SetupOutcome {
            recoverCount++
            lastRecoverAttemptId = attemptId
            return recoverOutcome ?: recoverDeferred.await()
        }
        override suspend fun cancel(): SetupOutcome =
            SetupOutcome.Failed(stage = null, reason = "fake service has nothing to cancel")

        override suspend fun testTool(toolId: String): SetupOutcome {
            testToolCount++
            return SetupOutcome.Succeeded()
        }

    }

    @Test
    fun testInitNoMutationOnlyPerformsInspection() = runEnvironmentTest { envScope ->
        val spyService = SpyToolchainProvisioningService()
        val stateHolder = EnvironmentSetupState(scope = envScope, toolchainService = spyService)

        // Perform initial check
        stateHolder.checkEnvironmentStatus()
        testScheduler.advanceUntilIdle()

        // Verify that only inspection/probing occurred
        assertEquals(1, spyService.verifyEnvironmentCount, "Inspection must be executed exactly once")
        assertEquals(0, spyService.fullSetupCount, "Initial check must NEVER trigger full setup automatically")
        assertEquals(0, spyService.remediationCount, "Initial check must NEVER auto-install packages")
        assertEquals(0, spyService.stageRetryCount, "Initial check must NEVER retry steps")
        assertNull(stateHolder.activeOperation.value, "Initial check must not leave an active mutation operation")
    }

    @Test
    fun testPreviewPresentsPlannedChangesAndRequiredElevationWithoutMutation() = runEnvironmentTest { envScope ->
        val spyService = SpyToolchainProvisioningService(
            initialState = ToolchainSetupState(
                steps = ToolchainSetupState.defaultSteps(),
                diagnostics = listOf(
                    DiagnosticCheckItem(
                        id = "host_compilers",
                        title = "Host Compilers",
                        category = DiagnosticCategory.COMPILERS,
                        status = StepStatus.FAILED,
                    )
                )
            )
        )
        val stateHolder = EnvironmentSetupState(scope = envScope, toolchainService = spyService)

        // Request preview
        stateHolder.previewSetup()
        testScheduler.advanceUntilIdle()

        val operation = stateHolder.activeOperation.value
        assertNotNull(operation, "Preview must produce a SetupOperation")
        assertEquals(SetupOperationState.PREVIEW, operation.executionState)
        assertEquals(SetupOperationKind.FULL_SETUP, operation.kind)

        // Assert planned changes details
        val changes = operation.plannedChanges
        assertTrue(changes.packagesToInstall.contains("build-essential"), "Planned changes must list required packages")
        assertTrue(changes.requiresElevation, "Elevation must be flagged when packages need installation")
        assertNotNull(changes.elevationReason, "Elevation reason must be clearly explained")

        // Zero mutation during preview
        assertEquals(0, spyService.fullSetupCount, "Preview must NOT execute full setup")
        assertEquals(0, spyService.remediationCount, "Preview must NOT mutate packages")

        // Dismiss preview
        stateHolder.dismissPreview()
        assertNull(stateHolder.activeOperation.value, "Dismissing preview must clear active operation")
    }

    @Test
    fun testRepeatedClicksDoNotDuplicateMutationsSingleFlight() = runEnvironmentTest { envScope ->
        val spyService = SpyToolchainProvisioningService()
        val stateHolder = EnvironmentSetupState(scope = envScope, toolchainService = spyService)

        // Mark the service as busy (running an operation)
        spyService._state.value = spyService._state.value.copy(isRunning = true)

        // Multiple repeated clicks
        stateHolder.checkEnvironmentStatus()
        stateHolder.previewSetup()
        stateHolder.confirmOperation()
        stateHolder.retryStage(SetupStepStage.TOOLCHAIN_COMPILATION)
        testScheduler.advanceUntilIdle()

        // All mutation counts must remain 0
        assertEquals(0, spyService.verifyEnvironmentCount)
        assertEquals(0, spyService.fullSetupCount)
        assertEquals(0, spyService.stageRetryCount)
        assertEquals(0, spyService.remediationCount)
    }

    @Test
    fun testAutoDoctorHonoredWhenEnabledAndPreventedWhenDisabled() = runEnvironmentTest { envScope ->
        val spyService = SpyToolchainProvisioningService(
            initialState = ToolchainSetupState(
                steps = ToolchainSetupState.defaultSteps(),
                diagnostics = listOf(
                    DiagnosticCheckItem(
                        id = "host_compilers",
                        title = "Host Compilers",
                        category = DiagnosticCategory.COMPILERS,
                        status = StepStatus.FAILED,
                    )
                )
            )
        )
        val stateHolder = EnvironmentSetupState(scope = envScope, toolchainService = spyService)

        // Case 1: Auto Doctor enabled (default)
        assertTrue(stateHolder.isAutoDoctorEnabled.value)
        stateHolder.previewSetup()
        stateHolder.confirmOperation()
        testScheduler.advanceUntilIdle()

        assertEquals(1, spyService.remediationCount, "Auto doctor remediation must run when enabled")
        assertEquals(1, spyService.fullSetupCount, "Full setup must run on confirmed operation")

        // Reset service state and configure Case 2: Auto Doctor disabled
        spyService._state.value = ToolchainSetupState(
            steps = ToolchainSetupState.defaultSteps(),
            diagnostics = listOf(
                DiagnosticCheckItem(
                    id = "host_compilers",
                    title = "Host Compilers",
                    category = DiagnosticCategory.COMPILERS,
                    status = StepStatus.FAILED,
                )
            )
        )
        stateHolder.setAutoDoctorEnabled(false)
        assertFalse(stateHolder.isAutoDoctorEnabled.value)

        stateHolder.previewSetup()
        assertFalse(stateHolder.activeOperation.value?.plannedChanges?.autoDoctorRemediation == true)

        stateHolder.confirmOperation()
        testScheduler.advanceUntilIdle()

        // remediationCount should remain 1 (from Case 1), NOT incremented in Case 2!
        assertEquals(1, spyService.remediationCount, "Auto doctor remediation must NOT run when disabled")
        assertEquals(2, spyService.fullSetupCount, "Full setup runs even with auto doctor disabled")
    }

    @Test
    fun testReconnectAttachesToDurableRunId() = runEnvironmentTest { envScope ->
        val spyService = SpyToolchainProvisioningService()
        val stateHolder = EnvironmentSetupState(scope = envScope, toolchainService = spyService)

        val durableRunId = "durable-run-submodule-001-abc"
        stateHolder.attachToDurableRun(durableRunId, stage = SetupStepStage.REPO_SYNCHRONIZATION)

        val op = stateHolder.activeOperation.value
        assertNotNull(op)
        assertEquals(durableRunId, op.operationId)
        assertEquals(SetupStepStage.REPO_SYNCHRONIZATION, op.stageId)
        assertEquals(SetupOperationState.RECONNECTING, op.executionState)
    }

    @Test
    fun testReconnectOnCreationAttachesToInFlightRunWithoutStartingANewOne() = runEnvironmentTest { envScope ->
        val spyService = SpyToolchainProvisioningService(
            initialState = ToolchainSetupState(
                steps = ToolchainSetupState.defaultSteps(),
                isRunning = true,
                currentStage = SetupStepStage.REPO_SYNCHRONIZATION,
                stepRunIds = mapOf(SetupStepStage.REPO_SYNCHRONIZATION to "durable-run-existing-123"),
            )
        )

        // Simulate re-creating the state holder while the underlying service is already mid-run,
        // e.g. navigating back to the Environment destination.
        val stateHolder = EnvironmentSetupState(scope = envScope, toolchainService = spyService)
        testScheduler.advanceUntilIdle()

        val op = stateHolder.activeOperation.value
        assertNotNull(op, "Re-creating the state holder must reconnect to the in-flight run")
        assertEquals("durable-run-existing-123", op.operationId)
        assertEquals(SetupStepStage.REPO_SYNCHRONIZATION, op.stageId)
        assertEquals(SetupOperationState.RECONNECTING, op.executionState)
        assertEquals(0, spyService.fullSetupCount, "Reconnecting must not start a new mutation")
        assertEquals(0, spyService.stageRetryCount, "Reconnecting must not start a new mutation")
    }

    @Test
    fun testSessionStateRetainedAcrossDestinationSwitches() = runEnvironmentTest { envScope ->
        val spyService = SpyToolchainProvisioningService()
        val stateHolder = EnvironmentSetupState(scope = envScope, toolchainService = spyService)

        // Mutate session properties
        stateHolder.selectStage(SetupStepStage.SYSTEM_DIAGNOSTICS)
        stateHolder.setAutoDoctorEnabled(false)

        assertEquals(SetupStepStage.SYSTEM_DIAGNOSTICS, stateHolder.selectedStage.value)
        assertFalse(stateHolder.isAutoDoctorEnabled.value)

        // Simulate reading mapped presentation
        val presentation = stateHolder.environmentPresentation.value
        assertEquals(5, presentation.stages.size)
    }

    @Test
    fun testFreshCheckOffersSetUpAfterOneCheck_SC001() = runEnvironmentTest { envScope ->
        // Fresh healthy runtime with missing tools/repos
        val initialSteps = ToolchainSetupState.defaultSteps().map {
            it.copy(status = StepStatus.PENDING, provenance = StepProvenance.LIVE)
        }
        val spyService = SpyToolchainProvisioningService(
            initialState = ToolchainSetupState(
                steps = initialSteps,
                checkedAt = null,
                lastReadyAt = null,
            )
        )
        val stateHolder = EnvironmentSetupState(scope = envScope, toolchainService = spyService)

        // Before check: state is Unknown, action is CHECK
        val initialPresentation = stateHolder.environmentPresentation.value
        assertEquals(EnvironmentState.Unknown, initialPresentation.environmentState)
        assertEquals(PrimaryAction.CHECK, initialPresentation.primaryAction)

        // When check executes: simulate live check completing with missing tools/repos
        // checkStatus updates checkedAt but NOT lastReadyAt
        val checkTime = 1700000000000L
        spyService._state.value = ToolchainSetupState(
            checkedAt = checkTime,
            lastReadyAt = null,
            steps = listOf(
                SetupStepDetail(
                    stage = SetupStepStage.WSL_DETECTION,
                    title = "WSL",
                    description = "",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.LIVE,
                ),
                SetupStepDetail(
                    stage = SetupStepStage.SERVER_CONNECTIVITY,
                    title = "Server",
                    description = "",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.LIVE,
                ),
                SetupStepDetail(
                    stage = SetupStepStage.SYSTEM_DIAGNOSTICS,
                    title = "Doctor",
                    description = "",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.LIVE,
                ),
                SetupStepDetail(
                    stage = SetupStepStage.REPO_SYNCHRONIZATION,
                    title = "Repos",
                    description = "",
                    status = StepStatus.PENDING,
                    provenance = StepProvenance.LIVE,
                ),
                SetupStepDetail(
                    stage = SetupStepStage.TOOLCHAIN_COMPILATION,
                    title = "Tools",
                    description = "",
                    status = StepStatus.PENDING,
                    provenance = StepProvenance.LIVE,
                ),
            )
        )
        stateHolder.checkEnvironmentStatus()
        testScheduler.advanceUntilIdle()

        // After ONE check: Set Up is offered immediately (SC-001)
        val postCheckPresentation = stateHolder.environmentPresentation.value
        assertTrue(postCheckPresentation.environmentState is EnvironmentState.CheckedNeedsSetup)
        assertEquals(PrimaryAction.SET_UP, postCheckPresentation.primaryAction)
        assertEquals(checkTime, postCheckPresentation.checkedAt)
        assertNull(postCheckPresentation.lastVerifiedAt, "lastReadyAt must remain null when tools are missing")
    }

    @Test
    fun testNullServiceProducesUnavailableStateNotWarningFallback() = runEnvironmentTest { envScope ->
        // No toolchain service bound in DI
        val stateHolder = EnvironmentSetupState(scope = envScope, toolchainService = null)
        testScheduler.advanceUntilIdle()

        assertFalse(stateHolder.toolchainSetupState.value.isAllReady, "Missing service must NEVER be ready")
        val presentation = stateHolder.environmentPresentation.value
        assertTrue(
            presentation.environmentState is EnvironmentState.Unavailable,
            "Missing service must be Unavailable, never ready-warning fallback"
        )
        assertEquals(PrimaryAction.RETRY_CONNECTION, presentation.primaryAction)
    }

    @Test
    fun testRestoredStateRequiresCheckAndIsNotPresentedAsReady() = runEnvironmentTest { envScope ->
        val persistedTimestamp = 1690000000000L
        val restoredSteps = ToolchainSetupState.defaultSteps().map {
            it.copy(
                status = StepStatus.PENDING,
                provenance = StepProvenance.RESTORED,
                verifiedAtEpochMs = persistedTimestamp
            )
        }
        val spyService = SpyToolchainProvisioningService(
            initialState = ToolchainSetupState(
                steps = restoredSteps,
                lastVerifiedTimestamp = persistedTimestamp,
                checkedAt = null, // No live check in this session
                lastReadyAt = null,
            )
        )
        val stateHolder = EnvironmentSetupState(scope = envScope, toolchainService = spyService)
        testScheduler.advanceUntilIdle()

        assertFalse(stateHolder.toolchainSetupState.value.isAllReady, "Restored state must not be isAllReady")
        val presentation = stateHolder.environmentPresentation.value
        assertTrue(presentation.environmentState is EnvironmentState.Restored, "State must be Restored")
        assertNull(presentation.checkedAt, "Restored state has null checkedAt")
        assertEquals(
            PrimaryAction.CHECK,
            presentation.primaryAction,
            "Restored state must offer Check, not live-ready or Set Up"
        )
    }

    @Test
    fun testCheckedAtVsLastReadyAtSeparation() = runEnvironmentTest { envScope ->
        val liveCheckTime = 1700000100000L
        val spyService = SpyToolchainProvisioningService(
            initialState = ToolchainSetupState(
                checkedAt = liveCheckTime,
                lastReadyAt = null,
                steps = listOf(
                    SetupStepDetail(
                        stage = SetupStepStage.WSL_DETECTION,
                        title = "WSL",
                        description = "",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    ),
                    SetupStepDetail(
                        stage = SetupStepStage.SERVER_CONNECTIVITY,
                        title = "Server",
                        description = "",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    ),
                    SetupStepDetail(
                        stage = SetupStepStage.REPO_SYNCHRONIZATION,
                        title = "Repos",
                        description = "",
                        status = StepStatus.PENDING,
                        provenance = StepProvenance.LIVE,
                    ),
                )
            )
        )
        val stateHolder = EnvironmentSetupState(scope = envScope, toolchainService = spyService)
        testScheduler.advanceUntilIdle()

        val p = stateHolder.environmentPresentation.value
        assertEquals(liveCheckTime, p.checkedAt)
        assertNull(p.lastVerifiedAt)
        assertEquals(PrimaryAction.SET_UP, p.primaryAction)
    }

    @Test
    fun testRetryRequiresSeparateConfirmation_FR003() = runEnvironmentTest { envScope ->
        val spyService = SpyToolchainProvisioningService()
        val stateHolder = EnvironmentSetupState(scope = envScope, toolchainService = spyService)

        // Calling retryStage must ONLY enter PREVIEW; it must NOT auto-confirm or mutate!
        stateHolder.retryStage(SetupStepStage.REPO_SYNCHRONIZATION)
        testScheduler.advanceUntilIdle()

        val op = stateHolder.activeOperation.value
        assertNotNull(op, "retryStage must generate an active operation in PREVIEW")
        assertEquals(SetupOperationState.PREVIEW, op.executionState)
        assertEquals(SetupStepStage.REPO_SYNCHRONIZATION, op.stageId)
        assertEquals(0, spyService.stageRetryCount, "retryStage must NOT mutate service before confirmation")
        assertEquals(0, spyService.confirmCount, "retryStage must NOT auto-confirm")

        // Now user explicitly confirms
        spyService.confirmOutcome = SetupOutcome.Succeeded()
        stateHolder.confirmOperation()
        testScheduler.advanceUntilIdle()

        assertEquals(1, spyService.confirmCount, "confirmOperation must invoke service confirm")
        assertEquals("fake-plan-1", spyService.lastConfirmedPlanId, "The displayed plan ID must be confirmed")
        assertTrue(
            spyService.lastConfirmedRevisionHash!!.startsWith("fake-rev-"),
            "The displayed revision must be confirmed",
        )
    }

    @Test
    fun testConfirmWithoutPlanIsRejected_FR003() = runEnvironmentTest { envScope ->
        val spyService = SpyToolchainProvisioningService()
        val stateHolder = EnvironmentSetupState(scope = envScope, toolchainService = spyService)

        // Ensure no plan has been previewed
        assertNull(stateHolder.activeOperation.value)

        // Attempting to confirm without a previewed plan must be rejected (no fallback synthesized!)
        stateHolder.confirmOperation()
        testScheduler.advanceUntilIdle()

        assertEquals(0, spyService.confirmCount, "Confirm without plan must NOT call service confirm")
        assertEquals(0, spyService.fullSetupCount, "Confirm without plan must NOT run full setup")
        assertNotNull(stateHolder.environmentNotice.value, "Rejection notice must be presented")
        assertTrue(
            stateHolder.environmentNotice.value!!.contains("plan", ignoreCase = true) ||
                stateHolder.environmentNotice.value!!.contains("preview", ignoreCase = true) ||
                stateHolder.environmentNotice.value!!.contains("available", ignoreCase = true)
        )
    }

    @Test
    fun testStaleConfirmationRejected_FR003_FR004() = runEnvironmentTest { envScope ->
        val spyService = SpyToolchainProvisioningService()
        spyService.confirmOutcome = SetupOutcome.Failed(
            stage = null,
            reason = "Confirmation requires the displayed plan ID and unchanged revision hash.",
        )
        val stateHolder = EnvironmentSetupState(scope = envScope, toolchainService = spyService)

        stateHolder.previewSetup()
        testScheduler.advanceUntilIdle()

        stateHolder.confirmOperation()
        testScheduler.advanceUntilIdle()

        val op = stateHolder.activeOperation.value
        assertNotNull(op)
        assertEquals(SetupOperationState.FAILED, op.executionState)
        val expectedReason = "Confirmation requires the displayed plan ID and unchanged revision hash."
        assertTrue(op.failure?.contains(expectedReason) == true)
        assertFalse(stateHolder.toolchainSetupState.value.isAllReady)
    }

    @Test
    fun testTypedTerminalOutcomesPresentedDistinctlyNeverAsGenericSuccess_FR006() = runEnvironmentTest { envScope ->
        val outcomesToTest = listOf(
            SetupOutcome.Failed("WSL_DETECTION", "Distribution missing"),
            SetupOutcome.Cancelled,
            SetupOutcome.Busy("owner-process-999"),
            SetupOutcome.Interrupted("Bridge communication lost mid-operation"),
        )

        for (terminalOutcome in outcomesToTest) {
            val spyService = SpyToolchainProvisioningService()
            spyService.confirmOutcome = terminalOutcome
            val stateHolder = EnvironmentSetupState(scope = envScope, toolchainService = spyService)

            stateHolder.previewSetup()
            testScheduler.advanceUntilIdle()

            stateHolder.confirmOperation()
            testScheduler.advanceUntilIdle()

            val op = stateHolder.activeOperation.value
            assertNotNull(op, "Terminal outcome must produce an operation record for $terminalOutcome")
            assertFalse(
                op.executionState == SetupOperationState.SUCCEEDED,
                "$terminalOutcome must NEVER be presented as SUCCEEDED"
            )

            when (terminalOutcome) {
                is SetupOutcome.Cancelled -> {
                    assertEquals(SetupOperationState.CANCELLED, op.executionState)
                }
                is SetupOutcome.Failed -> {
                    assertEquals(SetupOperationState.FAILED, op.executionState)
                    assertTrue(op.failure?.contains("Distribution missing") == true)
                }
                is SetupOutcome.Busy -> {
                    assertEquals(SetupOperationState.FAILED, op.executionState)
                    val matchesBusy = op.failure?.contains("owner-process-999") == true ||
                        op.failure?.contains("busy", ignoreCase = true) == true
                    assertTrue(matchesBusy)
                }
                is SetupOutcome.Interrupted -> {
                    assertEquals(SetupOperationState.INTERRUPTED, op.executionState)
                    assertTrue(op.failure?.contains("Bridge communication lost") == true)
                }
                is SetupOutcome.Succeeded -> {}
            }
        }
    }

    @Test
    fun testPendingJournaledAttemptIsReconciledOnStartupReusingItsAttemptId() = runEnvironmentTest { envScope ->
        val spyService = SpyToolchainProvisioningService(
            initialState = ToolchainSetupState(pendingAttemptId = "attempt-journaled-7"),
        )
        spyService.recoverOutcome = SetupOutcome.Succeeded()
        val stateHolder = EnvironmentSetupState(scope = envScope, toolchainService = spyService)

        // Before the reconciliation completes the shell shows Reconnecting, never Ready / needs-setup.
        val before = SetupPresentationMapper.mapEnvironment(
            state = spyService._state.value,
            isToolchainBound = true,
            activeOperation = stateHolder.activeOperation.value,
        )
        assertTrue(before.environmentState is EnvironmentState.Reconnecting, "$before")
        assertEquals(PrimaryAction.STAGE_PROGRESS, before.primaryAction)

        testScheduler.advanceUntilIdle()

        // "Reconnect reuses attemptId": startup reconciliation is recovery of that journaled attempt, not new work.
        assertEquals(1, spyService.recoverCount)
        assertEquals("attempt-journaled-7", spyService.lastRecoverAttemptId)
        assertEquals(0, spyService.prepareCount, "Startup reconciliation never prepares or confirms new work")
        assertEquals(0, spyService.confirmCount)
        assertNull(
            stateHolder.activeOperation.value,
            "A reconciled, succeeded attempt is history, not an active operation",
        )
    }

    @Test
    fun testBlockedJournalRefusesMutationsUntilExplicitResetPreview() = runEnvironmentTest { envScope ->
        val spyService = SpyToolchainProvisioningService(
            initialState = ToolchainSetupState(
                pendingAttemptId = "attempt-ambiguous-1",
                recoveryBlockReason = "2 server runs hold lock 'lock:Ubuntu:attempt-ambiguous-1:submodule:apktool'",
            ),
        )
        spyService.recoverOutcome =
            SetupOutcome.Interrupted("Recovery of attempt attempt-ambiguous-1 is blocked: 2 server runs hold the lock")
        val stateHolder = EnvironmentSetupState(scope = envScope, toolchainService = spyService)
        testScheduler.advanceUntilIdle()

        val presentation = stateHolder.environmentPresentation.value
        assertTrue(presentation.environmentState is EnvironmentState.Interrupted, "$presentation")
        assertEquals(PrimaryAction.RECONNECT, presentation.primaryAction)
        val op = stateHolder.activeOperation.value
        assertNotNull(op)
        assertEquals(SetupOperationState.INTERRUPTED, op.executionState)

        // Journal truth gates new mutations: nothing is prepared or confirmed while recovery is blocked.
        stateHolder.previewSetup()
        testScheduler.advanceUntilIdle()
        assertTrue(stateHolder.environmentNotice.value?.contains("Recovery required", ignoreCase = true) == true)
        stateHolder.retryStage(SetupStepStage.TOOLCHAIN_COMPILATION)
        stateHolder.confirmOperation()
        testScheduler.advanceUntilIdle()
        assertEquals(0, spyService.prepareCount, "previewSetup/retryStage are refused while the journal needs recovery")
        assertEquals(0, spyService.confirmCount)

        // Reconnect is offered and reuses the attemptId; it is the only recovery path besides reset.
        stateHolder.reconcilePendingAttempt()
        testScheduler.advanceUntilIdle()
        assertEquals(2, spyService.recoverCount)
        assertEquals("attempt-ambiguous-1", spyService.lastRecoverAttemptId)

        // The explicit cache reset preview is the user-directed escape and must stay reachable (FR-008).
        stateHolder.previewResetToolchainCache()
        testScheduler.advanceUntilIdle()
        assertEquals(1, spyService.prepareCount, "Cache reset preview is allowed while recovery is blocked")
        assertEquals(SetupPlanKind.CACHE_RESET, spyService.lastPreparedKind)
    }

    @Test
    fun testReconciledInterruptedHistoryAllowsFreshPreviewAsNewAttempt() = runEnvironmentTest { envScope ->
        val spyService = SpyToolchainProvisioningService(
            initialState = ToolchainSetupState(pendingAttemptId = "attempt-partial-3"),
        )
        spyService.recoverOutcome = SetupOutcome.Interrupted(
            "Attempt attempt-partial-3 was reconciled but 1 planned action(s) (build-recipes) never ran; " +
                "preview again to start a new attempt.",
        )
        val stateHolder = EnvironmentSetupState(scope = envScope, toolchainService = spyService)
        testScheduler.advanceUntilIdle()
        assertEquals("attempt-partial-3", spyService.lastRecoverAttemptId)
        assertEquals(SetupOperationState.INTERRUPTED, stateHolder.activeOperation.value?.executionState)

        // The adapter archived the attempt: the journal is clean again.
        spyService._state.value = spyService._state.value.copy(pendingAttemptId = null, recoveryBlockReason = null)
        testScheduler.advanceUntilIdle()

        // "Explicit retry allocates a new attemptId": a fresh preview is a separate, newly confirmed plan.
        stateHolder.previewSetup()
        testScheduler.advanceUntilIdle()
        assertEquals(1, spyService.prepareCount)
        assertEquals(SetupOperationState.PREVIEW, stateHolder.activeOperation.value?.executionState)
        assertEquals(0, spyService.confirmCount, "Preview alone never executes")
    }
}
