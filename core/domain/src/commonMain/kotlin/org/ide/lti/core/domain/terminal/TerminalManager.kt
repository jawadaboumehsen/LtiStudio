/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.terminal

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import org.ide.lti.core.model.terminal.TerminalSession

/**
 * Manages terminal sessions and process execution.
 */
class TerminalManager {
    private val _sessions = MutableStateFlow<List<TerminalSession>>(emptyList())
    val sessions: StateFlow<List<TerminalSession>> = _sessions.asStateFlow()

    fun createSession(name: String, workingDirectory: String): TerminalSession {
        val session = TerminalSession(
            id = System.currentTimeMillis().toString(),
            name = name,
            workingDirectory = workingDirectory,
        )
        _sessions.value = _sessions.value + session
        return session
    }

    fun closeSession(sessionId: String) {
        _sessions.value = _sessions.value.filterNot { it.id == sessionId }
    }

    fun executeCommand(sessionId: String, command: String): Flow<String> {
        val session = _sessions.value.find { it.id == sessionId }
        // TODO: Implement actual command execution using PTY
        return flow {
            val prefix = session?.let { "${it.name}$ " } ?: "$ "
            emit("$prefix$command\n")
            emit("Command executed (placeholder)\n")
        }
    }
}
