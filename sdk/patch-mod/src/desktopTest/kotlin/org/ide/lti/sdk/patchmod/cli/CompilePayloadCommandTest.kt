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

import org.ide.lti.core.domain.plugin.PayloadCompilationReceipt
import org.ide.lti.core.domain.plugin.PayloadCompilationRequest
import org.ide.lti.core.domain.plugin.PayloadCompilationResult
import org.ide.lti.core.domain.plugin.PayloadCompilerPort
import org.ide.lti.core.domain.plugin.ToolIdentity
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CompilePayloadCommandTest {

    private fun createTempDir(prefix: String): File = Files.createTempDirectory(prefix).toFile()

    @Test
    fun testAbsentPayloadJsonExits0WithNoPayloadToCompile() {
        val tempDir = createTempDir("cli-payload-absent-")
        try {
            val result = PatchModTool.compileModPayload(tempDir)
            assertIs<CliResult.Success>(result)
            assertEquals(0, result.exitCode)
            assertTrue(
                result.message.contains("no payload to compile"),
                "Expected message to contain 'no payload to compile', got: ${result.message}",
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testPayloadJsonWithStubbedCompilerPortWritesReceiptAndSmali() {
        val tempDir = createTempDir("cli-payload-stub-")
        try {
            val payloadJson = File(tempDir, "payload.json")
            payloadJson.writeText(
                """
                {
                  "minRuntimeApi": 34,
                  "frameworkStubJars": [],
                  "dependencyJars": []
                }
                """.trimIndent(),
            )

            val hooksDir = File(tempDir, "hooks").apply { mkdirs() }
            val hookFile = File(hooksDir, "TestHook.kt")
            hookFile.writeText("package org.example\nobject TestHook")

            val expectedReceipt = PayloadCompilationReceipt(
                sourceFingerprint = "111122223333444455556666777788889999aaaabbbbccccddddeeeeffff0000",
                outputSha256 = "aaaabbbbccccddddeeeeffff0000111122223333444455556666777788889999",
                kotlinCompiler = ToolIdentity("kotlinc", "2.0.21", "sha-kotlinc"),
                dexer = ToolIdentity("d8", "8.0.0", "sha-d8"),
                disassembler = ToolIdentity("baksmali", "2.5.2", "sha-baksmali"),
                frameworkStubDigest = "",
                minRuntimeApi = 34,
                dependencyDigests = emptyList(),
                emittedClassDescriptors = listOf("Lorg/example/TestHook;"),
            )

            val stubCompiler = object : PayloadCompilerPort {
                override suspend fun compile(request: PayloadCompilationRequest): PayloadCompilationResult {
                    val outDir = File(request.outputSmaliDir).apply { mkdirs() }
                    val smali = File(outDir, "TestHook.smali")
                    smali.writeText(".class public Lorg/example/TestHook;\n")
                    return PayloadCompilationResult.Compiled(
                        receipt = expectedReceipt,
                        smaliFiles = listOf(smali.absolutePath),
                    )
                }
            }

            val result = PatchModTool.compileModPayload(tempDir, compiler = stubCompiler)
            assertIs<CliResult.Success>(result)
            assertEquals(0, result.exitCode)

            val receiptFile = File(tempDir, "build/payload-receipt.json")
            assertTrue(receiptFile.exists(), "build/payload-receipt.json must be written")
            val receiptContent = receiptFile.readText()
            assertTrue(receiptContent.contains("111122223333444455556666777788889999aaaabbbbccccddddeeeeffff0000"))
            assertTrue(receiptContent.contains("Lorg/example/TestHook;"))

            val smaliFile = File(tempDir, "assets/payload/TestHook.smali")
            assertTrue(smaliFile.exists(), "assets/payload/TestHook.smali must exist")
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
