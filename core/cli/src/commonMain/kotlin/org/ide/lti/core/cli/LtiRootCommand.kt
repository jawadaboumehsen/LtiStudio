package org.ide.lti.core.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.optional

class LtiRootCommand(
    console: TerminalConsole,
    private val onToggleTheme: () -> Unit = {},
    private val onClear: () -> Unit = {},
) : CliktCommand(name = "lti") {

    override val invokeWithoutSubcommand: Boolean = true

    override fun help(context: Context): String = "LtiRom Studio terminal commands"

    init {
        context {
            terminal = console.terminal
        }
        subcommands(
            HelpSubcommand(this),
            ClearSubcommand(onClear),
            EchoSubcommand(),
            VersionSubcommand(),
            ThemeSubcommand(onToggleTheme),
        )
    }

    override fun run() {
        if (currentContext.invokedSubcommand == null) {
            echo(getFormattedHelp())
        }
    }

    private class HelpSubcommand(private val root: CliktCommand) : CliktCommand(name = "help") {
        override fun help(context: Context): String = "Display help information for built-in commands"

        val targetCommand by argument("command", help = "The subcommand to show help for").optional()

        override fun run() {
            val target = targetCommand?.let { name ->
                root.registeredSubcommands().firstOrNull { it.commandName == name }
            } ?: root
            echo(target.getFormattedHelp())
        }
    }

    private class ClearSubcommand(private val onClear: () -> Unit) : CliktCommand(name = "clear") {
        override fun help(context: Context): String = "Clear the terminal screen"

        override fun run() {
            onClear()
        }
    }

    private class EchoSubcommand : CliktCommand(name = "echo") {
        override fun help(context: Context): String = "Print arguments back to the terminal"

        val text by argument("text", help = "Text to print").multiple()

        override fun run() {
            echo(text.joinToString(" "))
        }
    }

    private class VersionSubcommand : CliktCommand(name = "version") {
        override fun help(context: Context): String = "Print version information"

        override fun run() {
            echo("LtiRom Studio terminal - clikt 5.0.1")
        }
    }

    private class ThemeSubcommand(private val onToggleTheme: () -> Unit) : CliktCommand(name = "theme") {
        override fun help(context: Context): String = "Toggle between light and dark theme"

        override fun run() {
            onToggleTheme()
            echo("Theme toggled")
        }
    }
}
