/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.desktop

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.ide.lti.core.common.notification.NotificationLevel
import org.ide.lti.core.common.notification.SystemNotification
import org.ide.lti.core.common.notification.SystemNotifier
import org.ide.lti.core.domain.pipeline.PipelineStageSequence
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.run.StageState
import org.ide.lti.core.model.target.ConnectedTargetDevice
import java.util.UUID

/** Category of long-running or background activity surfaced in the session drawer. */
enum class BackgroundSessionKind(val label: String) {
    BUILD("Build"),
    EXTRACTION("Extract"),
    REPACK("Repack"),
    FIRMWARE_IMPORT("Import"),
    DEVICE_SYNC("Device"),
    PLUGIN_IMPORT("Mod/Plugin"),
    AUDIT("Audit"),
    PARTITION_INSPECT("Partition"),
}

/** Execution lifecycle state of a [BackgroundSessionItem]. */
enum class BackgroundSessionState {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    ;

    val isActive: Boolean
        get() = this == QUEUED || this == RUNNING
}

/** Navigation destination invoked when a user clicks a session or notification entry. */
sealed interface BackgroundSessionJumpTarget {
    data class WorkspaceStage(val workspaceId: String, val stageId: StageId? = null) : BackgroundSessionJumpTarget
    data class PartitionExplorer(val imagePath: String) : BackgroundSessionJumpTarget
    data object BuildRunPanel : BackgroundSessionJumpTarget
    data object DiffInspector : BackgroundSessionJumpTarget
    data object PluginManager : BackgroundSessionJumpTarget
    data object DevicesPanel : BackgroundSessionJumpTarget
}

/** Single tracked background task or pipeline job. */
data class BackgroundSessionItem(
    val id: String,
    val kind: BackgroundSessionKind,
    val title: String,
    val detail: String = "",
    val workspaceId: String? = null,
    val progress: Float? = null,
    val state: BackgroundSessionState = BackgroundSessionState.RUNNING,
    val startedAt: Instant,
    val updatedAt: Instant,
    val jumpTarget: BackgroundSessionJumpTarget? = null,
    val canCancel: Boolean = false,
)

/** Persistent notification entry paired with optional deep-link navigation. */
data class BackgroundAlertItem(
    val id: String,
    val title: String,
    val message: String,
    val isError: Boolean = false,
    val timestamp: Instant,
    val unread: Boolean = true,
    val jumpTarget: BackgroundSessionJumpTarget? = null,
)

/** Immutable snapshot observed by status-bar triggers and the persistent notification drawer. */
data class BackgroundSessionSnapshot(
    val sessions: List<BackgroundSessionItem> = emptyList(),
    val alerts: List<BackgroundAlertItem> = emptyList(),
    val activeCount: Int = 0,
    val unreadAlertCount: Int = 0,
)

/**
 * Coordinates JetBrains-Air-style persistent background tasks (long payload extractions, super.img
 * repacks, ROM builds, firmware imports, APK audits, and device reconnect alerts) while mirroring
 * milestone completion/failure events to the OS tray via [SystemNotifier].
 */
class BackgroundSessionManager(private val notifier: SystemNotifier? = null, private val clock: Clock = Clock.System) {
    private val _state = MutableStateFlow(BackgroundSessionSnapshot())
    val state: StateFlow<BackgroundSessionSnapshot> = _state.asStateFlow()

    private val _jumpRequests = MutableSharedFlow<BackgroundSessionJumpTarget>(extraBufferCapacity = 16)
    val jumpRequests: SharedFlow<BackgroundSessionJumpTarget> = _jumpRequests.asSharedFlow()

    private val cancelHandlers = mutableMapOf<String, () -> Unit>()
    private var lastKnownDevices: Map<String, String> = emptyMap()
    private var devicesInitialized = false
    private val notifiedRunStates = mutableMapOf<String, RunState>()

    fun startSession(
        id: String = "bg_${UUID.randomUUID()}",
        kind: BackgroundSessionKind,
        title: String,
        detail: String = "",
        workspaceId: String? = null,
        progress: Float? = null,
        jumpTarget: BackgroundSessionJumpTarget? = null,
        canCancel: Boolean = false,
        onCancel: (() -> Unit)? = null,
    ): String {
        val now = clock.now()
        if (onCancel != null) {
            cancelHandlers[id] = onCancel
        }
        val item = BackgroundSessionItem(
            id = id,
            kind = kind,
            title = title,
            detail = detail,
            workspaceId = workspaceId,
            progress = progress,
            state = BackgroundSessionState.RUNNING,
            startedAt = now,
            updatedAt = now,
            jumpTarget = jumpTarget,
            canCancel = canCancel || onCancel != null,
        )
        upsertSession(item)
        return id
    }

    fun updateProgress(
        id: String,
        progress: Float?,
        detail: String? = null,
        state: BackgroundSessionState = BackgroundSessionState.RUNNING,
    ) {
        val now = clock.now()
        _state.update { current ->
            val updated = current.sessions.map { existing ->
                if (existing.id == id) {
                    existing.copy(
                        progress = progress?.coerceIn(0f, 1f),
                        detail = detail ?: existing.detail,
                        state = state,
                        updatedAt = now,
                    )
                } else {
                    existing
                }
            }
            recomputeSnapshot(updated, current.alerts)
        }
    }

    fun completeSession(
        id: String,
        detail: String? = null,
        notifyOs: Boolean = true,
        jumpTarget: BackgroundSessionJumpTarget? = null,
    ) {
        val now = clock.now()
        cancelHandlers.remove(id)
        var finishedTitle: String? = null
        var resolvedTarget: BackgroundSessionJumpTarget? = jumpTarget
        _state.update { current ->
            val updated = current.sessions.map { existing ->
                if (existing.id == id) {
                    finishedTitle = existing.title
                    if (resolvedTarget == null) resolvedTarget = existing.jumpTarget
                    existing.copy(
                        progress = 1f,
                        detail = detail ?: existing.detail,
                        state = BackgroundSessionState.SUCCEEDED,
                        updatedAt = now,
                        canCancel = false,
                        jumpTarget = resolvedTarget,
                    )
                } else {
                    existing
                }
            }
            val newAlerts = if (finishedTitle != null) {
                val alert = BackgroundAlertItem(
                    id = "alert_${UUID.randomUUID()}",
                    title = "$finishedTitle completed",
                    message = detail ?: "Completed successfully.",
                    isError = false,
                    timestamp = now,
                    jumpTarget = resolvedTarget,
                )
                (listOf(alert) + current.alerts).take(MAX_ALERTS)
            } else {
                current.alerts
            }
            recomputeSnapshot(updated, newAlerts)
        }
        val title = finishedTitle
        if (notifyOs && title != null) {
            notifier?.notify(
                SystemNotification(
                    title = "$title finished",
                    message = detail ?: "Completed successfully.",
                    level = NotificationLevel.Info,
                ),
            )
        }
    }

    fun failSession(id: String, reason: String, notifyOs: Boolean = true) {
        val now = clock.now()
        cancelHandlers.remove(id)
        var failedTitle: String? = null
        var target: BackgroundSessionJumpTarget? = null
        _state.update { current ->
            val updated = current.sessions.map { existing ->
                if (existing.id == id) {
                    failedTitle = existing.title
                    target = existing.jumpTarget
                    existing.copy(
                        detail = reason,
                        state = BackgroundSessionState.FAILED,
                        updatedAt = now,
                        canCancel = false,
                    )
                } else {
                    existing
                }
            }
            val newAlerts = if (failedTitle != null) {
                val alert = BackgroundAlertItem(
                    id = "alert_${UUID.randomUUID()}",
                    title = "$failedTitle failed",
                    message = reason,
                    isError = true,
                    timestamp = now,
                    jumpTarget = target,
                )
                (listOf(alert) + current.alerts).take(MAX_ALERTS)
            } else {
                current.alerts
            }
            recomputeSnapshot(updated, newAlerts)
        }
        val title = failedTitle
        if (notifyOs && title != null) {
            notifier?.notify(
                SystemNotification(
                    title = "$title failed",
                    message = reason,
                    level = NotificationLevel.Error,
                ),
            )
        }
    }

    fun cancelSession(id: String) {
        val now = clock.now()
        cancelHandlers.remove(id)?.invoke()
        _state.update { current ->
            val updated = current.sessions.map { existing ->
                if (existing.id == id) {
                    existing.copy(
                        state = BackgroundSessionState.CANCELLED,
                        detail = "Cancelled by user",
                        updatedAt = now,
                        canCancel = false,
                    )
                } else {
                    existing
                }
            }
            recomputeSnapshot(updated, current.alerts)
        }
    }

    /** Reflects a workspace [BuildRun] in the background drawer and alerts on terminal transitions. */
    fun syncBuildRun(run: BuildRun) {
        val totalStages = PipelineStageSequence.ORDER.size.coerceAtLeast(1)
        val completedStages = run.stages.count {
            it.state == StageState.EXECUTED || it.state == StageState.SKIPPED
        }
        val runningStageId = run.stages.firstOrNull { it.state == StageState.RUNNING }?.stageId
        val failedStageMessage = run.stages.firstOrNull { it.state == StageState.FAILED }?.message
        val sessionId = "run_${run.id}"
        val detail = resolveRunDetail(run, runningStageId, failedStageMessage, completedStages, totalStages)
        val existing = _state.value.sessions.firstOrNull { it.id == sessionId }

        upsertSession(
            BackgroundSessionItem(
                id = sessionId,
                kind = resolveRunSessionKind(runningStageId),
                title = "Run ${run.id}",
                detail = detail,
                workspaceId = run.workspaceId,
                progress = resolveRunProgress(run.state, completedStages, totalStages, existing?.progress),
                state = toBackgroundSessionState(run.state),
                startedAt = run.startedAt,
                updatedAt = clock.now(),
                jumpTarget = runningStageId?.let { BackgroundSessionJumpTarget.WorkspaceStage(run.workspaceId, it) }
                    ?: BackgroundSessionJumpTarget.BuildRunPanel,
                canCancel = false,
            ),
        )

        val previous = notifiedRunStates.put(run.id, run.state)
        maybeNotifyTerminalRun(run, previous, detail)
    }

    private fun resolveRunSessionKind(runningStageId: StageId?): BackgroundSessionKind = when (runningStageId) {
        StageId.FIRMWARE_EXTRACTION -> BackgroundSessionKind.EXTRACTION
        StageId.BUILD_FLASHABLE_ZIP -> BackgroundSessionKind.REPACK
        else -> BackgroundSessionKind.BUILD
    }

    private fun resolveRunDetail(
        run: BuildRun,
        runningStageId: StageId?,
        failedStageMessage: String?,
        completedStages: Int,
        totalStages: Int,
    ): String = when (run.state) {
        RunState.QUEUED -> "Waiting for execution slot…"
        RunState.RUNNING -> runningStageId?.let {
            "Running ${PipelineStageSequence.label(it)} ($completedStages/$totalStages)"
        } ?: "Stage $completedStages/$totalStages"
        RunState.PAUSED_WAITING_FOR_APP -> "Paused awaiting user/app input"
        RunState.CANCELLING -> "Cancelling running stage…"
        RunState.SUCCEEDED -> "All $completedStages stages completed (${run.artifacts.size} artifacts)"
        RunState.FAILED -> failedStageMessage ?: "Pipeline stopped with an error"
        RunState.INTERRUPTED -> "Build interrupted before completion"
        RunState.CANCELLED -> "Build cancelled"
    }

    private fun toBackgroundSessionState(runState: RunState): BackgroundSessionState = when (runState) {
        RunState.QUEUED -> BackgroundSessionState.QUEUED
        RunState.RUNNING,
        RunState.PAUSED_WAITING_FOR_APP,
        RunState.CANCELLING,
        -> BackgroundSessionState.RUNNING
        RunState.SUCCEEDED -> BackgroundSessionState.SUCCEEDED
        RunState.FAILED,
        RunState.INTERRUPTED,
        -> BackgroundSessionState.FAILED
        RunState.CANCELLED -> BackgroundSessionState.CANCELLED
    }

    private fun resolveRunProgress(
        runState: RunState,
        completedStages: Int,
        totalStages: Int,
        existingProgress: Float?,
    ): Float? = when (runState) {
        RunState.SUCCEEDED -> 1f
        RunState.QUEUED -> 0f
        RunState.RUNNING -> (completedStages.toFloat() / totalStages.toFloat()).coerceIn(0f, 0.98f)
        else -> existingProgress
    }

    private fun maybeNotifyTerminalRun(run: BuildRun, previous: RunState?, detail: String) {
        if (previous == null || !previous.isActive || run.state.isActive) return
        val notification = when (run.state) {
            RunState.SUCCEEDED -> Triple("ROM build ${run.id} succeeded", false, true)
            RunState.FAILED,
            RunState.INTERRUPTED,
            -> Triple("ROM build ${run.id} failed", true, true)
            RunState.CANCELLED -> Triple("ROM build ${run.id} cancelled", false, false)
            else -> null
        } ?: return
        recordAlert(
            title = notification.first,
            message = detail,
            isError = notification.second,
            notifyOs = notification.third,
            jumpTarget = BackgroundSessionJumpTarget.BuildRunPanel,
        )
    }

    /** Records device connect/disconnect/mode transitions while the user works elsewhere. */
    fun syncConnectedDevices(devices: List<ConnectedTargetDevice>) {
        val currentMap = devices.associate { it.serial to "${it.modelName}:${it.state.name}:${it.connectionType.name}" }
        if (!devicesInitialized) {
            devicesInitialized = true
            lastKnownDevices = currentMap
            return
        }
        currentMap.forEach { (serial, signature) ->
            val oldSig = lastKnownDevices[serial]
            if (oldSig == null) {
                val dev = devices.first { it.serial == serial }
                recordAlert(
                    title = "Device connected: ${dev.shortDisplayName}",
                    message = "Serial $serial (${dev.stateBadge})",
                    notifyOs = true,
                    jumpTarget = BackgroundSessionJumpTarget.DevicesPanel,
                )
            } else if (oldSig != signature) {
                val dev = devices.first { it.serial == serial }
                recordAlert(
                    title = "Device state changed: ${dev.shortDisplayName}",
                    message = "Serial $serial -> ${dev.stateBadge}",
                    notifyOs = false,
                    jumpTarget = BackgroundSessionJumpTarget.DevicesPanel,
                )
            }
        }
        lastKnownDevices.keys
            .filterNot { it in currentMap }
            .forEach { removed ->
                recordAlert(
                    title = "Device disconnected",
                    message = "Serial $removed went offline.",
                    notifyOs = false,
                    jumpTarget = BackgroundSessionJumpTarget.DevicesPanel,
                )
            }
        lastKnownDevices = currentMap
    }

    fun recordAlert(
        title: String,
        message: String,
        isError: Boolean = false,
        notifyOs: Boolean = false,
        jumpTarget: BackgroundSessionJumpTarget? = null,
    ) {
        val alert = BackgroundAlertItem(
            id = "alert_${UUID.randomUUID()}",
            title = title,
            message = message,
            isError = isError,
            timestamp = clock.now(),
            unread = true,
            jumpTarget = jumpTarget,
        )
        _state.update { current ->
            recomputeSnapshot(current.sessions, (listOf(alert) + current.alerts).take(MAX_ALERTS))
        }
        if (notifyOs) {
            notifier?.notify(
                SystemNotification(
                    title = title,
                    message = message,
                    level = if (isError) NotificationLevel.Error else NotificationLevel.Info,
                ),
            )
        }
    }

    fun requestJump(target: BackgroundSessionJumpTarget) {
        _jumpRequests.tryEmit(target)
    }

    fun markAllAlertsRead() {
        _state.update { current ->
            val read = current.alerts.map { it.copy(unread = false) }
            recomputeSnapshot(current.sessions, read)
        }
    }

    fun clearCompleted() {
        _state.update { current ->
            val remaining = current.sessions.filter { it.state.isActive }
            recomputeSnapshot(remaining, current.alerts)
        }
    }

    fun dismissAlert(id: String) {
        _state.update { current ->
            recomputeSnapshot(current.sessions, current.alerts.filterNot { it.id == id })
        }
    }

    fun dismissSession(id: String) {
        cancelHandlers.remove(id)
        _state.update { current ->
            recomputeSnapshot(current.sessions.filterNot { it.id == id }, current.alerts)
        }
    }

    private fun upsertSession(item: BackgroundSessionItem) {
        _state.update { current ->
            val filtered = current.sessions.filterNot { it.id == item.id }
            val ordered = (listOf(item) + filtered)
                .sortedWith(
                    compareByDescending<BackgroundSessionItem> { it.state.isActive }
                        .thenByDescending { it.updatedAt },
                )
                .take(MAX_SESSIONS)
            recomputeSnapshot(ordered, current.alerts)
        }
    }

    private fun recomputeSnapshot(
        sessions: List<BackgroundSessionItem>,
        alerts: List<BackgroundAlertItem>,
    ): BackgroundSessionSnapshot = BackgroundSessionSnapshot(
        sessions = sessions,
        alerts = alerts,
        activeCount = sessions.count { it.state.isActive },
        unreadAlertCount = alerts.count { it.unread },
    )

    private companion object {
        const val MAX_SESSIONS = 24
        const val MAX_ALERTS = 40
    }
}
