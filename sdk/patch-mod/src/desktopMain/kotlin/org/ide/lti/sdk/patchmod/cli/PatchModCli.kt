/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.sdk.patchmod.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.ide.lti.core.domain.plugin.PayloadCompilationReceipt
import org.ide.lti.core.domain.plugin.PayloadCompilationRequest
import org.ide.lti.core.domain.plugin.PayloadCompilationResult
import org.ide.lti.core.domain.plugin.PayloadCompilerPort
import org.ide.lti.core.domain.plugin.PluginPackageCodec
import org.ide.lti.core.domain.plugin.PluginPlanResolver
import org.ide.lti.core.domain.plugin.PluginPlanValidator
import org.ide.lti.core.domain.plugin.ResolutionInput
import org.ide.lti.core.domain.plugin.ResolutionResult
import org.ide.lti.sdk.patchmod.PackageWriter
import org.ide.lti.sdk.patchmod.runtime.PackageReadResult
import org.ide.lti.sdk.patchmod.runtime.PayloadCompilerAdapter
import org.ide.lti.sdk.patchmod.runtime.PluginPackageReader
import java.io.File
import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    PatchModCli().main(args)
}

class PatchModCli : CliktCommand(name = "patchmod") {
    override fun help(context: Context): String = "Patch-mod author CLI tools"

    init {
        subcommands(
            CompileModPayloadCommand(),
            GenerateModPlanCommand(),
            ValidateModCommand(),
            TestModCommand(),
            PackageModCommand(),
            InspectModCommand(),
        )
    }

    override fun run() = Unit
}

class CompileModPayloadCommand : CliktCommand(name = "compileModPayload") {
    override fun help(context: Context): String = "Compile author hook Kotlin sources into smali payloads"
    private val project by option("--project", help = "Author project directory").default(".")

    override fun run() {
        val result = PatchModTool.compileModPayload(File(project))
        handleResult(result)
    }
}

class GenerateModPlanCommand : CliktCommand(name = "generateModPlan") {
    override fun help(context: Context): String = "Generate and validate mod plan from author files"
    private val project by option("--project", help = "Author project directory").default(".")

    override fun run() {
        val result = PatchModTool.generateModPlan(File(project))
        handleResult(result)
    }
}

class ValidateModCommand : CliktCommand(name = "validateMod") {
    override fun help(context: Context): String = "Validate author mod plan and manifest"
    private val project by option("--project", help = "Author project directory").default(".")

    override fun run() {
        val result = PatchModTool.validateMod(File(project))
        handleResult(result)
    }
}

class TestModCommand : CliktCommand(name = "testMod") {
    override fun help(context: Context): String = "Run author test scenarios against the mod plan"
    private val project by option("--project", help = "Author project directory").default(".")

    override fun run() {
        val result = PatchModTool.testMod(File(project))
        handleResult(result)
    }
}

class PackageModCommand : CliktCommand(name = "packageMod") {
    override fun help(context: Context): String = "Package author mod into .lti-mod.zip"
    private val project by option("--project", help = "Author project directory").default(".")

    override fun run() {
        val result = PatchModTool.packageMod(File(project))
        handleResult(result)
    }
}

class InspectModCommand : CliktCommand(name = "inspectMod") {
    override fun help(context: Context): String = "Inspect a packaged .lti-mod.zip"
    private val project by option("--project", help = "Author project directory").default(".")
    private val packagePath by option("--package", help = "Direct path to .lti-mod.zip package")

    override fun run() {
        val packageFile = packagePath?.let { File(it) }
        val result = PatchModTool.inspectMod(File(project), packageFile)
        handleResult(result)
    }
}

private fun CliktCommand.handleResult(result: CliResult) {
    if (result is CliResult.Failure) {
        echo(result.message, err = true)
        result.errors.forEach { echo(it, err = true) }
        exitProcess(result.exitCode)
    } else {
        echo(result.message)
    }
}

sealed interface CliResult {
    val exitCode: Int
    val message: String

    data class Success(
        override val message: String,
        val details: Map<String, Any?> = emptyMap(),
        override val exitCode: Int = 0,
    ) : CliResult

    data class Failure(
        override val message: String,
        val errors: List<String> = emptyList(),
        override val exitCode: Int = 1,
    ) : CliResult
}

@Serializable
data class DiagnosticEntry(
    val code: String,
    val objectId: String? = null,
    val fieldPath: String,
    val severity: String,
    val message: String,
)

@Serializable
data class ModScenario(
    val name: String,
    val settings: Map<String, String> = emptyMap(),
    val facts: Map<String, String> = emptyMap(),
    val capabilities: List<String> = emptyList(),
    val expectResolved: Boolean,
)

@Serializable
data class PayloadConfig(
    val minRuntimeApi: Int = 34,
    val frameworkStubJars: List<String> = emptyList(),
    val dependencyJars: List<String> = emptyList(),
)

@Suppress("ReturnCount")
object PatchModTool {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Suppress("ReturnCount")
    fun compileModPayload(projectDir: File, compiler: PayloadCompilerPort = PayloadCompilerAdapter()): CliResult {
        val payloadFile = File(projectDir, "payload.json")
        if (!payloadFile.exists()) {
            return CliResult.Success("no payload to compile")
        }

        val config = try {
            json.decodeFromString(PayloadConfig.serializer(), payloadFile.readText())
        } catch (e: Exception) {
            val buildDir = File(projectDir, "build").apply { mkdirs() }
            val diag = listOf(
                DiagnosticEntry(
                    code = "INVALID_PAYLOAD_CONFIG",
                    objectId = null,
                    fieldPath = "payload.json",
                    severity = "ERROR",
                    message = e.message ?: "Failed to parse payload.json",
                ),
            )
            writeDiagnostics(buildDir, diag)
            return CliResult.Failure(
                message = "ERROR compileModPayload failed to parse payload.json: ${e.message}",
                errors = listOf("ERROR INVALID_PAYLOAD_CONFIG payload.json: ${e.message}"),
            )
        }

        val hooksDir = File(projectDir, "hooks")
        val sourceFiles = if (hooksDir.exists()) {
            hooksDir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .map { it.absolutePath }
                .toList()
        } else {
            emptyList()
        }

        val outSmaliDir = File(projectDir, "assets/payload")
        val request = PayloadCompilationRequest(
            sourceFiles = sourceFiles,
            frameworkStubJars = config.frameworkStubJars.map { resolveRel(projectDir, it) },
            dependencyJars = config.dependencyJars.map { resolveRel(projectDir, it) },
            minRuntimeApi = config.minRuntimeApi,
            outputSmaliDir = outSmaliDir.absolutePath,
        )

        val result = runBlocking { compiler.compile(request) }
        return when (result) {
            is PayloadCompilationResult.Failed -> {
                val buildDir = File(projectDir, "build").apply { mkdirs() }
                val diagnostics = result.report.errors.map {
                    DiagnosticEntry(
                        code = it.code,
                        objectId = it.objectId,
                        fieldPath = it.fieldPath,
                        severity = it.severity.name,
                        message = it.message,
                    )
                }
                writeDiagnostics(buildDir, diagnostics)
                val errorLines = diagnostics.map {
                    "ERROR ${it.code} ${it.objectId ?: ""}.${it.fieldPath}: ${it.message}"
                }
                val primaryCode = result.report.errors.firstOrNull()?.code ?: "COMPILATION_FAILED"
                CliResult.Failure(
                    message = "ERROR compileModPayload failed with $primaryCode",
                    errors = errorLines,
                )
            }
            is PayloadCompilationResult.Compiled -> {
                val buildDir = File(projectDir, "build").apply { mkdirs() }
                val receiptFile = File(buildDir, "payload-receipt.json")
                receiptFile.writeText(
                    json.encodeToString(PayloadCompilationReceipt.serializer(), result.receipt) + "\n",
                )
                CliResult.Success(
                    message = "OK compileModPayload ${result.smaliFiles.size} smali file(s) generated",
                    details = mapOf(
                        "receipt" to result.receipt,
                        "smaliFiles" to result.smaliFiles,
                    ),
                )
            }
        }
    }

    private fun resolveRel(projectDir: File, path: String): String {
        val f = File(path)
        return if (f.isAbsolute) f.absolutePath else File(projectDir, path).absolutePath
    }

    @Suppress("ReturnCount")
    fun generateModPlan(projectDir: File): CliResult {
        val planFile = File(projectDir, PluginPackageCodec.PLAN)
        if (!planFile.exists()) {
            return CliResult.Failure("ERROR generateModPlan plan.json not found in ${projectDir.path}")
        }

        val template = try {
            PluginPackageCodec.decodePlan(planFile.readText())
        } catch (e: Exception) {
            val buildDir = File(projectDir, "build").apply { mkdirs() }
            val diag = listOf(
                DiagnosticEntry(
                    code = "INVALID_JSON",
                    objectId = null,
                    fieldPath = PluginPackageCodec.PLAN,
                    severity = "ERROR",
                    message = e.message ?: "Failed to parse plan.json",
                ),
            )
            writeDiagnostics(buildDir, diag)
            return CliResult.Failure(
                message = "ERROR generateModPlan failed to parse plan.json: ${e.message}",
                errors = listOf("ERROR INVALID_JSON plan.json: ${e.message}"),
            )
        }

        val report = PluginPlanValidator.validate(template)
        val diagnostics = report.errors.map {
            DiagnosticEntry(
                code = it.code,
                objectId = it.objectId,
                fieldPath = it.fieldPath,
                severity = it.severity.name,
                message = it.message,
            )
        }

        val buildDir = File(projectDir, "build").apply { mkdirs() }
        writeDiagnostics(buildDir, diagnostics)

        if (report.hasBlockingErrors()) {
            val errorLines = report.errors.map {
                "ERROR ${it.code} ${it.objectId ?: ""}.${it.fieldPath}: ${it.message}"
            }
            return CliResult.Failure(
                message = "ERROR generateModPlan validation failed with ${report.errors.size} error(s)",
                errors = errorLines,
            )
        }

        val outPlanFile = File(buildDir, PluginPackageCodec.PLAN)
        outPlanFile.writeText(PluginPackageCodec.encodePlan(template) + "\n")
        val outSettingsFile = File(buildDir, PluginPackageCodec.SETTINGS)
        outSettingsFile.writeText(PluginPackageCodec.settingsSchema(template.settings) + "\n")

        return CliResult.Success("OK generateModPlan ${outPlanFile.path}")
    }

    @Suppress("ReturnCount")
    fun validateMod(projectDir: File): CliResult {
        val manifestFile = File(projectDir, PluginPackageCodec.MANIFEST)
        if (!manifestFile.exists()) {
            return CliResult.Failure("ERROR validateMod manifest.json not found in ${projectDir.path}")
        }

        val manifest = try {
            PluginPackageCodec.decodeManifest(manifestFile.readText())
        } catch (e: Exception) {
            return CliResult.Failure("ERROR validateMod failed to parse manifest.json: ${e.message}")
        }

        val planFile = File(projectDir, manifest.planPath)
        if (!planFile.exists()) {
            return CliResult.Failure(
                "ERROR validateMod declared planPath '${manifest.planPath}' does not resolve in ${projectDir.path}",
            )
        }

        val settingsFile = File(projectDir, manifest.settingsPath)
        val settingsResolvable = settingsFile.exists() || manifest.settingsPath == PluginPackageCodec.SETTINGS
        if (!settingsResolvable) {
            return CliResult.Failure(
                "ERROR validateMod declared settingsPath '${manifest.settingsPath}' does not resolve",
            )
        }

        val template = try {
            PluginPackageCodec.decodePlan(planFile.readText())
        } catch (e: Exception) {
            return CliResult.Failure("ERROR validateMod failed to parse ${manifest.planPath}: ${e.message}")
        }

        val report = PluginPlanValidator.validate(template)
        val diagnostics = report.errors.map {
            DiagnosticEntry(
                code = it.code,
                objectId = it.objectId,
                fieldPath = it.fieldPath,
                severity = it.severity.name,
                message = it.message,
            )
        }.toMutableList()

        if (manifest.publisher != template.publisher) {
            diagnostics.add(
                DiagnosticEntry(
                    code = "PUBLISHER_MISMATCH",
                    objectId = manifest.id,
                    fieldPath = "publisher",
                    severity = "ERROR",
                    message = "Manifest publisher '${manifest.publisher}' != plan publisher '${template.publisher}'",
                ),
            )
        }
        if (manifest.id != template.id) {
            diagnostics.add(
                DiagnosticEntry(
                    code = "ID_MISMATCH",
                    objectId = manifest.id,
                    fieldPath = "id",
                    severity = "ERROR",
                    message = "Manifest id '${manifest.id}' != plan id '${template.id}'",
                ),
            )
        }
        if (manifest.version != template.version) {
            diagnostics.add(
                DiagnosticEntry(
                    code = "VERSION_MISMATCH",
                    objectId = manifest.id,
                    fieldPath = "version",
                    severity = "ERROR",
                    message = "Manifest version '${manifest.version}' != plan version '${template.version}'",
                ),
            )
        }

        val buildDir = File(projectDir, "build").apply { mkdirs() }
        writeDiagnostics(buildDir, diagnostics)

        if (diagnostics.isNotEmpty()) {
            val errorLines = diagnostics.map {
                "ERROR ${it.code} ${it.objectId ?: ""}.${it.fieldPath}: ${it.message}"
            }
            return CliResult.Failure(
                message = "ERROR validateMod failed with ${diagnostics.size} error(s)",
                errors = errorLines,
            )
        }

        return CliResult.Success("OK validateMod ${projectDir.path}")
    }

    @Suppress("ReturnCount")
    fun testMod(projectDir: File): CliResult {
        val planFile = File(projectDir, PluginPackageCodec.PLAN)
        if (!planFile.exists()) {
            return CliResult.Failure("ERROR testMod plan.json not found in ${projectDir.path}")
        }

        val template = try {
            PluginPackageCodec.decodePlan(planFile.readText())
        } catch (e: Exception) {
            return CliResult.Failure("ERROR testMod failed to parse plan.json: ${e.message}")
        }

        val scenariosFile = File(projectDir, "scenarios.json")
        if (!scenariosFile.exists()) {
            return CliResult.Failure("ERROR testMod scenarios.json not found in ${projectDir.path}")
        }

        val scenarios = try {
            json.decodeFromString(ListSerializer(ModScenario.serializer()), scenariosFile.readText())
        } catch (e: Exception) {
            return CliResult.Failure("ERROR testMod failed to parse scenarios.json: ${e.message}")
        }

        val requiredCaps = runCatching {
            val mFile = File(projectDir, PluginPackageCodec.MANIFEST)
            if (mFile.exists()) {
                PluginPackageCodec.decodeManifest(mFile.readText()).requiredCapabilities
            } else {
                emptyList()
            }
        }.getOrDefault(emptyList())

        val failures = mutableListOf<String>()
        for (scenario in scenarios) {
            val input = ResolutionInput(
                template = template,
                packageContentDigest = "0".repeat(64),
                settingValues = scenario.settings,
                targetFacts = scenario.facts,
                toolCapabilities = scenario.capabilities.toSet(),
                requiredCapabilities = requiredCaps,
                upstreamCheckpointDigest = "0".repeat(64),
            )
            val resolution = PluginPlanResolver.resolve(input)
            val actualResolved = resolution is ResolutionResult.Resolved
            if (actualResolved != scenario.expectResolved) {
                val detail = if (resolution is ResolutionResult.Rejected) {
                    resolution.report.errors.joinToString("; ") { "${it.code}: ${it.message}" }
                } else {
                    "resolved successfully"
                }
                failures.add(
                    "ERROR testMod Scenario '${scenario.name}': expected resolved=${scenario.expectResolved}, " +
                        "actual resolved=$actualResolved ($detail)",
                )
            }
        }

        if (failures.isNotEmpty()) {
            return CliResult.Failure(
                message = "ERROR testMod ${failures.size}/${scenarios.size} scenarios failed",
                errors = failures,
            )
        }

        return CliResult.Success("OK testMod ${scenarios.size} scenarios passed")
    }

    @Suppress("ReturnCount")
    fun packageMod(projectDir: File): CliResult {
        val validation = validateMod(projectDir)
        if (validation is CliResult.Failure) {
            return validation
        }

        val manifestFile = File(projectDir, PluginPackageCodec.MANIFEST)
        val manifest = PluginPackageCodec.decodeManifest(manifestFile.readText())

        val planFile = File(projectDir, manifest.planPath)
        val template = PluginPackageCodec.decodePlan(planFile.readText())

        val readmeFile = File(projectDir, PluginPackageCodec.README)
        if (!readmeFile.exists()) {
            return CliResult.Failure("ERROR packageMod README.md not found in ${projectDir.path}")
        }

        val licenseFile = File(projectDir, PluginPackageCodec.LICENSE)
        if (!licenseFile.exists()) {
            return CliResult.Failure("ERROR packageMod LICENSE not found in ${projectDir.path}")
        }

        val assetsDir = File(projectDir, "assets")
        val assetMap = mutableMapOf<String, Path>()
        if (assetsDir.exists()) {
            assetsDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val rel = file.relativeTo(assetsDir).path.replace('\\', '/')
                assetMap["assets/$rel"] = file.toPath()
            }
        }

        val buildDir = File(projectDir, "build").apply { mkdirs() }
        val zipName = "${manifest.publisher}-${manifest.id}-${manifest.version}.lti-mod.zip"
        val outZip = File(buildDir, zipName)

        val updatedManifest = try {
            PackageWriter.writePackage(
                manifest = manifest,
                template = template,
                readme = readmeFile.readText(),
                license = licenseFile.readText(),
                assets = assetMap,
                out = outZip.toPath(),
            )
        } catch (e: Exception) {
            return CliResult.Failure("ERROR packageMod failed to write package: ${e.message}")
        }

        val contentDigest = PluginPackageCodec.contentIdentity(updatedManifest.payloads)
        return CliResult.Success(
            message = "OK packageMod ${outZip.absolutePath} $contentDigest",
            details = mapOf("output" to outZip, "contentDigest" to contentDigest),
        )
    }

    @Suppress("ReturnCount")
    fun inspectMod(projectDir: File, packageFile: File? = null): CliResult {
        val targetZip = packageFile ?: findPackageZip(projectDir)
            ?: return CliResult.Failure("ERROR inspectMod no package .lti-mod.zip found in ${projectDir.path}")

        if (!targetZip.exists()) {
            return CliResult.Failure("ERROR inspectMod file not found: ${targetZip.absolutePath}")
        }

        val readResult = PluginPackageReader.read(targetZip.toPath())
        return when (readResult) {
            is PackageReadResult.Rejected -> {
                val errorLines = readResult.report.errors.map {
                    "ERROR ${it.code} ${it.objectId ?: ""}.${it.fieldPath}: ${it.message}"
                }
                CliResult.Failure(
                    message = "ERROR inspectMod package rejected with ${readResult.report.errors.size} error(s)",
                    errors = errorLines,
                )
            }
            is PackageReadResult.Ok -> {
                val pkg = readResult.pkg
                val m = pkg.manifest
                val sb = StringBuilder()
                sb.appendLine("OK inspectMod ${targetZip.name}")
                sb.appendLine("Manifest: ${m.publisher}:${m.id}:${m.version} (${m.displayName})")
                sb.appendLine("Operations: ${pkg.template.operations.size}")
                sb.appendLine("Payloads (${m.payloads.size}):")
                for (payload in m.payloads) {
                    sb.appendLine("  - ${payload.path} (size=${payload.sizeBytes}, sha256=${payload.sha256})")
                }
                sb.append("Signature: ${if (pkg.hasSignature) "present" else "none"}")
                CliResult.Success(
                    message = sb.toString(),
                    details = mapOf(
                        "manifest" to m,
                        "template" to pkg.template,
                        "contentDigest" to pkg.contentDigest,
                        "hasSignature" to pkg.hasSignature,
                    ),
                )
            }
        }
    }

    fun inspectMod(packageFile: File): CliResult = inspectMod(packageFile.parentFile ?: File("."), packageFile)

    @Suppress("ReturnCount")
    private fun findPackageZip(projectDir: File): File? {
        val buildDir = File(projectDir, "build")
        if (!buildDir.exists()) return null

        val manifestFile = File(projectDir, PluginPackageCodec.MANIFEST)
        if (manifestFile.exists()) {
            val manifest = runCatching { PluginPackageCodec.decodeManifest(manifestFile.readText()) }.getOrNull()
            if (manifest != null) {
                val expectedZip = File(buildDir, "${manifest.publisher}-${manifest.id}-${manifest.version}.lti-mod.zip")
                if (expectedZip.exists()) return expectedZip
            }
        }

        return buildDir.listFiles()?.firstOrNull { it.isFile && it.name.endsWith(".lti-mod.zip") }
    }

    private fun writeDiagnostics(buildDir: File, diagnostics: List<DiagnosticEntry>) {
        val diagFile = File(buildDir, "diagnostics.json")
        diagFile.writeText(json.encodeToString(ListSerializer(DiagnosticEntry.serializer()), diagnostics) + "\n")
    }
}
