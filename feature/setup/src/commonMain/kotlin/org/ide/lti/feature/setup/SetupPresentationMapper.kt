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

import androidx.compose.runtime.Immutable
import kotlinx.datetime.Instant
import org.ide.lti.core.designsystem.component.display.IdeStatusSeverity
import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.model.setup.AttemptStatus
import org.ide.lti.core.model.setup.SetupAttemptRecord

@Immutable
data class EnvironmentSummaryItem(
    val label: String,
    val value: String,
    val severity: IdeStatusSeverity = IdeStatusSeverity.Neutral,
)

@Immutable
data class EnvironmentSummaryPresentation(
    val platform: EnvironmentSummaryItem,
    val runtime: EnvironmentSummaryItem,
    val buildService: EnvironmentSummaryItem,
    val toolReadiness: EnvironmentSummaryItem,
    val storageHeadroom: EnvironmentSummaryItem,
) {
    val items: List<EnvironmentSummaryItem>
        get() = listOf(platform, runtime, buildService, toolReadiness, storageHeadroom)
}

@Immutable
data class SetupStatusBarPresentation(val leadingStatusText: String? = null, val failureText: String? = null) {
    val isVisible: Boolean
        get() = leadingStatusText != null || failureText != null
}

@Immutable
data class ProjectsHeaderStatusPresentation(
    val statusText: String?,
    val actionLabel: String? = null,
    val isNeedsSetup: Boolean = false,
)

@Immutable
data class BuildServicePresentation(
    val title: String = "Build service",
    val detailText: String,
    val chipText: String,
    val severity: IdeStatusSeverity,
    val primaryAction: SetupRowAction? = null,
    val secondaryAction: SetupRowAction? = null,
    val isReady: Boolean = false,
    val isRunning: Boolean = false,
    val errorText: String? = null,
) {
    val primaryActionLabel: String? get() = primaryAction?.let { actionLabel(it) }
    val secondaryActionLabel: String? get() = secondaryAction?.let { actionLabel(it) }
}

enum class StagePresentationState {
    UNKNOWN,
    CHECKING,
    RESTORED,
    READY,
    WARNING,
    FAILED,
    UNAVAILABLE,
}

data class StagePresentation(
    val id: String,
    val title: String,
    val state: StagePresentationState,
    val description: String,
    val error: String? = null,
)

enum class PrimaryAction {
    CHECK,
    CHECKING,
    SET_UP,
    STAGE_PROGRESS,
    RETRY_STAGE,
    RETRY_CONNECTION,
    RECONNECT,
    OPEN_WORKSPACES,
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

    data class CheckedNeedsSetup(val missingPrerequisites: List<String> = emptyList(), val checkedAt: Long? = null) :
        EnvironmentState

    data class Ready(val lastReadyAt: Long, val checkedAt: Long) : EnvironmentState

    data class Failed(val stage: SetupStepStage?, val error: String, val checkedAt: Long? = null) : EnvironmentState

    data class Unavailable(val reason: String) : EnvironmentState

    data class Restored(val lastReadyAt: Long? = null, val checkedAt: Long? = null) : EnvironmentState

    data class MutationRunning(val operationId: String, val stage: SetupStepStage? = null) : EnvironmentState

    data class Reconnecting(val operationId: String) : EnvironmentState

    data class Interrupted(val reason: String) : EnvironmentState
}

data class EnvironmentPresentation(
    val environmentState: EnvironmentState,
    val stages: List<StagePresentation>,
    val activeOperationId: String?,
    val lastVerifiedAt: Long?,
    val checkedAt: Long?,
    val storageAvailableBytes: Long?,
    val storageMeasuredAt: Long?,
    val primaryAction: PrimaryAction,
)

private fun isMutating(state: ToolchainSetupState, activeOperation: SetupOperation?): Boolean {
    val opRunning = activeOperation?.executionState == SetupOperationState.RUNNING
    return state.activeOperationId != null || opRunning || state.isRunning
}

private fun isCheckingState(state: ToolchainSetupState): Boolean {
    val nonMutationBusy = state.isBusy && !state.isRunning
    return state.isChecking || nonMutationBusy
}

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
                stage = state.currentStage ?: activeOperation?.stageId,
            )
        }
        isCheckingState(state) -> EnvironmentState.Checking
        else -> null
    }
}

private fun checkFailures(state: ToolchainSetupState): EnvironmentState? {
    val connectionFailed =
        state.steps.firstOrNull {
            val isConn = it.stage == SetupStepStage.WSL_DETECTION || it.stage == SetupStepStage.SERVER_CONNECTIVITY
            isConn && it.status == StepStatus.FAILED
        }
    if (connectionFailed != null) {
        return EnvironmentState.Unavailable(
            reason = connectionFailed.error ?: connectionFailed.description,
        )
    }

    val failedStep = state.steps.firstOrNull { it.status == StepStatus.FAILED }
    return if (failedStep != null) {
        EnvironmentState.Failed(
            stage = failedStep.stage,
            error = failedStep.error ?: failedStep.description,
            checkedAt = state.checkedAt,
        )
    } else {
        null
    }
}

/**
 * The one readiness verdict, the same one Projects admits a workspace on: a completed live check with every
 * stage green (the Toolchain stage already requires the core binaries present and published). Whether a tool
 * was also individually tested is shown on its row, never a second, stricter definition of "ready".
 */
private fun isLiveReady(state: ToolchainSetupState): Boolean = state.checkedAt != null && state.canLaunchWorkspace

private fun checkRestored(state: ToolchainSetupState): EnvironmentState? {
    val hasRestoredStep = state.steps.any { it.provenance == StepProvenance.RESTORED }
    val hasHistory = hasRestoredStep || state.lastVerifiedTimestamp != null
    return if (state.checkedAt == null && hasHistory) {
        EnvironmentState.Restored(
            lastReadyAt = state.lastReadyAt ?: state.lastVerifiedTimestamp,
            checkedAt = null,
        )
    } else {
        null
    }
}

private fun checkReady(state: ToolchainSetupState): EnvironmentState? {
    val checkedAt = state.checkedAt
    return if (isLiveReady(state) && checkedAt != null) {
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
    val anyProbedLive =
        state.checkedAt != null ||
            state.steps.any {
                it.provenance == StepProvenance.LIVE && it.status != StepStatus.PENDING
            }
    return if (anyProbedLive) {
        val missing =
            state.steps
                .filter { it.status != StepStatus.SUCCESS && it.status != StepStatus.WARNING }
                .map { it.title }
        EnvironmentState.CheckedNeedsSetup(
            missingPrerequisites = missing,
            checkedAt = state.checkedAt,
        )
    } else {
        null
    }
}

private fun isJavaIssue(desc: String, err: String, systemPackagesStep: SetupStepDetail?): Boolean {
    val matchesText = desc.contains("Needs Java", ignoreCase = true) ||
        desc.contains("Java 21", ignoreCase = true) ||
        err.contains("Needs Java", ignoreCase = true) ||
        err.contains("Java 21", ignoreCase = true)
    val isPkgFailed = systemPackagesStep?.status == StepStatus.FAILED
    val pkgDesc = systemPackagesStep?.description.orEmpty()
    val pkgErr = systemPackagesStep?.error.orEmpty()
    val hasJavaPackageIssue = pkgDesc.contains("Java", ignoreCase = true) ||
        pkgDesc.contains("jdk", ignoreCase = true) ||
        pkgErr.contains("Java", ignoreCase = true) ||
        pkgErr.contains("jdk", ignoreCase = true)
    val matchesPkg = isPkgFailed && hasJavaPackageIssue
    return matchesText || matchesPkg
}

private fun isStartupFailure(desc: String, err: String): Boolean {
    val startupPhrases = listOf(
        "didn't start",
        "failed to start",
        "start server",
        "won't start",
        "port collision",
        "timeout",
    )
    return startupPhrases.any { desc.contains(it, ignoreCase = true) || err.contains(it, ignoreCase = true) }
}

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
        is EnvironmentState.Reconnecting -> PrimaryAction.RECONNECT
        is EnvironmentState.Interrupted -> PrimaryAction.RECONNECT
    }

    /**
     * Determines the authoritative [EnvironmentState] from state facts.
     */
    fun mapEnvironmentState(
        state: ToolchainSetupState,
        isToolchainBound: Boolean = true,
        activeOperation: SetupOperation? = null,
    ): EnvironmentState = checkActiveTransitions(state, isToolchainBound, activeOperation)
        ?: checkFailures(state)
        ?: checkRestored(state)
        ?: checkReady(state)
        ?: checkNeedsSetup(state)
        ?: EnvironmentState.Unknown

    fun mapEnvironment(
        state: ToolchainSetupState,
        isToolchainBound: Boolean = true,
        activeOperation: SetupOperation? = null,
    ): EnvironmentPresentation {
        val mappedStages =
            state.steps.map { detail ->
                StagePresentation(
                    id = detail.stage.name,
                    title = detail.title,
                    state = mapStageState(detail, state.isChecking),
                    description = detail.description,
                    error = detail.error,
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
            primaryAction = action,
        )
    }

    private fun mapRuntimeItem(toolchain: ToolchainSetupState): EnvironmentSummaryItem {
        val wslStep = toolchain.steps.firstOrNull { it.stage == SetupStepStage.WSL_DETECTION }
        return when (wslStep?.status) {
            StepStatus.SUCCESS -> {
                val distroLabel = toolchain.activeDistro?.let { "WSL 2 ($it)" } ?: "WSL 2"
                EnvironmentSummaryItem("Runtime", distroLabel, IdeStatusSeverity.Ready)
            }
            StepStatus.RUNNING -> EnvironmentSummaryItem("Runtime", "Detecting…", IdeStatusSeverity.Running)
            StepStatus.FAILED -> EnvironmentSummaryItem("Runtime", "Unavailable", IdeStatusSeverity.Failed)
            StepStatus.WARNING -> EnvironmentSummaryItem("Runtime", "Warning", IdeStatusSeverity.Warning)
            StepStatus.PENDING, null ->
                if (toolchain.isChecking) {
                    EnvironmentSummaryItem("Runtime", "Detecting…", IdeStatusSeverity.Running)
                } else {
                    EnvironmentSummaryItem("Runtime", "Not detected", IdeStatusSeverity.Neutral)
                }
        }
    }

    fun formatElapsedTime(elapsedMs: Long): String {
        val totalSeconds = (elapsedMs / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val mStr = if (minutes < 10) "0$minutes" else minutes.toString()
        val sStr = if (seconds < 10) "0$seconds" else seconds.toString()
        return "$mStr:$sStr"
    }

    fun formatProgressDetail(description: String, startedAt: Long?, nowEpochMs: Long?): String {
        if (startedAt == null || nowEpochMs == null || nowEpochMs < startedAt) return description
        val elapsed = formatElapsedTime(nowEpochMs - startedAt)
        return "$description · $elapsed"
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    fun mapBuildService(
        step: SetupStepDetail?,
        daemonPingMs: Long? = null,
        isChecking: Boolean = false,
        systemPackagesStep: SetupStepDetail? = null,
    ): BuildServicePresentation {
        if (step == null) {
            return BuildServicePresentation(
                detailText = if (isChecking) "Checking…" else "Pending verification.",
                chipText = if (isChecking) "Checking" else "Pending",
                severity = if (isChecking) IdeStatusSeverity.Running else IdeStatusSeverity.Neutral,
                isRunning = isChecking,
            )
        }

        val desc = step.description
        val err = step.error.orEmpty()

        if (desc.contains("missing from this app", ignoreCase = true) ||
            desc.contains("not bundled", ignoreCase = true) ||
            desc.contains("bundle missing", ignoreCase = true) ||
            err.contains("missing from this app", ignoreCase = true)
        ) {
            return BuildServicePresentation(
                detailText = "Build service missing from this app — reinstall LtiRom Studio",
                chipText = "Not bundled",
                severity = IdeStatusSeverity.Failed,
                errorText = step.error,
            )
        }

        val isJava = isJavaIssue(desc, err, systemPackagesStep)
        if (isJava && (step.status == StepStatus.FAILED || step.status == StepStatus.PENDING)) {
            return BuildServicePresentation(
                detailText = "Needs Java 21 (install via System packages)",
                chipText = "Needs Java",
                severity = IdeStatusSeverity.Failed,
                errorText = step.error,
            )
        }

        if (desc.contains("update", ignoreCase = true) && desc.contains("wait", ignoreCase = true)) {
            return BuildServicePresentation(
                detailText = desc,
                chipText = "Update waiting",
                severity = IdeStatusSeverity.Warning,
            )
        }

        if (step.status == StepStatus.RUNNING &&
            (desc.contains("install", ignoreCase = true) || desc.startsWith("Installing", ignoreCase = true))
        ) {
            return BuildServicePresentation(
                detailText = desc.ifBlank { "Installing build service…" },
                chipText = "Installing",
                severity = IdeStatusSeverity.Running,
                isRunning = true,
            )
        }

        // Only this step's own state: isChecking is true for the whole environment check, so using it here kept a
        // finished Server Bridge row spinning until the last step (toolchain) was done.
        if (step.status == StepStatus.RUNNING) {
            return BuildServicePresentation(
                detailText = if (desc.isNotBlank() && desc != "Pending verification.") desc else "Checking…",
                chipText = if (desc.contains("install", ignoreCase = true)) "Installing" else "Checking",
                severity = IdeStatusSeverity.Running,
                isRunning = true,
            )
        }

        if (step.status == StepStatus.SUCCESS) {
            val ping = daemonPingMs ?: 0L
            val chip = if (ping > 0L) "${ping}ms" else "Connected"
            return BuildServicePresentation(
                detailText = desc.ifBlank { "Connected" },
                chipText = chip,
                severity = IdeStatusSeverity.Ready,
                isReady = true,
            )
        }

        if (step.status == StepStatus.FAILED &&
            (desc.contains("install", ignoreCase = true) || err.contains("install", ignoreCase = true))
        ) {
            return BuildServicePresentation(
                detailText = desc.ifBlank { "Build service installation failed" },
                chipText = "Install failed",
                severity = IdeStatusSeverity.Failed,
                primaryAction = SetupRowAction.RETRY,
                errorText = step.error,
            )
        }

        if (step.status == StepStatus.FAILED && isStartupFailure(desc, err)) {
            val suffix = step.error?.let { ": $it" } ?: ""
            val detail = if (desc.contains("didn't start")) desc else "Build service didn't start$suffix"
            return BuildServicePresentation(
                detailText = detail,
                chipText = "Won't start",
                severity = IdeStatusSeverity.Failed,
                primaryAction = SetupRowAction.RETRY,
                secondaryAction = SetupRowAction.SHOW_LOG,
                errorText = step.error,
            )
        }

        if (step.status == StepStatus.FAILED) {
            val detail = if (desc.contains("Can't reach", ignoreCase = true)) {
                desc
            } else {
                "Can't reach the build service"
            }
            return BuildServicePresentation(
                detailText = detail,
                chipText = "Unreachable",
                severity = IdeStatusSeverity.Failed,
                primaryAction = SetupRowAction.RETRY,
                errorText = step.error,
            )
        }

        if (step.status == StepStatus.WARNING) {
            return BuildServicePresentation(
                detailText = desc.ifBlank { "Build service degraded" },
                chipText = "Degraded",
                severity = IdeStatusSeverity.Warning,
                primaryAction = SetupRowAction.RETRY,
                errorText = step.error,
            )
        }

        return BuildServicePresentation(
            detailText = desc.ifBlank { "Pending verification." },
            chipText = "Pending",
            severity = IdeStatusSeverity.Neutral,
        )
    }

    private fun mapBuildServiceItem(toolchain: ToolchainSetupState): EnvironmentSummaryItem {
        val serverStep = toolchain.steps.firstOrNull { it.stage == SetupStepStage.SERVER_CONNECTIVITY }
        return when (serverStep?.status) {
            StepStatus.SUCCESS -> EnvironmentSummaryItem("Build service", "Ready", IdeStatusSeverity.Ready)
            StepStatus.RUNNING -> EnvironmentSummaryItem("Build service", "Checking…", IdeStatusSeverity.Running)
            StepStatus.FAILED -> EnvironmentSummaryItem("Build service", "Needs attention", IdeStatusSeverity.Failed)
            StepStatus.WARNING -> EnvironmentSummaryItem("Build service", "Degraded", IdeStatusSeverity.Warning)
            StepStatus.PENDING, null ->
                if (toolchain.isChecking) {
                    EnvironmentSummaryItem("Build service", "Checking…", IdeStatusSeverity.Running)
                } else {
                    EnvironmentSummaryItem("Build service", "Not checked", IdeStatusSeverity.Neutral)
                }
        }
    }

    private fun mapToolReadinessItem(toolchain: ToolchainSetupState): EnvironmentSummaryItem {
        val toolsStep = toolchain.steps.firstOrNull { it.stage == SetupStepStage.TOOLCHAIN_COMPILATION }
        return when {
            toolchain.isRunning -> EnvironmentSummaryItem("Tool readiness", "Compiling…", IdeStatusSeverity.Running)
            toolsStep?.status == StepStatus.RUNNING || toolchain.isChecking -> {
                EnvironmentSummaryItem("Tool readiness", "Checking…", IdeStatusSeverity.Running)
            }
            toolsStep?.status == StepStatus.SUCCESS -> {
                EnvironmentSummaryItem("Tool readiness", "Ready", IdeStatusSeverity.Ready)
            }
            toolsStep?.status == StepStatus.FAILED -> {
                EnvironmentSummaryItem("Tool readiness", "Needs attention", IdeStatusSeverity.Failed)
            }
            toolsStep?.status == StepStatus.WARNING -> {
                EnvironmentSummaryItem("Tool readiness", "Partial", IdeStatusSeverity.Warning)
            }
            else -> EnvironmentSummaryItem("Tool readiness", "Not ready", IdeStatusSeverity.Neutral)
        }
    }

    private fun mapStorageHeadroomItem(toolchain: ToolchainSetupState): EnvironmentSummaryItem {
        val storageHeadroom =
            toolchain.storageAvailableBytes?.let { bytes ->
                "${bytes / (1024L * 1024L * 1024L)} GB"
            } ?: if (toolchain.isChecking) "Checking…" else "—"
        return EnvironmentSummaryItem("Storage headroom", storageHeadroom, IdeStatusSeverity.Neutral)
    }

    fun mapEnvironmentSummary(toolchain: ToolchainSetupState): EnvironmentSummaryPresentation =
        EnvironmentSummaryPresentation(
            platform = EnvironmentSummaryItem("Platform", "Windows", IdeStatusSeverity.Neutral),
            runtime = mapRuntimeItem(toolchain),
            buildService = mapBuildServiceItem(toolchain),
            toolReadiness = mapToolReadinessItem(toolchain),
            storageHeadroom = mapStorageHeadroomItem(toolchain),
        )

    fun mapStatusBar(
        activeOperation: SetupOperation?,
        toolchainSetupState: ToolchainSetupState,
    ): SetupStatusBarPresentation {
        val pendingAttempt = toolchainSetupState.pendingAttemptId
        val isChecking = toolchainSetupState.isChecking

        val leadingText =
            when {
                activeOperation != null ->
                    when (activeOperation.executionState) {
                        SetupOperationState.PREVIEW -> "Previewing environment setup…"
                        SetupOperationState.RUNNING -> "Running environment setup…"
                        SetupOperationState.RECONNECTING -> "Reconnecting to background setup…"
                        SetupOperationState.SUCCEEDED -> "Environment setup completed"
                        SetupOperationState.FAILED -> "Environment setup failed"
                        SetupOperationState.CANCELLED -> "Environment setup cancelled"
                        SetupOperationState.INTERRUPTED -> "Environment setup was interrupted"
                        SetupOperationState.AWAITING_USER_ACTION -> "Terminal authorization required"
                    }
                isChecking -> "Verifying machine environment…"
                pendingAttempt != null -> "A previous setup didn't finish • Review in Recovery"
                else -> null
            }

        return SetupStatusBarPresentation(
            leadingStatusText = leadingText,
            failureText = activeOperation?.failure,
        )
    }

    fun mapProjectsHeaderStatus(toolchain: ToolchainSetupState): ProjectsHeaderStatusPresentation = when {
        toolchain.isChecking ->
            ProjectsHeaderStatusPresentation(
                statusText = "Checking this machine…",
            )
        toolchain.canLaunchWorkspace && (toolchain.lastReadyAt != null || toolchain.isAllReady) ->
            ProjectsHeaderStatusPresentation(
                statusText = "Machine ready",
            )
        else ->
            ProjectsHeaderStatusPresentation(
                statusText = "This machine needs setup",
                actionLabel = "Set up this machine",
                isNeedsSetup = true,
            )
    }

    fun mapRecovery(
        toolchainSetupState: ToolchainSetupState,
        activeOperation: SetupOperation? = null,
        attemptRecord: SetupAttemptRecord? = null,
        hasLogs: Boolean = false,
    ): RecoveryPresentation {
        val operation = mapRecoveryOperation(
            attemptRecord = attemptRecord,
            activeOperation = activeOperation,
            pendingAttemptId = toolchainSetupState.pendingAttemptId,
            recoveryBlockReason = toolchainSetupState.recoveryBlockReason,
        )
        val service = resolveRecoveryService(toolchainSetupState, activeOperation)
        val blockedReason = resolveRecoveryBlockedReason(toolchainSetupState, attemptRecord)
        val problems = resolveRecoveryProblems(toolchainSetupState)
        val evidence = resolveRecoveryEvidence(attemptRecord)

        val isEmpty = operation == null &&
            service == null &&
            blockedReason == null &&
            problems.isEmpty() &&
            !evidence.hasEvidence &&
            !hasLogs

        return RecoveryPresentation(
            isEmpty = isEmpty,
            operation = operation,
            service = service,
            blockedReason = blockedReason,
            problems = problems,
            evidence = evidence,
        )
    }
}

private fun mapRecoveryOperation(
    attemptRecord: SetupAttemptRecord?,
    activeOperation: SetupOperation?,
    pendingAttemptId: String?,
    recoveryBlockReason: String?,
): RecoveryOperationPresentation? = when {
    attemptRecord != null -> RecoveryOperationPresentation(
        kind = formatPlanKind(attemptRecord.planKind),
        attemptId = attemptRecord.attemptId,
        state = formatAttemptStatus(attemptRecord.status),
        failure = attemptRecord.terminalReason,
        issuedAt = if (attemptRecord.createdAtEpochMs > 0) formatTimestamp(attemptRecord.createdAtEpochMs) else null,
    )
    activeOperation != null -> RecoveryOperationPresentation(
        kind = formatPlanKind(activeOperation.kind.name),
        attemptId = activeOperation.operationId ?: pendingAttemptId ?: "—",
        state = formatOperationState(activeOperation.executionState),
        failure = activeOperation.failure,
        issuedAt = null,
    )
    pendingAttemptId != null -> RecoveryOperationPresentation(
        kind = "Setup",
        attemptId = pendingAttemptId,
        state = "Interrupted",
        failure = recoveryBlockReason,
        issuedAt = null,
    )
    else -> null
}

private fun resolveRecoveryService(
    toolchainSetupState: ToolchainSetupState,
    activeOperation: SetupOperation?,
): BuildServicePresentation? {
    val serverStep = toolchainSetupState.steps.firstOrNull { it.stage == SetupStepStage.SERVER_CONNECTIVITY }
    val hasErrorOrWarning = serverStep?.status == StepStatus.FAILED ||
        serverStep?.status == StepStatus.WARNING ||
        serverStep?.error != null
    val isServiceIssue = serverStep != null && hasErrorOrWarning
    val isInterrupted = activeOperation?.executionState == SetupOperationState.INTERRUPTED ||
        activeOperation?.executionState == SetupOperationState.RECONNECTING ||
        toolchainSetupState.pendingAttemptId != null

    return if ((isServiceIssue || isInterrupted) && serverStep != null) {
        SetupPresentationMapper.mapBuildService(
            step = serverStep,
            daemonPingMs = toolchainSetupState.daemonPingMs,
            isChecking = toolchainSetupState.isChecking,
        )
    } else {
        null
    }
}

private fun resolveRecoveryBlockedReason(
    toolchainSetupState: ToolchainSetupState,
    attemptRecord: SetupAttemptRecord?,
): String? = toolchainSetupState.recoveryBlockReason
    ?: if (attemptRecord?.recoveryBlocked == true) attemptRecord.terminalReason else null

private fun resolveRecoveryProblems(toolchainSetupState: ToolchainSetupState): List<RecoveryProblemItem> {
    val failedSteps = toolchainSetupState.steps
        .filter { it.status == StepStatus.FAILED }
        .map {
            RecoveryProblemItem(
                title = it.title.ifBlank { it.stage.displayName },
                detail = it.error ?: it.description,
                isStep = true,
            )
        }
    val failedDiagnostics = toolchainSetupState.diagnostics
        .filter { it.status == StepStatus.FAILED }
        .map {
            RecoveryProblemItem(
                title = it.title,
                detail = it.detail,
                isStep = false,
            )
        }
    return failedSteps + failedDiagnostics
}

private fun resolveRecoveryEvidence(attemptRecord: SetupAttemptRecord?): RecoveryEvidencePresentation =
    if (attemptRecord != null) {
        RecoveryEvidencePresentation(
            intents = attemptRecord.orderedIntents,
            terminalProofs = attemptRecord.terminalProofs,
            completedActionIds = attemptRecord.completedActionIds,
        )
    } else {
        RecoveryEvidencePresentation()
    }

private fun formatPlanKind(kind: String): String = when (kind.uppercase()) {
    "FULL_SETUP" -> "Full setup"
    "RETRY_STAGE" -> "Stage retry"
    "REPAIR_TOOL" -> "Tool repair"
    "CACHE_RESET" -> "Cache reset"
    "BOOTSTRAP_PACKAGES" -> "Bootstrap packages"
    else -> kind.lowercase().replaceFirstChar { it.uppercase() }
}

private fun formatAttemptStatus(status: AttemptStatus): String = when (status) {
    AttemptStatus.AUTHORIZED -> "Authorized"
    AttemptStatus.RUNNING -> "Running"
    AttemptStatus.RECONNECTING -> "Reconnecting"
    AttemptStatus.AWAITING_USER_ACTION -> "Waiting for user action"
    AttemptStatus.SUCCEEDED -> "Succeeded"
    AttemptStatus.FAILED -> "Failed"
    AttemptStatus.CANCELLED -> "Cancelled"
    AttemptStatus.INTERRUPTED -> "Interrupted"
    AttemptStatus.ABANDONED -> "Abandoned"
}

private fun formatOperationState(state: SetupOperationState): String = when (state) {
    SetupOperationState.PREVIEW -> "Preview"
    SetupOperationState.RUNNING -> "Running"
    SetupOperationState.RECONNECTING -> "Reconnecting"
    SetupOperationState.SUCCEEDED -> "Succeeded"
    SetupOperationState.FAILED -> "Failed"
    SetupOperationState.CANCELLED -> "Cancelled"
    SetupOperationState.INTERRUPTED -> "Interrupted"
    SetupOperationState.AWAITING_USER_ACTION -> "Waiting for user action"
}

private fun formatTimestamp(epochMs: Long): String {
    if (epochMs <= 0L) return "—"
    return try {
        val instant = Instant.fromEpochMilliseconds(epochMs)
        instant.toString().replace("T", " ").replace("Z", " UTC")
    } catch (_: Exception) {
        epochMs.toString()
    }
}
