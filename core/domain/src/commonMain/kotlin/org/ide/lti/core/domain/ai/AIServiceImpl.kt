/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.ide.lti.core.model.ai.Message
import org.ide.lti.core.model.ai.MessageRole

/**
 * Default implementation of AIService.
 * TODO: Integrate with actual LLM API (OpenAI, Anthropic, etc.)
 */
class AIServiceImpl : AIService {
    private val history = mutableListOf<Message>()

    override fun sendMessage(message: String, context: String): Flow<String> {
        history.add(Message(MessageRole.USER, message))

        return flow {
            // TODO: Call actual AI API
            emit("This is a placeholder AI response. ")
            emit("Integration with LLM API pending. ")
            emit("\n\nYou said: $message")

            val response = history.lastOrNull()?.content ?: ""
            history.add(Message(MessageRole.ASSISTANT, response))
        }
    }

    override suspend fun getHistory(): List<Message> {
        return history.toList()
    }

    override suspend fun clearHistory() {
        history.clear()
    }
}
