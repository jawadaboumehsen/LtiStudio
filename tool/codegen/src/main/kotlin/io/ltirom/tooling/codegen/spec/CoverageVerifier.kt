package io.ltirom.tooling.codegen.spec

import io.ltirom.tooling.core.ToolId
import java.io.File

object CoverageVerifier {

    fun verify(spec: ToolSpecification, capturedHelpDir: File) {
        val errors = mutableListOf<String>()
        val toolId = ToolId.entries.firstOrNull { it.logicalName == spec.tool } ?: return
        
        // 1. Verify command coverage from the pinned root help.
        if (toolId == ToolId.GH || toolId == ToolId.ADB || toolId == ToolId.FASTBOOT) {
            val rootHelpFile = File(capturedHelpDir, "tool_${spec.tool}_help.txt")
            if (rootHelpFile.isFile) {
                val rootHelpText = rootHelpFile.readText()
                val rootSubcommands = discoverRootCommands(toolId, rootHelpText)
                
                for (sub in rootSubcommands) {
                    val subPath = listOf(sub)
                    verifySubcommandPathRecursively(spec, toolId, subPath, capturedHelpDir, errors)
                }
            }
        }

        // 2. Verify Option Coverage for each defined Command
        verifyOptionCoverage(spec, toolId, capturedHelpDir, errors)

        if (errors.isNotEmpty()) {
            throw IllegalStateException(
                "Coverage verification failed for tool '${spec.tool}':\n" +
                errors.joinToString("\n") { "  - $it" }
            )
        }
    }

    private fun verifySubcommandPathRecursively(
        spec: ToolSpecification,
        toolId: ToolId,
        path: List<String>,
        capturedHelpDir: File,
        errors: MutableList<String>
    ) {
        val pathStr = path.joinToString(" ")
        
        // Check if excluded
        if (spec.explicitExclusions.any { it == pathStr || it.startsWith(pathStr + " ") }) {
            return
        }

        // Check if command exists in spec
        val cmdExists = spec.commands.any { it.subcommandPath == path }
        if (!cmdExists) {
            // Check if there are deeper subcommands under this path
            val suffix = "_" + path.joinToString("_")
            val helpFile = File(capturedHelpDir, "tool_${spec.tool}${suffix}_help.txt")
            val hasNested = helpFile.isFile && discoverSubcommandsFromHelp(toolId, helpFile.readText()).isNotEmpty()
            
            // If it doesn't have nested subcommands, it should definitely be mapped as an executable command
            if (!hasNested) {
                errors.add("Missing command in specification: '$pathStr'")
            }
        }

        // Search deeper subcommands in captured help file
        val suffix = "_" + path.joinToString("_")
        val helpFile = File(capturedHelpDir, "tool_${spec.tool}${suffix}_help.txt")
        if (helpFile.isFile) {
            val nestedSubs = discoverSubcommandsFromHelp(toolId, helpFile.readText())
            for (sub in nestedSubs) {
                verifySubcommandPathRecursively(spec, toolId, path + sub, capturedHelpDir, errors)
            }
        }
    }

    private fun verifyOptionCoverage(spec: ToolSpecification, toolId: ToolId, capturedHelpDir: File, errors: MutableList<String>) {
        // Global options coverage
        val rootHelpFile = File(capturedHelpDir, "tool_${spec.tool}_help.txt")
        if (rootHelpFile.isFile) {
            val parsedGlobalOptions = parseOptionSpellings(toolId, rootHelpFile.readText())
            val specGlobalOptions = spec.globalOptions.flatMap { listOf(it.spelling) + it.aliases }.toSet()
            
            for (opt in parsedGlobalOptions) {
                if (opt !in specGlobalOptions && opt !in spec.explicitExclusions && opt != "-h" && opt != "--help" && opt != "--version") {
                    errors.add("Missing global option in specification: '$opt'")
                }
            }
        }

        // Command options coverage
        for (cmd in spec.commands) {
            val suffix = if (cmd.subcommandPath.isEmpty()) "" else "_" + cmd.subcommandPath.joinToString("_")
            val helpFile = File(capturedHelpDir, "tool_${spec.tool}${suffix}_help.txt")
            if (helpFile.isFile) {
                val parsedOptions = parseOptionSpellings(toolId, helpFile.readText())
                val cmdOptions = cmd.options.flatMap { listOf(it.spelling) + it.aliases }.toSet()
                val globalOptions = spec.globalOptions.flatMap { listOf(it.spelling) + it.aliases }.toSet()
                val allSpecOptions = cmdOptions + globalOptions
                
                val cmdPath = cmd.subcommandPath.joinToString(" ")
                for (opt in parsedOptions) {
                    if (opt !in allSpecOptions && opt !in spec.explicitExclusions && opt != "-h" && opt != "--help" && opt != "--version") {
                        errors.add("Missing option in command '$cmdPath': '$opt'")
                    }
                }
            }
        }
    }

    private fun discoverRootCommands(toolId: ToolId, helpText: String): List<String> {
        if (toolId == ToolId.ADB) {
            return setOf(
                "devices", "help", "version", "connect", "disconnect", "pair", "forward", "reverse", "mdns",
                "push", "pull", "sync", "shell", "emu", "install", "install-multiple", "install-multi-package",
                "uninstall", "bugreport", "jdwp", "logcat", "disable-verity", "enable-verity", "keygen",
                "wait-for", "get-state", "get-serialno", "get-devpath", "remount", "reboot", "sideload", "root",
                "unroot", "usb", "tcpip", "start-server", "kill-server", "reconnect", "attach", "detach"
            ).filter { command -> Regex("^\\s+${Regex.escape(command)}(?:\\s|$)", RegexOption.MULTILINE).containsMatchIn(helpText) }
        }
        if (toolId == ToolId.FASTBOOT) {
            return setOf(
                "update", "flashall", "flash", "devices", "getvar", "reboot", "flashing", "erase", "format",
                "set_active", "oem", "gsi", "wipe-super", "create-logical-partition", "delete-logical-partition",
                "resize-logical-partition", "snapshot-update", "fetch", "boot", "flash:raw", "stage", "get_staged"
            ).filter { command -> Regex("^\\s+${Regex.escape(command)}(?:\\s|$)", RegexOption.MULTILINE).containsMatchIn(helpText) }
        }
        return discoverSubcommandsFromHelp(toolId, helpText)
    }

    private fun discoverSubcommandsFromHelp(toolId: ToolId, helpText: String): List<String> {
        val subcommands = mutableListOf<String>()
        var inActiveSection = false
        val activeSectionHeaders = setOf(
            "CORE COMMANDS",
            "GITHUB ACTIONS COMMANDS",
            "ADDITIONAL COMMANDS",
            "AVAILABLE COMMANDS"
        )

        for (line in helpText.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.all { it.isUpperCase() || it.isWhitespace() || it == '-' }) {
                inActiveSection = trimmed in activeSectionHeaders
                continue
            }

            if (inActiveSection) {
                val match = Regex("""^\s*([a-z0-9-]+):\s+""").find(line)
                if (match != null) {
                    subcommands.add(match.groupValues[1])
                }
            }
        }
        return subcommands
    }

    private fun parseOptionSpellings(toolId: ToolId, helpText: String): List<String> {
        val spellings = mutableListOf<String>()
        var inOptionsSection = true
        for (line in helpText.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val isHeader = (line.startsWith(" ") == false && trimmed.endsWith(":")) ||
                           (trimmed.all { it.isUpperCase() || it.isWhitespace() || it == '-' } && trimmed.length > 2)

            if (isHeader) {
                val lower = trimmed.lowercase()
                inOptionsSection = lower.contains("option") || lower.contains("flag")
                continue
            }

            if (!inOptionsSection) continue

            if (toolId == ToolId.ADB || toolId == ToolId.FASTBOOT) {
                Regex("(?<![A-Za-z0-9])(?:--[A-Za-z0-9_-]+|-[A-Za-z0-9])")
                    .findAll(line)
                    .map { it.value.substringBefore("=").substringBefore("[") }
                    .filter { it != "--" }
                    .forEach { spellings.add(it) }
            } else {
                Regex("^\\s*(?:(-[a-zA-Z0-9]),\\s*)?(--[a-zA-Z0-9_-]+)")
                    .find(line)?.let { match ->
                        match.groupValues.getOrNull(1)?.takeIf(String::isNotEmpty)?.let(spellings::add)
                        match.groupValues[2].substringBefore("=").substringBefore("[")
                            .takeIf { value -> value.any { it != '-' } }
                            ?.let(spellings::add)
                    }
            }
        }
        return spellings
    }
}
