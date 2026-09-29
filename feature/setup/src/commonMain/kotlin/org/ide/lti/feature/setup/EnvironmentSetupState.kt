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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.ide.lti.core.common.timing.StartupTiming
import org.ide.lti.core.domain.ports.EnvironmentReadinessPort
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlan
import org.ide.lti.core.domain.setup.SetupPlanAction
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainProvisioningService
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.domain.setup.UserRepairHandoff

/**
 * Kind of setup mutation operation.
 */
enum class SetupOperationKind {
    FULL_SETUP,
    RETRY_STAGE,
    REPAIR_TOOL,
    CACHE_RESET,
    BOOTSTRAP_PACKAGES,
}

/**
 * Lifecycle states of an environment setup operation.
 */
enum class SetupOperationState {
    PREVIEW,
    RUNNING,
    RECONNECTING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    INTERRUPTED,
    AWAITING_USER_ACTION,
}

/**
 * UI states of the terminal handoff sheet (contracts/ui-states.md §4).
 */
sealed interface TerminalHandoffState {
    data object Idle : TerminalHandoffState

    data class TerminalLaunchFailed(val reason: String) : TerminalHandoffState

    data object Verifying : TerminalHandoffState

    data class StillMissing(val packages: List<String>, val command: String) : TerminalHandoffState

    data object AnotherOperationRunning : TerminalHandoffState

    data object Done : TerminalHandoffState
}

/**
 * Planned changes and elevation requirements surfaced to the user before confirmation.
 */
data class PlannedSetupChanges(
    val packagesToInstall: List<String> = emptyList(),
    val submodulesToSync: List<String> = emptyList(),
    val toolsToCompile: List<String> = emptyList(),
    val toolsToPublish: List<String> = emptyList(),
    val requiresElevation: Boolean = false,
    val elevationReason: String? = null,
    val targetDistro: String = "",
    val autoDoctorRemediation: Boolean = true,
) {
    /** True when the plan installs, syncs, compiles and publishes nothing (e.g. a cache reset). */
    val isEmpty: Boolean
        get() =
            packagesToInstall.isEmpty() &&
                submodulesToSync.isEmpty() &&
                toolsToCompile.isEmpty() &&
                toolsToPublish.isEmpty()
}

/**
 * Encapsulation of an active or previewed setup operation.
 */
data class SetupOperation(
    val operationId: String? = null,
    val kind: SetupOperationKind = SetupOperationKind.FULL_SETUP,
    val stageId: SetupStepStage? = null,
    val toolId: String? = null,
    val plannedChanges: PlannedSetupChanges = PlannedSetupChanges(),
    val executionState: SetupOperationState = SetupOperationState.PREVIEW,
    val autoDoctorEnabled: Boolean = true,
    val failure: String? = null,
    val plan: SetupPlan? = null,
    val awaitingHandoff: UserRepairHandoff? = null,
)

/**
 * Feature-local state holder for Environment setup and toolchain operations (US2).
 *
 * Responsibilities:
 * - Performs initial inspection only (no mutation on initial check; FR-005).
 * - Previews planned changes and required elevation before mutating the environment (FR-005).
 * - Enforces single-flight operation serialization (at most one active setup mutation per environment; FR-007, SC-002).
 * - Honors Auto Doctor toggle in all paths (FR-007).
 * - Attaches to existing durable operation IDs on reconnection (FR-007).
 * - Cleans up busy/mutation state in finally blocks on error/cancellation (FR-007).
 * - Preserves execution state across Setup destination navigation during a session (FR-004, FR-007).
 * - One state holder for check/preview/confirm/observe/recover of the environment.
 */
@Suppress(
    "TooManyFunctions",
    "LargeClass",
)
class EnvironmentSetupState(
    private val scope: CoroutineScope,
    private val toolchainService: ToolchainProvisioningService? = null,
    private val environmentReadinessPort: EnvironmentReadinessPort? = null,
) {
    val isToolchainBound: Boolean = toolchainService != null

    private val fallbackToolchainState =
        MutableStateFlow(
            ToolchainSetupState(
                steps =
                ToolchainSetupState.defaultSteps().map { step ->
                    step.copy(
                        status = StepStatus.FAILED,
                        description = "Toolchain service not bound in DI.",
                    )
                },
                checkedAt = null,
                lastReadyAt = null,
            ),
        )

    val toolchainSetupState: StateFlow<ToolchainSetupState> =
        toolchainService?.state ?: fallbackToolchainState.asStateFlow()

    private val _activeOperation = MutableStateFlow<SetupOperation?>(null)
    val activeOperation: StateFlow<SetupOperation?> = _activeOperation.asStateFlow()

    internal fun setActiveOperationForTesting(operation: SetupOperation?) {
        _activeOperation.value = operation
    }

    private val _isAutoDoctorEnabled = MutableStateFlow(true)
    val isAutoDoctorEnabled: StateFlow<Boolean> = _isAutoDoctorEnabled.asStateFlow()

    private val _environmentNotice = MutableStateFlow<String?>(null)
    val environmentNotice: StateFlow<String?> = _environmentNotice.asStateFlow()

    fun setEnvironmentNotice(notice: String?) {
        _environmentNotice.value = notice
    }

    private val _selectedStage = MutableStateFlow<SetupStepStage?>(null)
    val selectedStage: StateFlow<SetupStepStage?> = _selectedStage.asStateFlow()

    private val _isHandoffSheetVisible = MutableStateFlow(false)
    val isHandoffSheetVisible: StateFlow<Boolean> = _isHandoffSheetVisible.asStateFlow()

    private val _handoffSheetState = MutableStateFlow<TerminalHandoffState>(TerminalHandoffState.Idle)
    val handoffSheetState: StateFlow<TerminalHandoffState> = _handoffSheetState.asStateFlow()

    fun setTerminalLaunchFailure(reason: String) {
        _handoffSheetState.value = TerminalHandoffState.TerminalLaunchFailed(reason)
        _environmentNotice.value = "Could not open terminal: $reason. Please run the command manually."
    }

    private var currentExecutionJob: Job? = null

    /** The last reconciliation of the journaled attempt failed; the next completed check retries it. */
    private var lastRecoveryFailed = false

    /** The notice the last reconciliation left, cleared once the journal no longer needs recovery. */
    private var lastRecoveryNotice: String? = null

    init {
        val initial = toolchainSetupState.value
        when {
            // A journaled attempt survived a restart or a dropped connection: reconcile it against server
            // truth before anything else is offered (US3 T023). Reconnect reuses the attemptId.
            initial.pendingAttemptId != null -> reconcilePendingAttempt()
            // Reconnect to an already-running mutation on state-holder (re)creation, e.g. after
            // navigating away and back to the Environment destination during a session (FR-007).
            initial.isRunning -> {
                val stage = initial.currentStage
                val runId = stage?.let { initial.stepRunIds[it] } ?: initial.activeOperationId
                if (runId != null) {
                    attachToDurableRun(runId, stage)
                } else {
                    _activeOperation.value =
                        SetupOperation(
                            kind = if (stage != null) SetupOperationKind.RETRY_STAGE else SetupOperationKind.FULL_SETUP,
                            stageId = stage,
                            executionState = SetupOperationState.RECONNECTING,
                            autoDoctorEnabled = _isAutoDoctorEnabled.value,
                        )
                }
            }
        }
    }

    /**
     * True while the durable journal still holds an unreconciled attempt or a recovery block. New mutations
     * are refused until [reconcilePendingAttempt] archives the attempt, or the user confirms a cache reset
     * (the explicit, user-directed recovery for an unreconcilable journal, FR-008).
     */
    private val journalNeedsRecovery: Boolean
        get() =
            toolchainSetupState.value.pendingAttemptId != null ||
                toolchainSetupState.value.recoveryBlockReason != null

    private fun refuseWhileJournalNeedsRecovery(): Boolean {
        if (!journalNeedsRecovery) return false
        val state = toolchainSetupState.value
        _environmentNotice.value = state.recoveryBlockReason?.let { "Recovery required: $it" }
            ?: "Attempt ${state.pendingAttemptId} is still being reconciled; reconnect before starting new work."
        return true
    }

    /**
     * Single admission check for a new preview: the service must be bound, the journal must not need
     * recovery, and nothing may be running. Each refusal leaves a notice where it matters.
     */
    private fun canStartPreview(): Boolean {
        val serviceBound = toolchainService != null
        if (!serviceBound) {
            // Nothing can be planned or confirmed without the service; never synthesize a preview.
            _environmentNotice.value = "Toolchain service is not available in this session."
        }
        return serviceBound &&
            !refuseWhileJournalNeedsRecovery() &&
            !(toolchainSetupState.value.isBusy || currentExecutionJob?.isActive == true)
    }

    /**
     * Probes environment status without mutating filesystem or packages (FR-005).
     */
    fun checkEnvironmentStatus() {
        if (toolchainService == null) {
            _environmentNotice.value = "Toolchain service is not available in this session."
            return
        }
        if (toolchainSetupState.value.isBusy || currentExecutionJob?.isActive == true) return
        currentExecutionJob =
            scope.launch {
                try {
                    StartupTiming.log("Environment check started")
                    toolchainService.checkStatus()
                    StartupTiming.log("Environment check finished")
                } catch (e: Exception) {
                    _environmentNotice.value = "Environment verification notice: ${e.message}"
                } finally {
                    currentExecutionJob = null
                }
                // A reconciliation that failed earlier (e.g. at startup, before any service was up) is retried
                // once the check has connected, instead of leaving "Recovery failed" up next to a green check.
                if (lastRecoveryFailed && toolchainSetupState.value.pendingAttemptId != null) {
                    reconcilePendingAttempt()
                } else if (!journalNeedsRecovery && _environmentNotice.value == lastRecoveryNotice) {
                    // Nothing is left to recover: a recovery notice from earlier is stale, not a warning.
                    _environmentNotice.value = null
                }
            }
    }

    /** Switches the session to [distro] (WSL row picker) and re-checks against it. */
    fun selectDistro(distro: String) {
        val service = toolchainService ?: return
        if (toolchainSetupState.value.isBusy || currentExecutionJob?.isActive == true) return
        currentExecutionJob =
            scope.launch {
                try {
                    service.selectDistro(distro)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _environmentNotice.value = "Couldn't switch to $distro: ${e.message}"
                } finally {
                    currentExecutionJob = null
                }
            }
    }

    /**
     * Re-runs live environment verification without mutating the filesystem.
     */
    fun reverify() {
        checkEnvironmentStatus()
    }

    /**
     * Previews planned changes and required elevation without executing any mutation (FR-005).
     */
    fun previewSetup(stage: SetupStepStage? = null) {
        if (!canStartPreview()) return
        if (stage != null) {
            _selectedStage.value = stage
        }

        val kind = if (stage != null) SetupPlanKind.STAGE_RETRY else SetupPlanKind.FULL_SETUP
        launchPreview(kind, stage?.name, stage)
    }

    /**
     * Requests a domain plan and shows it as the preview. The plan is the only source of the preview:
     * what it lists is exactly what a confirmation executes (FR-004 package-summary parity).
     */
    private fun launchPreview(kind: SetupPlanKind, targetId: String?, stage: SetupStepStage?) {
        val service = toolchainService ?: return
        scope.launch {
            try {
                val plan = service.prepare(kind, targetId, _isAutoDoctorEnabled.value)
                _activeOperation.value =
                    SetupOperation(
                        operationId = null,
                        kind = plan.kind.toOperationKind(),
                        stageId = stage,
                        plannedChanges = plan.toPlannedChanges(),
                        executionState = SetupOperationState.PREVIEW,
                        autoDoctorEnabled = plan.autoDoctorEnabled,
                        plan = plan,
                    )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _environmentNotice.value = "Failed to preview setup: ${e.message}"
            }
        }
    }

    /**
     * Previews Auto-Fix (automatic doctor remediation) as an explicitly confirmable plan (FR-003).
     * Automatic remediation never runs when Auto Doctor is disabled (FR-004).
     */
    fun previewAutoFix() {
        if (!_isAutoDoctorEnabled.value) {
            _environmentNotice.value = "Auto Doctor is disabled; enable it to preview automatic remediation."
            return
        }
        previewSetup(SetupStepStage.SYSTEM_DIAGNOSTICS)
    }

    private fun SetupPlan.toPlannedChanges(): PlannedSetupChanges {
        val installs = orderedActions.filterIsInstance<SetupPlanAction.InstallPackages>()
        val aptPackages = installs.flatMap { it.packages }.distinct()
        val pipPackages = installs.flatMap { it.pipPackages }.distinct()
        val loopMountElevation = installs.any { it.configureLoopMountElevation }
        val requiresElevation = installs.any { it.requiresElevation }
        val elevationReason =
            when {
                aptPackages.isNotEmpty() ->
                    "Package installation requires administrator approval in your WSL terminal (sudo apt-get install)."
                else -> null
            }
        return PlannedSetupChanges(
            packagesToInstall = aptPackages + pipPackages.map { "$it (pip)" },
            submodulesToSync =
            orderedActions
                .filterIsInstance<SetupPlanAction.SyncSources>()
                .flatMap { it.submodules }
                .distinct(),
            toolsToCompile =
            orderedActions
                .filterIsInstance<SetupPlanAction.BuildRecipes>()
                .flatMap { it.recipeGroups }
                .distinct(),
            toolsToPublish =
            orderedActions
                .filterIsInstance<SetupPlanAction.PublishTools>()
                .flatMap { it.toolIds }
                .distinct(),
            requiresElevation = requiresElevation,
            elevationReason = elevationReason,
            targetDistro = environmentKey,
            autoDoctorRemediation = autoDoctorEnabled,
        )
    }

    private fun SetupPlanKind.toOperationKind(): SetupOperationKind = when (this) {
        SetupPlanKind.FULL_SETUP -> SetupOperationKind.FULL_SETUP
        SetupPlanKind.STAGE_RETRY -> SetupOperationKind.RETRY_STAGE
        SetupPlanKind.REPAIR_TOOL -> SetupOperationKind.REPAIR_TOOL
        SetupPlanKind.CACHE_RESET -> SetupOperationKind.CACHE_RESET
        SetupPlanKind.BOOTSTRAP_PACKAGES -> SetupOperationKind.BOOTSTRAP_PACKAGES
    }

    /**
     * Dismisses the active preview without performing any mutations.
     */
    fun dismissPreview() {
        if (_activeOperation.value?.executionState == SetupOperationState.PREVIEW) {
            _activeOperation.value = null
        }
    }

    /**
     * Confirms and starts the active or requested setup operation (FR-005, FR-007).
     * Single-flight: repeated activations while running will not queue duplicate operations.
     */
    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    fun confirmOperation() {
        if (toolchainService == null) {
            _environmentNotice.value = "Toolchain service is not available in this session."
            return
        }
        val currentOp = _activeOperation.value
        val plan = currentOp?.plan
        if (currentOp == null || plan == null || currentOp.executionState != SetupOperationState.PREVIEW) {
            _environmentNotice.value = "No setup plan available to confirm. Please preview the operation first."
            return
        }
        // The confirmed cache reset is the user-directed recovery for an unreconcilable journal (FR-008).
        if (plan.kind != SetupPlanKind.CACHE_RESET && refuseWhileJournalNeedsRecovery()) return
        if (toolchainSetupState.value.isBusy || currentExecutionJob?.isActive == true) {
            return
        }

        val opId = currentOp.operationId ?: plan.planId
        _activeOperation.value =
            currentOp.copy(
                operationId = opId,
                executionState = SetupOperationState.RUNNING,
                autoDoctorEnabled = plan.autoDoctorEnabled,
            )

        currentExecutionJob =
            scope.launch {
                var recheckAfterReset = false
                try {
                    val outcome = toolchainService.confirm(plan.planId, plan.revisionHash)
                    when (outcome) {
                        is SetupOutcome.Succeeded -> {
                            _activeOperation.value =
                                _activeOperation.value?.copy(
                                    executionState = SetupOperationState.SUCCEEDED,
                                )
                            recheckAfterReset = plan.kind == SetupPlanKind.CACHE_RESET
                        }
                        is SetupOutcome.Cancelled -> {
                            _activeOperation.value =
                                _activeOperation.value?.copy(
                                    executionState = SetupOperationState.CANCELLED,
                                    failure = "Operation cancelled",
                                )
                            _environmentNotice.value = "Operation was cancelled"
                        }
                        is SetupOutcome.Failed -> {
                            _activeOperation.value =
                                _activeOperation.value?.copy(
                                    executionState = SetupOperationState.FAILED,
                                    failure = outcome.reason,
                                )
                            _environmentNotice.value = "Operation failed: ${outcome.reason}"
                        }
                        is SetupOutcome.Busy -> applyBusy(outcome, "Operation rejected")
                        is SetupOutcome.Interrupted -> {
                            _activeOperation.value =
                                _activeOperation.value?.copy(
                                    executionState = SetupOperationState.INTERRUPTED,
                                    failure = outcome.reason,
                                )
                            _environmentNotice.value = "Operation interrupted: ${outcome.reason}"
                        }
                        is SetupOutcome.AwaitingUserAction -> {
                            _activeOperation.value =
                                _activeOperation.value?.copy(
                                    executionState = SetupOperationState.AWAITING_USER_ACTION,
                                    awaitingHandoff = outcome.handoff,
                                )
                            _environmentNotice.value = "Awaiting user action: terminal authorization required."
                        }
                    }
                } catch (e: CancellationException) {
                    // Explicit cancellation is a distinct terminal outcome, never presented as failure.
                    _activeOperation.value =
                        _activeOperation.value?.copy(
                            executionState = SetupOperationState.CANCELLED,
                            failure = "Operation cancelled",
                        )
                    _environmentNotice.value = "Operation was cancelled"
                    throw e
                } catch (e: Exception) {
                    _activeOperation.value =
                        _activeOperation.value?.copy(
                            executionState = SetupOperationState.FAILED,
                            failure = e.message,
                        )
                    _environmentNotice.value = "Operation failed: ${e.message}"
                } finally {
                    currentExecutionJob = null
                    if (_activeOperation.value?.executionState == SetupOperationState.SUCCEEDED) {
                        _activeOperation.value = null
                    }
                }
                if (recheckAfterReset) {
                    // The cleared cache is Unknown until a live check measures it (FR-001).
                    checkEnvironmentStatus()
                }
            }
    }

    /**
     * Retries a specific setup stage with targeted execution (FR-006, FR-007).
     */
    fun retryStage(stage: SetupStepStage) {
        if (!canStartPreview()) return
        previewSetup(stage)
    }

    /**
     * Attaches to a mutation that the application-lifetime operation owner is still running (FR-007).
     * The operation is not restarted and not re-owned: this state holder only observes it to its typed
     * terminal outcome.
     */
    fun attachToDurableRun(runId: String, stage: SetupStepStage? = null) {
        if (runId.isBlank()) return
        _activeOperation.value =
            SetupOperation(
                operationId = runId,
                kind = if (stage != null) SetupOperationKind.RETRY_STAGE else SetupOperationKind.FULL_SETUP,
                stageId = stage,
                executionState = SetupOperationState.RECONNECTING,
                autoDoctorEnabled = _isAutoDoctorEnabled.value,
            )
        val service = toolchainService
        // Only an operation the owner is actually running can be observed to its end; otherwise the
        // reconnecting marker stays until the owner reports something.
        if (service == null || !toolchainSetupState.value.isRunning || currentExecutionJob?.isActive == true) return
        currentExecutionJob =
            scope.launch {
                try {
                    toolchainSetupState.first { !it.isRunning }
                    applyTerminalOutcome(service.observe().first(), "Reconnected operation")
                } finally {
                    currentExecutionJob = null
                }
            }
    }

    /**
     * Reconciles the journaled pending attempt against server truth (US3 T023): known runs are re-attached
     * from their committed cursor, lost receipts are matched by their unique child lock, and anything
     * ambiguous is reported as interrupted without resubmission. Reconnect reuses the attemptId; a fresh
     * retry afterwards is a separate preview and a new attempt.
     */
    fun reconcilePendingAttempt() {
        val service = toolchainService
        val attemptId = toolchainSetupState.value.pendingAttemptId
        if (attemptId == null && toolchainSetupState.value.recoveryBlockReason == null) {
            _environmentNotice.value = "Nothing to reconnect: no journaled attempt is pending."
        }
        if (service == null || !journalNeedsRecovery || currentExecutionJob?.isActive == true) return
        _activeOperation.value =
            SetupOperation(
                operationId = attemptId,
                executionState = SetupOperationState.RECONNECTING,
                autoDoctorEnabled = _isAutoDoctorEnabled.value,
            )
        currentExecutionJob =
            scope.launch {
                try {
                    val outcome = service.recover(attemptId)
                    lastRecoveryFailed = outcome is SetupOutcome.Failed
                    if (outcome is SetupOutcome.Succeeded && _environmentNotice.value == lastRecoveryNotice) {
                        _environmentNotice.value = null
                    }
                    applyTerminalOutcome(outcome, "Recovery")
                    lastRecoveryNotice = _environmentNotice.value
                } catch (e: CancellationException) {
                    _activeOperation.value =
                        _activeOperation.value?.copy(
                            executionState = SetupOperationState.CANCELLED,
                            failure = "Operation cancelled",
                        )
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    _activeOperation.value =
                        _activeOperation.value?.copy(
                            executionState = SetupOperationState.INTERRUPTED,
                            failure = e.message,
                        )
                    lastRecoveryFailed = true
                    _environmentNotice.value = "Recovery failed: ${e.message}"
                    lastRecoveryNotice = _environmentNotice.value
                } finally {
                    currentExecutionJob = null
                }
            }
    }

    /**
     * Cancels the currently running setup operation (FR-023, T064).
     */
    fun cancelOperation() {
        val service = toolchainService ?: return
        scope.launch {
            try {
                val outcome = service.cancel()
                applyTerminalOutcome(outcome, "Operation")
            } catch (e: CancellationException) {
                _activeOperation.value =
                    _activeOperation.value?.copy(
                        executionState = SetupOperationState.CANCELLED,
                        failure = "Operation cancelled",
                    )
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                _environmentNotice.value = "Failed to cancel operation: ${e.message}"
            }
        }
    }

    /**
     * Retrieves the persisted attempt log text (T067, T068).
     */
    suspend fun getPersistedAttemptLog(attemptId: String? = null): String? = toolchainService?.getAttemptLog(attemptId)

    /**
     * Busy never destroys a pending terminal step: an operation that still has its handoff stays
     * AWAITING_USER_ACTION (the journal attempt is still pending), everything else is rejected.
     */
    private fun applyBusy(outcome: SetupOutcome.Busy, subject: String) {
        val current = _activeOperation.value
        if (current?.awaitingHandoff != null) {
            _activeOperation.value =
                current.copy(
                    executionState = SetupOperationState.AWAITING_USER_ACTION,
                    failure = "Another setup operation is running",
                )
            _environmentNotice.value = "Another setup operation is running. Try again when it finishes."
        } else {
            _activeOperation.value =
                current?.copy(
                    executionState = SetupOperationState.FAILED,
                    failure = "Environment is busy with owner: ${outcome.ownerId}",
                )
            _environmentNotice.value = "$subject: environment is busy (${outcome.ownerId})"
        }
    }

    /** Maps a typed outcome onto the active operation; every branch is distinct, nothing is generic success. */
    private fun applyTerminalOutcome(outcome: SetupOutcome?, subject: String) {
        when (outcome) {
            null -> _activeOperation.value = null
            is SetupOutcome.Succeeded -> _activeOperation.value = null
            is SetupOutcome.Cancelled -> {
                _activeOperation.value =
                    _activeOperation.value?.copy(
                        executionState = SetupOperationState.CANCELLED,
                        failure = "Operation cancelled",
                    )
                _environmentNotice.value = "$subject was cancelled"
            }
            is SetupOutcome.Failed -> {
                _activeOperation.value =
                    _activeOperation.value?.copy(
                        executionState = SetupOperationState.FAILED,
                        failure = outcome.reason,
                    )
                _environmentNotice.value = "$subject failed: ${outcome.reason}"
            }
            is SetupOutcome.Busy -> applyBusy(outcome, "$subject rejected")
            is SetupOutcome.Interrupted -> {
                _activeOperation.value =
                    _activeOperation.value?.copy(
                        executionState = SetupOperationState.INTERRUPTED,
                        failure = outcome.reason,
                    )
                _environmentNotice.value = "$subject interrupted: ${outcome.reason}"
            }
            is SetupOutcome.AwaitingUserAction -> {
                _activeOperation.value =
                    _activeOperation.value?.copy(
                        operationId = outcome.pendingPlanId,
                        executionState = SetupOperationState.AWAITING_USER_ACTION,
                        awaitingHandoff = outcome.handoff,
                    )
                // Modals never open by themselves (contracts/ui-states.md §1).
                // Do not auto-set _isHandoffSheetVisible.value = true here.
                _environmentNotice.value = "Action required: external authorization needed in WSL terminal."
            }
        }
    }

    /**
     * Toggling Auto Doctor regenerates an open preview so package/elevation summaries reflect the
     * policy the confirmed attempt will freeze (FR-004).
     */
    fun setAutoDoctorEnabled(enabled: Boolean) {
        _isAutoDoctorEnabled.value = enabled
        val op = _activeOperation.value
        val plan = op?.plan
        if (op != null && plan != null && op.executionState == SetupOperationState.PREVIEW) {
            launchPreview(plan.kind, plan.targetStageOrToolId, op.stageId)
        }
    }

    fun selectStage(stage: SetupStepStage?) {
        _selectedStage.value = stage
    }

    fun dismissEnvironmentNotice() {
        _environmentNotice.value = null
    }

    /**
     * Dismisses the active terminal handoff sheet without cancelling the underlying pending attempt ("Close for now").
     * The attempt remains in AWAITING_USER_ACTION with handoff details intact so resume controls stay available.
     */
    fun dismissHandoff() {
        _isHandoffSheetVisible.value = false
        _environmentNotice.value = "Terminal authorization pending. You can resume verification at any time."
    }

    /**
     * Re-opens the terminal handoff sheet if an operation is currently awaiting user action.
     */
    fun showHandoffSheet() {
        if (_activeOperation.value?.executionState == SetupOperationState.AWAITING_USER_ACTION) {
            _isHandoffSheetVisible.value = true
        }
    }

    /**
     * Initiates system package installation (US1).
     * If an operation is already awaiting terminal action, opens the sheet.
     * Otherwise, prepares and confirms a BOOTSTRAP_PACKAGES plan, then opens the handoff sheet on AwaitingUserAction.
     */
    fun installPackages() {
        val service = toolchainService
        when {
            _activeOperation.value?.executionState == SetupOperationState.AWAITING_USER_ACTION ->
                _isHandoffSheetVisible.value = true
            toolchainSetupState.value.isBusy || currentExecutionJob?.isActive == true -> Unit
            service == null -> _environmentNotice.value = "Toolchain service is not available in this session."
            else -> startBootstrapPackages(service)
        }
    }

    private fun startBootstrapPackages(service: ToolchainProvisioningService) {
        currentExecutionJob =
            scope.launch {
                try {
                    val plan =
                        service.prepare(
                            kind = SetupPlanKind.BOOTSTRAP_PACKAGES,
                            targetId = SetupStepStage.SYSTEM_PACKAGES.name,
                            autoDoctorEnabled = _isAutoDoctorEnabled.value,
                        )
                    _activeOperation.value =
                        SetupOperation(
                            operationId = plan.planId,
                            kind = SetupOperationKind.BOOTSTRAP_PACKAGES,
                            stageId = SetupStepStage.SYSTEM_PACKAGES,
                            plannedChanges = plan.toPlannedChanges(),
                            executionState = SetupOperationState.RUNNING,
                            autoDoctorEnabled = plan.autoDoctorEnabled,
                            plan = plan,
                        )
                    val outcome = service.confirm(plan.planId, plan.revisionHash)
                    applyTerminalOutcome(outcome, "Package installation")
                    if (outcome is SetupOutcome.AwaitingUserAction) {
                        _isHandoffSheetVisible.value = true
                    }
                } catch (e: CancellationException) {
                    _activeOperation.value =
                        _activeOperation.value?.copy(
                            executionState = SetupOperationState.CANCELLED,
                            failure = "Operation cancelled",
                        )
                    _environmentNotice.value = "Operation was cancelled"
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    _activeOperation.value =
                        _activeOperation.value?.copy(
                            executionState = SetupOperationState.FAILED,
                            failure = e.message,
                        )
                    _environmentNotice.value = "Package installation failed: ${e.message}"
                } finally {
                    currentExecutionJob = null
                }
            }
    }

    /**
     * Explicitly abandons the awaiting operation, cancelling both the UI operation and the durable journal attempt.
     */
    @Suppress("ReturnCount")
    fun abandonAwaitingOperation() {
        val current = _activeOperation.value ?: return
        if (current.executionState != SetupOperationState.AWAITING_USER_ACTION) return

        val service = toolchainService
        if (service == null) {
            _environmentNotice.value = "Toolchain service is not available in this session."
            return
        }

        _isHandoffSheetVisible.value = false
        _handoffSheetState.value = TerminalHandoffState.Idle
        scope.launch {
            try {
                when (val outcome = service.cancel()) {
                    is SetupOutcome.Cancelled -> {
                        _activeOperation.value =
                            current.copy(
                                executionState = SetupOperationState.CANCELLED,
                                failure = "Operation abandoned by user",
                                awaitingHandoff = null,
                            )
                        _environmentNotice.value = "Operation was cancelled"
                    }
                    is SetupOutcome.Interrupted -> {
                        _activeOperation.value =
                            current.copy(
                                executionState = SetupOperationState.AWAITING_USER_ACTION,
                                failure = outcome.reason,
                            )
                        _environmentNotice.value = "Could not abandon operation: ${outcome.reason}"
                    }
                    is SetupOutcome.Failed -> {
                        _activeOperation.value =
                            current.copy(
                                executionState = SetupOperationState.AWAITING_USER_ACTION,
                                failure = outcome.reason,
                            )
                        _environmentNotice.value = "Could not abandon operation: ${outcome.reason}"
                    }
                    else -> {
                        _activeOperation.value =
                            current.copy(
                                executionState = SetupOperationState.AWAITING_USER_ACTION,
                                failure = "Unexpected outcome when abandoning operation",
                            )
                        _environmentNotice.value = "Could not abandon operation"
                    }
                }
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                _activeOperation.value =
                    current.copy(
                        executionState = SetupOperationState.AWAITING_USER_ACTION,
                        failure = e.message ?: "Failed to abandon operation",
                    )
                _environmentNotice.value = "Could not abandon operation: ${e.message}"
            }
        }
    }

    /**
     * Resumes an operation currently paused in AWAITING_USER_ACTION after user terminal authorization.
     * A successful resume re-runs the environment check, so after a package handoff setup continues
     * with the build service instead of stopping at "Pending system packages" (the operation kind is
     * not reliable here: an attempt restored after a restart is not tagged BOOTSTRAP_PACKAGES).
     */
    fun resumeAwaitingOperation() {
        val service = toolchainService
        val currentOp = _activeOperation.value
        val planId = currentOp?.plan?.planId ?: currentOp?.operationId
        val awaiting = currentOp?.executionState == SetupOperationState.AWAITING_USER_ACTION ||
            _handoffSheetState.value == TerminalHandoffState.Verifying
        when {
            service == null -> _environmentNotice.value = "Toolchain service is not available in this session."
            currentOp == null || planId == null || !awaiting ->
                _environmentNotice.value = "No operation awaiting user action to resume."
            _handoffSheetState.value == TerminalHandoffState.Verifying ||
                toolchainSetupState.value.isBusy ||
                currentExecutionJob?.isActive == true -> Unit
            else -> startResume(service, currentOp, planId)
        }
    }

    private fun startResume(service: ToolchainProvisioningService, currentOp: SetupOperation, planId: String) {
        _handoffSheetState.value = TerminalHandoffState.Verifying
        val currentHandoff = currentOp.awaitingHandoff
        _activeOperation.value = currentOp.copy(executionState = SetupOperationState.RUNNING, failure = null)
        // A resumed plan verifies the machine itself; only a resume that ran no check (the package handoff)
        // needs one to continue to the build service. Measured, since the kind is lost across a restart.
        val checkedBefore = toolchainSetupState.value.checkedAt
        currentExecutionJob =
            scope.launch {
                var continueSetup = false
                try {
                    val outcome = service.resume(planId)
                    applyResumeOutcome(outcome, currentHandoff)
                    continueSetup = outcome is SetupOutcome.Succeeded
                } catch (e: CancellationException) {
                    endOperation(SetupOperationState.CANCELLED, "Operation cancelled")
                    _environmentNotice.value = "Operation was cancelled"
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    keepHandoffOrFail(currentHandoff, e.message ?: "Failed to verify requirements")
                    _environmentNotice.value = "Operation failed: ${e.message}"
                } finally {
                    currentExecutionJob = null
                    if (_activeOperation.value?.executionState == SetupOperationState.SUCCEEDED) {
                        _activeOperation.value = null
                    }
                }
                if (continueSetup && toolchainSetupState.value.checkedAt == checkedBefore) checkEnvironmentStatus()
            }
    }

    private fun applyResumeOutcome(outcome: SetupOutcome, currentHandoff: UserRepairHandoff?) {
        when (outcome) {
            is SetupOutcome.Succeeded -> {
                _activeOperation.value = _activeOperation.value?.copy(
                    executionState = SetupOperationState.SUCCEEDED,
                    awaitingHandoff = null,
                    failure = null,
                )
                _handoffSheetState.value = TerminalHandoffState.Done
                _isHandoffSheetVisible.value = false
            }
            is SetupOutcome.AwaitingUserAction -> {
                _activeOperation.value = _activeOperation.value?.copy(
                    executionState = SetupOperationState.AWAITING_USER_ACTION,
                    awaitingHandoff = outcome.handoff,
                    failure = outcome.reason,
                )
                _handoffSheetState.value = TerminalHandoffState.StillMissing(
                    packages = outcome.handoff.packages,
                    command = outcome.handoff.terminalCommand,
                )
                _environmentNotice.value = "Action still pending: requirements not yet satisfied."
            }
            is SetupOutcome.Cancelled -> {
                endOperation(SetupOperationState.CANCELLED, "Operation cancelled")
                _environmentNotice.value = "Operation was cancelled"
            }
            is SetupOutcome.Failed -> {
                val kept = keepHandoffOrFail(currentHandoff, outcome.reason)
                _environmentNotice.value = if (kept) {
                    "Pre-flight check failed before resumption: ${outcome.reason}"
                } else {
                    "Operation failed: ${outcome.reason}"
                }
            }
            is SetupOutcome.Busy -> {
                keepAwaiting(currentHandoff, "Another setup operation is running")
                _handoffSheetState.value = TerminalHandoffState.AnotherOperationRunning
                _environmentNotice.value = "Another setup operation is running. Try again when it finishes."
            }
            is SetupOutcome.Interrupted -> {
                keepAwaiting(currentHandoff, outcome.reason)
                _handoffSheetState.value = TerminalHandoffState.Idle
                _environmentNotice.value = "Operation interrupted: ${outcome.reason}"
            }
        }
    }

    /** Keeps the pending terminal step open with [reason]; the operation stays AWAITING_USER_ACTION. */
    private fun keepAwaiting(currentHandoff: UserRepairHandoff?, reason: String) {
        _activeOperation.value = _activeOperation.value?.copy(
            executionState = SetupOperationState.AWAITING_USER_ACTION,
            awaitingHandoff = currentHandoff ?: _activeOperation.value?.awaitingHandoff,
            failure = reason,
        )
    }

    /**
     * Shows the pending command again when there is one to retry, otherwise fails the operation.
     * Returns true when the handoff was kept.
     */
    private fun keepHandoffOrFail(currentHandoff: UserRepairHandoff?, reason: String): Boolean {
        val handoff = currentHandoff ?: _activeOperation.value?.awaitingHandoff
        if (handoff == null) {
            endOperation(SetupOperationState.FAILED, reason)
        } else {
            keepAwaiting(handoff, reason)
            _handoffSheetState.value = TerminalHandoffState.StillMissing(
                packages = handoff.packages,
                command = handoff.terminalCommand,
            )
        }
        return handoff != null
    }

    private fun endOperation(state: SetupOperationState, failure: String?) {
        _activeOperation.value = _activeOperation.value?.copy(
            executionState = state,
            failure = failure,
            awaitingHandoff = null,
        )
        _handoffSheetState.value = TerminalHandoffState.Idle
        _isHandoffSheetVisible.value = false
    }

    /**
     * Previews a cache reset. Reset is a mutation: it is shown as a confirmable plan and executes only
     * on explicit confirmation (FR-003); after a confirmed reset the environment is re-checked so the
     * cleared state is measured live rather than assumed.
     */
    fun previewResetToolchainCache() {
        if (toolchainService == null) {
            _environmentNotice.value = "Toolchain service is not available in this session."
            return
        }
        if (toolchainSetupState.value.isBusy || currentExecutionJob?.isActive == true) return
        launchPreview(SetupPlanKind.CACHE_RESET, null, null)
    }
}
