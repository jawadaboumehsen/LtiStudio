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

/**
 * Contextual state and service hooks passed to Clikt commands via Clikt Context.
 */
data class LtiCommandContext(
    val console: TerminalConsole,
    val getCwd: () -> String = { "" },
    val setCwd: (String) -> Unit = {},
    val onToggleTheme: () -> Unit = {},
    val onSetTheme: (String) -> Boolean = { false },
    val onClear: () -> Unit = {},
    val getSettings: () -> Map<String, Any> = { emptyMap() },
    val onUpdateSetting: (String, String) -> Boolean = { _, _ -> false },
)
