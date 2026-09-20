package io.ltirom.tooling.codegen.spec

import io.ltirom.tooling.core.DefaultToolResolver
import io.ltirom.tooling.core.ProcessEnvironment
import io.ltirom.tooling.core.ToolId
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

object Compiler {

    private val json = Json {
        ignoreUnknownKeys = false // Strict unknown-field rejection
        prettyPrint = true
    }

    fun runExtraction(
        toolsBinDir: File,
        projectRoot: File,
        capturedHelpDir: File,
        draftsDir: File,
        onlyTool: ToolId? = null
    ) {
        val resolver = DefaultToolResolver(toolsBinDir)
        val env = ProcessEnvironment(cwd = projectRoot)
        val capturer = HelpCapturer(resolver, env)

        println("=== Running Help Capture ===")
        capturer.captureAll(capturedHelpDir, onlyTool?.let { setOf(it) } ?: ToolId.entries.toSet())

        println("=== Generating Draft Specs ===")
        draftsDir.mkdirs()
        for (toolId in onlyTool?.let { setOf(it) } ?: ToolId.entries.toSet()) {
            val helpFile = File(capturedHelpDir, "tool_${toolId.logicalName}_help.txt")
            if (!helpFile.isFile) continue

            val versionFile = File(capturedHelpDir, "tool_${toolId.logicalName}_version.txt")
            val versionText = if (versionFile.isFile) versionFile.readText() else null

            try {
                val binary = resolver.resolve(toolId)
                val sha256 = calculateSha256(binary.file)
                val draft = SpecDraftGenerator.generateDraft(
                    toolId = toolId,
                    helpText = helpFile.readText(),
                    versionText = versionText,
                    binarySha256 = sha256,
                    sourceRevision = "HEAD",
                    capturedHelpDir = capturedHelpDir
                )
                
                val draftFile = File(draftsDir, "${toolId.logicalName}.json")
                draftFile.writeText(json.encodeToString(ToolSpecification.serializer(), draft))
                println("Generated draft spec for ${toolId.logicalName} to ${draftFile.absolutePath}")
            } catch (e: Exception) {
                println("Could not generate draft spec for ${toolId.logicalName}: ${e.message}")
            }
        }
    }

    fun runValidationAndGeneration(
        specsDir: File,
        capturedHelpDir: File,
        adaptersOutputDir: File,
        docsOutputDir: File,
        projectRootDir: File? = null
    ) {
        val specFiles = specsDir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()
        if (specFiles.isEmpty()) {
            println("No specifications found in ${specsDir.absolutePath}")
            return
        }

        val root = projectRootDir ?: findProjectRoot(adaptersOutputDir)
        val adapterBaseDir = if (adaptersOutputDir.name == "kotlin" || adaptersOutputDir.name == "adapters") {
            File(root, "tool/adapter")
        } else {
            adaptersOutputDir
        }

        println("=== Validating Specifications ===")
        val specs = mutableListOf<ToolSpecification>()
        for (file in specFiles) {
            try {
                val content = file.readText()
                val spec = json.decodeFromString(ToolSpecification.serializer(), content)
                SpecValidator.validate(spec)
                specs.add(spec)
                println("  Spec '${spec.tool}' is VALID.")
            } catch (e: Exception) {
                throw IllegalStateException("Validation failed for spec file ${file.name}: ${e.message}", e)
            }
        }

        println("=== Verifying Coverage ===")
        val specTools = specs.map { it.tool }.toSet()
        val missingSpecs = ToolId.entries.map { it.logicalName }.filterNot(specTools::contains)
        if (missingSpecs.isNotEmpty()) {
            throw IllegalStateException(
                "Coverage verification failed: missing specification(s) for ${missingSpecs.joinToString() }"
            )
        }
        for (spec in specs) {
            CoverageVerifier.verify(spec, capturedHelpDir)
            println("  Coverage verification passed for '${spec.tool}'.")
        }

        println("=== Generating Outputs (Run 1) ===")
        val tempDir1 = File(adapterBaseDir, "adapters_gen1")
        val tempDir2 = File(adapterBaseDir, "adapters_gen2")
        tempDir1.deleteRecursively()
        tempDir2.deleteRecursively()

        // Generate Run 1
        for (spec in specs) {
            val family = ToolFamily.forTool(spec.tool)
            val familyGen1 = File(tempDir1, "${family.directoryName}/src/main/kotlin")
            KotlinModelGenerator.generate(spec, familyGen1)
            CliktGenerator.generate(spec, familyGen1)
            MarkdownDocGenerator.generate(spec, docsOutputDir)
        }

        println("=== Generating Outputs (Run 2: Reproducibility Verification) ===")
        // Generate Run 2
        for (spec in specs) {
            val family = ToolFamily.forTool(spec.tool)
            val familyGen2 = File(tempDir2, "${family.directoryName}/src/main/kotlin")
            KotlinModelGenerator.generate(spec, familyGen2)
            CliktGenerator.generate(spec, familyGen2)
        }

        // Compare Run 1 vs Run 2
        println("=== Checking Byte-for-byte Reproducibility ===")
        compareDirectories(tempDir1, tempDir2)
        println("  Reproducibility check PASSED.")

        // Promote Run 1 to family output dirs under adapterBaseDir
        for (family in ToolFamily.entries) {
            val familyDestDir = File(adapterBaseDir, "${family.directoryName}/src/main/kotlin")
            val familySrcDir = File(tempDir1, "${family.directoryName}/src/main/kotlin")
            familyDestDir.deleteRecursively()
            if (familySrcDir.exists()) {
                familySrcDir.copyRecursively(familyDestDir, overwrite = true)
            }
        }
        tempDir1.deleteRecursively()
        tempDir2.deleteRecursively()
        println("Generated adapters promoted to family directories under ${adapterBaseDir.absolutePath}")

        // Generate ToolMetadataRegistry in tool:metadata
        val metadataSourceDir = File(root, "tool/metadata/src/main/kotlin")
        val registryFile = File(metadataSourceDir, "io/ltirom/tooling/core/ToolMetadataRegistry.kt")
        registryFile.parentFile.mkdirs()
        val specsJsonString = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(ToolSpecification.serializer()), specs)

        // Split specsJsonString into chunks of 60000 characters to bypass JVM 65KB constant pool limit
        val chunkLimit = 60000
        val chunks = mutableListOf<String>()
        var startIndex = 0
        while (startIndex < specsJsonString.length) {
            val endIndex = minOf(startIndex + chunkLimit, specsJsonString.length)
            chunks.add(specsJsonString.substring(startIndex, endIndex))
            startIndex = endIndex
        }

        val tripleQuote = "\"\"\""
        val chunksContent = chunks.joinToString(",\n") { chunk ->
            val escapedChunk = chunk.replace("$", "\${'$'}")
            "        $tripleQuote$escapedChunk$tripleQuote"
        }
        val platformEntries = specs.sortedBy { it.tool }.joinToString(",\n") { spec ->
            val values = spec.platform.sorted().joinToString(", ") { "\"$it\"" }
            "                    \"${spec.tool}\" to setOf($values)"
        }

        val registryContent = """
            // This file is generated by tooling-codegen — do not edit by hand.
            // Regenerate via `./gradlew :tool:codegen:generateToolAdapters`.
            package io.ltirom.tooling.core

            public object ToolMetadataRegistry {
                public val EXPECTED_SHA256: Map<String, String> = mapOf(
${specs.filter { it.binarySha256 != null }.sortedBy { it.tool }.joinToString(",\n") { "                    \"${it.tool}\" to \"${it.binarySha256}\"" }}
                )
                public val EXPECTED_PLATFORMS: Map<String, Set<String>> = mapOf(
$platformEntries
                )
                private val CHUNKS: Array<String> = arrayOf(
$chunksContent
                )
                public val METADATA_JSON: String by lazy { CHUNKS.joinToString("") }
            }
        """.trimIndent()
        registryFile.writeText(registryContent)
        println("Generated ToolMetadataRegistry to ${registryFile.absolutePath} with ${chunks.size} chunks")

        // Generate launcher aliases
        val aliasesDir = File(root, "tool/aliases")
        aliasesDir.deleteRecursively()
        aliasesDir.mkdirs()

        for (spec in specs) {
            val toolName = spec.tool
            // 1. Linux alias (lti-toolName)
            val shFile = File(aliasesDir, "lti-$toolName")
            shFile.writeText("""
                #!/usr/bin/env bash
                DIR="${'$'}(cd "${'$'}(dirname "${'$'}{BASH_SOURCE[0]}")" && pwd)"
                LTI_BIN="${'$'}DIR/lti"
                if [ ! -x "${'$'}LTI_BIN" ]; then
                    LTI_BIN="lti"
                fi
                exec "${'$'}LTI_BIN" tools $toolName "${'$'}@"
            """.trimIndent().trim() + "\n")
            shFile.setExecutable(true, false)

            // 2. Windows BAT alias (lti-toolName.bat)
            val batFile = File(aliasesDir, "lti-$toolName.bat")
            batFile.writeText("""
                @echo off
                setlocal
                set "DIR=%~dp0"
                set "LTI_BIN=%DIR%lti.bat"
                if not exist "%LTI_BIN%" set "LTI_BIN=lti"
                "%LTI_BIN%" tools $toolName %*
            """.trimIndent().trim() + "\r\n")

            // 3. PowerShell alias (lti-toolName.ps1)
            val psFile = File(aliasesDir, "lti-$toolName.ps1")
            psFile.writeText("""
                ${'$'}dir = Split-Path -Parent ${'$'}MyInvocation.MyCommand.Path
                ${'$'}ltiBin = Join-Path ${'$'}dir "lti"
                if (-not (Test-Path ${'$'}ltiBin)) {
                    ${'$'}ltiBin = "lti"
                }
                & ${'$'}ltiBin tools $toolName @args
                exit ${'$'}LASTEXITCODE
            """.trimIndent().trim() + "\r\n")
        }
        println("Generated launcher aliases for ${specs.size} tools to ${aliasesDir.absolutePath}")

        // Generate lti-completion.bash
        val completionFile = File(aliasesDir, "lti-completion.bash")
        val completionContent = buildString {
            appendLine("# Sourcing canonical lti completion first")
            appendLine("if command -v lti >/dev/null 2>&1; then")
            appendLine("    eval \"\$(env _LTI_COMPLETE=bash_source lti)\"")
            appendLine("fi")
            appendLine()
            appendLine("# Delegate completions for launcher aliases")
            for (spec in specs) {
                val toolName = spec.tool
                val funcName = "_lti_tools_" + toolName.replace("-", "_")
                appendLine("complete -F $funcName lti-$toolName")
            }
        }
        completionFile.writeText(completionContent)
        println("Generated completion delegate script to ${completionFile.absolutePath}")
    }

    fun promoteDraft(toolName: String, specsDir: File, draftsDir: File) {
        val specFile = File(specsDir, "$toolName.json")
        val draftFile = File(draftsDir, "$toolName.json")
        require(specFile.isFile) { "Committed specification is missing: ${specFile.absolutePath}" }
        require(draftFile.isFile) { "Draft specification is missing: ${draftFile.absolutePath}" }

        val current = json.decodeFromString(ToolSpecification.serializer(), specFile.readText())
        val draft = json.decodeFromString(ToolSpecification.serializer(), draftFile.readText())
        require(current.tool == draft.tool && current.tool == toolName) {
            "Draft/spec tool mismatch: ${current.tool} vs ${draft.tool}"
        }

        val draftCommands = draft.commands.associateBy { it.subcommandPath }
        val draftGlobalOptions = draft.globalOptions.associateBy { it.name }
        fun mergeOptions(options: List<OptionSpec>, freshOptions: Map<String, OptionSpec>): List<OptionSpec> =
            options.map { option ->
                val updated = freshOptions[option.name] ?: return@map option
                option.copy(
                    description = updated.description,
                    type = updated.type,
                    repeatable = updated.repeatable,
                    valueType = updated.valueType
                )
            }
        val mergedCommands = current.commands.map { command ->
            val fresh = draftCommands[command.subcommandPath] ?: return@map command
            val draftOptions = fresh.options.associateBy { it.name }
            command.copy(
                options = mergeOptions(command.options, draftOptions),
                arguments = if (fresh.arguments.isEmpty()) command.arguments else fresh.arguments
            )
        }
        val promoted = current.copy(
            helpSha256 = draft.helpSha256,
            globalOptions = mergeOptions(current.globalOptions, draftGlobalOptions),
            commands = mergedCommands
        )
        specFile.writeText(json.encodeToString(ToolSpecification.serializer(), promoted))
        println("Promoted reviewed option metadata for '$toolName' into ${specFile.absolutePath}")
    }

    private fun compareDirectories(dir1: File, dir2: File) {
        val files1 = dir1.walkTopDown().filter { it.isFile }.toList()
        val files2 = dir2.walkTopDown().filter { it.isFile }.toList()

        if (files1.size != files2.size) {
            throw IllegalStateException("Reproducibility failure: directory sizes mismatch (gen1 has ${files1.size} files, gen2 has ${files2.size} files)")
        }

        for (f1 in files1) {
            val relPath = f1.relativeTo(dir1).path
            val f2 = File(dir2, relPath)
            if (!f2.isFile) {
                throw IllegalStateException("Reproducibility failure: file $relPath missing in gen2")
            }
            if (f1.readText() != f2.readText()) {
                throw IllegalStateException("Reproducibility failure: file content mismatch in $relPath")
            }
        }
    }

    private fun calculateSha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                md.update(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun findProjectRoot(start: File): File {
        var current: File? = start.canonicalFile
        while (current != null) {
            if (File(current, "settings.gradle.kts").isFile) {
                return current
            }
            current = current.parentFile
        }
        return start.parentFile ?: start
    }
}
