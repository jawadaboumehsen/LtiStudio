/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.panels.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.ai.AIService
import org.ide.lti.core.model.ai.Message
import org.ide.lti.core.model.ai.MessageRole

sealed interface AIAssistantUiState {
    data object Empty : AIAssistantUiState
    data class Chat(
        val messages: List<Message>,
        val isGenerating: Boolean = false,
    ) : AIAssistantUiState
    data class Error(val message: String) : AIAssistantUiState
}

class AIAssistantViewModel(
    private val aiService: AIService,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AIAssistantUiState>(AIAssistantUiState.Empty)
    val uiState: StateFlow<AIAssistantUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            try {
                val history = aiService.getHistory()
                _uiState.value = if (history.isEmpty()) {
                    AIAssistantUiState.Empty
                } else {
                    AIAssistantUiState.Chat(messages = history)
                }
            } catch (e: Exception) {
                _uiState.value = AIAssistantUiState.Error(
                    e.message ?: "Failed to load chat history",
                )
            }
        }
    }

    fun sendMessage(message: String, context: String = "") {
        if (message.isBlank()) return

        viewModelScope.launch {
            try {
                // Add user message immediately
                val currentMessages = when (val state = _uiState.value) {
                    is AIAssistantUiState.Chat -> state.messages
                    else -> emptyList()
                }

                val userMessage = Message(MessageRole.USER, message)
                _uiState.value = AIAssistantUiState.Chat(
                    messages = currentMessages + userMessage,
                    isGenerating = true,
                )

                // Stream AI response
                val responseBuilder = StringBuilder()
                aiService.sendMessage(message, context).collect { chunk ->
                    responseBuilder.append(chunk)

                    val assistantMessage = Message(MessageRole.ASSISTANT, responseBuilder.toString())
                    _uiState.value = AIAssistantUiState.Chat(
                        messages = currentMessages + userMessage + assistantMessage,
                        isGenerating = true,
                    )
                }

                // Mark as done generating
                val finalMessages = when (val state = _uiState.value) {
                    is AIAssistantUiState.Chat -> state.messages
                    else -> emptyList()
                }
                _uiState.value = AIAssistantUiState.Chat(
                    messages = finalMessages,
                    isGenerating = false,
                )
            } catch (e: Exception) {
                _uiState.value = AIAssistantUiState.Error(
                    e.message ?: "Failed to send message",
                )
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            try {
                aiService.clearHistory()
                _uiState.value = AIAssistantUiState.Empty
            } catch (e: Exception) {
                _uiState.value = AIAssistantUiState.Error(
                    e.message ?: "Failed to clear history",
                )
            }
        }
    }

    fun retry() {
        loadHistory()
    }
}
