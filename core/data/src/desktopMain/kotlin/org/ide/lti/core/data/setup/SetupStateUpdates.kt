/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup

import org.ide.lti.core.domain.ports.DaemonFailureCategory
import org.ide.lti.core.domain.ports.ToolchainFailureCategory
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.model.setup.ToolchainPersistenceState

/*
 * The setup screen's state, changed only through these pure functions: what the provisioner does to it (a step
 * runs, progresses, ends, or stops the ones after it; a log line arrives; the journal changes) is each one call,
 * and each can be checked without WSL.
 */

/**
 * The state the journal left from an earlier session: the open attempt or recovery block first (the truth about
 * server work that may still run), then each verified stage and compiled tool as restored evidence, never as
 * success: a restored stage is pending until it is checked again.
 */
internal fun restoredSetupState(
    persisted: ToolchainPersistenceState?,
    now: Long = System.currentTimeMillis(),
): ToolchainSetupState {
    val journal = persisted?.let { ToolchainSetupState().withJournal(it) } ?: return ToolchainSetupState()
    val nothingVerified =
        !persisted.isSetupCompleted && persisted.completedStages.isEmpty() && persisted.tools.isEmpty()
    return if (nothingVerified) journal else journal.withEvidence(persisted, now)
}

/** The journal's verified stages and compiled tools, restored as evidence to check again. */
private fun ToolchainSetupState.withEvidence(persisted: ToolchainPersistenceState, now: Long): ToolchainSetupState {
    val lastVerified = persisted.lastVerifiedTimestamp
    val restoredSteps = ToolchainSetupState.defaultSteps().map { step ->
        if (persisted.isStageCompleted(step.stage.name) || persisted.isSetupCompleted) {
            step.copy(
                status = StepStatus.PENDING,
                provenance = StepProvenance.RESTORED,
                verifiedAtEpochMs = lastVerified,
                description = "Last verified ${formatAgo(lastVerified, now)} — re-checking…",
            )
        } else {
            step.copy(provenance = StepProvenance.RESTORED, verifiedAtEpochMs = lastVerified)
        }
    }
    val restoredTools = ToolchainSetupState.defaultToolsMatrix().map { tool ->
        val pTool = persisted.tools[tool.id]
        if (pTool != null && pTool.isCompiled) {
            // Installed is a persisted fact; "tested" only restores from a real verification timestamp, never from
            // the binary's mere existence (FR-010).
            tool.copy(
                status = StepStatus.SUCCESS,
                path = pTool.binaryPath,
                version = pTool.version,
                lastTested = pTool.lastVerifiedTimestamp?.takeIf { pTool.isVerified }?.let { "Restored" },
            )
        } else {
            tool
        }
    }
    return copy(
        steps = restoredSteps,
        toolsMatrix = restoredTools,
        isAvbKeyProvisioned = persisted.isAvbKeyProvisioned,
        avbKeyPath = persisted.avbKeyPath,
        activeDistro = persisted.activeDistro,
        lastVerifiedTimestamp = persisted.lastVerifiedTimestamp,
        checkedAt = null,
        lastReadyAt = persisted.lastVerifiedTimestamp,
    )
}

/** The journal's active attempt and recovery block, as the screen shows them. */
internal fun ToolchainSetupState.withJournal(persisted: ToolchainPersistenceState): ToolchainSetupState = copy(
    pendingAttemptId = persisted.activeAttempt?.attemptId,
    pendingAttempt = persisted.activeAttempt,
    recoveryBlockReason = persisted.recoveryBlockReason?.takeIf { persisted.recoveryBlocked },
)

/** [stage] starts (its start time kept when it already had one) and becomes the current stage. */
internal fun ToolchainSetupState.withStepRunning(stage: SetupStepStage, now: Long): ToolchainSetupState = copy(
    steps = steps.map { step ->
        if (step.stage != stage) {
            step
        } else {
            step.copy(status = StepStatus.RUNNING, provenance = StepProvenance.LIVE, startedAt = step.startedAt ?: now)
        }
    },
    currentStage = stage,
)

/** [stage] is at [currentItem], [index] of [total]: its description and the active line say so. */
internal fun ToolchainSetupState.withStepProgress(
    stage: SetupStepStage,
    currentItem: String,
    index: Int,
    total: Int,
    now: Long,
): ToolchainSetupState {
    val desc = when (stage) {
        SetupStepStage.TOOLCHAIN_COMPILATION -> "Building $currentItem ($index of $total)"
        else -> "$currentItem ($index of $total)"
    }
    return copy(
        steps = steps.map { step ->
            if (step.stage == stage) step.copy(description = desc, startedAt = step.startedAt ?: now) else step
        },
        activeLogLine = desc,
    )
}

/** [failedStage] stopped the check: every stage after it goes back to pending with [reason]; nothing is running. */
internal fun ToolchainSetupState.withRemainingPending(
    failedStage: SetupStepStage,
    reason: String,
    now: Long,
): ToolchainSetupState {
    val failedAt = steps.indexOfFirst { it.stage == failedStage }
    return copy(
        steps = steps.mapIndexed { i, step ->
            if (failedAt < 0 || i <= failedAt) {
                step
            } else {
                step.copy(
                    status = StepStatus.PENDING,
                    description = reason,
                    provenance = StepProvenance.LIVE,
                    error = null,
                )
            }
        },
        currentStage = null,
        isChecking = false,
        checkedAt = now,
    )
}

/** [stage] has [status] (a running one becomes the current stage); its description stays unless [desc] is given. */
@Suppress("LongParameterList") // the step's own fields, each optional
internal fun ToolchainSetupState.withStepStatus(
    stage: SetupStepStage,
    status: StepStatus,
    desc: String? = null,
    error: String? = null,
    failureCategory: DaemonFailureCategory? = null,
    toolchainFailureCategory: ToolchainFailureCategory? = null,
): ToolchainSetupState = copy(
    steps = steps.map { step ->
        if (step.stage != stage) {
            step
        } else {
            step.copy(
                status = status,
                description = desc ?: step.description,
                error = error,
                provenance = StepProvenance.LIVE,
                failureCategory = failureCategory,
                toolchainFailureCategory = toolchainFailureCategory,
            )
        }
    },
    currentStage = if (status == StepStatus.RUNNING) stage else null,
)

/** Tool [toolId]'s row has [status]; nothing else of it changes. */
internal fun ToolchainSetupState.withToolStatus(toolId: String, status: StepStatus): ToolchainSetupState =
    copy(toolsMatrix = toolsMatrix.map { if (it.id == toolId) it.copy(status = status) else it })

/** Tool [toolId]'s probe result, exactly: a [path] or [lastTested] of null clears what an earlier probe left. */
internal fun ToolchainSetupState.withToolResult(
    toolId: String,
    status: StepStatus,
    path: String?,
    lastTested: String?,
): ToolchainSetupState = copy(
    toolsMatrix = toolsMatrix.map {
        if (it.id == toolId) it.copy(status = status, path = path, lastTested = lastTested) else it
    },
)

/** What the service's tool list says next to what the product needs (see the provisioner's registry check). */
internal data class ToolRegistryCheck(val published: Set<String>, val unpublished: Set<String>, val fullyReady: Boolean)

/**
 * A check finished at [now] in [home]: its storage reading (kept from before when this one could not measure), the
 * tool list, and ready-since only when [registry] says everything is ready.
 */
internal fun ToolchainSetupState.withCheckFinished(
    home: String,
    storage: Pair<Long?, Long?>,
    registry: ToolRegistryCheck,
    now: Long,
): ToolchainSetupState = copy(
    storageAvailableBytes = storage.first ?: storageAvailableBytes,
    storageMeasuredAt = storage.second ?: storageMeasuredAt,
    userHome = home,
    workDirLinuxPath = "$home/LtiRomWorkDir",
    lastVerifiedTimestamp = if (registry.fullyReady) now else lastVerifiedTimestamp,
    checkedAt = now,
    lastReadyAt = if (registry.fullyReady) now else null,
    publishedToolIds = registry.published,
    unpublishedToolIds = registry.unpublished,
)

/** [line] is the newest log line: the state keeps the last [tail] of them. */
internal fun ToolchainSetupState.withLogLine(line: String, tail: Int): ToolchainSetupState =
    copy(logs = (logs + line).takeLast(tail), activeLogLine = line)

/** "just now", "5 min ago", "3 h ago", "2 d ago"; "previously" when the time is unknown. */
internal fun formatAgo(epochMs: Long?, now: Long): String {
    if (epochMs == null || epochMs <= 0L) return "previously"
    val minutes = (now - epochMs) / MINUTE_MS
    val hours = minutes / MINUTES_PER_HOUR
    return when {
        now - epochMs < MINUTE_MS -> "just now"
        minutes < MINUTES_PER_HOUR -> "$minutes min ago"
        hours < HOURS_PER_DAY -> "$hours h ago"
        else -> "${hours / HOURS_PER_DAY} d ago"
    }
}

private const val MINUTE_MS = 60_000L
private const val MINUTES_PER_HOUR = 60L
private const val HOURS_PER_DAY = 24L
