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
import org.ide.lti.core.designsystem.component.display.IdeStatusSeverity
import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.model.setup.DistroStatus

@Immutable
data class WslRowPresentation(
    val title: String = "WSL 2 Runtime",
    val detailText: String,
    val severity: IdeStatusSeverity,
    val primaryActionLabel: String? = null,
    val secondaryActionLabel: String? = null,
    val isReady: Boolean = false,
    val instruction: String? = null,
)

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
data class SystemPackagesRowPresentation(
    val title: String = "System packages",
    val detailText: String,
    val severity: IdeStatusSeverity,
    val primaryActionLabel: String? = null,
    val isReady: Boolean = false,
    val isChecking: Boolean = false,
)

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
    val primaryAction: PrimaryAction,
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
        is EnvironmentState.Reconnecting -> PrimaryAction.RECONNECT
        is EnvironmentState.Interrupted -> PrimaryAction.RECONNECT
    }

    private fun isMutating(state: ToolchainSetupState, activeOperation: SetupOperation?): Boolean {
        val opRunning = activeOperation?.executionState == SetupOperationState.RUNNING
        return state.activeOperationId != null || opRunning || state.isRunning
    }

    private fun isCheckingState(state: ToolchainSetupState): Boolean {
        val nonMutationBusy = state.isBusy && !state.isRunning
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
                    stage = state.currentStage ?: activeOperation?.stageId,
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
                checkedAt = null,
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
                checkedAt = state.checkedAt,
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
            StepStatus.PENDING, null -> if (toolchain.isChecking) {
                EnvironmentSummaryItem("Runtime", "Detecting…", IdeStatusSeverity.Running)
            } else {
                EnvironmentSummaryItem("Runtime", "Not detected", IdeStatusSeverity.Neutral)
            }
        }
    }

    private fun mapBuildServiceItem(toolchain: ToolchainSetupState): EnvironmentSummaryItem {
        val serverStep = toolchain.steps.firstOrNull { it.stage == SetupStepStage.SERVER_CONNECTIVITY }
        return when (serverStep?.status) {
            StepStatus.SUCCESS -> EnvironmentSummaryItem("Build service", "Ready", IdeStatusSeverity.Ready)
            StepStatus.RUNNING -> EnvironmentSummaryItem("Build service", "Checking…", IdeStatusSeverity.Running)
            StepStatus.FAILED -> EnvironmentSummaryItem("Build service", "Needs attention", IdeStatusSeverity.Failed)
            StepStatus.WARNING -> EnvironmentSummaryItem("Build service", "Degraded", IdeStatusSeverity.Warning)
            StepStatus.PENDING, null -> if (toolchain.isChecking) {
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
        val storageHeadroom = toolchain.storageAvailableBytes?.let { bytes ->
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

        val leadingText = when {
            activeOperation != null -> when (activeOperation.executionState) {
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

    fun mapDistroStatus(status: DistroStatus): WslRowPresentation = when (status) {
        is DistroStatus.WslUnavailable -> WslRowPresentation(
            detailText = "WSL isn't installed. Run 'wsl --install' in PowerShell.",
            severity = IdeStatusSeverity.Failed,
            instruction = "Run 'wsl --install' in PowerShell."
        )
        is DistroStatus.NoDistro -> WslRowPresentation(
            detailText = "No Linux distribution. Run 'wsl --install -d Ubuntu-24.04' in PowerShell.",
            severity = IdeStatusSeverity.Failed,
            instruction = "Run 'wsl --install -d Ubuntu-24.04' in PowerShell."
        )
        is DistroStatus.StartupFailed -> WslRowPresentation(
            detailText = "${status.distro} didn't start: ${status.detail}",
            severity = IdeStatusSeverity.Failed,
            primaryActionLabel = "Retry",
            secondaryActionLabel = "Show diagnostics"
        )
        is DistroStatus.Unsupported -> WslRowPresentation(
            detailText = "${status.distro} isn't a supported Ubuntu LTS (found ${status.reason})",
            severity = IdeStatusSeverity.Failed
        )
        is DistroStatus.NoUsableUser -> WslRowPresentation(
            detailText = "${status.distro} needs a user",
            severity = IdeStatusSeverity.Failed,
            primaryActionLabel = "Open ${status.distro}"
        )
        is DistroStatus.Usable -> WslRowPresentation(
            detailText = "${status.environment.distro} · ${status.environment.user}",
            severity = IdeStatusSeverity.Ready,
            isReady = true
        )
    }

    fun mapProjectsHeaderStatus(toolchain: ToolchainSetupState): ProjectsHeaderStatusPresentation {
        return when {
            toolchain.isChecking -> ProjectsHeaderStatusPresentation(
                statusText = "Checking this machine…",
            )
            toolchain.canLaunchWorkspace && (toolchain.lastReadyAt != null || toolchain.isAllReady) -> ProjectsHeaderStatusPresentation(
                statusText = "Machine ready",
            )
            else -> ProjectsHeaderStatusPresentation(
                statusText = "This machine needs setup",
                actionLabel = "Set up this machine",
                isNeedsSetup = true,
            )
        }
    }

    fun mapSystemPackages(
        step: SetupStepDetail?,
        isChecking: Boolean = false,
        activeOperation: SetupOperation? = null,
        handoffSheetState: TerminalHandoffState = TerminalHandoffState.Idle,
    ): SystemPackagesRowPresentation {
        if (handoffSheetState is TerminalHandoffState.Verifying) {
            return SystemPackagesRowPresentation(
                detailText = "Checking installation…",
                severity = IdeStatusSeverity.Running,
                isChecking = true,
            )
        }

        if (handoffSheetState is TerminalHandoffState.StillMissing) {
            val pkgList = handoffSheetState.packages.joinToString(", ")
            return SystemPackagesRowPresentation(
                detailText = "Still missing: $pkgList",
                severity = IdeStatusSeverity.Failed,
                primaryActionLabel = "Install packages",
            )
        }

        val isAwaiting = activeOperation?.executionState == SetupOperationState.AWAITING_USER_ACTION ||
            activeOperation?.awaitingHandoff != null
        if (isAwaiting) {
            return SystemPackagesRowPresentation(
                detailText = "Waiting for you in the terminal",
                severity = IdeStatusSeverity.Warning,
                primaryActionLabel = "Continue setup",
            )
        }

        if (isChecking && (step == null || step.status == StepStatus.RUNNING)) {
            return SystemPackagesRowPresentation(
                detailText = "Checking…",
                severity = IdeStatusSeverity.Running,
                isChecking = true,
            )
        }

        if (step == null) {
            return SystemPackagesRowPresentation(
                detailText = "Pending verification.",
                severity = IdeStatusSeverity.Neutral,
            )
        }

        return when (step.status) {
            StepStatus.SUCCESS -> SystemPackagesRowPresentation(
                detailText = "All present",
                severity = IdeStatusSeverity.Ready,
                isReady = true,
            )
            StepStatus.RUNNING -> SystemPackagesRowPresentation(
                detailText = "Checking…",
                severity = IdeStatusSeverity.Running,
                isChecking = true,
            )
            StepStatus.WARNING -> SystemPackagesRowPresentation(
                detailText = step.description.ifBlank { "All present" },
                severity = IdeStatusSeverity.Warning,
                isReady = true,
            )
            StepStatus.FAILED -> {
                val desc = step.description
                val err = step.error.orEmpty()
                val isUnavailable = desc.contains("isn't available on Ubuntu") || err.contains("isn't available on Ubuntu")
                val isProbeFailed = desc.startsWith("Couldn't check packages:") || err.startsWith("Couldn't check packages:")
                val isPartial = desc.startsWith("Still missing:")

                when {
                    isUnavailable -> SystemPackagesRowPresentation(
                        detailText = desc,
                        severity = IdeStatusSeverity.Failed,
                        primaryActionLabel = null,
                    )
                    isProbeFailed -> SystemPackagesRowPresentation(
                        detailText = if (desc.startsWith("Couldn't check packages:")) desc else "Couldn't check packages: $err",
                        severity = IdeStatusSeverity.Failed,
                        primaryActionLabel = "Retry",
                    )
                    isPartial -> SystemPackagesRowPresentation(
                        detailText = desc,
                        severity = IdeStatusSeverity.Failed,
                        primaryActionLabel = "Install packages",
                    )
                    else -> SystemPackagesRowPresentation(
                        detailText = desc.ifBlank { "Packages missing" },
                        severity = IdeStatusSeverity.Failed,
                        primaryActionLabel = "Install packages",
                    )
                }
            }
            StepStatus.PENDING -> SystemPackagesRowPresentation(
                detailText = if (isChecking) "Checking…" else step.description.ifBlank { "Pending verification." },
                severity = if (isChecking) IdeStatusSeverity.Running else IdeStatusSeverity.Neutral,
                isChecking = isChecking,
            )
        }
    }
}
