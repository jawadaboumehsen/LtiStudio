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
        var runFullSetupCount = 0
            private set
        var retryStepCount = 0
            private set
        var lastRetriedStage: SetupStepStage? = null
            private set
        var autoRemediateDoctorCount = 0
            private set
        var resetSetupCacheCount = 0
            private set
        var testToolCount = 0
            private set
        var repairToolCount = 0
            private set

        override suspend fun verifyEnvironment(): ToolchainSetupState {
            verifyEnvironmentCount++
            return _state.value
        }

        override suspend fun checkStatus(): ToolchainSetupState {
            verifyEnvironmentCount++
            return _state.value
        }

        override suspend fun runFullSetup() {
            runFullSetupCount++
            _state.value = _state.value.copy(
                steps = _state.value.steps.map { it.copy(status = StepStatus.SUCCESS, provenance = StepProvenance.LIVE) }
            )
        }

        override suspend fun retryStep(stage: SetupStepStage) {
            retryStepCount++
            lastRetriedStage = stage
        }

        override suspend fun provisionAvbKey(): Boolean = true

        override suspend fun autoRemediateDoctor(): Boolean {
            autoRemediateDoctorCount++
            return true
        }

        override suspend fun testTool(toolId: String): StepStatus {
            testToolCount++
            return StepStatus.SUCCESS
        }

        override suspend fun repairTool(toolId: String): Boolean {
            repairToolCount++
            return true
        }

        override suspend fun resetSetupCache() {
            resetSetupCacheCount++
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
        assertEquals(0, spyService.runFullSetupCount, "Initial check must NEVER trigger full setup automatically")
        assertEquals(0, spyService.autoRemediateDoctorCount, "Initial check must NEVER auto-install packages")
        assertEquals(0, spyService.retryStepCount, "Initial check must NEVER retry steps")
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
        assertEquals(0, spyService.runFullSetupCount, "Preview must NOT execute full setup")
        assertEquals(0, spyService.autoRemediateDoctorCount, "Preview must NOT mutate packages")

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
        assertEquals(0, spyService.runFullSetupCount)
        assertEquals(0, spyService.retryStepCount)
        assertEquals(0, spyService.autoRemediateDoctorCount)
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

        assertEquals(1, spyService.autoRemediateDoctorCount, "Auto doctor remediation must run when enabled")
        assertEquals(1, spyService.runFullSetupCount, "Full setup must run on confirmed operation")

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

        // autoRemediateDoctorCount should remain 1 (from Case 1), NOT incremented in Case 2!
        assertEquals(1, spyService.autoRemediateDoctorCount, "Auto doctor remediation must NOT run when disabled")
        assertEquals(2, spyService.runFullSetupCount, "Full setup runs even with auto doctor disabled")
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
        assertEquals(0, spyService.runFullSetupCount, "Reconnecting must not start a new mutation")
        assertEquals(0, spyService.retryStepCount, "Reconnecting must not start a new mutation")
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
}
