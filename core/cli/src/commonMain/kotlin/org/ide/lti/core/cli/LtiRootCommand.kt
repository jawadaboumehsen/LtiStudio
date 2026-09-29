/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.findOrSetObject
import com.github.ajalt.clikt.core.obj
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import java.io.File

class LtiRootCommand(
    val commandContext: LtiCommandContext,
) : CliktCommand(name = "lti") {

    constructor(
        console: TerminalConsole,
        onToggleTheme: () -> Unit = {},
        onClear: () -> Unit = {},
    ) : this(
        LtiCommandContext(
            console = console,
            onToggleTheme = onToggleTheme,
            onClear = onClear,
        ),
    )

    val ctxObj by findOrSetObject { commandContext }

    override val invokeWithoutSubcommand: Boolean = true

    override fun help(context: Context): String = "LtiRom Studio terminal commands"

    init {
        context {
            terminal = commandContext.console.terminal
        }
        subcommands(
            HelpSubcommand(this),
            ClearSubcommand(),
            EchoSubcommand(),
            VersionSubcommand(),
            ThemeSubcommand(),
            SettingsSubcommand(),
            WorkspaceSubcommand(),
            SystemSubcommand(),
        )
    }

    override fun run() {
        currentContext.obj = commandContext
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

    private class ClearSubcommand : CliktCommand(name = "clear") {
        override fun help(context: Context): String = "Clear the terminal screen"
        private val ctx by requireObject<LtiCommandContext>()

        override fun run() {
            ctx.onClear()
        }
    }

    private class EchoSubcommand : CliktCommand(name = "echo") {
        override fun help(context: Context): String = "Print arguments back to the terminal"

        val noNewline by option("-n", help = "Do not output trailing newline").flag(default = false)
        val toErr by option("-e", "--err", help = "Print to standard error").flag(default = false)
        val text by argument("text", help = "Text to print").multiple()

        override fun run() {
            val message = text.joinToString(" ")
            echo(message, trailingNewline = !noNewline, err = toErr)
        }
    }

    private class VersionSubcommand : CliktCommand(name = "version") {
        override fun help(context: Context): String = "Print version information"

        val short by option("-s", "--short", help = "Print short version string").flag(default = false)

        override fun run() {
            if (short) {
                echo("0.0.1-beta")
            } else {
                echo("LtiRom Studio terminal - clikt 5.0.1")
            }
        }
    }

    private class ThemeSubcommand : CliktCommand(name = "theme") {
        override val invokeWithoutSubcommand: Boolean = true
        override fun help(context: Context): String = "Manage or toggle application themes"
        private val ctx by requireObject<LtiCommandContext>()

        init {
            subcommands(
                ThemeSetSubcommand(),
                ThemeListSubcommand(),
                ThemeToggleSubcommand(),
            )
        }

        override fun run() {
            if (currentContext.invokedSubcommand == null) {
                ctx.onToggleTheme()
                echo("Theme toggled")
            }
        }

        private class ThemeToggleSubcommand : CliktCommand(name = "toggle") {
            override fun help(context: Context): String = "Toggle between light and dark theme"
            private val ctx by requireObject<LtiCommandContext>()
            override fun run() {
                ctx.onToggleTheme()
                echo("Theme toggled")
            }
        }

        private class ThemeSetSubcommand : CliktCommand(name = "set") {
            override fun help(context: Context): String = "Set the active theme explicitly"
            val name by option("-n", "--name", help = "Theme name (dark, blue, light)")
                .choice("dark", "blue", "light")
                .required()
            private val ctx by requireObject<LtiCommandContext>()

            override fun run() {
                ctx.onSetTheme(name)
                echo("Theme set to: $name")
            }
        }

        private class ThemeListSubcommand : CliktCommand(name = "list") {
            override fun help(context: Context): String = "List available application themes"
            override fun run() {
                echo("Available themes:")
                echo("  * dark   - JetBrains Fleet & Linear precision dark theme")
                echo("  * blue   - Signature liquid glass with cosmic aurora refraction")
                echo("  * light  - Clean high-contrast minimal light theme")
            }
        }
    }

    private class SettingsSubcommand : CliktCommand(name = "settings") {
        override val invokeWithoutSubcommand: Boolean = true
        override fun help(context: Context): String = "Inspect or modify IDE configuration settings"
        private val ctx by requireObject<LtiCommandContext>()

        init {
            subcommands(
                SettingsListSubcommand(),
                SettingsSetSubcommand(),
            )
        }

        override fun run() {
            if (currentContext.invokedSubcommand == null) {
                printSettings()
            }
        }

        private fun printSettings() {
            val settings = ctx.getSettings()
            if (settings.isEmpty()) {
                echo("No settings registered.")
            } else {
                echo("IDE Settings:")
                for ((k, v) in settings) {
                    echo("  $k = $v")
                }
            }
        }

        private class SettingsListSubcommand : CliktCommand(name = "list") {
            override fun help(context: Context): String = "List all current IDE settings"
            private val ctx by requireObject<LtiCommandContext>()
            override fun run() {
                val settings = ctx.getSettings()
                if (settings.isEmpty()) {
                    echo("No settings registered.")
                } else {
                    echo("IDE Settings:")
                    for ((k, v) in settings) {
                        echo("  $k = $v")
                    }
                }
            }
        }

        private class SettingsSetSubcommand : CliktCommand(name = "set") {
            override fun help(context: Context): String = "Update an IDE configuration setting"
            val key by option("-k", "--key", help = "Setting key").required()
            val value by option("-v", "--value", help = "Setting value").required()
            private val ctx by requireObject<LtiCommandContext>()

            override fun run() {
                val success = ctx.onUpdateSetting(key, value)
                if (success) {
                    echo("Setting '$key' updated to '$value'")
                } else {
                    echo("Failed to update setting '$key'")
                }
            }
        }
    }

    private class WorkspaceSubcommand : CliktCommand(name = "workspace") {
        override val invokeWithoutSubcommand: Boolean = true
        override fun help(context: Context): String = "Workspace and project information"
        private val ctx by requireObject<LtiCommandContext>()

        init {
            subcommands(
                WorkspaceInfoSubcommand(),
                WorkspaceListSubcommand(),
            )
        }

        override fun run() {
            if (currentContext.invokedSubcommand == null) {
                echo("Working Directory: ${ctx.getCwd()}")
            }
        }

        private class WorkspaceInfoSubcommand : CliktCommand(name = "info") {
            override fun help(context: Context): String = "Display current workspace information"
            private val ctx by requireObject<LtiCommandContext>()
            override fun run() {
                val cwd = ctx.getCwd()
                val dir = File(cwd)
                echo("Workspace Location: $cwd")
                echo("Directory exists: ${dir.exists()}")
                if (dir.exists() && dir.isDirectory) {
                    val count = dir.listFiles()?.size ?: 0
                    echo("Items in directory: $count")
                }
            }
        }

        private class WorkspaceListSubcommand : CliktCommand(name = "list") {
            override fun help(context: Context): String = "List files in current or specified path"
            val path by argument("path", help = "Optional path to list").optional()
            private val ctx by requireObject<LtiCommandContext>()

            override fun run() {
                val baseDir = ctx.getCwd()
                val target = path?.let { p ->
                    val f = File(p)
                    if (f.isAbsolute) f else File(baseDir, p)
                } ?: File(baseDir)

                if (!target.exists()) {
                    echo("Error: path does not exist: ${target.path}", err = true)
                    return
                }

                val items = target.listFiles() ?: emptyArray()
                if (items.isEmpty()) {
                    echo("(empty directory)")
                } else {
                    items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).forEach { item ->
                        val prefix = if (item.isDirectory) "[DIR] " else "      "
                        echo("$prefix${item.name}")
                    }
                }
            }
        }
    }

    private class SystemSubcommand : CliktCommand(name = "system") {
        override val invokeWithoutSubcommand: Boolean = true
        override fun help(context: Context): String = "Inspect system resources and runtime information"

        init {
            subcommands(
                SystemMemorySubcommand(),
                SystemInfoSubcommand(),
            )
        }

        override fun run() {
            if (currentContext.invokedSubcommand == null) {
                printSystemInfo()
            }
        }

        private fun printSystemInfo() {
            val os = System.getProperty("os.name") ?: "Unknown OS"
            val arch = System.getProperty("os.arch") ?: "Unknown Arch"
            val javaVer = System.getProperty("java.version") ?: "Unknown Java"
            val cpus = Runtime.getRuntime().availableProcessors()
            echo("OS: $os ($arch)")
            echo("Java: $javaVer")
            echo("Available Processors: $cpus")
        }

        private class SystemMemorySubcommand : CliktCommand(name = "memory") {
            override fun help(context: Context): String = "Display JVM memory usage"
            override fun run() {
                val runtime = Runtime.getRuntime()
                val total = runtime.totalMemory() / (1024 * 1024)
                val free = runtime.freeMemory() / (1024 * 1024)
                val used = total - free
                val max = runtime.maxMemory() / (1024 * 1024)
                echo("JVM Memory: ${used}MB used / ${total}MB allocated (max: ${max}MB)")
            }
        }

        private class SystemInfoSubcommand : CliktCommand(name = "info") {
            override fun help(context: Context): String = "Display runtime environment details"
            override fun run() {
                val os = System.getProperty("os.name") ?: "Unknown OS"
                val arch = System.getProperty("os.arch") ?: "Unknown Arch"
                val javaVer = System.getProperty("java.version") ?: "Unknown Java"
                val cpus = Runtime.getRuntime().availableProcessors()
                echo("OS: $os ($arch)")
                echo("Java: $javaVer")
                echo("CPUs: $cpus")
            }
        }
    }
}
