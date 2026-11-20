/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.panels.run

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ide.lti.core.common.utils.runCatchingCancellable
import org.ide.lti.core.domain.workspace.BuildProfile
import org.ide.lti.core.domain.workspace.BuildProfiles
import org.ide.lti.core.domain.workspace.BuildQueue
import org.ide.lti.core.domain.workspace.QueueState
import org.ide.lti.core.domain.workspace.WorkspaceManager

public data class ProfilesUiState(
    val workspaceId: String? = null,
    val profiles: List<BuildProfile> = emptyList(),
    val nameInput: String = "",
    /** Per profile name: what using it would change in the current settings (shown when asked for). */
    val differences: Map<String, List<String>> = emptyMap(),
    /** The profiles picked for the queue, in the order they will build. */
    val queued: List<String> = emptyList(),
    val queue: QueueState = QueueState(),
    val message: String? = null,
)

/** Run panel > Profiles: named settings of the workspace, their differences, using one, and building several. */
public class ProfilesViewModel(
    private val workspaceManager: WorkspaceManager,
    private val profiles: BuildProfiles? = null,
    private val queue: BuildQueue? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfilesUiState())
    public val state: StateFlow<ProfilesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            workspaceManager.currentWorkspace.collectLatest { ws ->
                _state.update { ProfilesUiState(workspaceId = ws?.id, queue = it.queue) }
                if (ws != null) reload(ws.id)
            }
        }
        queue?.let { q ->
            viewModelScope.launch { q.state.collectLatest { s -> _state.update { it.copy(queue = s) } } }
        }
    }

    public fun setName(name: String) {
        _state.update { it.copy(nameInput = name) }
    }

    /** The current settings saved under the typed name. */
    public fun saveCurrent() {
        act { ws, store ->
            store.saveCurrent(ws, _state.value.nameInput).also { _state.update { s -> s.copy(nameInput = "") } }
        }
    }

    public fun delete(name: String) {
        act { ws, store -> store.delete(ws, name) }
        _state.update { it.copy(queued = it.queued - name) }
    }

    /** Shows (or hides) what using [profile] would change. */
    public fun toggleDifferences(profile: BuildProfile) {
        val shown = _state.value.differences
        val ws = _state.value.workspaceId
        val store = profiles
        if (profile.name in shown || ws == null || store == null) {
            _state.update { it.copy(differences = shown - profile.name) }
            return
        }
        viewModelScope.launch {
            val lines = runCatchingCancellable { store.differences(ws, profile) }.getOrElse { listOf("${it.message}") }
            _state.update { it.copy(differences = it.differences + (profile.name to lines)) }
        }
    }

    /** [profile]'s settings made the current ones. */
    public fun use(profile: BuildProfile) {
        val ws = _state.value.workspaceId ?: return
        val store = profiles ?: return
        viewModelScope.launch {
            val done = store.apply(ws, profile).isSuccess
            val message = if (done) "Now using ${profile.name}" else "Could not use ${profile.name}"
            _state.update { it.copy(message = message, differences = emptyMap()) }
        }
    }

    /** Adds [name] to the end of the queue, or takes it out. */
    public fun toggleQueued(name: String) {
        _state.update { s -> s.copy(queued = if (name in s.queued) s.queued - name else s.queued + name) }
    }

    public fun startQueue() {
        val ws = _state.value.workspaceId ?: return
        queue?.start(ws, _state.value.queued)
    }

    public fun stopQueue() {
        queue?.stop()
    }

    private fun act(change: suspend (String, BuildProfiles) -> List<BuildProfile>) {
        val ws = _state.value.workspaceId ?: return
        val store = profiles ?: return
        viewModelScope.launch {
            runCatchingCancellable { change(ws, store) }
                .onSuccess { list -> _state.update { it.copy(profiles = list, message = null) } }
                .onFailure { e -> _state.update { it.copy(message = e.message ?: "The profile could not be saved") } }
        }
    }

    private suspend fun reload(workspaceId: String) {
        val list = profiles?.let { runCatchingCancellable { it.list(workspaceId) }.getOrNull() }.orEmpty()
        _state.update { if (it.workspaceId == workspaceId) it.copy(profiles = list) else it }
    }
}
