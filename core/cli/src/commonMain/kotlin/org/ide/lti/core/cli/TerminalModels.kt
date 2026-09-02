package org.ide.lti.core.cli

/**
 * A rendered line in the terminal console history.
 */
sealed interface TerminalLine {
    data class Command(val text: String, val cwd: String) : TerminalLine
    data class Output(val text: String) : TerminalLine
    data class Error(val text: String) : TerminalLine
}

/**
 * An event emitted during the execution/dispatch of a terminal command.
 */
sealed interface TerminalOutputEvent {
    data class Output(val text: String) : TerminalOutputEvent
    data class Error(val text: String) : TerminalOutputEvent
    data object Clear : TerminalOutputEvent
}
