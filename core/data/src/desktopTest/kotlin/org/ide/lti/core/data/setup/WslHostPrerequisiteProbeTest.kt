/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup

import io.ltirom.tooling.client.wsl.CliExecutionResult
import io.ltirom.tooling.client.wsl.WslCliExecutor
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.model.setup.Need
import org.ide.lti.core.model.setup.RequirementCheck
import org.ide.lti.core.model.setup.RequirementStatus
import org.ide.lti.core.model.setup.SetupEnvironment
import org.ide.lti.core.model.setup.SystemRequirement
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WslHostPrerequisiteProbeTest {

    private class FakeProbeCli(
        var packageListsFresh: Boolean = true,
        var lines: List<String> = emptyList(),
        var aptCachePolicyLines: Map<String, String> = emptyMap(),
        var javaListingOutput: String = "/usr/lib/jvm/java-21-openjdk-amd64|openjdk version \"21.0.4\" " +
            "2024-07-16\n/usr/lib/jvm/java-17-openjdk-amd64|openjdk version \"17.0.12\" 2024-07-16",
    ) : WslCliExecutor() {
        var lastExecutedCommand: List<String>? = null

        override fun execute(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            charset: Charset,
            user: String?,
        ): CliExecutionResult {
            lastExecutedCommand = command
            // Single probe script execution
            val combinedOutput = buildString {
                if (packageListsFresh) {
                    appendLine("PACKAGES_FRESH=1")
                } else {
                    appendLine("PACKAGES_FRESH=0")
                }
                appendLine("JAVA_HOMES_BEGIN")
                appendLine(javaListingOutput)
                appendLine("JAVA_HOMES_END")
                for (line in lines) {
                    appendLine(line)
                }
                // A real run prints a line for every non-Java requirement; unspecified ones are present.
                val reported = lines.map { it.substringBefore("=") }.toSet()
                RequirementCatalog.REQUIREMENTS.filter {
                    it.id !in reported && it.check !is RequirementCheck.JavaMajor
                }.forEach { appendLine("${it.id}=present") }
                for ((pkg, policy) in aptCachePolicyLines) {
                    appendLine("APT_POLICY:$pkg:$policy")
                }
            }

            return CliExecutionResult(0, combinedOutput, "")
        }
    }

    private val testEnv = SetupEnvironment(
        distro = "Ubuntu-24.04",
        wslVersion = 2,
        osId = "ubuntu",
        osVersionId = "24.04",
        user = "testuser",
        home = "/home/testuser",
    )

    @Test
    fun `missing requirements appear in missingPackages sorted and deduplicated`() = runTest {
        val fakeCli = FakeProbeCli(
            packageListsFresh = true,
            lines = listOf(
                "git=missing",
                "cmake=missing",
                "build-essential=present",
            ),
        )
        val probe = WslHostPrerequisiteProbe(cli = fakeCli)
        val report = probe.probe(testEnv)

        assertEquals(RequirementStatus.Missing, report.results["git"])
        assertEquals(RequirementStatus.Missing, report.results["cmake"])
        assertEquals(RequirementStatus.Present, report.results["build-essential"])

        // missingPackages sorted and de-duplicated
        assertEquals(listOf("cmake", "git"), report.missingPackages)
        assertEquals("sudo apt-get update && sudo apt-get install -y cmake git", report.installCommand)
    }

    @Test
    fun `python-crypto produces python3-cryptography and python3-pyasn1 in missingPackages`() = runTest {
        val fakeCli = FakeProbeCli(
            packageListsFresh = true,
            lines = listOf(
                "python-crypto=missing",
            ),
        )
        val probe = WslHostPrerequisiteProbe(cli = fakeCli)
        val report = probe.probe(testEnv)

        assertEquals(RequirementStatus.Missing, report.results["python-crypto"])
        assertTrue(report.missingPackages.contains("python3-cryptography"))
        assertTrue(report.missingPackages.contains("python3-pyasn1"))
        assertEquals(
            "sudo apt-get update && sudo apt-get install -y python3-cryptography python3-pyasn1",
            report.installCommand,
        )
    }

    @Test
    fun `java discovery selects java 21 path when both 17 and 21 are installed`() = runTest {
        val fakeCli = FakeProbeCli(
            packageListsFresh = true,
            javaListingOutput = "/usr/lib/jvm/java-17-openjdk-amd64|openjdk version \"17.0.12\" " +
                "2024-07-16\n/usr/lib/jvm/java-21-openjdk-amd64|openjdk version \"21.0.4\" 2024-07-16",
            lines = listOf(
                "java-server=present",
            ),
        )
        val probe = WslHostPrerequisiteProbe(cli = fakeCli)
        val report = probe.probe(testEnv)

        assertEquals(RequirementStatus.Present, report.results["java-server"])
        assertEquals("/usr/lib/jvm/java-21-openjdk-amd64", report.javaHome)
    }

    @Test
    fun `java discovery reports Missing and null javaHome when only java 17 is installed`() = runTest {
        val fakeCli = FakeProbeCli(
            packageListsFresh = true,
            javaListingOutput = "/usr/lib/jvm/java-17-openjdk-amd64|openjdk version \"17.0.12\" 2024-07-16",
            lines = listOf(
                "java-server=missing",
            ),
        )
        val probe = WslHostPrerequisiteProbe(cli = fakeCli)
        val report = probe.probe(testEnv)

        assertEquals(RequirementStatus.Missing, report.results["java-server"])
        assertNull(report.javaHome)
        assertTrue(report.missingPackages.contains("openjdk-21-jdk"))
    }

    @Test
    fun `fresh package lists with candidate none marks package Unavailable and excludes from missingPackages`() =
        runTest {
            val fakeCli = FakeProbeCli(
                packageListsFresh = true,
                lines = listOf(
                    "legacy-pkg=missing",
                    "git=missing",
                ),
                aptCachePolicyLines = mapOf(
                    "legacy-pkg" to "none",
                    "git" to "1:2.43.0-1ubuntu7.1",
                ),
            )
            val legacy =
                SystemRequirement(
                    "legacy-pkg",
                    RequirementCheck.Package("legacy-pkg"),
                    listOf("legacy-pkg"),
                    Need.BUILD,
                )
            val probe = WslHostPrerequisiteProbe(cli = fakeCli, requirements = RequirementCatalog.REQUIREMENTS + legacy)
            val report = probe.probe(testEnv)

            val legacyStatus = report.results["legacy-pkg"]
            assertTrue(
                legacyStatus is RequirementStatus.Unavailable,
                "legacy-pkg should be Unavailable, was $legacyStatus",
            )
            assertEquals("24.04", (legacyStatus as RequirementStatus.Unavailable).release)
            assertEquals(listOf("git"), report.missingPackages)
            assertEquals("sudo apt-get update && sudo apt-get install -y git", report.installCommand)
        }

    @Test
    fun `empty package lists does not mark package Unavailable and still offers install command`() = runTest {
        val fakeCli = FakeProbeCli(
            packageListsFresh = false,
            lines = listOf(
                "git=missing",
            ),
            aptCachePolicyLines = emptyMap(),
        )
        val probe = WslHostPrerequisiteProbe(cli = fakeCli)
        val report = probe.probe(testEnv)

        assertFalse(report.packageListsFresh)
        assertEquals(RequirementStatus.Missing, report.results["git"])
        assertEquals(listOf("git"), report.missingPackages)
        assertEquals("sudo apt-get update && sudo apt-get install -y git", report.installCommand)
    }

    @Test
    fun `a failed probe reports CheckFailed and never assumes requirements are present`() = runTest {
        val failingCli = object : WslCliExecutor() {
            override fun execute(
                distro: String,
                command: List<String>,
                timeoutSeconds: Long,
                charset: Charset,
                user: String?,
            ): CliExecutionResult = CliExecutionResult(1, "", "wsl: distro stopped")
        }
        val report = WslHostPrerequisiteProbe(cli = failingCli).probe(testEnv)

        assertTrue(report.results.values.all { it is RequirementStatus.CheckFailed }, "${report.results}")
        assertTrue(report.missingPackages.isEmpty())
        assertNull(report.installCommand)
    }

    @Test
    fun `missing java 21 adds its package even though the script prints no java line`() = runTest {
        val fakeCli = FakeProbeCli(
            javaListingOutput = "/usr/lib/jvm/java-17-openjdk-amd64|openjdk version " +
                "\"17.0.12\" 2024-07-16",
        )
        val report = WslHostPrerequisiteProbe(cli = fakeCli).probe(testEnv)

        assertEquals(RequirementStatus.Missing, report.results["java-server"])
        assertTrue("openjdk-21-jdk" in report.missingPackages)
    }

    @Test
    fun `probe script checks apt list freshness in the shell and asks apt for candidates`() = runTest {
        val fakeCli = FakeProbeCli()
        WslHostPrerequisiteProbe(cli = fakeCli).probe(testEnv)

        val script = fakeCli.lastExecutedCommand!!.last()
        assertTrue("ls /var/lib/apt/lists/*_Packages" in script)
        assertTrue("apt-cache policy" in script)
        assertTrue("'git=missing'" in script)
    }

    @Test
    fun `java is judged by its own version output, not its folder name`() = runTest {
        val fakeCli = FakeProbeCli(
            javaListingOutput = listOf(
                // Custom folder name, real JDK 21: accepted.
                "/usr/lib/jvm/corretto|openjdk version \"21.0.4\" 2024-07-16",
                // Named like 21 but actually 17: rejected.
                "/usr/lib/jvm/java-21-fake|openjdk version \"17.0.12\" 2024-07-16",
                // Broken binary prints no version: rejected.
                "/usr/lib/jvm/java-22-broken|Error: could not find libjava.so",
            ).joinToString("\n"),
        )
        val report = WslHostPrerequisiteProbe(cli = fakeCli).probe(testEnv)

        assertEquals("/usr/lib/jvm/corretto", report.javaHome)
        assertEquals(RequirementStatus.Present, report.results["java-server"])
    }

    @Test
    fun `a misleadingly named or broken java is not accepted`() = runTest {
        val fakeCli = FakeProbeCli(
            javaListingOutput = listOf(
                "/usr/lib/jvm/java-21-fake|openjdk version \"17.0.12\" 2024-07-16",
                "/usr/lib/jvm/java-21-broken|",
                "/usr/lib/jvm/legacy|java version \"1.8.0_392\"",
            ).joinToString("\n"),
        )
        val report = WslHostPrerequisiteProbe(cli = fakeCli).probe(testEnv)

        assertNull(report.javaHome)
        assertEquals(RequirementStatus.Missing, report.results["java-server"])
        assertTrue("openjdk-21-jdk" in report.missingPackages)
    }
}
