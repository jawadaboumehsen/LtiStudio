/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.ide.lti.core.common.event.PanelId
import org.ide.lti.core.domain.panels.PanelManager
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.panels.PanelState

class WorkspaceViewModel(
    private val workspaceManager: WorkspaceManager,
    private val panelManager: PanelManager,
) : ViewModel() {

    val workspaceName: StateFlow<String> = workspaceManager.currentWorkspace
        .map { it?.name ?: "Lti" }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = workspaceManager.currentWorkspace.value?.name ?: "Lti",
        )

    val branch: StateFlow<String> = MutableStateFlow("main")

    val panelsState = panelManager.panels

    fun getPanelState(panelId: PanelId): StateFlow<PanelState?> {
        return panelsState.map { it[panelId] }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = panelManager.getPanelState(panelId),
            )
    }

    fun togglePanelVisibility(panelId: PanelId) {
        panelManager.togglePanelVisibility(panelId)
    }

    fun setActiveTab(panelId: PanelId, tabIndex: Int) {
        panelManager.setActiveTab(panelId, tabIndex)
    }

    fun closeTab(panelId: PanelId, tabId: String) {
        panelManager.removeTab(panelId, tabId)
    }
}
