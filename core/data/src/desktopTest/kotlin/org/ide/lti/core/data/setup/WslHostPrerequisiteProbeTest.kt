package org.ide.lti.core.data.setup

import io.ltirom.tooling.client.wsl.CliExecutionResult
import io.ltirom.tooling.client.wsl.WslCliExecutor
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.model.setup.PrerequisiteReport
import org.ide.lti.core.model.setup.RequirementStatus
import org.ide.lti.core.model.setup.SetupEnvironment
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WslHostPrerequisiteProbeTest {

    private class FakeProbeCli(
        var packageListsFresh: Boolean = true,
        var lines: List<String> = emptyList(),
        var aptCachePolicyLines: Map<String, String> = emptyMap(),
        var javaListingOutput: String = "/usr/lib/jvm/java-21-openjdk-amd64\n/usr/lib/jvm/java-17-openjdk-amd64",
    ) : WslCliExecutor() {
        var lastExecutedCommand: List<String>? = null

        override fun execute(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            charset: Charset,
            user: String?
        ): CliExecutionResult {
            lastExecutedCommand = command
            val cmdStr = command.joinToString(" ")
            
            // Check for test -e /var/lib/apt/lists/*_Packages or similar fresh check
            if (cmdStr.contains("/var/lib/apt/lists") && (cmdStr.contains("test") || cmdStr.contains("find") || cmdStr.contains("ls"))) {
                return if (packageListsFresh) {
                    CliExecutionResult(0, "/var/lib/apt/lists/archive.ubuntu.com_ubuntu_dists_noble_main_binary-amd64_Packages", "")
                } else {
                    CliExecutionResult(1, "", "")
                }
            }

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
        home = "/home/testuser"
    )

    @Test
    fun `missing requirements appear in missingPackages sorted and deduplicated`() = runTest {
        val fakeCli = FakeProbeCli(
            packageListsFresh = true,
            lines = listOf(
                "git=missing",
                "cmake=missing",
                "build-essential=present"
            )
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
                "python-crypto=missing"
            )
        )
        val probe = WslHostPrerequisiteProbe(cli = fakeCli)
        val report = probe.probe(testEnv)

        assertEquals(RequirementStatus.Missing, report.results["python-crypto"])
        assertTrue(report.missingPackages.contains("python3-cryptography"))
        assertTrue(report.missingPackages.contains("python3-pyasn1"))
        assertEquals("sudo apt-get update && sudo apt-get install -y python3-cryptography python3-pyasn1", report.installCommand)
    }

    @Test
    fun `java discovery selects java 21 path when both 17 and 21 are installed`() = runTest {
        val fakeCli = FakeProbeCli(
            packageListsFresh = true,
            javaListingOutput = "/usr/lib/jvm/java-17-openjdk-amd64\n/usr/lib/jvm/java-21-openjdk-amd64",
            lines = listOf(
                "java-server=present"
            )
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
            javaListingOutput = "/usr/lib/jvm/java-17-openjdk-amd64",
            lines = listOf(
                "java-server=missing"
            )
        )
        val probe = WslHostPrerequisiteProbe(cli = fakeCli)
        val report = probe.probe(testEnv)

        assertEquals(RequirementStatus.Missing, report.results["java-server"])
        assertNull(report.javaHome)
        assertTrue(report.missingPackages.contains("openjdk-21-jdk"))
    }

    @Test
    fun `fresh package lists with candidate none marks package Unavailable and excludes from missingPackages`() = runTest {
        val fakeCli = FakeProbeCli(
            packageListsFresh = true,
            lines = listOf(
                "legacy-pkg=missing",
                "git=missing"
            ),
            aptCachePolicyLines = mapOf(
                "legacy-pkg" to "none",
                "git" to "1:2.43.0-1ubuntu7.1"
            )
        )
        val probe = WslHostPrerequisiteProbe(cli = fakeCli)
        val report = probe.probe(testEnv)

        val legacyStatus = report.results["legacy-pkg"]
        assertTrue(legacyStatus is RequirementStatus.Unavailable, "legacy-pkg should be Unavailable, was $legacyStatus")
        assertEquals("24.04", (legacyStatus as RequirementStatus.Unavailable).release)
        assertEquals(listOf("git"), report.missingPackages)
        assertEquals("sudo apt-get update && sudo apt-get install -y git", report.installCommand)
    }

    @Test
    fun `empty package lists does not mark package Unavailable and still offers install command`() = runTest {
        val fakeCli = FakeProbeCli(
            packageListsFresh = false,
            lines = listOf(
                "git=missing"
            ),
            aptCachePolicyLines = emptyMap()
        )
        val probe = WslHostPrerequisiteProbe(cli = fakeCli)
        val report = probe.probe(testEnv)

        assertFalse(report.packageListsFresh)
        assertEquals(RequirementStatus.Missing, report.results["git"])
        assertEquals(listOf("git"), report.missingPackages)
        assertEquals("sudo apt-get update && sudo apt-get install -y git", report.installCommand)
    }
}
