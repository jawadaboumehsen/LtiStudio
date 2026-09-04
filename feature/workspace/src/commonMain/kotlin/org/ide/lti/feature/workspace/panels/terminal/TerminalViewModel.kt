/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.panels.terminal

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ide.lti.core.cli.DesktopShellExecutor
import org.ide.lti.core.cli.ShellExecutor
import org.ide.lti.core.cli.TerminalCommandDispatcher
import org.ide.lti.core.cli.TerminalCommandDispatcherImpl
import org.ide.lti.core.cli.TerminalLine
import org.ide.lti.core.cli.TerminalOutputEvent
import org.ide.lti.core.data.settings.AppSettingsState
import org.ide.lti.core.domain.workspace.WorkspaceManager

@Immutable
data class TerminalUiState(
    val lines: List<TerminalLine> = emptyList(),
    val currentInput: String = "",
    val isRunning: Boolean = false,
    val cwd: String = "",
)

class TerminalViewModel(
    workspaceManager: WorkspaceManager,
    appSettingsState: AppSettingsState,
    shellExecutor: ShellExecutor = DesktopShellExecutor(),
) : ViewModel() {

    private val initialCwd = workspaceManager.currentWorkspace.value?.path
        ?: System.getProperty("user.home")
        ?: "."

    private val dispatcher: TerminalCommandDispatcher = TerminalCommandDispatcherImpl(
        initialCwd = initialCwd,
        shellExecutor = shellExecutor,
        onToggleTheme = {
            appSettingsState.isDarkTheme = !appSettingsState.isDarkTheme
        },
        onSetTheme = { themeName ->
            when (themeName.lowercase()) {
                "blue" -> { appSettingsState.theme = AppSettingsState.THEME_BLUE; true }
                "dark" -> { appSettingsState.theme = AppSettingsState.THEME_DARK; true }
                "light" -> { appSettingsState.theme = AppSettingsState.THEME_LIGHT; true }
                else -> false
            }
        },
        getSettings = {
            mapOf(
                "theme" to appSettingsState.theme,
                "effectsEnabled" to appSettingsState.effectsEnabled,
                "reducedMotion" to appSettingsState.reducedMotion,
            )
        },
        onUpdateSetting = { key, value ->
            when (key) {
                "theme" -> {
                    when (value.lowercase()) {
                        "blue" -> { appSettingsState.theme = AppSettingsState.THEME_BLUE; true }
                        "dark" -> { appSettingsState.theme = AppSettingsState.THEME_DARK; true }
                        "light" -> { appSettingsState.theme = AppSettingsState.THEME_LIGHT; true }
                        else -> false
                    }
                }
                "effectsEnabled" -> {
                    value.toBooleanStrictOrNull()?.let {
                        appSettingsState.effectsEnabled = it
                        true
                    } ?: false
                }
                "reducedMotion" -> {
                    value.toBooleanStrictOrNull()?.let {
                        appSettingsState.reducedMotion = it
                        true
                    } ?: false
                }
                else -> false
            }
        },
    )

    private val _uiState = MutableStateFlow(
        TerminalUiState(
            lines = emptyList(),
            currentInput = "",
            isRunning = false,
            cwd = dispatcher.cwd.value,
        ),
    )
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    private var runningJob: Job? = null
    private val commandHistory = mutableListOf<String>()
    private var historyIndex = -1
    private var savedInput = ""

    init {
        viewModelScope.launch {
            dispatcher.cwd.collect { newCwd ->
                _uiState.update { it.copy(cwd = newCwd) }
            }
        }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(currentInput = text) }
    }

    fun onSubmit() {
        val input = _uiState.value.currentInput
        if (_uiState.value.isRunning) return
        if (input.isBlank()) return

        val currentCwd = dispatcher.cwd.value
        commandHistory.add(input)
        historyIndex = -1
        savedInput = ""

        _uiState.update { state ->
            state.copy(
                lines = state.lines + TerminalLine.Command(text = input, cwd = currentCwd),
                currentInput = "",
                isRunning = true,
            )
        }

        runningJob = viewModelScope.launch {
            try {
                dispatcher.dispatch(input).collect { event ->
                    when (event) {
                        is TerminalOutputEvent.Output -> {
                            _uiState.update { state ->
                                state.copy(lines = state.lines + TerminalLine.Output(event.text))
                            }
                        }
                        is TerminalOutputEvent.Error -> {
                            _uiState.update { state ->
                                state.copy(lines = state.lines + TerminalLine.Error(event.text))
                            }
                        }
                        is TerminalOutputEvent.Clear -> {
                            _uiState.update { state ->
                                state.copy(lines = emptyList())
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Any unexpected failure in a built-in command or the shell chain must still
                // surface as visible terminal output - never fail silently or crash the
                // collecting coroutine, matching how a real terminal reports a broken command.
                val errorText = e.message ?: "Unexpected error: ${e::class.simpleName}"
                _uiState.update { state ->
                    state.copy(lines = state.lines + TerminalLine.Error(errorText))
                }
            } finally {
                _uiState.update { it.copy(isRunning = false, cwd = dispatcher.cwd.value) }
            }
        }
    }

    fun onCancelRunning() {
        runningJob?.cancel()
        runningJob = null
        _uiState.update { state ->
            state.copy(
                lines = state.lines + TerminalLine.Error("^C"),
                isRunning = false,
            )
        }
    }

    fun onHistoryUp() {
        if (commandHistory.isEmpty()) return

        if (historyIndex == -1) {
            savedInput = _uiState.value.currentInput
            historyIndex = commandHistory.size - 1
            _uiState.update { it.copy(currentInput = commandHistory[historyIndex]) }
        } else if (historyIndex > 0) {
            historyIndex--
            _uiState.update { it.copy(currentInput = commandHistory[historyIndex]) }
        }
    }

    fun onHistoryDown() {
        if (historyIndex == -1) return

        if (historyIndex < commandHistory.size - 1) {
            historyIndex++
            _uiState.update { it.copy(currentInput = commandHistory[historyIndex]) }
        } else {
            historyIndex = -1
            _uiState.update { it.copy(currentInput = savedInput) }
        }
    }
}
