package org.ide.lti.feature.setup

import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainSetupState

enum class StagePresentationState {
    UNKNOWN, CHECKING, RESTORED, READY, WARNING, FAILED, UNAVAILABLE
}

data class StagePresentation(
    val id: String,
    val title: String,
    val state: StagePresentationState,
    val description: String,
    val error: String? = null
)

enum class PrimaryAction {
    CHECK, CHECKING, SET_UP, STAGE_PROGRESS, RETRY_STAGE, RETRY_CONNECTION, OPEN_WORKSPACES
}

data class EnvironmentPresentation(
    val stages: List<StagePresentation>,
    val activeOperationId: String?,
    val lastVerifiedAt: Long?,
    val storageAvailableBytes: Long?,
    val storageMeasuredAt: Long?,
    val primaryAction: PrimaryAction
)

object SetupPresentationMapper {
    fun mapStageState(detail: SetupStepDetail, isChecking: Boolean): StagePresentationState {
        if (isChecking && detail.status == StepStatus.RUNNING) return StagePresentationState.CHECKING
        if (detail.status == StepStatus.PENDING) return StagePresentationState.UNKNOWN
        if (detail.provenance == StepProvenance.RESTORED) return StagePresentationState.RESTORED
        return when (detail.status) {
            StepStatus.SUCCESS -> StagePresentationState.READY
            StepStatus.WARNING -> StagePresentationState.WARNING
            StepStatus.FAILED -> StagePresentationState.FAILED
            else -> StagePresentationState.UNAVAILABLE
        }
    }

    fun mapPrimaryAction(state: ToolchainSetupState): PrimaryAction {
        if (state.activeOperationId != null) return PrimaryAction.STAGE_PROGRESS
        if (state.isChecking) return PrimaryAction.CHECKING
        
        val anyFailed = state.steps.any { it.status == StepStatus.FAILED }
        if (anyFailed) return PrimaryAction.RETRY_STAGE
        
        val serverFailed = state.steps.find { it.stage.name == "SERVER_CONNECTIVITY" }?.status == StepStatus.FAILED
        if (serverFailed) return PrimaryAction.RETRY_CONNECTION
        
        if (state.isAllReady) return PrimaryAction.OPEN_WORKSPACES
        
        val anyProbed = state.steps.any { it.provenance == StepProvenance.LIVE && it.status != StepStatus.PENDING }
        if (!anyProbed) return PrimaryAction.CHECK
        
        return PrimaryAction.SET_UP
    }

    fun mapEnvironment(state: ToolchainSetupState): EnvironmentPresentation {
        val mappedStages = state.steps.map { detail ->
            StagePresentation(
                id = detail.stage.name,
                title = detail.title,
                state = mapStageState(detail, state.isChecking),
                description = detail.description,
                error = detail.error
            )
        }
        
        return EnvironmentPresentation(
            stages = mappedStages,
            activeOperationId = state.activeOperationId,
            lastVerifiedAt = state.lastVerifiedTimestamp,
            storageAvailableBytes = state.storageAvailableBytes,
            storageMeasuredAt = state.storageMeasuredAt,
            primaryAction = mapPrimaryAction(state)
        )
    }
}
