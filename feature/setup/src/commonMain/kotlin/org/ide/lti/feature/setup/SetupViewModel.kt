/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace

class SetupViewModel(
    private val workspaceManager: WorkspaceManager,
) : ViewModel() {

    val recentProjects: StateFlow<List<RecentProject>> = workspaceManager.getRecentProjects()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    fun openFolder(onWorkspaceOpened: (Workspace) -> Unit) {
        viewModelScope.launch {
            val selectedPath = org.ide.lti.core.common.utils.DialogUtils.selectDirectory()
            if (selectedPath != null) {
                val result = workspaceManager.openWorkspaceByPath(selectedPath)
                result.onSuccess { workspace ->
                    onWorkspaceOpened(workspace)
                }
            }
        }
    }

    fun openRecentProject(project: RecentProject, onWorkspaceOpened: (Workspace) -> Unit) {
        viewModelScope.launch {
            val result = workspaceManager.openWorkspace(project.workspaceId)
            result.onSuccess { workspace ->
                onWorkspaceOpened(workspace)
            }
        }
    }
}
