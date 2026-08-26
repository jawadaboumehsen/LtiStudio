/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.panels.terminal

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface TerminalUiState {
    data object Empty : TerminalUiState
}

class TerminalViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<TerminalUiState>(TerminalUiState.Empty)
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()
}
