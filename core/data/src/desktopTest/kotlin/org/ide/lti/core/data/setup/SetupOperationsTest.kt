package org.ide.lti.core.data.setup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainSetupState

class SetupOperationsTest {

    @Test
    fun testRestoredResultsCannotEstablishLiveReadiness() {
        val state = ToolchainSetupState(
            steps = listOf(
                SetupStepDetail(
                    stage = SetupStepStage.WSL_DETECTION,
                    title = "WSL",
                    description = "WSL",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.RESTORED
                )
            )
        )
        assertFalse(state.isAllReady, "Restored results should not establish live readiness")

        val liveState = ToolchainSetupState(
            steps = listOf(
                SetupStepDetail(
                    stage = SetupStepStage.WSL_DETECTION,
                    title = "WSL",
                    description = "WSL",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.LIVE
                )
            )
        )
        assertTrue(liveState.isAllReady, "Live success should establish readiness")
    }

    @Test
    fun testNullableMeasuredStorage() {
        val stateChecked = ToolchainSetupState(storageAvailableBytes = 1024L)
        assertEquals(1024L, stateChecked.storageAvailableBytes)

        val stateUnchecked = ToolchainSetupState(storageAvailableBytes = null)
        assertNull(stateUnchecked.storageAvailableBytes)
    }

    @Test
    fun testSingleOperationSerialization() {
        val stateIdle = ToolchainSetupState()
        assertFalse(stateIdle.isBusy, "State should not be busy initially")

        val stateRunning = ToolchainSetupState(isRunning = true)
        assertTrue(stateRunning.isBusy, "isRunning should make state busy")

        val stateChecking = ToolchainSetupState(isChecking = true)
        assertTrue(stateChecking.isBusy, "isChecking should make state busy")

        val stateFixing = ToolchainSetupState(isFixing = true)
        assertTrue(stateFixing.isBusy, "isFixing should make state busy")

        val stateWithOp = ToolchainSetupState(activeOperationId = "op-123")
        assertTrue(stateWithOp.isBusy, "activeOperationId should make state busy")
    }
}
