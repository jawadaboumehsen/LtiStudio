/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.sdk.patchmod.runtime

import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.domain.plugin.PayloadCompilationReceipt
import org.ide.lti.core.domain.plugin.PayloadCompilationRequest
import org.ide.lti.core.domain.plugin.PayloadCompilationResult
import org.ide.lti.core.domain.plugin.PayloadCompilerCodes
import org.ide.lti.core.domain.plugin.PayloadCompilerPort
import org.ide.lti.core.domain.plugin.ToolIdentity
import org.ide.lti.core.model.run.StageId
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture

/**
 * Adapter compiling author Kotlin hook sources into smali payloads using external subprocesses
 * (kotlinc, d8, baksmali).
 */
@Suppress("ReturnCount", "TooManyFunctions", "CyclomaticComplexMethod")
class PayloadCompilerAdapter(private val toolPaths: Map<String, String> = defaultToolPaths()) : PayloadCompilerPort {

    override suspend fun compile(request: PayloadCompilationRequest): PayloadCompilationResult {
        val missingToolError = validateToolPaths()
        if (missingToolError != null) {
            return PayloadCompilationResult.Failed(ValidationReport(listOf(missingToolError)))
        }

        val tempClassesDir = Files.createTempDirectory("lti-classes-").toFile()
        val tempDexDir = Files.createTempDirectory("lti-dex-").toFile()
        try {
            val kotlincResult = runKotlinc(request, tempClassesDir)
            if (kotlincResult != null) return kotlincResult

            val classFiles = tempClassesDir.walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .toList()

            val forbiddenResult = checkClassFiles(classFiles)
            if (forbiddenResult != null) return forbiddenResult

            val d8Result = runD8(request, tempClassesDir, classFiles, tempDexDir)
            if (d8Result != null) return d8Result

            val outSmaliDir = File(request.outputSmaliDir).apply { mkdirs() }
            val baksmaliResult = runBaksmali(tempDexDir, outSmaliDir)
            if (baksmaliResult != null) return baksmaliResult

            val smaliFiles = outSmaliDir.walkTopDown()
                .filter { it.isFile && it.extension == "smali" }
                .sortedBy { it.path.replace('\\', '/') }
                .toList()

            val validationResult = validateEmittedClasses(smaliFiles, outSmaliDir)
            if (validationResult.first != null) return validationResult.first!!

            val emittedDescriptors = validationResult.second
            val receipt = buildReceipt(request, smaliFiles, emittedDescriptors)
            return PayloadCompilationResult.Compiled(
                receipt = receipt,
                smaliFiles = smaliFiles.map { it.absolutePath.replace('\\', '/') },
            )
        } finally {
            tempClassesDir.deleteRecursively()
            tempDexDir.deleteRecursively()
        }
    }

    private fun checkClassFiles(classFiles: List<File>): PayloadCompilationResult? {
        for (classFile in classFiles) {
            val forbidden = checkForbiddenReference(classFile.readBytes())
            if (forbidden != null) {
                val className = classFile.nameWithoutExtension
                return PayloadCompilationResult.Failed(
                    ValidationReport(
                        listOf(
                            ValidationError(
                                stageId = StageId.MODULE_APPLICATION,
                                objectId = className,
                                fieldPath = classFile.name,
                                code = PayloadCompilerCodes.KOTLIN_RUNTIME_REFERENCE,
                                severity = Severity.ERROR,
                                message = "Class '$className' contains forbidden reference '$forbidden'",
                            ),
                        ),
                    ),
                )
            }
        }
        return null
    }

    private fun validateEmittedClasses(
        smaliFiles: List<File>,
        outSmaliDir: File,
    ): Pair<PayloadCompilationResult?, List<String>> {
        if (smaliFiles.isEmpty()) {
            return PayloadCompilationResult.Failed(
                ValidationReport(
                    listOf(
                        ValidationError(
                            stageId = StageId.MODULE_APPLICATION,
                            objectId = "smali",
                            fieldPath = "outputSmaliDir",
                            code = PayloadCompilerCodes.NO_CLASSES_EMITTED,
                            severity = Severity.ERROR,
                            message = "No classes were emitted to ${outSmaliDir.path}",
                        ),
                    ),
                ),
            ) to emptyList()
        }

        val emittedDescriptors = smaliFiles.mapNotNull { file ->
            extractClassDescriptor(file) ?: deriveDescriptor(file, outSmaliDir)
        }

        if (emittedDescriptors.isEmpty()) {
            return PayloadCompilationResult.Failed(
                ValidationReport(
                    listOf(
                        ValidationError(
                            stageId = StageId.MODULE_APPLICATION,
                            objectId = "smali",
                            fieldPath = "outputSmaliDir",
                            code = PayloadCompilerCodes.NO_CLASSES_EMITTED,
                            severity = Severity.ERROR,
                            message = "No class descriptors could be extracted from smali files",
                        ),
                    ),
                ),
            ) to emptyList()
        }

        for (desc in emittedDescriptors) {
            if (desc.startsWith("Landroid/") || desc.startsWith("Ljava/")) {
                return PayloadCompilationResult.Failed(
                    ValidationReport(
                        listOf(
                            ValidationError(
                                stageId = StageId.MODULE_APPLICATION,
                                objectId = desc,
                                fieldPath = "emittedClassDescriptors",
                                code = PayloadCompilerCodes.PLATFORM_CLASS_EMITTED,
                                severity = Severity.ERROR,
                                message = "Platform class descriptor emitted: '$desc'",
                            ),
                        ),
                    ),
                ) to emptyList()
            }
        }
        return null to emittedDescriptors
    }

    private fun validateToolPaths(): ValidationError? {
        val requiredTools = listOf("kotlinc", "d8", "baksmali")
        for (tool in requiredTools) {
            val path = toolPaths[tool]
            if (path == null) {
                return ValidationError(
                    stageId = StageId.MODULE_APPLICATION,
                    objectId = tool,
                    fieldPath = "toolPaths",
                    code = PayloadCompilerCodes.TOOL_NOT_FOUND,
                    severity = Severity.ERROR,
                    message = "Tool '$tool' not configured in toolPaths",
                )
            }
            val file = File(path)
            if (!file.exists() || !file.isFile || !isExecutable(file)) {
                return ValidationError(
                    stageId = StageId.MODULE_APPLICATION,
                    objectId = tool,
                    fieldPath = "toolPaths",
                    code = PayloadCompilerCodes.TOOL_NOT_FOUND,
                    severity = Severity.ERROR,
                    message = "Tool '$tool' not found or not executable at '$path'",
                )
            }
        }
        return null
    }

    private fun runKotlinc(request: PayloadCompilationRequest, tempClassesDir: File): PayloadCompilationResult? {
        val kotlincArgs = mutableListOf<String>()
        kotlincArgs.add(toolPaths.getValue("kotlinc"))
        kotlincArgs.add("-d")
        kotlincArgs.add(tempClassesDir.absolutePath)
        val classpathJars = request.frameworkStubJars + request.dependencyJars
        if (classpathJars.isNotEmpty()) {
            kotlincArgs.add("-classpath")
            kotlincArgs.add(classpathJars.joinToString(File.pathSeparator))
        }
        kotlincArgs.add("-Xno-param-assertions")
        kotlincArgs.add("-Xno-call-assertions")
        kotlincArgs.add("-Xno-receiver-assertions")
        kotlincArgs.add("-Xno-optimized-callable-references")
        kotlincArgs.addAll(request.sourceFiles)

        val result = runProcess(kotlincArgs)
        if (result.exitCode != 0) {
            val errorMsg = result.stderr.ifBlank { result.stdout }
            return PayloadCompilationResult.Failed(
                ValidationReport(
                    listOf(
                        ValidationError(
                            stageId = StageId.MODULE_APPLICATION,
                            objectId = "kotlinc",
                            fieldPath = "kotlinc",
                            code = PayloadCompilerCodes.KOTLINC_FAILED,
                            severity = Severity.ERROR,
                            message = "kotlinc failed with exit code ${result.exitCode}: $errorMsg",
                        ),
                    ),
                ),
            )
        }
        return null
    }

    private fun runD8(
        request: PayloadCompilationRequest,
        tempClassesDir: File,
        classFiles: List<File>,
        tempDexDir: File,
    ): PayloadCompilationResult? {
        val d8Args = mutableListOf<String>()
        d8Args.add(toolPaths.getValue("d8"))
        d8Args.add("--output")
        d8Args.add(tempDexDir.absolutePath)
        d8Args.add("--min-api")
        d8Args.add(request.minRuntimeApi.toString())
        for (stub in request.frameworkStubJars) {
            d8Args.add("--lib")
            d8Args.add(stub)
        }
        if (classFiles.isNotEmpty()) {
            classFiles.forEach { d8Args.add(it.absolutePath) }
        } else {
            d8Args.add(tempClassesDir.absolutePath)
        }

        val result = runProcess(d8Args)
        if (result.exitCode != 0) {
            val errorMsg = result.stderr.ifBlank { result.stdout }
            return PayloadCompilationResult.Failed(
                ValidationReport(
                    listOf(
                        ValidationError(
                            stageId = StageId.MODULE_APPLICATION,
                            objectId = "d8",
                            fieldPath = "d8",
                            code = PayloadCompilerCodes.DEX_FAILED,
                            severity = Severity.ERROR,
                            message = "d8 failed with exit code ${result.exitCode}: $errorMsg",
                        ),
                    ),
                ),
            )
        }
        return null
    }

    private fun runBaksmali(tempDexDir: File, outSmaliDir: File): PayloadCompilationResult? {
        val dexFiles = tempDexDir.walkTopDown()
            .filter { it.isFile && it.extension == "dex" }
            .toList()

        if (dexFiles.isEmpty()) {
            return PayloadCompilationResult.Failed(
                ValidationReport(
                    listOf(
                        ValidationError(
                            stageId = StageId.MODULE_APPLICATION,
                            objectId = "d8",
                            fieldPath = "tempDexDir",
                            code = PayloadCompilerCodes.DEX_FAILED,
                            severity = Severity.ERROR,
                            message = "No dex files found in output directory",
                        ),
                    ),
                ),
            )
        }

        for (dex in dexFiles) {
            val baksmaliArgs = listOf(
                toolPaths.getValue("baksmali"),
                "d",
                dex.absolutePath,
                "-o",
                outSmaliDir.absolutePath,
            )
            val result = runProcess(baksmaliArgs)
            if (result.exitCode != 0) {
                val errorMsg = result.stderr.ifBlank { result.stdout }
                return PayloadCompilationResult.Failed(
                    ValidationReport(
                        listOf(
                            ValidationError(
                                stageId = StageId.MODULE_APPLICATION,
                                objectId = "baksmali",
                                fieldPath = "baksmali",
                                code = PayloadCompilerCodes.DISASSEMBLE_FAILED,
                                severity = Severity.ERROR,
                                message = "baksmali failed with exit code ${result.exitCode}: $errorMsg",
                            ),
                        ),
                    ),
                )
            }
        }
        return null
    }

    private fun buildReceipt(
        request: PayloadCompilationRequest,
        smaliFiles: List<File>,
        emittedDescriptors: List<String>,
    ): PayloadCompilationReceipt {
        val sortedSourceFiles = request.sourceFiles.sorted()
        val sourceDigest = MessageDigest.getInstance("SHA-256")
        for (sf in sortedSourceFiles) {
            sourceDigest.update(File(sf).readBytes())
        }
        val sourceFingerprint = sourceDigest.digest().joinToString("") { "%02x".format(it) }

        val smaliDigest = MessageDigest.getInstance("SHA-256")
        for (smali in smaliFiles) {
            smaliDigest.update(smali.readBytes())
        }
        val outputSha256 = smaliDigest.digest().joinToString("") { "%02x".format(it) }

        val frameworkStubDigest = if (request.frameworkStubJars.isNotEmpty()) {
            val stubDigest = MessageDigest.getInstance("SHA-256")
            for (stub in request.frameworkStubJars.sorted()) {
                val stubFile = File(stub)
                if (stubFile.isFile) {
                    stubDigest.update(stubFile.readBytes())
                }
            }
            stubDigest.digest().joinToString("") { "%02x".format(it) }
        } else {
            ""
        }

        val dependencyDigests = request.dependencyJars.sorted().map { dep ->
            val f = File(dep)
            if (f.isFile) sha256OfFile(f) else ""
        }

        return PayloadCompilationReceipt(
            sourceFingerprint = sourceFingerprint,
            outputSha256 = outputSha256,
            kotlinCompiler = getToolIdentity("kotlinc", toolPaths.getValue("kotlinc")),
            dexer = getToolIdentity("d8", toolPaths.getValue("d8")),
            disassembler = getToolIdentity("baksmali", toolPaths.getValue("baksmali")),
            frameworkStubDigest = frameworkStubDigest,
            minRuntimeApi = request.minRuntimeApi,
            dependencyDigests = dependencyDigests,
            emittedClassDescriptors = emittedDescriptors.sorted(),
        )
    }

    private fun getToolIdentity(toolName: String, toolPath: String): ToolIdentity {
        val file = File(toolPath)
        val sha = if (file.isFile) sha256OfFile(file) else null
        val version = queryToolVersion(toolPath)
        return ToolIdentity(name = toolName, version = version, sha256 = sha)
    }

    private fun queryToolVersion(toolPath: String): String {
        val res = runProcess(listOf(toolPath, "--version"))
        val out = res.stdout.trim().ifEmpty { res.stderr.trim() }
        return if (out.isNotEmpty()) {
            out.lines().first().trim()
        } else {
            "unknown"
        }
    }

    private fun checkForbiddenReference(bytes: ByteArray): String? {
        if (containsUtf8Bytes(bytes, "kotlin/jvm/internal")) {
            return "kotlin/jvm/internal"
        }
        if (containsUtf8Bytes(bytes, "kotlin/Metadata")) {
            return "kotlin/Metadata"
        }
        return null
    }

    private fun containsUtf8Bytes(haystack: ByteArray, needle: String): Boolean {
        val needleBytes = needle.toByteArray(Charsets.UTF_8)
        if (needleBytes.isEmpty() || haystack.size < needleBytes.size) return false
        outer@ for (i in 0..(haystack.size - needleBytes.size)) {
            for (j in needleBytes.indices) {
                if (haystack[i + j] != needleBytes[j]) continue@outer
            }
            return true
        }
        return false
    }

    private fun extractClassDescriptor(file: File): String? {
        val lines = file.readLines()
        val classLine = lines.firstOrNull {
            val trimmed = it.trim()
            trimmed.startsWith(".class ") || trimmed.startsWith(".class\t")
        } ?: return null
        return classLine.trim().split(Regex("\\s+")).lastOrNull { it.startsWith("L") && it.endsWith(";") }
    }

    private fun deriveDescriptor(file: File, baseDir: File): String {
        val rel = file.relativeTo(baseDir).path.replace('\\', '/').removeSuffix(".smali")
        return "L$rel;"
    }

    private fun sha256OfFile(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead == -1) break
                md.update(buffer, 0, bytesRead)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun isExecutable(file: File): Boolean {
        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
        if (isWindows) {
            val ext = file.extension.lowercase()
            return ext in setOf("exe", "bat", "cmd") || file.canExecute()
        }
        return file.canExecute()
    }

    private fun runProcess(command: List<String>, workingDir: File? = null): ProcessResult {
        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
        val fullCommand = if (isWindows && command.isNotEmpty() &&
            (command[0].endsWith(".bat", ignoreCase = true) || command[0].endsWith(".cmd", ignoreCase = true))
        ) {
            listOf("cmd.exe", "/c") + command
        } else {
            command
        }
        val pb = ProcessBuilder(fullCommand)
        if (workingDir != null) {
            pb.directory(workingDir)
        }
        val process = pb.start()
        val stdoutFuture = CompletableFuture.supplyAsync {
            process.inputStream.bufferedReader().readText()
        }
        val stderrFuture = CompletableFuture.supplyAsync {
            process.errorStream.bufferedReader().readText()
        }
        val exitCode = process.waitFor()
        val stdout = stdoutFuture.get()
        val stderr = stderrFuture.get()
        return ProcessResult(exitCode, stdout, stderr)
    }

    private data class ProcessResult(val exitCode: Int, val stdout: String, val stderr: String)

    companion object {
        fun defaultToolPaths(): Map<String, String> {
            val paths = mutableMapOf<String, String>()
            fun resolve(name: String, propKey: String, envKey: String): String? =
                System.getProperty(propKey) ?: System.getenv(envKey) ?: findOnPath(name)
            resolve("kotlinc", "lti.tool.kotlinc", "KOTLINC_PATH")?.let { paths["kotlinc"] = it }
            resolve("d8", "lti.tool.d8", "D8_PATH")?.let { paths["d8"] = it }
            resolve("baksmali", "lti.tool.baksmali", "BAKSMALI_PATH")?.let { paths["baksmali"] = it }
            return paths
        }

        private fun findOnPath(executable: String): String? {
            val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
            val pathVar = System.getenv("PATH") ?: return null
            val extensions = if (isWindows) listOf(".bat", ".cmd", ".exe", "") else listOf("")
            for (dir in pathVar.split(File.pathSeparator)) {
                for (ext in extensions) {
                    val candidate = File(dir, executable + ext)
                    if (candidate.isFile && candidate.canExecute()) {
                        return candidate.absolutePath
                    }
                }
            }
            return null
        }
    }
}
