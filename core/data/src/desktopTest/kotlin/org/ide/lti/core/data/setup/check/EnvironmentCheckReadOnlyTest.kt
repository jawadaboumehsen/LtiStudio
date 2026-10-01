/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.check

import io.ltirom.tooling.client.wsl.CliExecutionResult
import io.ltirom.tooling.client.wsl.WslCliExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.setup.ToolVerifier
import org.ide.lti.core.data.setup.check.stages.DoctorStage
import org.ide.lti.core.data.setup.check.stages.SourcesStage
import org.ide.lti.core.data.setup.check.stages.ToolchainStage
import org.ide.lti.core.data.setup.fakeSha256
import org.ide.lti.core.data.setup.state.CheckScope
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.model.setup.SetupEnvironment
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnvironmentCheckReadOnlyTest {

    private class RecordingInspectionCli : WslCliExecutor() {
        val executedCommands = mutableListOf<List<String>>()

        override fun execute(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            charset: Charset,
            user: String?,
        ): CliExecutionResult {
            executedCommands.add(command)
            return when {
                command.first() == "sha256sum" -> fakeSha256(command.getOrNull(1).orEmpty())
                command.first() == "test" -> CliExecutionResult(0, "", "")
                command.first() == "find" -> {
                    val entries = "android-tools\nerofs-utils\nimg2sdat\napktool\nsignapk\n"
                    CliExecutionResult(0, entries, "")
                }
                command.first() == "git" && command.contains("rev-parse") -> {
                    val dir = command.getOrNull(command.indexOf("-C") + 1)?.trimEnd('/') ?: ""
                    CliExecutionResult(0, "$dir\nf9bef94306f93be88b0cf98f9eeb0e24fc7b6aa8\n", "")
                }
                command.first() == "dpkg-query" -> CliExecutionResult(0, "install ok installed", "")
                command.first() == "df" -> CliExecutionResult(0, "Filesystem 1000000 500000 500000 50% /", "")
                command.first() == "stat" -> CliExecutionResult(0, "1790000000", "")
                command.first() == "readlink" -> CliExecutionResult(0, "/bin/mke2fs", "")
                command.first().endsWith("mkfs.erofs") -> CliExecutionResult(0, "mkfs.erofs 1.8", "")
                command.first().endsWith("adb") -> CliExecutionResult(0, "Android Debug Bridge version 1.0.41", "")
                else -> CliExecutionResult(0, "", "")
            }
        }
    }

    @Test
    fun fullEnvironmentCheckIssuesOnlyAllowlistedReadOnlyCommands() = runTest {
        val cli = RecordingInspectionCli()
        val readOnlyCommands = ReadOnlyCommands(cli)
        val toolVerifier = ToolVerifier(readOnlyCommands)
        val stateFlow = MutableStateFlow(ToolchainSetupState())
        val checkScope = CheckScope("Ubuntu", "check-test", stateFlow) { "check-test" }

        val env = SetupEnvironment(
            distro = "Ubuntu",
            wslVersion = 2,
            osId = "ubuntu",
            osVersionId = "24.04",
            user = "lti",
            home = "/home/lti",
        )

        val checkContext = CheckContext(
            environment = env,
            readOnlyCommands = readOnlyCommands,
            toolVerifier = toolVerifier,
            userHome = "/home/lti",
            workDir = "/home/lti/LtiRomWorkDir",
            binDir = "/home/lti/LtiRomTools/bin",
            extDir = "/home/lti/LtiRomTools/external",
        )

        val stages = listOf(
            DoctorStage(),
            SourcesStage(),
            ToolchainStage(),
        )

        val environmentCheck = EnvironmentCheck(stages = stages)
        environmentCheck.run(checkContext, checkScope)

        assertTrue(cli.executedCommands.isNotEmpty(), "Inspection must issue commands")

        // Assert EVERY single command issued satisfies ReadOnlyCommands validation
        for (cmd in cli.executedCommands) {
            readOnlyCommands.validate(cmd) // throws if not allowlisted
            assertFalse(cmd.contains("mkdir"), "Must not issue mkdir in read-only inspection: $cmd")
            assertFalse(cmd.contains("cp"), "Must not issue cp in read-only inspection: $cmd")
            assertFalse(cmd.contains("rm"), "Must not issue rm in read-only inspection: $cmd")
            assertFalse(cmd.contains("chmod"), "Must not issue chmod in read-only inspection: $cmd")
            assertFalse(cmd.contains("ln"), "Must not issue ln in read-only inspection: $cmd")
            assertFalse(cmd.contains("bash"), "Must not issue bash in read-only inspection: $cmd")
        }
    }
}
