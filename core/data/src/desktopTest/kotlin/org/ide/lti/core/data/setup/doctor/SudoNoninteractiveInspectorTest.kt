package org.ide.lti.core.data.setup.doctor

import io.ltirom.tooling.client.wsl.CliExecutionResult
import io.ltirom.tooling.client.wsl.WslCliExecutor
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.setup.DiagnosticCategory
import org.ide.lti.core.domain.setup.DiagnosticCheckItem
import org.ide.lti.core.domain.setup.StepStatus
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SudoNoninteractiveInspectorTest {

    private class MockCliExecutor(
        var sudoSuccess: Boolean = true
    ) : WslCliExecutor() {
        val executedCommands = mutableListOf<List<String>>()
        val usersUsed = mutableListOf<String?>()

        override fun execute(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            charset: Charset,
            user: String?
        ): CliExecutionResult {
            executedCommands.add(command)
            usersUsed.add(user)

            return when {
                command == listOf("sudo", "-n", "true") -> {
                    if (sudoSuccess) {
                        CliExecutionResult(0, "", "")
                    } else {
                        CliExecutionResult(1, "", "sudo: a password is required")
                    }
                }
                command.firstOrNull() == "apt-get" || command.contains("apt-get") -> {
                    CliExecutionResult(0, "apt success", "")
                }
                else -> CliExecutionResult(0, "ok", "")
            }
        }
    }

    private fun createContext(): DiagnosticContext = DiagnosticContext(
        distro = "Ubuntu",
        home = "/home/lti",
        binDir = "/home/lti/LtiRomTools/bin",
        binariesPresent = false,
        availableCommands = emptySet()
    )

    @Test
    fun `sudo -n true success produces SUCCESS status`() = runTest {
        val cli = MockCliExecutor(sudoSuccess = true)
        val inspector = SudoNoninteractiveInspector(cli)

        val result = inspector.inspect(createContext())

        assertEquals("sudo_noninteractive", result.id)
        assertEquals(DiagnosticCategory.OS_UTILITIES, result.category)
        assertEquals(StepStatus.SUCCESS, result.status)
        assertTrue(result.detail.contains("Passwordless sudo configured"))
        assertNull(result.copyableCommand)
        assertNull(result.remediation)
        assertTrue(cli.executedCommands.contains(listOf("sudo", "-n", "true")))
    }

    @Test
    fun `sudo -n true failure produces WARNING status with one-time sudoers instruction`() = runTest {
        val cli = MockCliExecutor(sudoSuccess = false)
        val inspector = SudoNoninteractiveInspector(cli)

        val result = inspector.inspect(createContext())

        assertEquals("sudo_noninteractive", result.id)
        assertEquals(DiagnosticCategory.OS_UTILITIES, result.category)
        assertEquals(StepStatus.WARNING, result.status)
        assertTrue(result.detail.contains("Non-interactive sudo is not configured"))
        assertNotNull(result.remediation)
        assertNotNull(result.copyableCommand)
        assertTrue(result.copyableCommand!!.contains("/etc/sudoers.d/ltirom"))
        assertTrue(result.isOptionalForRuntime)
    }

    @Test
    fun `WslPackageRemediator refuses to run when passwordless sudo fails and never falls back to wsl -u root`() = runTest {
        val cli = MockCliExecutor(sudoSuccess = false)
        val remediator = WslPackageRemediator(cli)

        val failedItems = listOf(
            DiagnosticCheckItem(
                id = "host_compilers",
                title = "Host Compilers",
                status = StepStatus.FAILED
            )
        )

        val progressMessages = mutableListOf<String>()
        val success = remediator.remediate(createContext(), failedItems) { progressMessages.add(it) }

        assertFalse(success, "Remediator must return false when sudo -n true fails")
        assertTrue(
            progressMessages.any { it.contains("/etc/sudoers.d/ltirom") || it.contains("Passwordless non-interactive sudo") },
            "Must surface the one-time sudoers instruction: $progressMessages"
        )
        // Verify it NEVER used user="root"
        assertFalse(
            cli.usersUsed.any { it == "root" },
            "WslPackageRemediator must NEVER use user='root' fallback: ${cli.usersUsed}"
        )
        // Verify no apt-get command was actually executed
        assertFalse(
            cli.executedCommands.any { cmd -> cmd.contains("apt-get") },
            "No apt-get command must run without passwordless sudo"
        )
    }

    @Test
    fun `WslPackageRemediator executes apt-get using sudo -n without user root when passwordless sudo passes`() = runTest {
        val cli = MockCliExecutor(sudoSuccess = true)
        val remediator = WslPackageRemediator(cli)

        val failedItems = listOf(
            DiagnosticCheckItem(
                id = "host_compilers",
                title = "Host Compilers",
                status = StepStatus.FAILED
            )
        )

        val progressMessages = mutableListOf<String>()
        val success = remediator.remediate(createContext(), failedItems) { progressMessages.add(it) }

        assertTrue(success, "Remediator must succeed when sudo -n true passes")
        // Verify all apt-get commands used sudo -n and never user="root"
        assertFalse(
            cli.usersUsed.any { it == "root" },
            "WslPackageRemediator must not specify user='root': ${cli.usersUsed}"
        )
        val aptCommands = cli.executedCommands.filter { cmd -> cmd.contains("apt-get") }
        assertTrue(aptCommands.isNotEmpty(), "apt-get commands must be executed")
        for (cmd in aptCommands) {
            assertEquals("sudo", cmd[0], "apt-get command must start with sudo")
            assertEquals("-n", cmd[1], "sudo command must specify -n for non-interactive execution")
        }
    }
}
