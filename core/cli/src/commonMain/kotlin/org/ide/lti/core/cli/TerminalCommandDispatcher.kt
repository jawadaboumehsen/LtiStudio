package org.ide.lti.core.cli

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface TerminalCommandDispatcher {
    /**
     * Currently tracked working directory.
     */
    val cwd: StateFlow<String>

    /**
     * Dispatches a raw input line.
     * Special-cases `cd`, built-in commands (help, clear, echo, version, theme),
     * and forwards any other command to the underlying shell executor.
     */
    fun dispatch(line: String): Flow<TerminalOutputEvent>
}
