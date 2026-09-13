package org.ide.lti.feature.setup

import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.SetupStepStage
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
    CHECK, CHECKING, SET_UP, STAGE_PROGRESS, RETRY_STAGE, RETRY_CONNECTION, RECONNECT, OPEN_WORKSPACES
}

/**
 * Authoritative, exhaustive sealed domain-to-presentation environment states (FR-001).
 *
 * Covers every FR-001 state:
 * - [Unknown]: Never checked
 * - [Checking]: Live check actively running
 * - [CheckedNeedsSetup]: Live check completed, prerequisites/tools missing
 * - [Ready]: All required live evidence ready
 * - [Failed]: Stage error with preserved evidence
 * - [Unavailable]: Connection unavailable / daemon unreachable / null service
 * - [Restored]: Restored from persistence, not live-verified (never presented as live-ready)
 * - [MutationRunning]: Active setup/repair mutation in progress
 * - [Reconnecting]: Reconnecting to a journaled attempt / existing durable run (reuses the attemptId)
 * - [Interrupted]: Reconciliation stopped or is blocked; user-directed recovery (reconnect or reset) required
 */
sealed interface EnvironmentState {
    data object Unknown : EnvironmentState
    data object Checking : EnvironmentState
    data class CheckedNeedsSetup(
        val missingPrerequisites: List<String> = emptyList(),
        val checkedAt: Long? = null,
    ) : EnvironmentState
    data class Ready(
        val lastReadyAt: Long,
        val checkedAt: Long,
    ) : EnvironmentState
    data class Failed(
        val stage: SetupStepStage?,
        val error: String,
        val checkedAt: Long? = null,
    ) : EnvironmentState
    data class Unavailable(
        val reason: String,
    ) : EnvironmentState
    data class Restored(
        val lastReadyAt: Long? = null,
        val checkedAt: Long? = null,
    ) : EnvironmentState
    data class MutationRunning(
        val operationId: String,
        val stage: SetupStepStage? = null,
    ) : EnvironmentState
    data class Reconnecting(
        val operationId: String,
    ) : EnvironmentState
    data class Interrupted(
        val reason: String,
    ) : EnvironmentState
}

data class EnvironmentPresentation(
    val environmentState: EnvironmentState,
    val stages: List<StagePresentation>,
    val activeOperationId: String?,
    val lastVerifiedAt: Long?,
    val checkedAt: Long?,
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
            StepStatus.RUNNING -> StagePresentationState.CHECKING
            StepStatus.PENDING -> StagePresentationState.UNKNOWN
        }
    }

    /**
     * Exhaustive sealed-when mapper selecting the next primary action for every state (FR-001).
     * No `else` branch is permitted.
     */
    fun mapAction(state: EnvironmentState): PrimaryAction = when (state) {
        is EnvironmentState.Unknown -> PrimaryAction.CHECK
        is EnvironmentState.Checking -> PrimaryAction.CHECKING
        is EnvironmentState.CheckedNeedsSetup -> PrimaryAction.SET_UP
        is EnvironmentState.Ready -> PrimaryAction.OPEN_WORKSPACES
        is EnvironmentState.Failed -> PrimaryAction.RETRY_STAGE
        is EnvironmentState.Unavailable -> PrimaryAction.RETRY_CONNECTION
        is EnvironmentState.Restored -> PrimaryAction.CHECK
        is EnvironmentState.MutationRunning -> PrimaryAction.STAGE_PROGRESS
        is EnvironmentState.Reconnecting -> PrimaryAction.STAGE_PROGRESS
        is EnvironmentState.Interrupted -> PrimaryAction.RECONNECT
    }

    private fun isMutating(state: ToolchainSetupState, activeOperation: SetupOperation?): Boolean {
        val opRunning = activeOperation?.executionState == SetupOperationState.RUNNING
        return state.activeOperationId != null || opRunning || state.isRunning || state.isFixing
    }

    private fun isCheckingState(state: ToolchainSetupState): Boolean {
        val nonMutationBusy = state.isBusy && !state.isRunning && !state.isFixing
        return state.isChecking || nonMutationBusy
    }

    /**
     * Journal truth outranks evidence (US3): a durable recovery block needs user-directed recovery and an
     * unreconciled attempt is reconnecting - neither is ever presented as ready or needs-setup.
     */
    private fun checkJournal(state: ToolchainSetupState, activeOperation: SetupOperation?): EnvironmentState? {
        val blockReason = state.recoveryBlockReason
        val pendingAttempt = state.pendingAttemptId
        return when {
            blockReason != null -> EnvironmentState.Interrupted(blockReason)
            activeOperation?.executionState == SetupOperationState.INTERRUPTED -> {
                EnvironmentState.Interrupted(activeOperation.failure ?: "Operation was interrupted.")
            }
            activeOperation?.executionState == SetupOperationState.RECONNECTING -> {
                EnvironmentState.Reconnecting(activeOperation.operationId ?: pendingAttempt ?: "reconnecting")
            }
            pendingAttempt != null && !state.isRunning -> EnvironmentState.Reconnecting(pendingAttempt)
            else -> null
        }
    }

    private fun checkActiveTransitions(
        state: ToolchainSetupState,
        isToolchainBound: Boolean,
        activeOperation: SetupOperation?,
    ): EnvironmentState? {
        if (!isToolchainBound) {
            return EnvironmentState.Unavailable("Toolchain service is not available in this session.")
        }
        return checkJournal(state, activeOperation) ?: when {
            isMutating(state, activeOperation) -> {
                val opId = state.activeOperationId ?: activeOperation?.operationId ?: "running"
                EnvironmentState.MutationRunning(
                    operationId = opId,
                    stage = state.currentStage ?: activeOperation?.stageId
                )
            }
            isCheckingState(state) -> EnvironmentState.Checking
            else -> null
        }
    }

    private fun checkFailures(state: ToolchainSetupState): EnvironmentState? {
        val connectionFailed = state.steps.firstOrNull {
            val isConn = it.stage == SetupStepStage.WSL_DETECTION || it.stage == SetupStepStage.SERVER_CONNECTIVITY
            isConn && it.status == StepStatus.FAILED
        }
        if (connectionFailed != null) {
            return EnvironmentState.Unavailable(
                reason = connectionFailed.error ?: connectionFailed.description
            )
        }

        val failedStep = state.steps.firstOrNull { it.status == StepStatus.FAILED }
        return if (failedStep != null) {
            EnvironmentState.Failed(
                stage = failedStep.stage,
                error = failedStep.error ?: failedStep.description,
                checkedAt = state.checkedAt
            )
        } else {
            null
        }
    }

    private fun isLiveReady(state: ToolchainSetupState): Boolean {
        if (!state.isAllReady || state.toolsMatrix.isEmpty() || state.checkedAt == null) {
            return false
        }
        val coreTools = state.toolsMatrix.filter { it.isCore }
        val allVerified = coreTools.isNotEmpty() && coreTools.all {
            it.status == StepStatus.SUCCESS && it.lastTested != null
        }
        val allPublished = state.publishedToolIds.containsAll(coreTools.map { it.id })
        return allVerified && allPublished
    }

    private fun checkRestored(state: ToolchainSetupState): EnvironmentState? {
        val hasRestoredStep = state.steps.any { it.provenance == StepProvenance.RESTORED }
        val hasHistory = hasRestoredStep || state.lastVerifiedTimestamp != null
        return if (state.checkedAt == null && hasHistory) {
            EnvironmentState.Restored(
                lastReadyAt = state.lastReadyAt ?: state.lastVerifiedTimestamp,
                checkedAt = null
            )
        } else {
            null
        }
    }

    private fun checkReady(state: ToolchainSetupState): EnvironmentState? {
        val checkedAt = state.checkedAt
        return if (isLiveReady(state) && checkedAt != null) {
            // Ready is live-only: never borrow a persisted lastVerifiedTimestamp as the ready moment.
            val readyTs = state.lastReadyAt ?: checkedAt
            EnvironmentState.Ready(
                lastReadyAt = readyTs,
                checkedAt = checkedAt,
            )
        } else {
            null
        }
    }

    private fun checkNeedsSetup(state: ToolchainSetupState): EnvironmentState? {
        val anyProbedLive = state.checkedAt != null || state.steps.any {
            it.provenance == StepProvenance.LIVE && it.status != StepStatus.PENDING
        }
        return if (anyProbedLive) {
            val missing = state.steps
                .filter { it.status != StepStatus.SUCCESS && it.status != StepStatus.WARNING }
                .map { it.title }
            EnvironmentState.CheckedNeedsSetup(
                missingPrerequisites = missing,
                checkedAt = state.checkedAt
            )
        } else {
            null
        }
    }

    /**
     * Determines the authoritative [EnvironmentState] from state facts.
     */
    fun mapEnvironmentState(
        state: ToolchainSetupState,
        isToolchainBound: Boolean = true,
        activeOperation: SetupOperation? = null,
    ): EnvironmentState {
        return checkActiveTransitions(state, isToolchainBound, activeOperation)
            ?: checkFailures(state)
            ?: checkRestored(state)
            ?: checkReady(state)
            ?: checkNeedsSetup(state)
            ?: EnvironmentState.Unknown
    }

    fun mapEnvironment(
        state: ToolchainSetupState,
        isToolchainBound: Boolean = true,
        activeOperation: SetupOperation? = null,
    ): EnvironmentPresentation {
        val mappedStages = state.steps.map { detail ->
            StagePresentation(
                id = detail.stage.name,
                title = detail.title,
                state = mapStageState(detail, state.isChecking),
                description = detail.description,
                error = detail.error
            )
        }
        val envState = mapEnvironmentState(state, isToolchainBound, activeOperation)
        val action = mapAction(envState)

        return EnvironmentPresentation(
            environmentState = envState,
            stages = mappedStages,
            activeOperationId = state.activeOperationId ?: activeOperation?.operationId,
            lastVerifiedAt = state.lastReadyAt ?: state.lastVerifiedTimestamp,
            checkedAt = state.checkedAt,
            storageAvailableBytes = state.storageAvailableBytes,
            storageMeasuredAt = state.storageMeasuredAt,
            primaryAction = action
        )
    }
}
