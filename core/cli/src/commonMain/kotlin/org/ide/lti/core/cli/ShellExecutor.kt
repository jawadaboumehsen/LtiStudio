package org.ide.lti.core.cli

import kotlinx.coroutines.flow.Flow

interface ShellExecutor {
    /**
     * Executes the given [command] in the specified [workingDirectory].
     * Emits [ShellOutputEvent.Line] for each line of stdout and stderr as they arrive,
     * followed by [ShellOutputEvent.Finished] with the process exit code.
     * When the collecting coroutine is cancelled, the underlying process must be killed.
     */
    fun run(command: String, workingDirectory: String): Flow<ShellOutputEvent>
}

sealed interface ShellOutputEvent {
    data class Line(val text: String, val isError: Boolean) : ShellOutputEvent
    data class Finished(val exitCode: Int) : ShellOutputEvent
}
