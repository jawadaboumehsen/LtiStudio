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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InspectorCommandsTest {

    private class MissingCliExecutor : WslCliExecutor() {
        override fun execute(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            charset: Charset,
            user: String?,
        ): CliExecutionResult {
            // Everything is missing / failing
            return CliExecutionResult(1, "", "not found")
        }
    }

    private val missingContext = DiagnosticContext(
        distro = "Ubuntu-24.04",
        home = "/home/testuser",
        binDir = "/home/testuser/LtiRomTools/bin",
        binariesPresent = false,
        systemCommands = emptySet(),
        availableCommands = emptySet(),
    )

    private val allInspectors: List<SystemDiagnosticInspector> = listOf(
        ArchiveToolsInspector(MissingCliExecutor()),
        BuildCacheInspector(MissingCliExecutor()),
        CompilersInspector(MissingCliExecutor()),
        DualJdkInspector(MissingCliExecutor()),
        KernelFuseInspector(MissingCliExecutor()),
        NativeLibrariesInspector(MissingCliExecutor()),
        PythonRuntimeInspector(MissingCliExecutor()),
        SecurityToolingInspector(MissingCliExecutor()),
        SelinuxAttrInspector(MissingCliExecutor()),
        TransferToolsInspector(MissingCliExecutor()),
    )

    @Test
    fun `every inspector suggested command contains apt-get update`() = runTest {
        for (inspector in allInspectors) {
            val item = inspector.inspect(missingContext)
            val cmd = item.copyableCommand
            if (cmd != null) {
                assertTrue(
                    cmd.contains("apt-get update"),
                    "Inspector ${inspector::class.simpleName} generated command without 'apt-get update': $cmd",
                )
                assertTrue(
                    cmd.startsWith("sudo apt-get update && sudo apt-get install -y "),
                    "Inspector ${inspector::class.simpleName} generated non-standard apt command: $cmd",
                )
            }
        }
    }

    @Test
    fun `no inspector contains pip3 or break-system-packages`() = runTest {
        for (inspector in allInspectors) {
            val item = inspector.inspect(missingContext)
            val fullText = "${item.detail} ${item.remediation ?: ""} ${item.copyableCommand ?: ""}"
            assertFalse(
                fullText.contains("pip3", ignoreCase = true),
                "Inspector ${inspector::class.simpleName} references pip3: $fullText",
            )
            assertFalse(
                fullText.contains("--break-system-packages", ignoreCase = true),
                "Inspector ${inspector::class.simpleName} references --break-system-packages: $fullText",
            )
        }
    }

    @Test
    fun `PythonRuntimeInspector missing crypto is FAILED`() = runTest {
        // Test with python3 present but cryptography missing
        class PythonPresentNoCryptoCli : WslCliExecutor() {
            override fun execute(
                distro: String,
                command: List<String>,
                timeoutSeconds: Long,
                charset: Charset,
                user: String?,
            ): CliExecutionResult {
                val cmdStr = command.joinToString(" ")
                return when {
                    cmdStr.contains("which python3") -> CliExecutionResult(0, "/usr/bin/python3", "")
                    cmdStr.contains("python3 --version") -> CliExecutionResult(0, "Python 3.12.3", "")
                    cmdStr.contains(
                        "import cryptography",
                    ) -> CliExecutionResult(1, "", "No module named 'cryptography'")
                    else -> CliExecutionResult(1, "", "")
                }
            }
        }

        val inspector = PythonRuntimeInspector(PythonPresentNoCryptoCli())
        val item = inspector.inspect(missingContext.copy(availableCommands = setOf("python3")))
        assertEquals(
            StepStatus.FAILED,
            item.status,
            "Missing crypto packages must result in FAILED status, was ${item.status}",
        )
        assertNotNull(item.copyableCommand)
        assertTrue(item.copyableCommand!!.contains("python3-cryptography"))
        assertTrue(item.copyableCommand!!.contains("python3-pyasn1"))
        assertFalse(item.copyableCommand!!.contains("pip"))
    }

    @Test
    fun `no inspector command contains privilege escalation or sudoers writes`() = runTest {
        val prohibitedPatterns = listOf(
            "NOPASSWD",
            "sudoers",
            "visudo",
            "sudo -S",
            "echo",
            "-p ''",
            "| sudo",
        )
        for (inspector in allInspectors) {
            val item = inspector.inspect(missingContext)
            val cmd = item.copyableCommand ?: continue
            for (prohibited in prohibitedPatterns) {
                assertFalse(
                    cmd.contains(prohibited),
                    "Inspector ${inspector::class.simpleName} command contains prohibited pattern '$prohibited': $cmd",
                )
            }
        }
    }
}
