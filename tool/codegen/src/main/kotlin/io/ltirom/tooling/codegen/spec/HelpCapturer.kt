package io.ltirom.tooling.codegen.spec

import io.ltirom.tooling.core.DefaultToolResolver
import io.ltirom.tooling.core.ProcessEnvironment
import io.ltirom.tooling.core.ToolBinary
import io.ltirom.tooling.core.ToolId
import java.io.File
import java.util.concurrent.TimeUnit

class HelpCapturer(
    private val resolver: DefaultToolResolver,
    private val environment: ProcessEnvironment
) {

    fun captureAll(outputDir: File, selectedTools: Set<ToolId> = ToolId.entries.toSet()) {
        val stagingDir = File(outputDir.parentFile, "${outputDir.name}.staging")
        stagingDir.deleteRecursively()
        stagingDir.mkdirs()
        if (selectedTools.size != ToolId.entries.size) {
            outputDir.copyRecursively(stagingDir, overwrite = true)
            stagingDir.listFiles()
                ?.filter { file -> selectedTools.any { file.name.startsWith("tool_${it.logicalName}") } }
                ?.forEach(File::deleteRecursively)
        }
        var failure: Exception? = null
        for (toolId in selectedTools) {
            try {
                val binary = resolver.resolve(toolId)
                captureTool(toolId, binary, stagingDir)
            } catch (e: Exception) {
                println("Skipping help capture for ${toolId.logicalName}: ${e.message}")
                failure = failure ?: e
            }
        }
        if (failure != null) {
            stagingDir.deleteRecursively()
            throw IllegalStateException("Help capture failed; existing fixtures were preserved", failure)
        }

        outputDir.deleteRecursively()
        check(stagingDir.renameTo(outputDir)) {
            "Could not promote staged help captures to ${outputDir.absolutePath}"
        }
    }

    private fun captureTool(toolId: ToolId, binary: ToolBinary, outputDir: File) {
        println("Capturing help for tool: ${toolId.logicalName}")
        
        // 1. Version command (if supported)
        if (supportsVersion(toolId)) {
            val versionText = runCommand(binary, listOf("--version"))
            val normalizedVersion = normalizeOutput(versionText, binary.file.absolutePath, toolId.logicalName)
            File(outputDir, "tool_${toolId.logicalName}_version.txt").writeText(normalizedVersion)
        }

        // 2. Help command (recursive for tools with discoverable command trees)
        val visited = mutableSetOf<List<String>>()
        if (toolId == ToolId.GH || toolId == ToolId.AAPT2 ||
            toolId == ToolId.MKDTBOIMG || toolId == ToolId.APKTOOL ||
            toolId == ToolId.ADB || toolId == ToolId.FASTBOOT ||
            toolId == ToolId.AVBTOOL) {
            recursiveCapture(toolId, binary, outputDir, emptyList(), visited)
        } else {
            val helpText = runCommand(binary, listOf("--help"))
            val normalizedHelp = normalizeOutput(helpText, binary.file.absolutePath, toolId.logicalName)
            File(outputDir, "tool_${toolId.logicalName}_help.txt").writeText(normalizedHelp)
        }
    }

    private fun recursiveCapture(
        toolId: ToolId,
        binary: ToolBinary,
        outputDir: File,
        subcommands: List<String>,
        visited: MutableSet<List<String>>
    ) {
        if (subcommands.size > 4) return // Depth limit
        if (!visited.add(subcommands)) return

        val helpFlag = if ((toolId == ToolId.MKDTBOIMG || toolId == ToolId.APKTOOL) && subcommands.isNotEmpty()) "-h" else "--help"
        val args = subcommands + helpFlag
        val helpText = runCommand(binary, args)
        val normalizedHelp = normalizeOutput(helpText, binary.file.absolutePath, toolId.logicalName)
        
        val suffix = if (subcommands.isEmpty()) "" else "_" + subcommands.joinToString("_")
        val helpFile = File(outputDir, "tool_${toolId.logicalName}${suffix}_help.txt")
        helpFile.writeText(normalizedHelp)

        val discovered = if ((toolId == ToolId.MKDTBOIMG || toolId == ToolId.APKTOOL || toolId == ToolId.AVBTOOL) && subcommands.isNotEmpty()) {
            emptyList()
        } else {
            discoverSubcommands(normalizedHelp, toolId)
        }
        for (sub in discovered) {
            recursiveCapture(toolId, binary, outputDir, subcommands + sub, visited)
        }
    }

    private fun discoverSubcommands(helpText: String, toolId: ToolId): List<String> {
        if (toolId == ToolId.ADB || toolId == ToolId.FASTBOOT) {
            val candidates = if (toolId == ToolId.ADB) {
                setOf("devices", "help", "version", "connect", "disconnect", "pair", "forward", "reverse", "mdns",
                    "push", "pull", "sync", "shell", "emu", "install", "install-multiple", "install-multi-package",
                    "uninstall", "bugreport", "jdwp", "logcat", "disable-verity", "enable-verity", "keygen",
                    "wait-for", "get-state", "get-serialno", "get-devpath", "remount", "reboot", "sideload", "root",
                    "unroot", "usb", "tcpip", "start-server", "kill-server", "reconnect", "attach", "detach")
            } else {
                setOf("update", "flashall", "flash", "devices", "getvar", "reboot", "flashing", "erase", "format",
                    "set_active", "oem", "gsi", "wipe-super", "create-logical-partition", "delete-logical-partition",
                    "resize-logical-partition", "snapshot-update", "fetch", "boot", "flash:raw", "stage", "get_staged")
            }
            return candidates.filter { candidate ->
                val pattern = if (candidate == "wait-for") "^\\s+wait-for\\[" else "^\\s+${Regex.escape(candidate)}(?:\\s|$)"
                Regex(pattern, RegexOption.MULTILINE).containsMatchIn(helpText)
            }
        }
        val subcommands = mutableListOf<String>()
        var inActiveSection = false
        val activeSectionHeaders = setOf(
            "CORE COMMANDS",
            "GITHUB ACTIONS COMMANDS",
            "ADDITIONAL COMMANDS",
            "AVAILABLE COMMANDS",
            "SUBCOMMANDS",
            "SUBCOMMAND"
        )

        for (line in helpText.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Check if section header
            if (trimmed.all { it.isUpperCase() || it.isWhitespace() || it == '-' }) {
                inActiveSection = trimmed in activeSectionHeaders
                continue
            }
            if (trimmed.trimEnd(':').uppercase() in activeSectionHeaders) {
                inActiveSection = true
                continue
            }

            // argparse-style help lists commands in a brace group, for example
            // `{create,cfg_create,dump,help}`. Do not treat option groups as commands.
            Regex("""\{([a-z0-9_-]+(?:,[a-z0-9_-]+)+)\}""")
                .find(trimmed)
                ?.groupValues
                ?.get(1)
                ?.split(',')
                ?.filter { !it.startsWith('-') }
                ?.let { subcommands.addAll(it) }

            if (inActiveSection) {
                // Parse both gh's `auth:` format and aapt2's aligned command list.
                val colonMatch = Regex("""^\s*([a-z0-9-]+):\s+""").find(line)
                val alignedMatch = Regex("""^\s{1,}([a-z][a-z0-9_-]*)(?:\s{2,}|$)""").find(line)
                val command = colonMatch?.groupValues?.get(1) ?: alignedMatch?.groupValues?.get(1)
                if (command != null && command !in subcommands) subcommands.add(command)
            }

            // Apktool documents aliases in usage rows rather than a named
            // subcommand section, e.g. `apktool d|decode [options] ...`.
            if (toolId == ToolId.APKTOOL) {
                Regex("^\\s*\\S+\\s+([a-z][a-z0-9-]*(?:\\|[a-z][a-z0-9-]*)?)\\s+(?:\\[|<)")
                    .find(line)
                    ?.groupValues
                    ?.get(1)
                    ?.split('|')
                    ?.filter { it !in subcommands }
                    ?.let { subcommands.addAll(it) }
            }

            if (toolId == ToolId.ADB && trimmed.startsWith("wait-for[")) {
                subcommands.add("wait-for")
            }
        }

        return subcommands
    }

    private fun runCommand(binary: ToolBinary, args: List<String>): String {
        val cmd = if (binary.file.name.endsWith(".jar")) {
            listOf("java", "-jar", binary.file.absolutePath) + args
        } else {
            listOf(binary.file.absolutePath) + args
        }

        val pb = ProcessBuilder(cmd)
        environment.applyTo(pb)
        
        // Merge stdout and stderr for help captures as some tools output help to stderr (e.g. apktool, etc.)
        pb.redirectErrorStream(true)

        val process = pb.start()
        val finished = process.waitFor(15, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            throw IllegalStateException("Command timed out: $cmd")
        }

        val output = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return output
    }

    private fun supportsVersion(toolId: ToolId): Boolean {
        return toolId == ToolId.ADB || toolId == ToolId.FASTBOOT || toolId == ToolId.GH
    }

    private fun normalizeOutput(text: String, binaryPath: String, logicalName: String): String {
        val parentPath = File(binaryPath).parentFile.absolutePath
        return text
            .replace("\r\n", "\n")
            .replace(binaryPath, logicalName)
            .replace(parentPath, "<tools-bin>")
            .lines()
            .map { it.trimEnd() }
            .joinToString("\n")
            .trim()
    }
}
