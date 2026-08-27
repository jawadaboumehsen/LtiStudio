/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.panels.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.ide.lti.core.data.repository.filesystem.FileSystemRepository
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.filesystem.FileNode

sealed interface ExplorerUiState {
    data object Loading : ExplorerUiState
    data class Success(val rootNode: FileNode) : ExplorerUiState
    data class Error(val message: String) : ExplorerUiState
}

class ExplorerViewModel(
    private val workspaceManager: WorkspaceManager,
    private val fileSystemRepository: FileSystemRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ExplorerUiState>(ExplorerUiState.Loading)
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    private val _expandedNodes = MutableStateFlow<Set<String>>(emptySet())
    val expandedNodes: StateFlow<Set<String>> = _expandedNodes.asStateFlow()

    private val _selectedFile = MutableStateFlow<FileNode?>(null)
    val selectedFile: StateFlow<FileNode?> = _selectedFile.asStateFlow()

    init {
        observeWorkspaceAndWatchFiles()
    }

    private fun observeWorkspaceAndWatchFiles() {
        viewModelScope.launch {
            workspaceManager.currentWorkspace.collectLatest { workspace ->
                if (workspace != null) {
                    loadWorkspaceFiles(workspace.path, workspace.name)
                    fileSystemRepository.watchDirectory(workspace.path).collect {
                        loadWorkspaceFiles(workspace.path, workspace.name)
                    }
                } else {
                    _uiState.value = ExplorerUiState.Error("No workspace selected")
                }
            }
        }
    }

    private suspend fun loadWorkspaceFiles(path: String, name: String) {
        try {
            val result = fileSystemRepository.listFiles(path)
            result.fold(
                onSuccess = { files ->
                    val rootNode = FileNode(
                        path = path,
                        name = name,
                        type = org.ide.lti.core.model.filesystem.FileType.DIRECTORY,
                        children = files,
                    )
                    _uiState.value = ExplorerUiState.Success(rootNode)
                },
                onFailure = { error ->
                    _uiState.value = ExplorerUiState.Error(
                        error.message ?: "Failed to load files",
                    )
                },
            )
        } catch (e: Exception) {
            _uiState.value = ExplorerUiState.Error(e.message ?: "Failed to load files")
        }
    }

    fun toggleNodeExpansion(path: String) {
        _expandedNodes.value = if (path in _expandedNodes.value) {
            _expandedNodes.value - path
        } else {
            _expandedNodes.value + path
        }
    }

    fun onFileSelected(fileNode: FileNode) {
        _selectedFile.value = fileNode
        // TODO: Open file in editor
        // Will be implemented when editor feature is created
    }

    fun openFolder() {
        viewModelScope.launch {
            val selectedPath = org.ide.lti.core.common.utils.DialogUtils.selectDirectory()
            if (selectedPath != null) {
                workspaceManager.openWorkspaceByPath(selectedPath)
            }
        }
    }

    fun refresh() {
        _uiState.value = ExplorerUiState.Loading
        val workspace = workspaceManager.currentWorkspace.value
        if (workspace != null) {
            viewModelScope.launch {
                loadWorkspaceFiles(workspace.path, workspace.name)
            }
        }
    }
}
