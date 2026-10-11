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
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.model.setup.DistroStatus
import org.ide.lti.core.model.setup.closestToUsable
import org.ide.lti.core.model.setup.distroName

/** What a row button does; the tracker dispatches on this, never on the button text. */
enum class SetupRowAction {
    RETRY,
    INSTALL_PACKAGES,
    CONTINUE_SETUP,
    OPEN_DISTRO,
    SHOW_DIAGNOSTICS,
    SHOW_LOG,
    CANCEL,
    INSTALL_UBUNTU,
    USE_DISTRO,
    CHECK_AGAIN,
}

fun actionLabel(action: SetupRowAction, distro: String? = null): String = when (action) {
    SetupRowAction.RETRY -> "Retry"
    SetupRowAction.INSTALL_PACKAGES -> "Install packages"
    SetupRowAction.CONTINUE_SETUP -> "Continue setup"
    SetupRowAction.OPEN_DISTRO -> "Open ${distro ?: "Ubuntu"}"
    SetupRowAction.SHOW_DIAGNOSTICS -> "Show diagnostics"
    SetupRowAction.SHOW_LOG -> "Show log"
    SetupRowAction.CANCEL -> "Cancel"
    SetupRowAction.INSTALL_UBUNTU -> "Install Ubuntu"
    SetupRowAction.USE_DISTRO -> "Use ${distro ?: "Ubuntu"}"
    SetupRowAction.CHECK_AGAIN -> "Check again"
}

@Immutable
data class WslRowPresentation(
    val title: String = "WSL 2 Runtime",
    val detailText: String,
    val severity: IdeStatusSeverity,
    val primaryAction: SetupRowAction? = null,
    val secondaryAction: SetupRowAction? = null,
    val isReady: Boolean = false,
    val instruction: String? = null,
    /** Distro the actions target (e.g. the one to open so the user can create an account). */
    val distro: String? = null,
    /** Usable distros to pick from when WSL has no default among several. */
    val choices: List<String> = emptyList(),
) {
    val primaryActionLabel: String? get() = primaryAction?.let { actionLabel(it, distro) }
    val secondaryActionLabel: String? get() = secondaryAction?.let { actionLabel(it, distro) }
}

@Immutable
data class SystemPackagesRowPresentation(
    val title: String = "System packages",
    val detailText: String,
    val severity: IdeStatusSeverity,
    val primaryAction: SetupRowAction? = null,
    val isReady: Boolean = false,
    val isChecking: Boolean = false,
) {
    val primaryActionLabel: String? get() = primaryAction?.let { actionLabel(it) }
}

@Immutable
data class ToolchainRowPresentation(
    val title: String = "Toolchain Binaries",
    val detailText: String,
    val severity: IdeStatusSeverity,
    val primaryAction: SetupRowAction? = null,
    val primaryActionLabel: String = "Retry failed",
    val secondaryAction: SetupRowAction? = null,
    val secondaryActionLabel: String? = null,
    val errorText: String? = null,
    val isRunning: Boolean = false,
    val isReady: Boolean = false,
)

/**
 * Maps the "This machine" rows that carry their own actions (WSL 2 Runtime, System packages).
 * Row texts follow `contracts/ui-states.md` §2.
 */
object MachineRowMapper {
    private const val PROBE_FAILED = "Couldn't check packages:"
    private const val UNAVAILABLE = "isn't available on Ubuntu"

    fun mapDistroStatus(status: DistroStatus, allStatuses: List<DistroStatus> = emptyList()): WslRowPresentation =
        when (status) {
            is DistroStatus.WslUnavailable ->
                WslRowPresentation(
                    detailText = "WSL isn't installed. Run 'wsl --install' in PowerShell.",
                    severity = IdeStatusSeverity.Failed,
                    instruction = "Run 'wsl --install' in PowerShell.",
                    primaryAction = null,
                    secondaryAction = SetupRowAction.CHECK_AGAIN,
                )
            is DistroStatus.NoDistro ->
                WslRowPresentation(
                    detailText = "No Linux distribution. Run 'wsl --install -d Ubuntu-24.04' in PowerShell.",
                    severity = IdeStatusSeverity.Failed,
                    instruction = "Run 'wsl --install -d Ubuntu-24.04' in PowerShell.",
                    primaryAction = SetupRowAction.INSTALL_UBUNTU,
                    secondaryAction = SetupRowAction.CHECK_AGAIN,
                )
            is DistroStatus.StartupFailed ->
                WslRowPresentation(
                    detailText = "${status.distro} didn't start: ${status.detail}",
                    severity = IdeStatusSeverity.Failed,
                    primaryAction = null,
                    secondaryAction = SetupRowAction.CHECK_AGAIN,
                    distro = status.distro,
                )
            is DistroStatus.Unsupported -> {
                val usable = allStatuses.filterIsInstance<DistroStatus.Usable>()
                val hasUsable = usable.isNotEmpty()
                WslRowPresentation(
                    detailText = "${status.distro} isn't a supported Ubuntu LTS (found ${status.reason})",
                    severity = IdeStatusSeverity.Failed,
                    primaryAction = if (hasUsable) SetupRowAction.USE_DISTRO else SetupRowAction.INSTALL_UBUNTU,
                    secondaryAction = SetupRowAction.CHECK_AGAIN,
                    distro = if (hasUsable) usable.first().environment.distro else status.distro,
                )
            }
            is DistroStatus.NoUsableUser ->
                WslRowPresentation(
                    detailText = "${status.distro} needs a user",
                    severity = IdeStatusSeverity.Failed,
                    primaryAction = SetupRowAction.OPEN_DISTRO,
                    secondaryAction = SetupRowAction.CHECK_AGAIN,
                    distro = status.distro,
                )
            is DistroStatus.Usable ->
                WslRowPresentation(
                    detailText = "${status.environment.distro} · ${status.environment.user}",
                    severity = IdeStatusSeverity.Ready,
                    isReady = true,
                    distro = status.environment.distro,
                )
        }

    /**
     * The WSL row from the last detection, or null when there is none to show (the generic step row
     * is used then). Several usable distros with no WSL default become a picker; otherwise the
     * status closest to usable explains the failure.
     */
    fun mapWslRow(step: SetupStepDetail, statuses: List<DistroStatus>, activeDistro: String?): WslRowPresentation? {
        val usable = statuses.filterIsInstance<DistroStatus.Usable>()
        val active = activeDistro?.let { name -> statuses.firstOrNull { it.distroName == name } }
        return when {
            statuses.isEmpty() || step.status == StepStatus.RUNNING || step.status == StepStatus.PENDING -> null
            step.status == StepStatus.SUCCESS ->
                usable.firstOrNull { it.environment.distro == activeDistro }?.let { mapDistroStatus(it, statuses) }
            usable.size > 1 && activeDistro == null ->
                WslRowPresentation(
                    detailText = "Several Ubuntu distributions are usable. Choose one for LtiRom.",
                    severity = IdeStatusSeverity.Warning,
                    choices = usable.map { it.environment.distro },
                )
            active != null -> mapDistroStatus(active, statuses)
            else ->
                statuses.filter { it !is DistroStatus.Usable }.closestToUsable()?.let { mapDistroStatus(it, statuses) }
                    ?: statuses.closestToUsable()?.let { mapDistroStatus(it, statuses) }
        }
    }

    fun mapSystemPackages(
        step: SetupStepDetail?,
        isChecking: Boolean = false,
        activeOperation: SetupOperation? = null,
        handoffSheetState: TerminalHandoffState = TerminalHandoffState.Idle,
    ): SystemPackagesRowPresentation {
        val isAwaiting =
            activeOperation?.executionState == SetupOperationState.AWAITING_USER_ACTION ||
                activeOperation?.awaitingHandoff != null
        return when {
            handoffSheetState is TerminalHandoffState.Verifying -> checking("Checking installation…")
            handoffSheetState is TerminalHandoffState.StillMissing ->
                SystemPackagesRowPresentation(
                    detailText = "Still missing: ${handoffSheetState.packages.joinToString(", ")}",
                    severity = IdeStatusSeverity.Failed,
                    primaryAction = SetupRowAction.INSTALL_PACKAGES,
                )
            isAwaiting ->
                SystemPackagesRowPresentation(
                    detailText = "Waiting for you in the terminal",
                    severity = IdeStatusSeverity.Warning,
                    primaryAction = SetupRowAction.CONTINUE_SETUP,
                )
            isChecking && (step == null || step.status == StepStatus.RUNNING) -> checking("Checking…")
            step == null ->
                SystemPackagesRowPresentation(
                    detailText = "Pending verification.",
                    severity = IdeStatusSeverity.Neutral,
                )
            else -> mapPackagesStep(step, isChecking)
        }
    }

    private fun mapPackagesStep(step: SetupStepDetail, isChecking: Boolean): SystemPackagesRowPresentation =
        when (step.status) {
            StepStatus.SUCCESS ->
                SystemPackagesRowPresentation(
                    detailText = "All present",
                    severity = IdeStatusSeverity.Ready,
                    isReady = true,
                )
            StepStatus.RUNNING -> checking("Checking…")
            StepStatus.WARNING ->
                SystemPackagesRowPresentation(
                    detailText = step.description.ifBlank { "All present" },
                    severity = IdeStatusSeverity.Warning,
                    isReady = true,
                )
            StepStatus.FAILED -> mapPackagesFailure(step.description, step.error.orEmpty())
            StepStatus.PENDING ->
                SystemPackagesRowPresentation(
                    detailText = if (isChecking) "Checking…" else step.description.ifBlank { "Pending verification." },
                    severity = if (isChecking) IdeStatusSeverity.Running else IdeStatusSeverity.Neutral,
                    isChecking = isChecking,
                )
        }

    /** Failure texts written by the provisioner's SYSTEM_PACKAGES step decide which action applies. */
    private fun mapPackagesFailure(desc: String, err: String): SystemPackagesRowPresentation = when {
        UNAVAILABLE in desc || UNAVAILABLE in err ->
            SystemPackagesRowPresentation(
                detailText = desc,
                severity = IdeStatusSeverity.Failed,
            )
        desc.startsWith(PROBE_FAILED) || err.startsWith(PROBE_FAILED) ->
            SystemPackagesRowPresentation(
                detailText = if (desc.startsWith(PROBE_FAILED)) desc else "$PROBE_FAILED $err",
                severity = IdeStatusSeverity.Failed,
                primaryAction = SetupRowAction.RETRY,
            )
        else ->
            SystemPackagesRowPresentation(
                detailText = desc.ifBlank { "Packages missing" },
                severity = IdeStatusSeverity.Failed,
                primaryAction = SetupRowAction.INSTALL_PACKAGES,
            )
    }

    private fun checking(text: String) = SystemPackagesRowPresentation(
        detailText = text,
        severity = IdeStatusSeverity.Running,
        isChecking = true,
    )

    fun mapBuildService(
        step: SetupStepDetail?,
        daemonPingMs: Long? = null,
        isChecking: Boolean = false,
        systemPackagesStep: SetupStepDetail? = null,
    ): BuildServicePresentation = SetupPresentationMapper.mapBuildService(
        step = step,
        daemonPingMs = daemonPingMs,
        isChecking = isChecking,
        systemPackagesStep = systemPackagesStep,
    )

    /**
     * [canCancel]: an owned, cancellable operation (a build) is running. A check also shows the step RUNNING but
     * has no operation to cancel, so it gets no Cancel button.
     */
    fun mapToolchainRow(
        step: SetupStepDetail?,
        nowEpochMs: Long? = null,
        canCancel: Boolean = false,
    ): ToolchainRowPresentation? {
        if (step == null) return null
        return when (step.status) {
            StepStatus.SUCCESS ->
                ToolchainRowPresentation(
                    detailText = step.description.ifBlank { "All tools ready" },
                    severity = IdeStatusSeverity.Ready,
                    isReady = true,
                )
            StepStatus.RUNNING -> {
                val desc = step.description.ifBlank { "Compiling…" }
                val detail =
                    if (nowEpochMs != null && step.startedAt != null) {
                        SetupPresentationMapper.formatProgressDetail(desc, step.startedAt, nowEpochMs)
                    } else {
                        desc
                    }
                ToolchainRowPresentation(
                    detailText = detail,
                    severity = IdeStatusSeverity.Running,
                    primaryAction = if (canCancel) SetupRowAction.CANCEL else null,
                    primaryActionLabel = "Cancel",
                    isRunning = true,
                )
            }
            StepStatus.FAILED ->
                ToolchainRowPresentation(
                    detailText = step.description.ifBlank { "Tool compilation failed" },
                    severity = IdeStatusSeverity.Failed,
                    primaryAction = SetupRowAction.RETRY,
                    primaryActionLabel = "Retry failed",
                    secondaryAction = SetupRowAction.SHOW_LOG,
                    secondaryActionLabel = "View log",
                    errorText = step.error,
                )
            StepStatus.WARNING ->
                ToolchainRowPresentation(
                    detailText = step.description.ifBlank { "Some tools require attention" },
                    severity = IdeStatusSeverity.Warning,
                    primaryAction = SetupRowAction.RETRY,
                    primaryActionLabel = "Retry failed",
                    secondaryAction = SetupRowAction.SHOW_LOG,
                    secondaryActionLabel = "View log",
                    errorText = step.error,
                )
            StepStatus.PENDING ->
                ToolchainRowPresentation(
                    detailText = step.description.ifBlank { "Waiting for prerequisites" },
                    severity = IdeStatusSeverity.Neutral,
                )
        }
    }
}
