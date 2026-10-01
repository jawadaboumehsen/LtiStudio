/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.configuration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.usecase.target.CreateTargetScaffoldUseCase
import org.ide.lti.core.domain.usecase.target.DeleteTargetUseCase
import org.ide.lti.core.domain.usecase.target.DuplicateTargetUseCase
import org.ide.lti.core.domain.usecase.target.GetAvailableTargetsUseCase
import org.ide.lti.core.domain.usecase.target.GetSelectedTargetUseCase
import org.ide.lti.core.domain.usecase.target.SaveTargetConfigurationUseCase
import org.ide.lti.core.domain.usecase.target.SelectTargetUseCase
import org.ide.lti.core.domain.usecase.target.ValidateTargetConfigurationUseCase
import org.ide.lti.core.model.target.TargetDevice

/**
 * Dedicated Presentation ViewModel for IDE Target and Run Configurations.
 *
 * Responsibilities:
 * - Reactive target catalog observation and selection state holder.
 * - Live in-memory draft editing with dirty-tracking against baseline.
 * - Delegates validation, scaffolding, duplication, deletion, and save orchestration
 *   to dedicated Clean Architecture domain use cases.
 */
class ConfigurationViewModel(
    private val getAvailableTargetsUseCase: GetAvailableTargetsUseCase,
    private val getSelectedTargetUseCase: GetSelectedTargetUseCase,
    private val selectTargetUseCase: SelectTargetUseCase,
    private val createTargetScaffoldUseCase: CreateTargetScaffoldUseCase,
    private val duplicateTargetUseCase: DuplicateTargetUseCase,
    private val deleteTargetUseCase: DeleteTargetUseCase,
    private val validateTargetConfigurationUseCase: ValidateTargetConfigurationUseCase,
    private val saveTargetConfigurationUseCase: SaveTargetConfigurationUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConfigurationUiState())
    val uiState: StateFlow<ConfigurationUiState> = _uiState.asStateFlow()

    private var pendingNavigationIntent: Triple<String?, ConfigurationNavMode, ConfigurationTab?>? = null

    init {
        viewModelScope.launch {
            getAvailableTargetsUseCase().collectLatest { targets ->
                _uiState.update { current ->
                    val selectedId = if (current.selectedTargetId.isNotEmpty() &&
                        targets.any { it.id == current.selectedTargetId }
                    ) {
                        current.selectedTargetId
                    } else {
                        targets.firstOrNull()?.id.orEmpty()
                    }
                    val selected = targets.find { it.id == selectedId } ?: targets.firstOrNull()
                    current.copy(
                        availableTargets = targets,
                        selectedTargetId = selectedId,
                        editingTarget = if (!current.isDirty && selected != null) selected else current.editingTarget,
                        originalTarget = selected,
                    )
                }

                pendingNavigationIntent?.let { (targetId, mode, tab) ->
                    if (targetId.isNullOrBlank() || targets.any { it.id == targetId }) {
                        executeNavigationIntent(targetId, mode, tab)
                        pendingNavigationIntent = null
                    }
                }
            }
        }
    }

    /**
     * Applies a deep navigation intent (e.g. from route arguments or deep links),
     * switching to [targetId], applying [mode] (EDIT, CREATE, CLONE), and selecting [tab].
     */
    fun applyNavigationIntent(
        targetId: String? = null,
        mode: ConfigurationNavMode = ConfigurationNavMode.EDIT,
        tab: ConfigurationTab? = null,
    ) {
        val targets = _uiState.value.availableTargets
        if (targets.isEmpty() || (!targetId.isNullOrBlank() && targets.none { it.id == targetId })) {
            pendingNavigationIntent = Triple(targetId, mode, tab)
        } else {
            executeNavigationIntent(targetId, mode, tab)
        }
    }

    private fun executeNavigationIntent(targetId: String?, mode: ConfigurationNavMode, tab: ConfigurationTab?) {
        if (tab != null) {
            onSelectTab(tab)
        }

        when (mode) {
            ConfigurationNavMode.CREATE -> onAddNewTarget()
            ConfigurationNavMode.CLONE -> {
                if (!targetId.isNullOrBlank()) {
                    val target = _uiState.value.availableTargets.find { it.id == targetId }
                    if (target != null) {
                        onDuplicateTarget(target)
                    } else {
                        onSelectTarget(targetId)
                        onDuplicateTarget(_uiState.value.editingTarget)
                    }
                } else {
                    onDuplicateTarget(_uiState.value.editingTarget)
                }
            }
            ConfigurationNavMode.EDIT -> {
                if (!targetId.isNullOrBlank()) {
                    onSelectTarget(targetId)
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onSelectTab(tab: ConfigurationTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun onSelectTarget(targetId: String) {
        val current = _uiState.value
        val target = current.availableTargets.find { it.id == targetId }
        if (target == null || current.isSyncing) return

        if (current.isDirty && targetId != current.selectedTargetId) {
            _uiState.update { it.copy(pendingTargetId = targetId, statusMessage = null) }
        } else {
            selectTargetImmediately(target)
        }
    }

    /** Resolves a dirty target switch explicitly; no edit is silently discarded. */
    fun resolvePendingTargetSelection(decision: DirtyTargetSelectionDecision) {
        val targetId = _uiState.value.pendingTargetId ?: return
        when (decision) {
            DirtyTargetSelectionDecision.STAY -> _uiState.update { it.copy(pendingTargetId = null) }
            DirtyTargetSelectionDecision.DISCARD -> {
                onRevertChanges()
                _uiState.value.availableTargets.find { it.id == targetId }?.let(::selectTargetImmediately)
            }
            DirtyTargetSelectionDecision.SAVE -> onApplyChanges {
                _uiState.value.availableTargets.find { it.id == targetId }?.let(::selectTargetImmediately)
            }
        }
    }

    private fun selectTargetImmediately(target: TargetDevice) {
        _uiState.update {
            it.copy(
                selectedTargetId = target.id,
                editingTarget = target,
                originalTarget = target,
                isDirty = false,
                validationErrors = emptyList(),
                pendingTargetId = null,
                statusMessage = null,
            )
        }
    }

    fun onUpdateEditingTarget(updated: TargetDevice) {
        val original = _uiState.value.originalTarget
        val isDirty = original == null || original != updated
        val errors = validateTargetConfigurationUseCase(updated)
        _uiState.update {
            it.copy(
                editingTarget = updated,
                isDirty = isDirty,
                validationErrors = errors,
            )
        }
    }

    fun onAddNewTarget() {
        val baseCount = _uiState.value.availableTargets.size + 1
        val newTarget = createTargetScaffoldUseCase(index = baseCount)
        _uiState.update {
            it.copy(
                selectedTargetId = newTarget.id,
                editingTarget = newTarget,
                originalTarget = null,
                isDirty = true,
                validationErrors = validateTargetConfigurationUseCase(newTarget),
                statusMessage = "Created new target scaffold",
            )
        }
    }

    fun onDuplicateTarget(target: TargetDevice) {
        val existingIds = _uiState.value.availableTargets.map { it.id }.toSet()
        val duplicated = duplicateTargetUseCase(target, existingIds)
        _uiState.update {
            it.copy(
                selectedTargetId = duplicated.id,
                editingTarget = duplicated,
                originalTarget = null,
                isDirty = true,
                validationErrors = validateTargetConfigurationUseCase(duplicated),
                statusMessage = "Duplicated from ${target.name}",
            )
        }
    }

    fun onDeleteTarget(targetId: String) {
        viewModelScope.launch {
            val result = deleteTargetUseCase(targetId)
            if (result.isSuccess) {
                _uiState.update { it.copy(statusMessage = "Target deleted successfully") }
            } else {
                _uiState.update {
                    it.copy(statusMessage = result.exceptionOrNull()?.message ?: "Failed to delete target")
                }
            }
        }
    }

    fun onRevertChanges() {
        val original = _uiState.value.originalTarget ?: return
        _uiState.update {
            it.copy(
                editingTarget = original,
                isDirty = false,
                validationErrors = emptyList(),
                statusMessage = "Reverted changes",
            )
        }
    }

    fun onApplyChanges(onSuccess: (() -> Unit)? = null) {
        // Apply is an atomic user operation. A second click while the first save is
        // outstanding must not mint a second revision or race its completion.
        if (_uiState.value.isSyncing) return
        val target = _uiState.value.editingTarget
        val errors = validateTargetConfigurationUseCase(target)
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(validationErrors = errors) }
            return
        }

        viewModelScope.launch {
            // Catalog collection can update the baseline while a duplicate draft is
            // active. Membership, rather than that mutable baseline, defines whether
            // this identity needs an insert or a revisioned update.
            val isNew = _uiState.value.availableTargets.none { candidate ->
                candidate.id == target.id
            }
            val targetId = target.id
            _uiState.update { it.copy(isSyncing = true, statusMessage = null) }
            val toSave = if (isNew) target else target.copy(revision = target.revision + 1)
            val result = saveTargetConfigurationUseCase(
                target = toSave,
                isNew = isNew,
            )
            _uiState.update { state ->
                if (state.editingTarget.id == targetId) state.copy(isSyncing = false) else state
            }

            if (result.isSuccess) {
                _uiState.update {
                    // Do not let an older target's completion replace a newer editor.
                    if (it.editingTarget.id != targetId) return@update it.copy(isSyncing = false)
                    if (it.editingTarget != target) {
                        return@update it.copy(
                            originalTarget = toSave,
                            isDirty = true,
                            isSyncing = false,
                            statusMessage = "Saved an earlier revision; newer local changes are still unsaved",
                        )
                    }
                    it.copy(
                        originalTarget = toSave,
                        editingTarget = toSave,
                        selectedTargetId = toSave.id,
                        isDirty = false,
                        validationErrors = emptyList(),
                        statusMessage = "Configuration saved successfully",
                    )
                }
                onSuccess?.invoke()
            } else {
                _uiState.update {
                    if (it.editingTarget.id != targetId) return@update it.copy(isSyncing = false)
                    it.copy(
                        isSyncing = false,
                        statusMessage = "Error saving configuration: ${result.exceptionOrNull()?.message}",
                    )
                }
            }
        }
    }
}
