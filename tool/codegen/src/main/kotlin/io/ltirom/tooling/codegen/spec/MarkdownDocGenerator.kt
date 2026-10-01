package io.ltirom.tooling.codegen.spec

import java.io.File

object MarkdownDocGenerator {

    fun generate(spec: ToolSpecification, outputDir: File) {
        outputDir.mkdirs()
        val file = File(outputDir, "${spec.tool.lowercase()}.md")
        
        val content = buildString {
            appendLine("# ${spec.tool.capitalize()} Tool Reference")
            appendLine()
            appendLine("## Metadata")
            appendLine("- **Executable**: `${spec.executable}`")
            appendLine("- **Platform Support**: ${spec.platform.joinToString { "`$it`" }}")
            appendLine("- **Default Risk**: `${spec.risk}`")
            if (spec.defaultTimeout != null) {
                appendLine("- **Default Timeout**: `${spec.defaultTimeout}`")
            }
            if (spec.sourceRevision != null) {
                appendLine("- **Source Revision**: `${spec.sourceRevision}`")
            }
            if (spec.binarySha256 != null) {
                appendLine("- **Binary SHA-256**: `${spec.binarySha256}`")
            }
            appendLine()

            if (spec.globalOptions.isNotEmpty()) {
                appendLine("## Global Options")
                appendLine("| Option | Property Name | Type | Description |")
                appendLine("|---|---|---|---|")
                for (opt in spec.globalOptions.sortedBy { it.name }) {
                    val aliases = if (opt.aliases.isNotEmpty()) ", " + opt.aliases.joinToString() else ""
                    appendLine("| `${opt.spelling}$aliases` | `${opt.name}` | `${opt.type}` | ${opt.description} |")
                }
                appendLine()
            }

            appendLine("## Commands")
            for (cmd in spec.commands.sortedBy { it.name }) {
                appendLine("### `${cmd.name}`")
                appendLine("${cmd.description}")
                appendLine()
                appendLine("- **Subcommand argv path**: `${cmd.subcommandPath.joinToString(" ")}`")
                if (cmd.risk != null) {
                    appendLine("- **Risk Override**: `${cmd.risk}`")
                }
                if (cmd.timeout != null) {
                    appendLine("- **Timeout**: `${cmd.timeout}`")
                }
                appendLine()

                if (cmd.options.isNotEmpty()) {
                    appendLine("#### Command Options")
                    appendLine("| Option | Property Name | Type | Description |")
                    appendLine("|---|---|---|---|")
                    for (opt in cmd.options.sortedBy { it.name }) {
                        val aliases = if (opt.aliases.isNotEmpty()) ", " + opt.aliases.joinToString() else ""
                        appendLine("| `${opt.spelling}$aliases` | `${opt.name}` | `${opt.type}` | ${opt.description} |")
                    }
                    appendLine()
                }

                if (cmd.arguments.isNotEmpty()) {
                    appendLine("#### Positional Arguments")
                    appendLine("| Argument | Type | Required | Description |")
                    appendLine("|---|---|---|---|")
                    for (arg in cmd.arguments) {
                        appendLine("| `${arg.name}` | `${arg.type}` | `${arg.required}` | ${arg.description} |")
                    }
                    appendLine()
                }
            }

            if (spec.quirks.isNotEmpty() || spec.explicitExclusions.isNotEmpty()) {
                appendLine("## Compatibility and Quirks")
                if (spec.quirks.isNotEmpty()) {
                    appendLine("### Quirks")
                    for (quirk in spec.quirks) {
                        appendLine("- $quirk")
                    }
                    appendLine()
                }
                if (spec.explicitExclusions.isNotEmpty()) {
                    appendLine("### Explicit Exclusions")
                    for (exc in spec.explicitExclusions) {
                        appendLine("- $exc")
                    }
                    appendLine()
                }
            }
        }
        file.writeText(content.trimEnd() + "\n")
    }
}
private fun String.capitalize() = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
