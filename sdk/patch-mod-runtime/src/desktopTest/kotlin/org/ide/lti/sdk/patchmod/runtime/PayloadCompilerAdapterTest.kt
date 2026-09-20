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

import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.plugin.PayloadCompilationRequest
import org.ide.lti.core.domain.plugin.PayloadCompilationResult
import org.ide.lti.core.domain.plugin.PayloadCompilerCodes
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Suppress("CyclomaticComplexMethod")
class PayloadCompilerAdapterTest {

    private val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true

    private fun createTempDir(prefix: String): File = Files.createTempDirectory(prefix).toFile()

    private fun createScript(dir: File, name: String, batBody: String, shBody: String): File {
        val file = if (isWindows) {
            File(dir, "$name.bat").apply {
                writeText("@echo off\r\n$batBody\r\n")
            }
        } else {
            File(dir, "$name.sh").apply {
                writeText("#!/bin/sh\n$shBody\n")
                setExecutable(true)
            }
        }
        return file
    }

    private fun canExecuteScripts(vararg scripts: File): Boolean {
        if (isWindows) return true
        return scripts.all { it.canExecute() }
    }

    private fun createFakeKotlinc(dir: File, classToCopy: File? = null): File {
        val batBody = if (classToCopy != null) {
            "@echo off\r\nif \"%~1\"==\"--version\" (echo 2.0.21 & exit /b 0)\r\n" +
                "copy /Y \"${classToCopy.absolutePath}\" \"%~2\\${classToCopy.name}\" >nul\r\nexit /b 0"
        } else {
            "if \"%~1\"==\"--version\" (echo 2.0.21 & exit /b 0)\r\nexit /b 0"
        }
        val shBody = if (classToCopy != null) {
            "if [ \"$1\" = \"--version\" ]; then echo \"2.0.21\"; exit 0; fi\n" +
                "cp \"${classToCopy.absolutePath}\" \"$2/${classToCopy.name}\"\nexit 0"
        } else {
            "if [ \"$1\" = \"--version\" ]; then echo \"2.0.21\"; exit 0; fi\nexit 0"
        }
        return createScript(dir, "kotlinc", batBody, shBody)
    }

    private fun createFakeD8(dir: File): File {
        val batBody = "if \"%~1\"==\"--version\" (echo 8.0.0 & exit /b 0)\r\n" +
            "echo dummy_dex > \"%~2\\classes.dex\"\r\nexit /b 0"
        val shBody = "if [ \"$1\" = \"--version\" ]; then echo \"8.0.0\"; exit 0; fi\n" +
            "echo dummy_dex > \"$2/classes.dex\"\nexit 0"
        return createScript(dir, "d8", batBody, shBody)
    }

    private fun createFakeBaksmali(dir: File, smaliToCopy: File? = null): File {
        val batBody = if (smaliToCopy != null) {
            "@echo off\r\nif \"%~1\"==\"--version\" (echo 2.5.2 & exit /b 0)\r\n" +
                "copy /Y \"${smaliToCopy.absolutePath}\" \"%~4\\${smaliToCopy.name}\" >nul\r\nexit /b 0"
        } else {
            "if \"%~1\"==\"--version\" (echo 2.5.2 & exit /b 0)\r\nexit /b 0"
        }
        val shBody = if (smaliToCopy != null) {
            "if [ \"$1\" = \"--version\" ]; then echo \"2.5.2\"; exit 0; fi\n" +
                "cp \"${smaliToCopy.absolutePath}\" \"$4/${smaliToCopy.name}\"\nexit 0"
        } else {
            "if [ \"$1\" = \"--version\" ]; then echo \"2.5.2\"; exit 0; fi\nexit 0"
        }
        return createScript(dir, "baksmali", batBody, shBody)
    }

    @Test
    fun testToolPathsPointingAtNonExistentBinariesFailsWithToolNotFoundAndNothingWritten() = runTest {
        val tempDir = createTempDir("payload-test-missing-")
        try {
            val outputSmaliDir = File(tempDir, "assets/payload")
            val toolPaths = mapOf(
                "kotlinc" to File(tempDir, "nonexistent_kotlinc").absolutePath,
                "d8" to File(tempDir, "nonexistent_d8").absolutePath,
                "baksmali" to File(tempDir, "nonexistent_baksmali").absolutePath,
            )
            val adapter = PayloadCompilerAdapter(toolPaths)
            val request = PayloadCompilationRequest(
                sourceFiles = listOf(File(tempDir, "Hook.kt").apply { writeText("// test") }.absolutePath),
                frameworkStubJars = emptyList(),
                dependencyJars = emptyList(),
                minRuntimeApi = 34,
                outputSmaliDir = outputSmaliDir.absolutePath,
            )

            val result = adapter.compile(request)
            assertIs<PayloadCompilationResult.Failed>(result)
            val errors = result.report.errors
            assertTrue(errors.isNotEmpty(), "Expected at least one error")
            assertEquals(PayloadCompilerCodes.TOOL_NOT_FOUND, errors.first().code)
            assertTrue(
                errors.any { it.objectId == "kotlinc" || it.message.contains("kotlinc") },
                "Expected error to name the missing tool",
            )
            assertFalse(outputSmaliDir.exists(), "Smali output directory must not be created when tool is missing")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testKotlincExitingNonZeroFailsWithKotlincFailedCarryingStderr() = runTest {
        val tempDir = createTempDir("payload-test-kotlinc-fail-")
        try {
            val bat = "if \"%~1\"==\"--version\" (echo 2.0.21 & exit /b 0)\r\n" +
                ">&2 echo Kotlinc compilation syntax error\r\nexit /b 1"
            val sh = "if [ \"$1\" = \"--version\" ]; then echo \"2.0.21\"; exit 0; fi\n" +
                ">&2 echo \"Kotlinc compilation syntax error\"\nexit 1"
            val kotlinc = createScript(tempDir, "kotlinc", bat, sh)
            val d8 = createFakeD8(tempDir)
            val baksmali = createFakeBaksmali(tempDir)

            if (!canExecuteScripts(kotlinc, d8, baksmali)) {
                println("Skipping testKotlincExitingNonZero: cannot execute scripts")
                return@runTest
            }

            val adapter = PayloadCompilerAdapter(
                mapOf(
                    "kotlinc" to kotlinc.absolutePath,
                    "d8" to d8.absolutePath,
                    "baksmali" to baksmali.absolutePath,
                ),
            )
            val sourceFile = File(tempDir, "Hook.kt").apply { writeText("class Hook") }
            val request = PayloadCompilationRequest(
                sourceFiles = listOf(sourceFile.absolutePath),
                frameworkStubJars = emptyList(),
                dependencyJars = emptyList(),
                minRuntimeApi = 34,
                outputSmaliDir = File(tempDir, "assets/payload").absolutePath,
            )

            val result = adapter.compile(request)
            assertIs<PayloadCompilationResult.Failed>(result)
            val err = result.report.errors.firstOrNull { it.code == PayloadCompilerCodes.KOTLINC_FAILED }
            assertNotNull(err, "Expected KOTLINC_FAILED error code")
            assertTrue(
                err.message.contains("Kotlinc compilation syntax error"),
                "Expected error message to carry stderr: ${err.message}",
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testClassFixtureContainingKotlinJvmInternalRejectedWithKotlinRuntimeReference() = runTest {
        val tempDir = createTempDir("payload-test-runtime-ref-")
        try {
            val classDir = File(tempDir, "classes").apply { mkdirs() }
            val classFile = File(classDir, "BadHook.class")
            val dummyBytes = byteArrayOf(
                0xCA.toByte(),
                0xFE.toByte(),
                0xBA.toByte(),
                0xBE.toByte(),
                0x00,
                0x00,
                0x00,
                0x41,
            ) + "kotlin/jvm/internal/Intrinsics".toByteArray(Charsets.UTF_8)
            classFile.writeBytes(dummyBytes)

            val kotlinc = createFakeKotlinc(tempDir, classFile)
            val d8 = createFakeD8(tempDir)
            val baksmali = createFakeBaksmali(tempDir)

            if (!canExecuteScripts(kotlinc, d8, baksmali)) {
                println("Skipping testClassFixtureContainingKotlinJvmInternalRejectedWithKotlinRuntimeReference")
                return@runTest
            }

            val adapter = PayloadCompilerAdapter(
                mapOf(
                    "kotlinc" to kotlinc.absolutePath,
                    "d8" to d8.absolutePath,
                    "baksmali" to baksmali.absolutePath,
                ),
            )
            val sourceFile = File(tempDir, "BadHook.kt").apply { writeText("class BadHook") }
            val request = PayloadCompilationRequest(
                sourceFiles = listOf(sourceFile.absolutePath),
                frameworkStubJars = emptyList(),
                dependencyJars = emptyList(),
                minRuntimeApi = 34,
                outputSmaliDir = File(tempDir, "assets/payload").absolutePath,
            )

            val result = adapter.compile(request)
            assertIs<PayloadCompilationResult.Failed>(result)
            val err = result.report.errors.firstOrNull { it.code == PayloadCompilerCodes.KOTLIN_RUNTIME_REFERENCE }
            assertNotNull(err, "Expected KOTLIN_RUNTIME_REFERENCE error")
            assertTrue(
                err.objectId?.contains("BadHook") == true || err.message.contains("BadHook"),
                "Expected error to name the offending class: ${err.message}",
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testSmaliOutputDirContainingOnlyAndroidClassesFailsWithPlatformClassEmitted() = runTest {
        val tempDir = createTempDir("payload-test-platform-class-")
        try {
            val cleanClassDir = File(tempDir, "classes").apply { mkdirs() }
            val cleanClassFile = File(cleanClassDir, "Clean.class").apply {
                writeBytes(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()))
            }
            val smaliDir = File(tempDir, "smali_fixture").apply { mkdirs() }
            val smaliFile = File(smaliDir, "Activity.smali").apply {
                writeText(".class public Landroid/app/Activity;\n.super Ljava/lang/Object;\n")
            }

            val kotlinc = createFakeKotlinc(tempDir, cleanClassFile)
            val d8 = createFakeD8(tempDir)
            val baksmali = createFakeBaksmali(tempDir, smaliFile)

            if (!canExecuteScripts(kotlinc, d8, baksmali)) {
                println("Skipping testSmaliOutputDirContainingOnlyAndroidClasses")
                return@runTest
            }

            val adapter = PayloadCompilerAdapter(
                mapOf(
                    "kotlinc" to kotlinc.absolutePath,
                    "d8" to d8.absolutePath,
                    "baksmali" to baksmali.absolutePath,
                ),
            )
            val request = PayloadCompilationRequest(
                sourceFiles = listOf(File(tempDir, "Source.kt").apply { writeText("class Source") }.absolutePath),
                frameworkStubJars = emptyList(),
                dependencyJars = emptyList(),
                minRuntimeApi = 34,
                outputSmaliDir = File(tempDir, "out_smali").apply { mkdirs() }.absolutePath,
            )

            val result = adapter.compile(request)
            assertIs<PayloadCompilationResult.Failed>(result)
            val err = result.report.errors.firstOrNull { it.code == PayloadCompilerCodes.PLATFORM_CLASS_EMITTED }
            assertNotNull(err, "Expected PLATFORM_CLASS_EMITTED error")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testEmptySmaliOutputDirFailsWithNoClassesEmitted() = runTest {
        val tempDir = createTempDir("payload-test-empty-smali-")
        try {
            val cleanClassDir = File(tempDir, "classes").apply { mkdirs() }
            val cleanClassFile = File(cleanClassDir, "Clean.class").apply {
                writeBytes(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()))
            }

            val kotlinc = createFakeKotlinc(tempDir, cleanClassFile)
            val d8 = createFakeD8(tempDir)
            val baksmali = createFakeBaksmali(tempDir)

            if (!canExecuteScripts(kotlinc, d8, baksmali)) {
                println("Skipping testEmptySmaliOutputDirFailsWithNoClassesEmitted")
                return@runTest
            }

            val adapter = PayloadCompilerAdapter(
                mapOf(
                    "kotlinc" to kotlinc.absolutePath,
                    "d8" to d8.absolutePath,
                    "baksmali" to baksmali.absolutePath,
                ),
            )
            val request = PayloadCompilationRequest(
                sourceFiles = listOf(File(tempDir, "Source.kt").apply { writeText("class Source") }.absolutePath),
                frameworkStubJars = emptyList(),
                dependencyJars = emptyList(),
                minRuntimeApi = 34,
                outputSmaliDir = File(tempDir, "out_smali").apply { mkdirs() }.absolutePath,
            )

            val result = adapter.compile(request)
            assertIs<PayloadCompilationResult.Failed>(result)
            val err = result.report.errors.firstOrNull { it.code == PayloadCompilerCodes.NO_CLASSES_EMITTED }
            assertNotNull(err, "Expected NO_CLASSES_EMITTED error")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testSuccessfulRunOverFakeToolchainProducesReceiptMatchingInputs() = runTest {
        val tempDir = createTempDir("payload-test-success-")
        try {
            val cleanClassDir = File(tempDir, "classes").apply { mkdirs() }
            val cleanClassFile = File(cleanClassDir, "ExampleHook.class").apply {
                writeBytes(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()))
            }

            val smaliDir = File(tempDir, "smali_fixture").apply { mkdirs() }
            val smaliFile = File(smaliDir, "ExampleHook.smali").apply {
                writeText(".class public Lorg/ide/lti/example/ExampleHook;\n.super Ljava/lang/Object;\n")
            }

            val kotlinc = createFakeKotlinc(tempDir, cleanClassFile)
            val d8 = createFakeD8(tempDir)
            val baksmali = createFakeBaksmali(tempDir, smaliFile)

            if (!canExecuteScripts(kotlinc, d8, baksmali)) {
                println("Skipping testSuccessfulRunOverFakeToolchainProducesReceiptMatchingInputs")
                return@runTest
            }

            val sourceFile = File(tempDir, "ExampleHook.kt").apply {
                writeText("package org.ide.lti.example\nobject ExampleHook\n")
            }
            val outputSmaliDir = File(tempDir, "out_smali").apply { mkdirs() }

            val adapter = PayloadCompilerAdapter(
                mapOf(
                    "kotlinc" to kotlinc.absolutePath,
                    "d8" to d8.absolutePath,
                    "baksmali" to baksmali.absolutePath,
                ),
            )
            val request = PayloadCompilationRequest(
                sourceFiles = listOf(sourceFile.absolutePath),
                frameworkStubJars = emptyList(),
                dependencyJars = emptyList(),
                minRuntimeApi = 34,
                outputSmaliDir = outputSmaliDir.absolutePath,
            )

            val result = adapter.compile(request)
            assertIs<PayloadCompilationResult.Compiled>(result)
            assertEquals(1, result.smaliFiles.size)

            val receipt = result.receipt
            val expectedSourceSha = sha256Bytes(sourceFile.readBytes())
            val expectedSmaliSha = sha256Bytes(File(outputSmaliDir, "ExampleHook.smali").readBytes())

            assertEquals(expectedSourceSha, receipt.sourceFingerprint)
            assertEquals(expectedSmaliSha, receipt.outputSha256)
            assertEquals("kotlinc", receipt.kotlinCompiler.name)
            assertEquals("2.0.21", receipt.kotlinCompiler.version)
            assertEquals("d8", receipt.dexer.name)
            assertEquals("8.0.0", receipt.dexer.version)
            assertEquals("baksmali", receipt.disassembler.name)
            assertEquals("2.5.2", receipt.disassembler.version)
            assertEquals(34, receipt.minRuntimeApi)
            assertEquals(listOf("Lorg/ide/lti/example/ExampleHook;"), receipt.emittedClassDescriptors)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun sha256Bytes(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
