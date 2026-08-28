/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.panels.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.repository.filesystem.FileSystemRepository
import org.ide.lti.core.domain.workspace.WorkspaceManager

data class EditorFile(
    val path: String,
    val name: String,
    val content: String,
    val isModified: Boolean = false,
    val language: String = "text",
)

sealed interface EditorUiState {
    data object Empty : EditorUiState
    data class Loaded(
        val openFiles: List<EditorFile>,
        val activeFileIndex: Int,
    ) : EditorUiState
    data class Error(val message: String) : EditorUiState
}

class EditorViewModel(
    private val fileSystemRepository: FileSystemRepository,
    private val workspaceManager: WorkspaceManager,
) : ViewModel() {

    val isWorkspaceOpen: StateFlow<Boolean> = workspaceManager.currentWorkspace
        .map { it != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = workspaceManager.currentWorkspace.value != null,
        )

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Empty)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val openFiles = mutableListOf<EditorFile>()
    private var activeFileIndex = 0

    fun openFile(filePath: String) {
        viewModelScope.launch {
            // Check if file is already open
            val existingIndex = openFiles.indexOfFirst { it.path == filePath }
            if (existingIndex >= 0) {
                // File already open, just switch to it
                activeFileIndex = existingIndex
                updateUiState()
                return@launch
            }

            // Load file content
            val result = fileSystemRepository.readFile(filePath)
            result.fold(
                onSuccess = { content ->
                    val fileName = filePath.substringAfterLast('/')
                        .substringAfterLast('\\')
                    val language = detectLanguage(fileName)

                    val editorFile = EditorFile(
                        path = filePath,
                        name = fileName,
                        content = content,
                        language = language,
                    )

                    openFiles.add(editorFile)
                    activeFileIndex = openFiles.size - 1
                    updateUiState()
                },
                onFailure = { error ->
                    _uiState.value = EditorUiState.Error(
                        error.message ?: "Failed to open file",
                    )
                },
            )
        }
    }

    fun closeFile(index: Int) {
        if (index in openFiles.indices) {
            openFiles.removeAt(index)

            // Adjust active index if necessary
            if (activeFileIndex >= openFiles.size) {
                activeFileIndex = (openFiles.size - 1).coerceAtLeast(0)
            }

            updateUiState()
        }
    }

    fun setActiveFile(index: Int) {
        if (index in openFiles.indices) {
            activeFileIndex = index
            updateUiState()
        }
    }

    fun updateContent(content: String) {
        if (openFiles.isEmpty()) return

        val currentFile = openFiles[activeFileIndex]
        openFiles[activeFileIndex] = currentFile.copy(
            content = content,
            isModified = true,
        )
        updateUiState()
    }

    fun saveFile() {
        if (openFiles.isEmpty()) return

        viewModelScope.launch {
            val currentFile = openFiles[activeFileIndex]
            val result = fileSystemRepository.writeFile(
                currentFile.path,
                currentFile.content,
            )

            result.fold(
                onSuccess = {
                    // Mark as not modified
                    openFiles[activeFileIndex] = currentFile.copy(isModified = false)
                    updateUiState()
                },
                onFailure = { error ->
                    _uiState.value = EditorUiState.Error(
                        error.message ?: "Failed to save file",
                    )
                },
            )
        }
    }

    private fun updateUiState() {
        _uiState.value = if (openFiles.isEmpty()) {
            EditorUiState.Empty
        } else {
            EditorUiState.Loaded(
                openFiles = openFiles.toList(),
                activeFileIndex = activeFileIndex,
            )
        }
    }

    private fun detectLanguage(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "")
        return EXTENSION_TO_LANGUAGE[extension] ?: "text"
    }

    private companion object {
        private val EXTENSION_TO_LANGUAGE = mapOf(
            "kt" to "kotlin",
            "kts" to "kotlin",
            "java" to "java",
            "js" to "javascript",
            "jsx" to "javascript",
            "ts" to "typescript",
            "tsx" to "typescript",
            "py" to "python",
            "rs" to "rust",
            "go" to "go",
            "md" to "markdown",
            "json" to "json",
            "xml" to "xml",
            "yaml" to "yaml",
            "yml" to "yaml",
            "html" to "html",
            "htm" to "html",
            "css" to "css",
            "gradle" to "gradle",
        )
    }
}
