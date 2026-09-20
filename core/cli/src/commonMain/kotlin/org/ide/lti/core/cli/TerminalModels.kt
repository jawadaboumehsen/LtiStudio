package org.ide.lti.core.cli

typealias TerminalLine = org.ide.lti.core.model.terminal.TerminalLine
typealias TerminalOutputEvent = org.ide.lti.core.model.terminal.TerminalOutputEvent

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
