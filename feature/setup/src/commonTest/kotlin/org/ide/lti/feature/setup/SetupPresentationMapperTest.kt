package org.ide.lti.feature.setup

import kotlin.test.Test
import kotlin.test.assertEquals
import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainSetupState

class SetupPresentationMapperTest {

    @Test
    fun testMapStageState() {
        val detailPending = SetupStepDetail(stage = SetupStepStage.WSL_DETECTION, title = "", description = "", status = StepStatus.PENDING, provenance = StepProvenance.LIVE)
        assertEquals(StagePresentationState.UNKNOWN, SetupPresentationMapper.mapStageState(detailPending, false))

        val detailRestored = SetupStepDetail(stage = SetupStepStage.WSL_DETECTION, title = "", description = "", status = StepStatus.SUCCESS, provenance = StepProvenance.RESTORED)
        assertEquals(StagePresentationState.RESTORED, SetupPresentationMapper.mapStageState(detailRestored, false))

        val detailChecking = SetupStepDetail(stage = SetupStepStage.WSL_DETECTION, title = "", description = "", status = StepStatus.RUNNING, provenance = StepProvenance.LIVE)
        assertEquals(StagePresentationState.CHECKING, SetupPresentationMapper.mapStageState(detailChecking, true))

        val detailReady = SetupStepDetail(stage = SetupStepStage.WSL_DETECTION, title = "", description = "", status = StepStatus.SUCCESS, provenance = StepProvenance.LIVE)
        assertEquals(StagePresentationState.READY, SetupPresentationMapper.mapStageState(detailReady, false))

        val detailFailed = SetupStepDetail(stage = SetupStepStage.WSL_DETECTION, title = "", description = "", status = StepStatus.FAILED, provenance = StepProvenance.LIVE)
        assertEquals(StagePresentationState.FAILED, SetupPresentationMapper.mapStageState(detailFailed, false))
    }

    @Test
    fun testMapPrimaryAction() {
        // Initial state -> CHECK
        val stateInitial = ToolchainSetupState()
        assertEquals(PrimaryAction.CHECK, SetupPresentationMapper.mapPrimaryAction(stateInitial))

        // Checking state -> CHECKING
        val stateChecking = ToolchainSetupState(isChecking = true)
        assertEquals(PrimaryAction.CHECKING, SetupPresentationMapper.mapPrimaryAction(stateChecking))

        // Active operation -> STAGE_PROGRESS
        val stateOp = ToolchainSetupState(activeOperationId = "123")
        assertEquals(PrimaryAction.STAGE_PROGRESS, SetupPresentationMapper.mapPrimaryAction(stateOp))

        // Failed step -> RETRY_STAGE
        val stateFailed = ToolchainSetupState(steps = listOf(
            SetupStepDetail(stage = SetupStepStage.WSL_DETECTION, title = "", description = "", status = StepStatus.FAILED, provenance = StepProvenance.LIVE)
        ))
        assertEquals(PrimaryAction.RETRY_STAGE, SetupPresentationMapper.mapPrimaryAction(stateFailed))

        // All ready -> OPEN_WORKSPACES
        val stateReady = ToolchainSetupState(steps = listOf(
            SetupStepDetail(stage = SetupStepStage.WSL_DETECTION, title = "", description = "", status = StepStatus.SUCCESS, provenance = StepProvenance.LIVE)
        ))
        assertEquals(PrimaryAction.OPEN_WORKSPACES, SetupPresentationMapper.mapPrimaryAction(stateReady))
        
        // Some live steps, not failed, not all ready -> SET_UP
        val stateLiveButNotReady = ToolchainSetupState(steps = listOf(
            SetupStepDetail(stage = SetupStepStage.WSL_DETECTION, title = "", description = "", status = StepStatus.SUCCESS, provenance = StepProvenance.LIVE),
            SetupStepDetail(stage = SetupStepStage.SERVER_CONNECTIVITY, title = "", description = "", status = StepStatus.PENDING, provenance = StepProvenance.LIVE)
        ))
        assertEquals(PrimaryAction.SET_UP, SetupPresentationMapper.mapPrimaryAction(stateLiveButNotReady))
    }

    @Test
    fun testMapEnvironment() {
        val state = ToolchainSetupState(
            storageAvailableBytes = 5000L,
            storageMeasuredAt = 1000L,
            activeOperationId = "op-1",
            lastVerifiedTimestamp = 2000L
        )
        val presentation = SetupPresentationMapper.mapEnvironment(state)
        assertEquals(5000L, presentation.storageAvailableBytes)
        assertEquals(1000L, presentation.storageMeasuredAt)
        assertEquals("op-1", presentation.activeOperationId)
        assertEquals(2000L, presentation.lastVerifiedAt)
    }
}
