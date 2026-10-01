/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.ai

import kotlinx.coroutines.flow.Flow
import org.ide.lti.core.model.ai.Message

/**
 * Service for AI interactions (LLM API).
 */
interface AIService {
    /**
     * Send a message to the AI and stream the response.
     */
    fun sendMessage(message: String, context: String = ""): Flow<String>

    /**
     * Get conversation history.
     */
    suspend fun getHistory(): List<Message>

    /**
     * Clear conversation history.
     */
    suspend fun clearHistory()
}
