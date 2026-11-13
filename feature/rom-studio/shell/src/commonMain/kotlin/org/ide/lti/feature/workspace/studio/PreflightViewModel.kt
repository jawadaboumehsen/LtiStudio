/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.studio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.pipeline.stages.Preflight
import org.ide.lti.core.domain.pipeline.stages.PreflightFinding
import org.ide.lti.core.domain.pipeline.stages.PreflightFix
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException
import org.ide.lti.core.domain.workspace.SamePhoneSettings
import org.ide.lti.core.domain.workspace.WorkspaceSettingsStore
import org.ide.lti.core.model.workspace.Workspace

public data class PreflightUiState(
    val findings: List<PreflightFinding>? = null,
    val checking: Boolean = false,
    val error: String? = null,
    /** Other workspaces of the same phone, whose settings can be carried here. */
    val samePhone: List<Workspace> = emptyList(),
    val message: String? = null,
)

/** Will it build? Runs the preflight and applies a finding's settings fix, then checks again. */
public class PreflightViewModel(
    private val workspaceId: String,
    private val preflight: Preflight? = null,
    private val settings: WorkspaceSettingsStore? = null,
    private val samePhone: SamePhoneSettings? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(PreflightUiState())
    public val state: StateFlow<PreflightUiState> = _state.asStateFlow()

    init {
        samePhone?.let { finder ->
            viewModelScope.launch {
                val others = runCatching { finder.others(workspaceId) }.getOrDefault(emptyList())
                _state.update { it.copy(samePhone = others) }
            }
        }
    }

    /** Copies [from]'s phone settings (assembly, debloat, mods, build) here, then checks again. */
    public fun carryFrom(from: Workspace) {
        val finder = samePhone ?: return
        viewModelScope.launch {
            finder.carry(from.id, workspaceId)
                .onSuccess {
                    _state.update { it.copy(message = "Took the settings of ${from.name}.") }
                    check()
                }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "The settings could not be copied") } }
        }
    }

    public fun check() {
        val checker = preflight ?: return
        if (_state.value.checking) return
        _state.update { it.copy(checking = true, error = null) }
        viewModelScope.launch {
            try {
                val found = checker.check(workspaceId)
                _state.update { it.copy(findings = found) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RefusalException) {
                _state.update { it.copy(error = "${e.refusal.cause} ${e.refusal.nextStep}") }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                _state.update { it.copy(error = e.message ?: e::class.simpleName) }
            } finally {
                _state.update { it.copy(checking = false) }
            }
        }
    }

    /** Saves the fix's settings change and checks again. */
    public fun apply(fix: PreflightFix.Setting) {
        val store = settings ?: return
        viewModelScope.launch {
            store.update(workspaceId, fix.apply)
                .onSuccess { check() }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "The setting could not be saved") } }
        }
    }
}
