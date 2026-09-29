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
import org.ide.lti.core.domain.setup.StepStatus
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolchainIntegrityInspectorTest {

    private class FakeCli(val existingFilesInBin: Set<String> = emptySet()) : WslCliExecutor() {
        val executedCommands = mutableListOf<String>()

        override fun execute(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            charset: Charset,
            user: String?,
        ): CliExecutionResult {
            val cmdStr = command.joinToString(" ")
            executedCommands.add(cmdStr)
            if (command.size >= 3 && command[0] == "test" && command[1] == "-e") {
                val path = command[2]
                val fileName = path.substringAfterLast('/')
                return if (fileName in existingFilesInBin) {
                    CliExecutionResult(0, "", "")
                } else {
                    CliExecutionResult(1, "", "No such file")
                }
            }
            return CliExecutionResult(0, "", "")
        }
    }

    @Test
    fun `inspect succeeds when all tools are in projectBinaries`() = runTest {
        val inspector = ToolchainIntegrityInspector()
        val context = DiagnosticContext(
            distro = "Ubuntu",
            home = "/home/lti",
            binDir = "/home/lti/LtiRomTools/bin",
            projectBinaries = ToolchainIntegrityInspector.DEFAULT_CORE_TOOLS.toSet(),
            availableCommands = emptySet(),
            binariesPresent = true,
        )

        val result = inspector.inspect(context)
        assertEquals(StepStatus.SUCCESS, result.status)
        assertTrue(result.detail.contains("All 14 core tools active"))
    }

    @Test
    fun `system PATH commands in availableCommands do not mask missing project binaries`() = runTest {
        // System PATH has adb, fastboot, etc., but project bin directory has none of them!
        val fakeCli = FakeCli(existingFilesInBin = emptySet())
        val inspector = ToolchainIntegrityInspector(cli = fakeCli)
        val context = DiagnosticContext(
            distro = "Ubuntu",
            home = "/home/lti",
            binDir = "/home/lti/LtiRomTools/bin",
            projectBinaries = emptySet(),
            availableCommands = ToolchainIntegrityInspector.DEFAULT_CORE_TOOLS.toSet(),
            binariesPresent = false,
        )

        val result = inspector.inspect(context)
        // Must NOT be SUCCESS! System commands must not mask missing project binaries.
        assertFalse(
            result.status == StepStatus.SUCCESS,
            "System PATH commands must never satisfy project toolchain requirements!",
        )
        assertTrue(result.detail.contains("Missing: adb"))
    }

    @Test
    fun `inspect checks binDir directly when projectBinaries is empty`() = runTest {
        val fakeCli = FakeCli(existingFilesInBin = ToolchainIntegrityInspector.DEFAULT_CORE_TOOLS.toSet())
        val inspector = ToolchainIntegrityInspector(cli = fakeCli)
        val context = DiagnosticContext(
            distro = "Ubuntu",
            home = "/home/lti",
            binDir = "/home/lti/LtiRomTools/bin",
            projectBinaries = emptySet(),
            availableCommands = emptySet(),
            binariesPresent = true,
        )

        val result = inspector.inspect(context)
        assertEquals(StepStatus.SUCCESS, result.status)
        assertTrue(result.detail.contains("All 14 core tools active"))
    }
}
