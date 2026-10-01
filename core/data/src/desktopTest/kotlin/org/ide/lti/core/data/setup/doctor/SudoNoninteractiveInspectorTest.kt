/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SudoNoninteractiveInspectorTest {

    private class MockCliExecutor(var sudoSuccess: Boolean = true) : WslCliExecutor() {
        val executedCommands = mutableListOf<List<String>>()
        val usersUsed = mutableListOf<String?>()

        override fun execute(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            charset: Charset,
            user: String?,
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
        availableCommands = emptySet(),
    )

    @Test
    fun `sudo -n true success produces SUCCESS status without copyable sudoers command`() = runTest {
        val cli = MockCliExecutor(sudoSuccess = true)
        val inspector = SudoNoninteractiveInspector(cli)

        val result = inspector.inspect(createContext())

        assertEquals("sudo_noninteractive", result.id)
        assertEquals(DiagnosticCategory.OS_UTILITIES, result.category)
        assertEquals(StepStatus.SUCCESS, result.status)
        assertTrue(result.detail.contains("Headless non-interactive elevation active"))
        assertNull(result.copyableCommand)
        assertNull(result.remediation)
        assertTrue(cli.executedCommands.contains(listOf("sudo", "-n", "true")))
    }

    @Test
    fun `sudo -n true failure produces SUCCESS status informing interactive elevation without sudoers command`() =
        runTest {
            val cli = MockCliExecutor(sudoSuccess = false)
            val inspector = SudoNoninteractiveInspector(cli)

            val result = inspector.inspect(createContext())

            assertEquals("sudo_noninteractive", result.id)
            assertEquals(DiagnosticCategory.OS_UTILITIES, result.category)
            assertEquals(StepStatus.SUCCESS, result.status)
            assertTrue(result.detail.contains("Interactive terminal elevation required"))
            assertNull(result.remediation)
            assertNull(result.copyableCommand)
            assertTrue(result.isOptionalForRuntime)
        }

    @Test
    fun `WslPackageRemediator requires terminal authorization for apt packages and never executes wsl -u root`() =
        runTest {
            val cli = MockCliExecutor(sudoSuccess = false)
            val remediator = WslPackageRemediator(cli)

            val failedItems = listOf(
                DiagnosticCheckItem(
                    id = "host_compilers",
                    title = "Host Compilers",
                    status = StepStatus.FAILED,
                ),
            )

            val progressMessages = mutableListOf<String>()
            val success = remediator.remediate(createContext(), failedItems) { progressMessages.add(it) }

            assertFalse(success, "Remediator must return false for privileged actions requiring terminal authorization")
            assertTrue(
                progressMessages.any { it.contains("requires administrator authorization in your WSL terminal") },
                "Must surface terminal authorization requirement: $progressMessages",
            )
            // Verify zero sudoers suggestions
            assertFalse(
                progressMessages.any { it.contains("sudoers") || it.contains("NOPASSWD") },
                "Must NEVER suggest sudoers modification in messages: $progressMessages",
            )
            // Verify it NEVER used user="root"
            assertFalse(
                cli.usersUsed.any { it == "root" },
                "WslPackageRemediator must NEVER use user='root' fallback: ${cli.usersUsed}",
            )
            // Verify no apt-get command was executed silently in background
            assertFalse(
                cli.executedCommands.any { cmd -> cmd.contains("apt-get") },
                "No apt-get command must run headlessly in background",
            )
        }
}
