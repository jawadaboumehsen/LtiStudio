/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.tools

import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.tools.GroupState
import org.ide.lti.core.domain.tools.GroupStatus
import org.ide.lti.core.domain.tools.OperationPhase
import org.ide.lti.core.domain.tools.SetupToolsSummary
import org.ide.lti.core.domain.tools.ToolError

public enum class ToolRowStatusKind {
    NotInstalled,
    Ready,
    NeedsRepair,
    Installing,
    Failed,
    Unknown,
    Interrupted,
}

public data class ToolRowPresentation(
    val groupId: String,
    val displayName: String,
    val inUseLabel: String,
    val statusLabel: String,
    val statusKind: ToolRowStatusKind,
    val phaseLabel: String? = null,
    val canCancel: Boolean = false,
    val actionLabel: String? = null,
)

public data class ToolsSummaryPresentation(
    val summaryText: String,
    val isComplete: Boolean,
    val hasFailures: Boolean,
    val canRetry: Boolean,
)

public data class ToolErrorPresentation(val message: String, val nextAction: String?)

public object ToolsPresentationMapper {
    @Suppress("CyclomaticComplexMethod")
    public fun mapRow(state: GroupState, isConnected: Boolean = true): ToolRowPresentation {
        val groupDef = ToolGroupCatalog.group(state.group)
        val displayName = groupDef?.id?.value ?: state.group.value
        val cur = state.current
        val inUse =
            when {
                cur == null -> "—"
                state.selection != null -> "${cur.id} (yours)"
                else -> cur.id
            }

        val op = state.operation
        val isOpRunning = op != null && op.result == null

        val statusInfo =
            when {
                !isConnected ->
                    RowStatusInfo("Unknown", ToolRowStatusKind.Unknown, null, false, "Reconnect")
                isOpRunning -> {
                    val phase =
                        when (op.phase) {
                            OperationPhase.Fetching -> "Fetching…"
                            OperationPhase.Building -> "Building…"
                            OperationPhase.Verifying -> "Verifying…"
                            OperationPhase.Publishing -> "Publishing…"
                            OperationPhase.Switching -> "Switching…"
                        }
                    RowStatusInfo("Installing", ToolRowStatusKind.Installing, phase, true, null)
                }
                state.interrupted != null || state.status == GroupStatus.Interrupted ->
                    RowStatusInfo("Interrupted", ToolRowStatusKind.Interrupted, null, false, "Retry")
                state.status == GroupStatus.Ready ->
                    RowStatusInfo("Ready", ToolRowStatusKind.Ready, null, false, null)
                state.status == GroupStatus.NeedsRepair ->
                    RowStatusInfo("Needs repair", ToolRowStatusKind.NeedsRepair, null, false, "Repair")
                state.status == GroupStatus.Failed ->
                    RowStatusInfo("Failed", ToolRowStatusKind.Failed, null, false, null)
                state.status == GroupStatus.NotInstalled ->
                    RowStatusInfo("Not installed", ToolRowStatusKind.NotInstalled, null, false, null)
                else ->
                    RowStatusInfo("Unknown", ToolRowStatusKind.Unknown, null, false, "Reconnect")
            }

        return ToolRowPresentation(
            groupId = state.group.value,
            displayName = displayName,
            inUseLabel = inUse,
            statusLabel = statusInfo.statusLabel,
            statusKind = statusInfo.statusKind,
            phaseLabel = statusInfo.phaseLabel,
            canCancel = statusInfo.canCancel,
            actionLabel = statusInfo.actionLabel,
        )
    }

    public fun mapSummary(summary: SetupToolsSummary): ToolsSummaryPresentation {
        val ready = summary.readyCount
        val failed = summary.failedCount
        val notAttempted = summary.notAttemptedCount

        val text =
            if (summary.isComplete) {
                "All $ready tools ready"
            } else {
                "$ready ready · $failed failed · $notAttempted not attempted"
            }

        return ToolsSummaryPresentation(
            summaryText = text,
            isComplete = summary.isComplete,
            hasFailures = failed > 0,
            canRetry = failed > 0 || notAttempted > 0,
        )
    }

    @Suppress("CyclomaticComplexMethod")
    public fun mapError(error: ToolError): ToolErrorPresentation = when (error) {
        is ToolError.MissingPackages ->
            ToolErrorPresentation(
                message =
                if (error.packages.isEmpty()) {
                    "Needs packages: …"
                } else {
                    "Needs packages: ${error.packages.joinToString(
                        ", ",
                    )}"
                },
                nextAction = "Show command",
            )
        is ToolError.Network ->
            ToolErrorPresentation(
                message = "Can't reach ${error.reason.ifBlank { "host" }} after 3 tries",
                nextAction = "Retry",
            )
        is ToolError.Timeout ->
            ToolErrorPresentation(
                message = "Took too long: ${error.detail}. Retry continues where it stopped",
                nextAction = "Retry",
            )
        is ToolError.RegistrationFailed ->
            ToolErrorPresentation(
                message = "Switched, but the build service doesn't list its tools: ${error.detail}",
                nextAction = "Reconnect",
            )
        is ToolError.RefNotFound ->
            ToolErrorPresentation(
                message = "Version ${error.ref} not found",
                nextAction = "Choose another",
            )
        is ToolError.UnsupportedLayout ->
            ToolErrorPresentation(
                message = "This repository isn't compatible: missing ${error.missingPaths.joinToString(", ")}",
                nextAction = "Choose another",
            )
        is ToolError.PatchFailed ->
            ToolErrorPresentation(
                message = "Vendor patches don't apply to this version",
                nextAction = "Use Studio's version",
            )
        is ToolError.BuildFailed ->
            ToolErrorPresentation(
                message = "Build failed at ${error.detail}",
                nextAction = "View log · Retry",
            )
        is ToolError.VerifyFailed ->
            ToolErrorPresentation(
                message = "${error.toolId} didn't run after building",
                nextAction = "View log · Retry",
            )
        is ToolError.ChecksumMismatch ->
            ToolErrorPresentation(
                message = "Download checksum doesn't match",
                nextAction = "Retry",
            )
        is ToolError.DiskFull ->
            ToolErrorPresentation(
                message = "Not enough disk space",
                nextAction = "Check again",
            )
        is ToolError.Busy ->
            ToolErrorPresentation(
                message = "Another change is running for this group",
                nextAction = "(wait)",
            )
        is ToolError.InUse ->
            ToolErrorPresentation(
                message = "Can't remove the version in use, or one a running job uses",
                nextAction = "—",
            )
        is ToolError.NotFound ->
            ToolErrorPresentation(
                message = "Unknown group or version",
                nextAction = "—",
            )
        is ToolError.ConnectionLost ->
            ToolErrorPresentation(
                message = "Lost connection to the build service",
                nextAction = "Reconnect",
            )
        is ToolError.AdmissionRequired ->
            ToolErrorPresentation(
                message = "Build service hasn't accepted this app yet",
                nextAction = "Reconnect",
            )
        is ToolError.Internal ->
            ToolErrorPresentation(
                message = "Unexpected result",
                nextAction = "View log",
            )
    }
}

private data class RowStatusInfo(
    val statusLabel: String,
    val statusKind: ToolRowStatusKind,
    val phaseLabel: String?,
    val canCancel: Boolean,
    val actionLabel: String? = null,
)
