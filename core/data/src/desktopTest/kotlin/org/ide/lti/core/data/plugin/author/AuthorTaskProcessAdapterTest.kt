/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.plugin.author

import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.plugin.AuthorProject
import org.ide.lti.core.domain.plugin.AuthorTaskRefusal
import org.ide.lti.core.domain.plugin.AuthorTaskResult
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthorTaskProcessAdapterTest {

    private lateinit var tempDir: Path
    private lateinit var trustStoreDir: Path
    private lateinit var projectDir: Path
    private lateinit var adapter: AuthorTaskProcessAdapter

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("author-task-test-")
        trustStoreDir = tempDir.resolve("trust-store")
        projectDir = tempDir.resolve("project")
        Files.createDirectories(projectDir)
        adapter = AuthorTaskProcessAdapter(trustStoreDir = trustStoreDir)
    }

    @AfterTest
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun testFingerprintChangesWhenAnyCoveredFileChangesAndIsStableOtherwise() = runTest {
        // Create initial project files
        Files.writeString(projectDir.resolve("build.gradle.kts"), "// build script\n")
        Files.writeString(projectDir.resolve("settings.gradle.kts"), "// settings\n")
        Files.writeString(projectDir.resolve("gradle.properties"), "prop=val\n")

        val wrapperDir = projectDir.resolve("gradle/wrapper")
        Files.createDirectories(wrapperDir)
        Files.writeString(wrapperDir.resolve("gradle-wrapper.properties"), "distributionUrl=gradle.zip\n")

        val srcDir = projectDir.resolve("src/main/kotlin")
        Files.createDirectories(srcDir)
        Files.writeString(srcDir.resolve("Hook.kt"), "class Hook\n")

        val hooksDir = projectDir.resolve("hooks")
        Files.createDirectories(hooksDir)
        Files.writeString(hooksDir.resolve("Extra.kt"), "fun extra() = 1\n")

        val readme = projectDir.resolve("README.md")
        Files.writeString(readme, "# Project Documentation\n")

        // 1. Initial fingerprint
        val fp1 = adapter.fingerprint(projectDir.toString())
        assertNotNull(fp1)

        // 2. Stable when re-evaluated without changes
        val fp2 = adapter.fingerprint(projectDir.toString())
        assertEquals(fp1, fp2)

        // 3. Modifying uncovered file (README.md) does not change fingerprint
        Files.writeString(readme, "# Updated Documentation\n")
        val fp3 = adapter.fingerprint(projectDir.toString())
        assertEquals(fp1, fp3)

        // 4. Modifying covered file changes fingerprint
        Files.writeString(srcDir.resolve("Hook.kt"), "class Hook { val x = 42 }\n")
        val fp4 = adapter.fingerprint(projectDir.toString())
        assertNotEquals(fp1, fp4)

        // 5. Adding a new .kt file under hooks changes fingerprint
        Files.writeString(hooksDir.resolve("NewHook.kt"), "class NewHook\n")
        val fp5 = adapter.fingerprint(projectDir.toString())
        assertNotEquals(fp4, fp5)

        // 6. Missing directory returns null
        val missingDir = tempDir.resolve("non-existent-dir")
        val fpMissing = adapter.fingerprint(missingDir.toString())
        assertNull(fpMissing)
    }

    @Test
    fun testUnknownTaskNameIsRefused() = runTest {
        val project = AuthorProject(
            path = projectDir.toString(),
            fingerprint = "fp123",
            sdkVersion = "1.0.0",
            generatorEntry = "main",
        )

        val result = adapter.runTask(project, "maliciousArbitraryTask", timeoutMs = 5000L)
        assertIs<AuthorTaskResult.Refused>(result)
        assertEquals(AuthorTaskRefusal.UNKNOWN_TASK, result.reason)
    }

    @Test
    fun testRunningWithoutApprovalIsRefused() = runTest {
        Files.writeString(projectDir.resolve("build.gradle.kts"), "// build")
        val fp = adapter.fingerprint(projectDir.toString())!!

        val project = AuthorProject(
            path = projectDir.toString(),
            fingerprint = fp,
            sdkVersion = "1.0.0",
            generatorEntry = "main",
        )

        // Project has not been approved
        val result = adapter.runTask(project, "generateModPlan", timeoutMs = 5000L)
        assertIs<AuthorTaskResult.Refused>(result)
        assertEquals(AuthorTaskRefusal.NOT_TRUSTED, result.reason)
    }

    @Test
    fun testFakeGradlewNonzeroExitWithDiagnosticsYieldsFailed() = runTest {
        Files.writeString(projectDir.resolve("build.gradle.kts"), "// build")
        val fp = adapter.fingerprint(projectDir.toString())!!

        val project = AuthorProject(
            path = projectDir.toString(),
            fingerprint = fp,
            sdkVersion = "1.0.0",
            generatorEntry = "main",
        )
        adapter.approve(project)

        createFakeGradlew(
            projectDir = projectDir.toFile(),
            exitCode = 1,
            stdoutLines = listOf(
                "ERROR|INVALID_PLAN|plan.json|10|5|op1|field1|Plan validation failed",
                "ERROR COMPILATION_FAILED src/Hook.kt: Syntax error in file",
                "Regular unparsed log message",
            ),
        )

        val result = adapter.runTask(project, "generateModPlan", timeoutMs = 10000L)
        assertIs<AuthorTaskResult.Failed>(result)
        assertEquals(1, result.exitCode)
        assertEquals(2, result.diagnostics.size)

        val diag1 = result.diagnostics[0]
        assertEquals("ERROR", diag1.severity)
        assertEquals("INVALID_PLAN", diag1.code)
        assertEquals("plan.json", diag1.file)
        assertEquals(10, diag1.line)
        assertEquals(5, diag1.col)
        assertEquals("op1", diag1.operationId)
        assertEquals("field1", diag1.fieldId)
        assertEquals("Plan validation failed", diag1.message)

        val diag2 = result.diagnostics[1]
        assertEquals("ERROR", diag2.severity)
        assertEquals("COMPILATION_FAILED", diag2.code)
        assertEquals("src/Hook.kt", diag2.file)
        assertEquals("Syntax error in file", diag2.message)
    }

    @Test
    fun testOutputBoundHoldsAt256KiB() = runTest {
        Files.writeString(projectDir.resolve("build.gradle.kts"), "// build")
        val fp = adapter.fingerprint(projectDir.toString())!!

        val project = AuthorProject(
            path = projectDir.toString(),
            fingerprint = fp,
            sdkVersion = "1.0.0",
            generatorEntry = "main",
        )
        adapter.approve(project)

        // Create script emitting > 300 KiB
        val chunk = "A".repeat(1024)
        val lines = (1..350).map { chunk }

        createFakeGradlew(
            projectDir = projectDir.toFile(),
            exitCode = 0,
            stdoutLines = lines,
        )

        val result = adapter.runTask(project, "validateMod", timeoutMs = 15000L)
        assertIs<AuthorTaskResult.Success>(result)

        val stdoutByteCount = result.stdout.toByteArray(Charsets.UTF_8).size
        assertTrue(
            stdoutByteCount <= 256 * 1024,
            "stdout ($stdoutByteCount bytes) must not exceed 256 KiB bound",
        )
    }

    private fun createFakeGradlew(projectDir: File, exitCode: Int, stdoutLines: List<String>) {
        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
        if (isWindows) {
            val bat = File(projectDir, "gradlew.bat")
            val sb = StringBuilder()
            sb.append("@echo off\r\n")
            for (line in stdoutLines) {
                // Escape pipe characters for Windows batch
                val escaped = line.replace("|", "^|")
                sb.append("echo $escaped\r\n")
            }
            sb.append("exit /b $exitCode\r\n")
            bat.writeText(sb.toString())
        } else {
            val sh = File(projectDir, "gradlew")
            val sb = StringBuilder()
            sb.append("#!/bin/sh\n")
            for (line in stdoutLines) {
                sb.append("printf '%s\\n' \"").append(line.replace("\"", "\\\"")).append("\"\n")
            }
            sb.append("exit $exitCode\n")
            sh.writeText(sb.toString())
            sh.setExecutable(true)
        }
    }
}
