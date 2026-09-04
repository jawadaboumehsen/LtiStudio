package org.ide.lti.core.cli

import com.github.ajalt.clikt.core.main

/**
 * Entry point for executing LTI CLI commands as a standalone CLI application.
 */
object LtiCliApp {
    /**
     * Executes the given command arguments using Clikt.
     */
    fun run(
        args: Array<String>,
        commandContext: LtiCommandContext = LtiCommandContext(console = TerminalConsole()),
    ) {
        LtiRootCommand(commandContext).main(args)
    }
}
