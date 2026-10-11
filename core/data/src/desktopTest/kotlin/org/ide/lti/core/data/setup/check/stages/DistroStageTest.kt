/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.check.stages

import io.ltirom.tooling.client.wsl.CliExecutionResult
import io.ltirom.tooling.client.wsl.WslCliExecutor
import io.ltirom.tooling.client.wsl.WslEnvironmentDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.setup.check.CheckContext
import org.ide.lti.core.data.setup.check.ReadOnlyCommands
import org.ide.lti.core.data.setup.check.StageOutcome
import org.ide.lti.core.data.setup.check.stages.fixtures.DistroFixtures
import org.ide.lti.core.data.setup.state.CheckScope
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.model.setup.SetupEnvironment
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DistroStageTest {
    private class FakeWslCliExecutor : WslCliExecutor() {
        var hostResponses = mutableMapOf<List<String>, CliExecutionResult>()
        var distroResponses = mutableMapOf<Pair<String, List<String>>, CliExecutionResult>()
        var defaultHostResponse = CliExecutionResult(0, "", "")
        var defaultDistroResponse = CliExecutionResult(0, "", "")

        override fun executeHost(command: List<String>, timeoutSeconds: Long, charset: Charset): CliExecutionResult =
            hostResponses[command] ?: defaultHostResponse

        override fun execute(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            charset: Charset,
            user: String?,
        ): CliExecutionResult = distroResponses[distro to command] ?: defaultDistroResponse
    }

    private fun createTestContext(cli: WslCliExecutor): Pair<CheckContext, CheckScope> {
        val readOnlyCommands = ReadOnlyCommands(cli)
        val stateFlow = MutableStateFlow(ToolchainSetupState())
        val scope = CheckScope("Ubuntu", "test-check", stateFlow) { "test-check" }
        val env = SetupEnvironment("Ubuntu", 2, "ubuntu", "24.04", "user", "/home/user")
        val ctx =
            CheckContext(
                environment = env,
                readOnlyCommands = readOnlyCommands,
                userHome = "/home/user",
                workDir = "/home/user/LtiRomWorkDir",
                binDir = "/home/user/LtiRomTools/bin",
                extDir = "/home/user/LtiRomTools/external",
            )
        return ctx to scope
    }

    @Test
    fun `default usable Ubuntu 24_04 produces Success with distro and user`() = runTest {
        val cli =
            FakeWslCliExecutor().apply {
                hostResponses[listOf("wsl.exe", "-l", "-q")] = CliExecutionResult(0, "Ubuntu\ndocker-desktop\n", "")
                hostResponses[listOf("wsl.exe", "-l", "-v")] = CliExecutionResult(0, DistroFixtures.WSL_LV_REAL, "")
                hostResponses[listOf("wsl.exe", "--status")] = CliExecutionResult(0, DistroFixtures.WSL_STATUS_REAL, "")
                distroResponses["Ubuntu" to listOf("cat", "/etc/os-release")] =
                    CliExecutionResult(0, DistroFixtures.OS_RELEASE_UBUNTU_24_04, "")
                distroResponses["Ubuntu" to listOf("sh", "-c", "id -un; printf '%s' \"\$HOME\"")] =
                    CliExecutionResult(0, "user\n/home/user", "")
            }

        val stage = DistroStage(WslEnvironmentDetector(cli))
        val (ctx, scope) = createTestContext(cli)

        val outcome = stage.run(ctx, scope)
        assertTrue(outcome is StageOutcome.Success, "Expected Success but got $outcome")
        assertEquals("Ubuntu · user user", outcome.description)
    }

    @Test
    fun `default usable Ubuntu 22_04 produces Success with distro and user`() = runTest {
        val cli =
            FakeWslCliExecutor().apply {
                hostResponses[listOf("wsl.exe", "-l", "-q")] = CliExecutionResult(0, "Ubuntu\n", "")
                hostResponses[listOf("wsl.exe", "-l", "-v")] = CliExecutionResult(0, "* Ubuntu Running 2\n", "")
                hostResponses[listOf("wsl.exe", "--status")] =
                    CliExecutionResult(0, "Default Distribution: Ubuntu\nDefault Version: 2", "")
                distroResponses["Ubuntu" to listOf("cat", "/etc/os-release")] =
                    CliExecutionResult(0, DistroFixtures.OS_RELEASE_UBUNTU_22_04, "")
                distroResponses["Ubuntu" to listOf("sh", "-c", "id -un; printf '%s' \"\$HOME\"")] =
                    CliExecutionResult(0, "lti\n/home/lti", "")
            }

        val stage = DistroStage(WslEnvironmentDetector(cli))
        val (ctx, scope) = createTestContext(cli)

        val outcome = stage.run(ctx, scope)
        assertTrue(outcome is StageOutcome.Success, "Expected Success but got $outcome")
        assertEquals("Ubuntu · user lti", outcome.description)
    }

    @Test
    fun `default non-LTS Ubuntu 25_04 produces Failed and publishes statuses`() = runTest {
        val cli =
            FakeWslCliExecutor().apply {
                hostResponses[listOf("wsl.exe", "-l", "-q")] = CliExecutionResult(0, "Ubuntu\n", "")
                hostResponses[listOf("wsl.exe", "-l", "-v")] = CliExecutionResult(0, "* Ubuntu Running 2\n", "")
                hostResponses[listOf("wsl.exe", "--status")] =
                    CliExecutionResult(0, "Default Distribution: Ubuntu\nDefault Version: 2", "")
                distroResponses["Ubuntu" to listOf("cat", "/etc/os-release")] =
                    CliExecutionResult(0, DistroFixtures.OS_RELEASE_UBUNTU_25_04, "")
                distroResponses["Ubuntu" to listOf("sh", "-c", "id -un; printf '%s' \"\$HOME\"")] =
                    CliExecutionResult(0, "user\n/home/user", "")
            }

        val stage = DistroStage(WslEnvironmentDetector(cli))
        val (ctx, scope) = createTestContext(cli)

        val outcome = stage.run(ctx, scope)
        assertTrue(outcome is StageOutcome.Failed, "Expected Failed but got $outcome")
    }

    @Test
    fun `default Debian produces Failed`() = runTest {
        val cli =
            FakeWslCliExecutor().apply {
                hostResponses[listOf("wsl.exe", "-l", "-q")] = CliExecutionResult(0, "Debian\n", "")
                hostResponses[listOf("wsl.exe", "-l", "-v")] = CliExecutionResult(0, "* Debian Running 2\n", "")
                hostResponses[listOf("wsl.exe", "--status")] =
                    CliExecutionResult(0, "Default Distribution: Debian\nDefault Version: 2", "")
                distroResponses["Debian" to listOf("cat", "/etc/os-release")] =
                    CliExecutionResult(0, DistroFixtures.OS_RELEASE_DEBIAN, "")
                distroResponses["Debian" to listOf("sh", "-c", "id -un; printf '%s' \"\$HOME\"")] =
                    CliExecutionResult(0, "user\n/home/user", "")
            }

        val stage = DistroStage(WslEnvironmentDetector(cli))
        val (ctx, scope) = createTestContext(cli)

        val outcome = stage.run(ctx, scope)
        assertTrue(outcome is StageOutcome.Failed, "Expected Failed but got $outcome")
    }

    // An earlier check verified Ubuntu; its OS and user are kept, but the cheap facts are read again.
    private fun verifiedContext(cli: WslCliExecutor) =
        createTestContext(cli).let { (ctx, scope) -> ctx.copy(alreadyVerifiedWsl = true) to scope }

    @Test
    fun `a verified WSL that is still the default keeps its cached facts`() = runTest {
        val cli =
            FakeWslCliExecutor().apply {
                hostResponses[listOf("wsl.exe", "-l", "-v")] = CliExecutionResult(0, "* Ubuntu Running 2\n", "")
                // No os-release or user answers: reading them would fail the stage.
                defaultDistroResponse = CliExecutionResult(1, "", "must not be read")
            }
        val (ctx, scope) = verifiedContext(cli)

        val outcome = DistroStage(WslEnvironmentDetector(cli)).run(ctx, scope)

        assertTrue(outcome is StageOutcome.Success, "Expected Success but got $outcome")
        assertEquals("Ubuntu · user user", outcome.description)
    }

    @Test
    fun `a verified WSL whose default distribution changed is detected again`() = runTest {
        val cli =
            FakeWslCliExecutor().apply {
                hostResponses[listOf("wsl.exe", "-l", "-q")] = CliExecutionResult(0, "Debian\nUbuntu\n", "")
                hostResponses[listOf("wsl.exe", "-l", "-v")] =
                    CliExecutionResult(0, "* Debian Running 2\n  Ubuntu Running 2\n", "")
                distroResponses["Debian" to listOf("cat", "/etc/os-release")] =
                    CliExecutionResult(0, DistroFixtures.OS_RELEASE_DEBIAN, "")
                distroResponses["Debian" to listOf("sh", "-c", "id -un; printf '%s' \"\$HOME\"")] =
                    CliExecutionResult(0, "user\n/home/user", "")
            }
        val (ctx, scope) = verifiedContext(cli)

        val outcome = DistroStage(WslEnvironmentDetector(cli)).run(ctx, scope)

        assertTrue(outcome is StageOutcome.Failed, "the cached Ubuntu must not hide the new default: $outcome")
    }

    @Test
    fun `a verified WSL that was converted to WSL 1 is detected again`() = runTest {
        val cli =
            FakeWslCliExecutor().apply {
                hostResponses[listOf("wsl.exe", "-l", "-q")] = CliExecutionResult(0, "Ubuntu\n", "")
                hostResponses[listOf("wsl.exe", "-l", "-v")] = CliExecutionResult(0, "* Ubuntu Running 1\n", "")
            }
        val (ctx, scope) = verifiedContext(cli)

        val outcome = DistroStage(WslEnvironmentDetector(cli)).run(ctx, scope)

        assertTrue(outcome is StageOutcome.Failed, "Expected Failed but got $outcome")
    }

    @Test
    fun `no distro produces Failed`() = runTest {
        val cli =
            FakeWslCliExecutor().apply {
                hostResponses[listOf("wsl.exe", "-l", "-q")] = CliExecutionResult(0, "\n", "")
                hostResponses[listOf("wsl.exe", "-l", "-v")] = CliExecutionResult(0, "", "")
                hostResponses[listOf("wsl.exe", "--status")] = CliExecutionResult(0, "", "")
            }

        val stage = DistroStage(WslEnvironmentDetector(cli))
        val (ctx, scope) = createTestContext(cli)

        val outcome = stage.run(ctx, scope)
        assertTrue(outcome is StageOutcome.Failed, "Expected Failed but got $outcome")
    }

    @Test
    fun `no usable user produces Failed`() = runTest {
        val cli =
            FakeWslCliExecutor().apply {
                hostResponses[listOf("wsl.exe", "-l", "-q")] = CliExecutionResult(0, "Ubuntu\n", "")
                hostResponses[listOf("wsl.exe", "-l", "-v")] = CliExecutionResult(0, "* Ubuntu Running 2\n", "")
                hostResponses[listOf("wsl.exe", "--status")] =
                    CliExecutionResult(0, "Default Distribution: Ubuntu\nDefault Version: 2", "")
                distroResponses["Ubuntu" to listOf("cat", "/etc/os-release")] =
                    CliExecutionResult(0, DistroFixtures.OS_RELEASE_UBUNTU_24_04, "")
                distroResponses["Ubuntu" to listOf("sh", "-c", "id -un; printf '%s' \"\$HOME\"")] =
                    CliExecutionResult(0, "root\n/root", "")
            }

        val stage = DistroStage(WslEnvironmentDetector(cli))
        val (ctx, scope) = createTestContext(cli)

        val outcome = stage.run(ctx, scope)
        assertTrue(outcome is StageOutcome.Failed, "Expected Failed but got $outcome")
    }

    @Test
    fun `WSL unavailable produces Failed`() = runTest {
        val cli =
            FakeWslCliExecutor().apply {
                defaultHostResponse = CliExecutionResult(-1, "", "wsl.exe is not recognized")
            }

        val stage = DistroStage(WslEnvironmentDetector(cli))
        val (ctx, scope) = createTestContext(cli)

        val outcome = stage.run(ctx, scope)
        assertTrue(outcome is StageOutcome.Failed, "Expected Failed but got $outcome")
    }
}
