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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.workbench.PhoneFacts
import org.ide.lti.core.domain.workbench.PhoneMode
import org.ide.lti.core.domain.workbench.PhoneProbe
import org.ide.lti.core.domain.workbench.ProjectImage

data class PhoneUiState(
    val phones: List<PhoneFacts> = emptyList(),
    val serial: String? = null,
    val images: List<ProjectImage> = emptyList(),
    /** Paths of the images ticked for the plan. */
    val picked: Set<String> = emptySet(),
    val read: Boolean = false,
    val busy: String? = null,
    val error: String? = null,
) {
    val phone: PhoneFacts? get() = phones.firstOrNull { it.serial == serial } ?: phones.firstOrNull()
}

/** The phone attached to WSL and the plan of what would go where; it reads and reboots, never writes. */
class PhoneViewModel(private val probe: PhoneProbe? = null) : ViewModel() {
    private val _state = MutableStateFlow(PhoneUiState())
    val state: StateFlow<PhoneUiState> = _state.asStateFlow()

    fun read(workspaceId: String) = busy("Reading the phone…") {
        val phones = probe!!.read(workspaceId)
        val images = probe.images(workspaceId)
        _state.update { s ->
            s.copy(
                phones = phones,
                images = images,
                picked = s.picked.filter { p -> images.any { it.path == p } }.toSet(),
                read = true,
            )
        }
    }

    fun select(serial: String) = _state.update { it.copy(serial = serial) }

    fun toggle(path: String) = _state.update {
        it.copy(picked = if (path in it.picked) it.picked - path else it.picked + path)
    }

    fun reboot(workspaceId: String, to: PhoneMode) {
        val phone = _state.value.phone ?: return
        busy("Rebooting to ${to.words} and waiting for it…") {
            val back = probe!!.reboot(workspaceId, phone, to)
            _state.update { s ->
                s.copy(
                    phones = back?.let { b -> s.phones.map { if (it.serial == b.serial) b else it } } ?: s.phones,
                    error = if (back == null) "The phone did not come back in ${to.words} within 90 seconds." else null,
                )
            }
        }
    }

    private fun busy(what: String, block: suspend () -> Unit) {
        if (probe == null || _state.value.busy != null) return
        _state.update { it.copy(busy = what, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: e::class.simpleName) }
            } finally {
                _state.update { it.copy(busy = null) }
            }
        }
    }
}
