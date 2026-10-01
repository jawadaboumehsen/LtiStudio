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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.ide.lti.core.domain.plugin.PluginPackageCodec
import org.ide.lti.core.domain.plugin.PluginPlanValidator
import org.ide.lti.sdk.patchmod.runtime.PackageReadResult
import org.ide.lti.sdk.patchmod.runtime.PluginPackageReader
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PatchModCliTest {

    private fun locateExampleDir(): File {
        var dir = File(System.getProperty("user.dir"))
        while (!File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: error("Could not locate repo root (settings.gradle.kts not found)")
        }
        val exampleDir = File(dir, "sdk/patch-mod/examples/file-property")
        check(exampleDir.exists()) { "Example dir not found at: ${exampleDir.absolutePath}" }
        return exampleDir
    }

    private fun copyDirectory(source: File, target: File) {
        source.walkTopDown().forEach { file ->
            val rel = file.relativeTo(source).path
            val dest = File(target, rel)
            if (file.isDirectory) {
                dest.mkdirs()
            } else {
                dest.parentFile?.mkdirs()
                file.copyTo(dest, overwrite = true)
            }
        }
    }

    private fun createTempExampleCopy(): Path {
        val tempDir = createTempDirectory("patchmod-test-")
        copyDirectory(locateExampleDir(), tempDir.toFile())
        return tempDir
    }

    @Test
    fun testGenerateModPlanOnExampleAndTamperedPlan() {
        val tempDir = createTempExampleCopy().toFile()
        val result = PatchModTool.generateModPlan(tempDir)
        assertIs<CliResult.Success>(result)
        assertEquals(0, result.exitCode)

        val buildPlanFile = File(tempDir, "build/plan.json")
        assertTrue(buildPlanFile.exists(), "build/plan.json should be written")

        val originalSourcePlan = PluginPackageCodec.decodePlan(File(tempDir, "plan.json").readText())
        val generatedPlan = PluginPackageCodec.decodePlan(buildPlanFile.readText())
        assertEquals(originalSourcePlan, generatedPlan)

        val diagnosticsFile = File(tempDir, "build/diagnostics.json")
        assertTrue(diagnosticsFile.exists(), "build/diagnostics.json should be written")
        val jsonArray = Json.parseToJsonElement(diagnosticsFile.readText()).jsonArray
        assertEquals(0, jsonArray.size, "diagnostics.json should be empty on valid plan")

        // Tamper plan: duplicate operation id
        val tamperedPlan = originalSourcePlan.copy(
            operations = originalSourcePlan.operations + originalSourcePlan.operations.first(),
        )
        File(tempDir, "plan.json").writeText(PluginPackageCodec.encodePlan(tamperedPlan))

        val tamperedResult = PatchModTool.generateModPlan(tempDir)
        assertIs<CliResult.Failure>(tamperedResult)
        assertNotEquals(0, tamperedResult.exitCode)
        assertTrue(
            tamperedResult.errors.any { it.contains(PluginPlanValidator.CODE_DUPLICATE_OPERATION_ID) } ||
                tamperedResult.message.contains(PluginPlanValidator.CODE_DUPLICATE_OPERATION_ID),
            "Tampered plan must report DUPLICATE_OPERATION_ID",
        )

        val tamperedDiagnostics = Json.parseToJsonElement(diagnosticsFile.readText()).jsonArray
        assertTrue(tamperedDiagnostics.size > 0, "diagnostics.json must not be empty on tampered plan")
        assertTrue(
            tamperedDiagnostics.any { it.toString().contains(PluginPlanValidator.CODE_DUPLICATE_OPERATION_ID) },
            "diagnostics.json must contain DUPLICATE_OPERATION_ID",
        )
    }

    @Test
    fun testPackageModProducesValidZipAndInspectModInspectsIt() {
        val tempDir = createTempExampleCopy().toFile()
        val packageResult = PatchModTool.packageMod(tempDir)
        assertIs<CliResult.Success>(packageResult)
        assertEquals(0, packageResult.exitCode)

        val zipFile = File(tempDir, "build/org.ide.lti-example-file-property-1.0.0.lti-mod.zip")
        assertTrue(zipFile.exists(), "Package zip must exist at: ${zipFile.absolutePath}")

        val readResult = PluginPackageReader.read(zipFile.toPath())
        assertIs<PackageReadResult.Ok>(readResult)
        val readPkg = readResult.pkg
        assertEquals("org.ide.lti", readPkg.manifest.publisher)
        assertEquals("example-file-property", readPkg.manifest.id)
        assertEquals("1.0.0", readPkg.manifest.version)

        val expectedDigest = PluginPackageCodec.contentIdentity(readPkg.manifest.payloads)
        assertEquals(expectedDigest, readPkg.contentDigest)

        val inspectResult = PatchModTool.inspectMod(tempDir, zipFile)
        assertIs<CliResult.Success>(inspectResult)
        assertTrue(
            inspectResult.message.contains("example-file-property"),
            "Inspect output should include manifest id",
        )
        assertTrue(
            inspectResult.message.contains("assets/overlay/hosts"),
            "Inspect output should include payload path",
        )
    }

    @Test
    fun testTestModPassesShippedScenariosAndFailsWhenFlipped() {
        val tempDir = createTempExampleCopy().toFile()
        val testResult = PatchModTool.testMod(tempDir)
        assertIs<CliResult.Success>(testResult)
        assertEquals(0, testResult.exitCode)

        // Flip expectResolved in scenarios.json
        val scenariosFile = File(tempDir, "scenarios.json")
        val content = scenariosFile.readText()
        val flippedContent = content.replace("\"expectResolved\": true", "\"expectResolved\": false")
        scenariosFile.writeText(flippedContent)

        val flippedResult = PatchModTool.testMod(tempDir)
        assertIs<CliResult.Failure>(flippedResult)
        assertNotEquals(0, flippedResult.exitCode)
    }

    @Test
    fun testValidateModOnExampleAndOnMissingManifest() {
        val tempDir = createTempExampleCopy().toFile()
        val validResult = PatchModTool.validateMod(tempDir)
        assertIs<CliResult.Success>(validResult)
        assertEquals(0, validResult.exitCode)

        // Delete manifest.json
        File(tempDir, "manifest.json").delete()
        val invalidResult = PatchModTool.validateMod(tempDir)
        assertIs<CliResult.Failure>(invalidResult)
        assertNotEquals(0, invalidResult.exitCode)
    }
}
