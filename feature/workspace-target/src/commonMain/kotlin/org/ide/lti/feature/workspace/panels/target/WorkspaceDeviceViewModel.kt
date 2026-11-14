/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.panels.target

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.pipeline.configuration.DeviceConfigReadiness
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.domain.repository.snapshot.SnapshotRepository
import org.ide.lti.core.domain.repository.target.TargetRepository
import org.ide.lti.core.domain.repository.target.WorkspaceDeviceRepository
import org.ide.lti.core.domain.usecase.build.ObserveRunUseCase
import org.ide.lti.core.domain.workbench.DeviceFirmwareCheck
import org.ide.lti.core.domain.workbench.FirmwareFact
import org.ide.lti.core.domain.workbench.FirmwareFacts
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.target.TargetBinding
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.Workspace

data class WorkspaceDeviceUiState(
    val workspaceId: String? = null,
    val saved: TargetDevice? = null,
    val draft: TargetDevice? = null,
    /** Blocking problems with [draft]; empty means a build may start with this device configuration. */
    val issues: List<ValidationError> = emptyList(),
    val runActive: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null,
    /** Why the device file of the workspace could not be read; never treated as "no device". */
    val loadError: String? = null,
    /** Catalog templates a workspace without a device can start from (copied, never linked). */
    val templates: List<TargetDevice> = emptyList(),
    /** Bumped whenever the draft is replaced from outside (load, discard, save) so text fields resync. */
    val resetCount: Int = 0,
    /** What the workspace's extracted firmware says, once checked; compared with [draft] as it is edited. */
    val firmware: FirmwareFacts? = null,
    val firmwareChecking: Boolean = false,
    val firmwareError: String? = null,
    /** The device file is saved but the workspace did not record its revision: Save tries that again. */
    val revisionUnbound: Boolean = false,
) {
    /** [draft] next to the firmware's facts (empty before a check). */
    val firmwareFacts: List<FirmwareFact>
        get() = draft?.let { d -> firmware?.let { DeviceFirmwareCheck.compare(d, it) } }.orEmpty()

    val isDirty: Boolean get() = draft != null && draft != saved

    val canSave: Boolean get() = (isDirty || revisionUnbound) && !locked && !isSaving

    /** The device of a workspace with a running build is read-only: edits apply to the next run. */
    val locked: Boolean get() = runActive

    fun issuesFor(field: String): List<ValidationError> = issues.filter { it.fieldPath == field }

    /** Problems this section can fix; the others (firmware, signing) belong to their pipeline stage. */
    val deviceIssues: List<ValidationError> get() = issues.filter(DeviceConfigReadiness::isDeviceIssue)
}

/**
 * Edits the device configuration that belongs to the current workspace (one to one). Saving is refused while a
 * run is active, so a running build never sees its device change underneath it.
 */
class WorkspaceDeviceViewModel(
    private val workspaceManager: WorkspaceManager,
    private val workspaceDeviceRepository: WorkspaceDeviceRepository,
    private val snapshotRepository: SnapshotRepository,
    private val observeRunUseCase: ObserveRunUseCase? = null,
    private val targetRepository: TargetRepository? = null,
    private val firmwareCheck: DeviceFirmwareCheck? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WorkspaceDeviceUiState())
    val uiState: StateFlow<WorkspaceDeviceUiState> = _uiState.asStateFlow()

    private var snapshot: ConfigurationSnapshot? = null

    init {
        targetRepository?.let { catalog ->
            viewModelScope.launch {
                catalog.getAvailableTargets().collect { found -> _uiState.update { it.copy(templates = found) } }
            }
        }
        viewModelScope.launch {
            workspaceManager.currentWorkspace.collectLatest { workspace ->
                if (workspace == null) {
                    // The catalog is the app's, not the workspace's: it does not come again for the next one.
                    _uiState.update { WorkspaceDeviceUiState(templates = it.templates) }
                } else {
                    load(workspace)
                    coroutineScope {
                        launch { observeAgainAfterFailure { followSnapshot(workspace.id) } }
                        observeAgainAfterFailure { observeRuns(workspace.id) }
                    }
                }
            }
        }
    }

    private suspend fun load(workspace: Workspace) {
        val readSnapshot = runCatching { workspace.effectiveSnapshotId?.let { snapshotRepository.getSnapshot(it) } }
            .onFailure { if (it is CancellationException) throw it }
        val loadedSnapshot = readSnapshot.getOrNull()
        val read = runCatching { workspaceDeviceRepository.get(workspace.id) }
        val device = read.getOrNull()
        val failure = read.exceptionOrNull() ?: readSnapshot.exceptionOrNull()
        _uiState.update {
            // A load for a workspace that is no longer shown (a slow retry) changes nothing.
            if (workspaceManager.currentWorkspace.value?.id != workspace.id) return@update it
            // Only the workspace now shown sets the snapshot its device is checked against.
            snapshot = loadedSnapshot
            val same = it.workspaceId == workspace.id
            it.copy(
                // A save still running for another workspace never locks this one's editor.
                isSaving = it.isSaving && same,
                workspaceId = workspace.id,
                saved = device,
                draft = device,
                issues = issuesOf(device),
                message = null,
                loadError = failure?.let { error -> error.message ?: "Unknown error" },
                revisionUnbound = false,
                resetCount = it.resetCount + 1,
                // Another workspace's firmware facts must never be offered here.
                firmware = it.firmware.takeIf { same },
                firmwareError = it.firmwareError.takeIf { same },
                firmwareChecking = it.firmwareChecking && same,
            )
        }
    }

    /** Reads the device file of the workspace again (after the build service comes back, for example). */
    fun retry() {
        val workspaceId = _uiState.value.workspaceId ?: return
        viewModelScope.launch {
            workspaceManager.repository.getWorkspace(workspaceId)?.let { load(it) }
        }
    }

    /** Re-checks the device when the saved configuration of the workspace changes (a new snapshot). */
    private suspend fun followSnapshot(workspaceId: String) {
        workspaceManager.getWorkspaces()
            .map { all -> all.firstOrNull { it.id == workspaceId }?.effectiveSnapshotId }
            .distinctUntilChanged()
            .collect { snapshotId ->
                snapshot = snapshotId?.let { snapshotRepository.getSnapshot(it) }
                _uiState.update { it.copy(issues = issuesOf(it.draft)) }
            }
    }

    private suspend fun observeRuns(workspaceId: String) {
        observeRunUseCase?.observeRuns(workspaceId)?.collectLatest { runs ->
            _uiState.update { it.copy(runActive = runs.any { run -> run.state.isActive }) }
        }
    }

    fun update(change: (TargetDevice) -> TargetDevice) {
        _uiState.update { state ->
            val draft = state.draft
            if (draft == null || state.locked || state.isSaving) return@update state
            val changed = change(draft)
            state.copy(draft = changed, issues = issuesOf(changed), message = null)
        }
    }

    /** A workspace with no device of its own gets a copy of [template]; later edits never touch the template. */
    fun startFromTemplate(template: TargetDevice) {
        val workspaceId = _uiState.value.workspaceId ?: return
        val state = _uiState.value
        if (state.draft != null || state.loadError != null || state.isSaving || state.locked) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                createFromTemplate(workspaceId, template)
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private suspend fun createFromTemplate(workspaceId: String, template: TargetDevice) {
        run {
            // Only a folder that definitely has no device gets the template; an existing file is never replaced.
            val existing = runCatching { workspaceDeviceRepository.get(workspaceId) }
            if (existing.isFailure || existing.getOrNull() != null) {
                retry()
                return
            }
            val stored = runCatching { workspaceDeviceRepository.save(workspaceId, template) }.getOrElse { error ->
                _uiState.update {
                    if (it.workspaceId != workspaceId) return@update it
                    it.copy(message = error.message ?: "Failed to write the device configuration")
                }
                return
            }
            val bound = bindRevision(workspaceId, stored.revision)
            _uiState.update {
                if (it.workspaceId != workspaceId) return@update it
                it.copy(
                    saved = stored,
                    draft = stored,
                    issues = issuesOf(stored),
                    revisionUnbound = !bound,
                    message = "Started from ${template.name}",
                    resetCount = it.resetCount + 1,
                )
            }
        }
    }

    /** Reads the extracted firmware's own facts (build.prop, boot image footers, super table) to compare with. */
    private var firmwareRequest = 0L

    fun checkFirmware() {
        val check = firmwareCheck
        val state = _uiState.value
        val workspaceId = state.workspaceId
        val device = state.draft
        if (check == null || workspaceId == null || device == null) return
        _uiState.update { it.copy(firmwareChecking = true, firmwareError = null) }
        // Only the latest check reports: an older one finishing later (same workspace or not) is dropped.
        val request = ++firmwareRequest
        viewModelScope.launch {
            val read = runCatching { check.read(workspaceId, device.bootPartitions) }
            _uiState.update {
                if (it.workspaceId != workspaceId || request != firmwareRequest) return@update it
                it.copy(
                    firmware = read.getOrNull() ?: it.firmware,
                    firmwareChecking = false,
                    firmwareError = read.exceptionOrNull()?.let { e -> e.message ?: "The firmware could not be read" },
                )
            }
        }
    }

    /** Puts [fact]'s firmware value into the draft; saving keeps it. */
    fun useFirmwareValue(fact: FirmwareFact) {
        update { DeviceFirmwareCheck.apply(it, fact) }
    }

    fun discard() {
        _uiState.update { it.copy(draft = it.saved, issues = issuesOf(it.saved), resetCount = it.resetCount + 1) }
    }

    fun save() {
        val state = _uiState.value
        val workspaceId = state.workspaceId
        val draft = state.draft
        if (workspaceId == null || draft == null || !state.canSave) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            val stored = runCatching { workspaceDeviceRepository.save(workspaceId, draft) }
            stored.onSuccess { device ->
                val bound = bindRevision(workspaceId, device.revision)
                _uiState.update {
                    if (it.workspaceId != workspaceId) return@update it
                    it.copy(
                        saved = device,
                        draft = device,
                        issues = issuesOf(device),
                        isSaving = false,
                        revisionUnbound = !bound,
                        message = if (bound) {
                            "Device configuration saved"
                        } else {
                            "Device saved, but the workspace could not record revision ${device.revision}; " +
                                "the next build uses the device file as it is. Save again to retry."
                        },
                        resetCount = it.resetCount + 1,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    if (it.workspaceId != workspaceId) return@update it
                    it.copy(isSaving = false, message = error.message ?: "Failed to save device configuration")
                }
            }
        }
    }

    /**
     * The workspace records which revision of its device it is on; each run snapshot carries that number. False
     * when that write failed (the device file itself is saved).
     */
    private suspend fun bindRevision(workspaceId: String, revision: Int): Boolean = runCatching {
        val workspace = workspaceManager.repository.getWorkspace(workspaceId) ?: return true
        workspaceManager.repository.saveWorkspace(
            workspace.copy(targetBinding = TargetBinding(workspaceId, revision)),
        )
    }.onFailure { e -> if (e is CancellationException) throw e }.isSuccess

    private fun issuesOf(device: TargetDevice?): List<ValidationError> {
        val base = snapshot ?: return emptyList()
        return device?.let { DeviceConfigReadiness.check(it, base) }.orEmpty()
    }
}

/** Runs [observe] again after a failure, for as long as the workspace stays open: the service may come back. */
internal suspend fun observeAgainAfterFailure(observe: suspend () -> Unit) {
    while (true) {
        try {
            return observe()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            delay(OBSERVE_RETRY_MS)
        }
    }
}

private const val OBSERVE_RETRY_MS = 5_000L
