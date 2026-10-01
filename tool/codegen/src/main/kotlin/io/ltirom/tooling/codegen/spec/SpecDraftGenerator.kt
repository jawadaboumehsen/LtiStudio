package io.ltirom.tooling.codegen.spec

import io.ltirom.tooling.core.ToolId
import io.ltirom.tooling.core.ToolRisk
import java.io.File
import java.security.MessageDigest

object SpecDraftGenerator {

    fun generateDraft(
        toolId: ToolId,
        helpText: String,
        versionText: String?,
        binarySha256: String,
        sourceRevision: String,
        capturedHelpDir: File
    ): ToolSpecification {
        val globalOptions = parseOptions(helpText)
        val commands = mutableListOf<CommandSpec>()

        // Root help for tools with subcommands is itself a command graph node, not
        // merely a bag of global options. Keep the graph discovery in one place so
        // capture and draft generation cannot silently disagree.
        val rootSubcommands = discoverSubcommands(helpText, toolId)
        for (subcommand in rootSubcommands) {
            val helpFile = File(capturedHelpDir, "tool_${toolId.logicalName}_${subcommand}_help.txt")
            val fileText = if (helpFile.isFile) helpFile.readText() else ""
            commands.add(
                CommandSpec(
                    name = toCamelCase(subcommand),
                    subcommandPath = listOf(subcommand),
                    description = fileText.lines().firstOrNull { it.trim().isNotEmpty() }?.trim()
                        ?: "Subcommand $subcommand",
                    options = parseOptions(fileText),
                    arguments = parseArguments(fileText),
                )
            )
        }

        val prefix = "tool_${toolId.logicalName}_"
        val suffix = "_help.txt"
        val helpFiles = capturedHelpDir.listFiles { _, name ->
            name.startsWith(prefix) && name.endsWith(suffix) && name != "tool_${toolId.logicalName}_help.txt"
        } ?: emptyArray()

        for (file in helpFiles.sortedBy { it.name }) {
            val relativeName = file.name.substring(prefix.length, file.name.length - suffix.length)
            val rootCommand = rootSubcommands
                .filter { relativeName == it || relativeName.startsWith("${it}_") }
                .maxByOrNull { it.length }
            val subPath = if (rootCommand == null) {
                relativeName.split("_")
            } else {
                listOf(rootCommand) + relativeName.removePrefix(rootCommand)
                    .removePrefix("_")
                    .takeIf { it.isNotEmpty() }
                    ?.split("_")
                    .orEmpty()
            }
            val cmdName = toCamelCase(relativeName)
            val fileText = file.readText()
            
            val cmdOptions = parseOptions(fileText)
            val firstLine = fileText.lines().firstOrNull { it.trim().isNotEmpty() } ?: "Subcommand $relativeName"
            
            if (commands.any { it.subcommandPath == subPath }) continue
            commands.add(
                CommandSpec(
                    name = cmdName,
                    subcommandPath = subPath,
                    description = firstLine.trim(),
                    options = cmdOptions,
                    arguments = parseArguments(fileText),
                    risk = null,
                    timeout = null,
                    outputParser = null,
                    artifacts = emptyList()
                )
            )
        }

        // Several Unix-style tools expose their complete interface as options and
        // positional operands rather than subcommands.  An empty command list would
        // generate an unusable facade, so model that surface explicitly as one raw
        // execution command while retaining every parsed option above.
        if (commands.isEmpty()) {
            commands.add(
                CommandSpec(
                    name = "execute",
                    subcommandPath = listOf("_self"),
                    description = "Execute the tool with its options and positional operands",
                    options = emptyList(),
                    arguments = listOf(
                        ArgumentSpec(
                            name = "positionalArgs",
                            description = "Tool-specific positional operands",
                            type = OptionType.STRING,
                            required = false,
                            repeatable = true
                        )
                    ),
                    risk = null,
                    outputParser = "identity",
                    outputType = "String"
                )
            )
        }
        
        return ToolSpecification(
            schemaVersion = 1,
            tool = toolId.logicalName,
            executable = toolId.logicalName,
            aliases = emptyList(),
            platform = listOf("linux-x86_64"),
            sourceRevision = sourceRevision,
            binarySha256 = binarySha256,
            versionText = versionText,
            helpSha256 = sha256(helpText),
            commands = commands,
            globalOptions = globalOptions,
            exitCodes = listOf(
                ExitCodeSpec(0, "Success", true),
                ExitCodeSpec(1, "Error/Failure", false)
            ),
            defaultTimeout = "30s",
            risk = ToolRisk.READ_ONLY,
            outputParser = null,
            quirks = emptyList(),
            explicitExclusions = emptyList()
        )
    }

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun discoverSubcommands(helpText: String, toolId: ToolId): List<String> {
        val result = linkedSetOf<String>()
        var inSection = false
        for (line in helpText.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.all { it.isUpperCase() || it.isWhitespace() || it == '-' }) {
                inSection = trimmed in setOf("SUBCOMMANDS", "SUBCOMMAND", "AVAILABLE COMMANDS", "CORE COMMANDS")
                continue
            }
            if (trimmed.trimEnd(':').uppercase() in setOf("SUBCOMMANDS", "SUBCOMMAND", "AVAILABLE COMMANDS", "CORE COMMANDS")) {
                inSection = true
                continue
            }
            Regex("""\{([a-z0-9_-]+(?:,[a-z0-9_-]+)+)\}""")
                .find(trimmed)?.groupValues?.get(1)?.split(',')
                ?.filter { !it.startsWith('-') }?.forEach(result::add)
            if (inSection) {
                Regex("""^\s*([a-z][a-z0-9_-]*)(?::|\s{2,})""")
                    .find(line)?.groupValues?.get(1)?.let(result::add)
            }
            if (toolId == ToolId.APKTOOL) {
                Regex("^\\s*\\S+\\s+([a-z][a-z0-9-]*(?:\\|[a-z][a-z0-9-]*)?)\\s+(?:\\[|<)")
                    .find(line)?.groupValues?.get(1)?.split('|')?.forEach(result::add)
            }
        }
        return result.toList()
    }

    fun parseOptions(text: String): List<OptionSpec> {
        val options = mutableListOf<OptionSpec>()
        val tokenRegex = Regex("""(--[a-zA-Z0-9_-]+(?:\[?=[a-zA-Z0-9#<>|\[\]_=,-]+\]?)?|-[a-zA-Z0-9#<>-]+)""")
        
        var inOptionsSection = true
        val lines = text.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                i++
                continue
            }

            val isHeader = (line.startsWith(" ") == false && trimmed.endsWith(":")) ||
                           (trimmed.all { it.isUpperCase() || it.isWhitespace() || it == '-' } && trimmed.length > 2)

            if (isHeader) {
                val lower = trimmed.lowercase()
                inOptionsSection = lower.contains("option") || lower.contains("flag")
                i++
                continue
            }

            if (!inOptionsSection) {
                i++
                continue
            }

            if (line.take(1).any { it.isWhitespace() } && trimmed.startsWith("-")) {
                val tokens = mutableListOf<String>()
                var remaining = trimmed
                
                for (t in 0..1) {
                    val match = tokenRegex.find(remaining)
                    if (match != null && remaining.startsWith(match.value)) {
                        tokens.add(match.value)
                        remaining = remaining.substring(match.value.length).trim().removePrefix(",").trim()
                    } else {
                        break
                    }
                }
                
                if (tokens.isNotEmpty()) {
                    var longToken = tokens.firstOrNull { it.startsWith("--") }
                    var shortToken = tokens.firstOrNull { it.startsWith("-") && !it.startsWith("--") }
                    
                    var argType: String? = null
                    if (shortToken != null && shortToken.length > 2) {
                        argType = shortToken.substring(2)
                        shortToken = shortToken.substring(0, 2)
                    }

                    val cleanLong = if (longToken != null) {
                        longToken.substringBefore("=").substringBefore("[")
                    } else {
                        shortToken!!
                    }

                    if (longToken != null && longToken.contains("=")) {
                        argType = longToken.substringAfter("=").removeSuffix("]")
                    } else if (argType == null && remaining.isNotEmpty() && !remaining.startsWith("-")) {
                        val word = remaining.substringBefore(" ")
                        val valueMarker = word.matches(Regex("""^<[^>]+>$""")) ||
                            word.matches(Regex("""^[A-Z0-9<>|_\\[\\]-]+$""")) ||
                            word.lowercase() in setOf("string", "expression", "fields", "file", "dir", "path", "num", "number")
                        if (valueMarker) {
                            remaining = remaining.substring(word.length).trim()
                            argType = word
                        }
                    }

                    if (!cleanLong.all { it == '-' }) {
                        var description = remaining
                        if (description.isEmpty() && i + 1 < lines.size) {
                            val nextLine = lines[i + 1]
                            if (nextLine.startsWith(" ") && nextLine.trim().isNotEmpty() && !nextLine.trim().startsWith("-")) {
                                description = nextLine.trim()
                                i++
                            }
                        }
                        while (i + 1 < lines.size) {
                            val nextLine = lines[i + 1]
                            val nextTrimmed = nextLine.trim()
                            if (!nextLine.startsWith(" ") || nextTrimmed.isEmpty() || nextTrimmed.startsWith("-")) break
                            description = listOf(description, nextTrimmed).filter(String::isNotEmpty).joinToString(" ")
                            i++
                        }

                        val name = toCamelCase(cleanLong.removePrefix("--"))
                        if (!options.any { it.spelling == cleanLong } && !options.any { it.name == name }) {
                            val resolvedAliases = if (shortToken != null && cleanLong != shortToken) {
                                val isDuplicateAlias = options.any { existing ->
                                    existing.spelling == shortToken || existing.aliases.contains(shortToken)
                                }
                                if (isDuplicateAlias) emptyList() else listOf(shortToken)
                            } else {
                                emptyList()
                            }

                            val type = if (argType != null) {
                                val normalizedArgType = argType.trim('<', '>')
                                if (normalizedArgType.contains("int", ignoreCase = true) ||
                                    normalizedArgType.equals("num", ignoreCase = true) ||
                                    normalizedArgType.equals("number", ignoreCase = true)
                                ) OptionType.INT
                                else if (normalizedArgType.equals("path", ignoreCase = true) ||
                                    normalizedArgType.equals("file", ignoreCase = true) ||
                                    normalizedArgType.equals("dir", ignoreCase = true)
                                ) OptionType.PATH
                                else OptionType.STRING
                            } else {
                                OptionType.BOOLEAN
                            }

                            options.add(
                                OptionSpec(
                                    name = name,
                                    spelling = cleanLong,
                                    aliases = resolvedAliases,
                                    description = description,
                                    type = type,
                                    choices = null,
                                    default = null,
                                    required = false,
                                    repeatable = description.contains("Can be specified multiple times", ignoreCase = true),
                                    cardinality = null,
                                    mutuallyExclusiveGroup = null,
                                    requiredTogetherGroup = null,
                                    risk = null,
                                    valueType = if (type == OptionType.PATH) "path" else null,
                                    redact = false
                                )
                            )
                        }
                    }
                }
            }
            i++
        }
        return options
    }

    fun parseArguments(text: String): List<ArgumentSpec> {
        val usage = text.lineSequence().firstOrNull { line ->
            val trimmed = line.trim()
            line.contains("<") &&
                !trimmed.startsWith("-") &&
                !trimmed.contains("copyright", ignoreCase = true) &&
                !trimmed.contains("apache", ignoreCase = true) &&
                (trimmed.contains("[options]", ignoreCase = true) ||
                    trimmed.contains("[flags]", ignoreCase = true) ||
                    trimmed.matches(Regex("^[a-zA-Z0-9_.-]+\\s+.*<.*>.*$")))
        } ?: return emptyList()
        return Regex("<([^>]+)>").findAll(usage).map { match ->
            val marker = match.groupValues[1]
            val normalized = marker.lowercase()
            val isPath = normalized.contains("dir") || normalized.contains("file") || normalized.contains("path")
            ArgumentSpec(
                name = toCamelCase(marker.replace(Regex("[^a-zA-Z0-9_-]"), "-")),
                description = "Positional $marker accepted by the tool",
                type = if (isPath) OptionType.PATH else OptionType.STRING,
                required = true,
                valueType = if (isPath) "path" else null
            )
        }.toList().distinctBy { it.name }
    }

    private fun toCamelCase(text: String): String {
        val parts = text.split("-", "_").filter { it.isNotEmpty() }
        val raw = (parts.firstOrNull() ?: "value") + parts.drop(1).joinToString("") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        val sanitized = raw.replace(Regex("[^a-zA-Z0-9]"), "")
        return if (sanitized.firstOrNull()?.isDigit() == true) "value$sanitized" else sanitized
    }
}
