/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.workspace

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.repository.snapshot.SnapshotRepository
import org.ide.lti.core.domain.repository.target.WorkspaceDeviceRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.domain.usecase.workspace.SaveConfigurationSnapshotUseCase
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.ConfigurationSnapshot

/** What a stage editor needs to load and save its part of the workspace configuration. */
public interface WorkspaceSettingsStore {
    public val pendingEdits: PendingWorkspaceEdits? get() = null

    public suspend fun flush(workspaceId: String): Result<Unit> =
        pendingEdits?.flush(workspaceId) ?: Result.success(Unit)

    public suspend fun current(workspaceId: String): ConfigurationSnapshot?

    public suspend fun update(
        workspaceId: String,
        change: (ConfigurationSnapshot) -> ConfigurationSnapshot,
    ): Result<ConfigurationSnapshot>

    public suspend fun device(workspaceId: String): TargetDevice?

    /** The id of each workspace whose settings were just saved. */
    public val changes: Flow<String> get() = emptyFlow()

    /** The workspace's device now, then as it is saved (edited in the Target panel while an editor stays open). */
    public fun deviceUpdates(workspaceId: String): Flow<TargetDevice?> = flow { emit(device(workspaceId)) }
}

/** The workspace's saved snapshot, changed through [SaveConfigurationSnapshotUseCase.update]. */
public class SnapshotSettingsStore(
    private val workspaces: WorkspaceRepository,
    private val snapshots: SnapshotRepository,
    private val save: SaveConfigurationSnapshotUseCase,
    private val devices: WorkspaceDeviceRepository,
) : WorkspaceSettingsStore {
    override val pendingEdits: PendingWorkspaceEdits = PendingWorkspaceEdits(::update)

    override suspend fun current(workspaceId: String): ConfigurationSnapshot? =
        workspaces.getWorkspace(workspaceId)?.effectiveSnapshotId?.let { snapshots.getSnapshot(it) }

    override suspend fun update(
        workspaceId: String,
        change: (ConfigurationSnapshot) -> ConfigurationSnapshot,
    ): Result<ConfigurationSnapshot> = save.update(workspaceId, change).onSuccess {
        revisions.update { it + (workspaceId to (it[workspaceId] ?: 0L) + 1) }
    }

    /** Saves per workspace. A slow listener misses intermediate saves, never a workspace: it sees every changed id. */
    private val revisions = MutableStateFlow<Map<String, Long>>(emptyMap())
    override val changes: Flow<String> = flow {
        var seen = revisions.value
        revisions.collect { now ->
            now.filter { (id, revision) -> seen[id] != revision }.keys.forEach { emit(it) }
            seen = now
        }
    }

    override suspend fun device(workspaceId: String): TargetDevice? =
        runCatching { devices.get(workspaceId) }.getOrNull()

    // An unreadable device (service down) is not an update: the editor keeps the device it has and reads again later.
    override fun deviceUpdates(workspaceId: String): Flow<TargetDevice?> = devices.observe(workspaceId)
        .retryWhen { cause, _ -> (cause !is CancellationException).also { if (it) delay(DEVICE_RETRY_MS) } }

    private companion object {
        const val DEVICE_RETRY_MS = 5_000L
    }
}

/**
 * One stage editor's settings group, kept in step with the workspace configuration: loaded from the saved
 * snapshot when the editor opens, and saved back (only this group) shortly after each edit. Without a [store]
 * or a workspace id the settings stay local, as before.
 */
public class StageSettingsSync<T>(
    private val scope: CoroutineScope,
    private val workspaceId: String,
    private val store: WorkspaceSettingsStore?,
    initial: T,
    private val read: (ConfigurationSnapshot) -> T,
    private val write: (ConfigurationSnapshot, T) -> ConfigurationSnapshot,
) {
    private val _settings = MutableStateFlow(initial)
    public val settings: StateFlow<T> = _settings.asStateFlow()

    private val _device = MutableStateFlow<TargetDevice?>(null)

    /** The workspace's own device, once loaded; null before or when it has none. */
    public val device: StateFlow<TargetDevice?> = _device.asStateFlow()

    private val _saveError = MutableStateFlow<String?>(null)

    /** Why the last save failed (for example a build is running), or null. */
    public val saveError: StateFlow<String?> = _saveError.asStateFlow()

    private var pendingSave: Job? = null
    private val connected = store != null && workspaceId.isNotBlank()

    // Each stage uses a distinct settings type. Reopening it replaces, rather than competes with, an old draft.
    private val owner = workspaceId to initial?.let { it::class }
    private var revision = 0L
    private var loadGeneration = 0L
    private val edits = store?.pendingEdits ?: PendingWorkspaceEdits { id, change -> store!!.update(id, change) }

    init {
        if (connected) {
            scope.launch { reload() }
            // The device as it is now, then each save while this editor is open: one source, so no stale read wins.
            scope.launch { store!!.deviceUpdates(workspaceId).collect { _device.value = it } }
            scope.launch {
                // Show other writers' saves, retaining this group's pending value while it is being edited.
                store!!.changes.filter { it == workspaceId }.collect {
                    reload()
                }
            }
        }
    }

    public fun update(value: T) {
        revision++
        _settings.value = value
        if (!connected) return
        edits.enqueue(owner, workspaceId) { write(it, value) }
        pendingSave?.cancel()
        pendingSave = scope.launch {
            delay(SAVE_DELAY_MS) // typing in a field saves once it pauses, not on every key
            // Further typing cancels only a waiting debounce, never a commit already in progress.
            pendingSave = null
            val result = edits.save(owner)
            _saveError.value = result.exceptionOrNull()?.let { it.message ?: "Could not save" }
        }
    }

    private suspend fun reload() {
        val requestedAt = revision
        val generation = ++loadGeneration
        val snapshot = store?.current(workspaceId)
        if (snapshot != null && revision == requestedAt && generation == loadGeneration) {
            _settings.value = read(edits.applyTo(owner, snapshot))
        }
    }

    private companion object {
        const val SAVE_DELAY_MS = 600L
    }
}
