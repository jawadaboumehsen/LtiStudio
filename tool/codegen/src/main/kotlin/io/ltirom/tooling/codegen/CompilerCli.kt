package io.ltirom.tooling.codegen

import io.ltirom.tooling.codegen.spec.Compiler
import io.ltirom.tooling.core.ToolId
import java.io.File

public fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printUsage()
        return
    }

    val command = args[0]
    val startDir = File(System.getProperty("user.dir")).absoluteFile
    
    // Find project root by traversing upwards until settings.gradle.kts is found
    var current = startDir
    var rootDir = startDir
    while (current != null) {
        if (File(current, "settings.gradle.kts").isFile) {
            rootDir = current
            break
        }
        current = current.parentFile
    }
    
    val projectRoot = rootDir
    val toolsBinDir = File(projectRoot, "out/tools/bin").canonicalFile
    val specsDir = File(projectRoot, "tool/specs")
    val draftsDir = File(projectRoot, "tool/specs/drafts")
    val capturedHelpDir = File(projectRoot, "tool/captured-help")
    val adapterBaseDir = File(projectRoot, "tool/adapter")
    val docsOutputDir = File(projectRoot, "tool/docs")

    when (command) {
        "extract" -> {
            println("Extracting tool specs...")
            Compiler.runExtraction(
                toolsBinDir = toolsBinDir,
                projectRoot = projectRoot,
                capturedHelpDir = capturedHelpDir,
                draftsDir = draftsDir,
                onlyTool = args.getOrNull(1)?.let { requested ->
                    ToolId.entries.firstOrNull { it.logicalName == requested }
                        ?: error("Unknown tool '$requested'")
                }
            )
        }
        "generate" -> {
            println("Validating and generating adapters/docs...")
            Compiler.runValidationAndGeneration(
                specsDir = specsDir,
                capturedHelpDir = capturedHelpDir,
                adaptersOutputDir = adapterBaseDir,
                docsOutputDir = docsOutputDir,
                projectRootDir = projectRoot
            )
        }
        "promote" -> {
            val toolName = args.getOrNull(1) ?: error("promote requires a tool name")
            Compiler.promoteDraft(toolName, specsDir, draftsDir)
        }
        else -> {
            println("Unknown command: $command")
            printUsage()
        }
    }
}

private fun printUsage() {
    println("Usage: CompilerCli <command>")
    println("Commands:")
    println("  extract [tool] - Capture tool help and generate draft JSON specifications")
    println("  generate  - Validate JSON specifications, verify coverage, and generate adapters/docs")
    println("  promote <tool> - Merge reviewed draft option metadata into a committed spec")
}
