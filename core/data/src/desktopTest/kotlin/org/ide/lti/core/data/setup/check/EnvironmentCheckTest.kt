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
import org.ide.lti.core.data.setup.state.CheckScope
import org.ide.lti.core.domain.setup.SetupLogKind
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.model.setup.SetupEnvironment
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnvironmentCheckTest {
    private class TestCliExecutor : WslCliExecutor() {
        override fun executeHost(command: List<String>, timeoutSeconds: Long, charset: Charset): CliExecutionResult =
            CliExecutionResult(0, "", "")

        override fun execute(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            charset: Charset,
            user: String?,
        ): CliExecutionResult = CliExecutionResult(0, "", "")
    }

    private fun createTestContext(onLog: (String, SetupLogKind) -> Unit = { _, _ -> }): Pair<CheckContext, CheckScope> {
        val cli = TestCliExecutor()
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
                log = onLog,
            )
        return ctx to scope
    }

    private fun createMockStage(
        stageEnum: SetupStepStage,
        outcome: StageOutcome,
        executionLog: MutableList<SetupStepStage>? = null,
    ): CheckStage = object : CheckStage {
        override val stage: SetupStepStage = stageEnum

        override suspend fun run(ctx: CheckContext, scope: CheckScope): StageOutcome {
            executionLog?.add(stageEnum)
            return outcome
        }
    }

    @Test
    fun `no Doctor stage exists in SetupStepStage`() {
        val names = SetupStepStage.entries.map { it.name }
        assertFalse(names.contains("SYSTEM_DIAGNOSTICS"), "SYSTEM_DIAGNOSTICS must not exist in SetupStepStage")
        assertFalse(names.contains("REPO_SYNCHRONIZATION"), "REPO_SYNCHRONIZATION must not exist in SetupStepStage")
        assertEquals(
            listOf(
                SetupStepStage.WSL_DETECTION,
                SetupStepStage.SERVER_CONNECTIVITY,
                SetupStepStage.SYSTEM_PACKAGES,
                SetupStepStage.TOOLCHAIN_COMPILATION,
            ),
            SetupStepStage.entries,
        )
    }

    @Test
    fun `stages run in strict order Distro, Service, Packages, Tools when all succeed`() = runTest {
        val executionOrder = mutableListOf<SetupStepStage>()
        val stages =
            listOf(
                createMockStage(SetupStepStage.WSL_DETECTION, StageOutcome.Success("Ubuntu 24.04"), executionOrder),
                createMockStage(
                    SetupStepStage.SERVER_CONNECTIVITY,
                    StageOutcome.Success("Daemon healthy"),
                    executionOrder,
                ),
                createMockStage(
                    SetupStepStage.SYSTEM_PACKAGES,
                    StageOutcome.Success("All packages present"),
                    executionOrder,
                ),
                createMockStage(
                    SetupStepStage.TOOLCHAIN_COMPILATION,
                    StageOutcome.Success("7/7 tools ready"),
                    executionOrder,
                ),
            )

        val check = EnvironmentCheck(stages)
        val (ctx, scope) = createTestContext()

        val results = check.run(ctx, scope)

        assertEquals(
            listOf(
                SetupStepStage.WSL_DETECTION,
                SetupStepStage.SERVER_CONNECTIVITY,
                SetupStepStage.SYSTEM_PACKAGES,
                SetupStepStage.TOOLCHAIN_COMPILATION,
            ),
            executionOrder,
        )
        assertTrue(results.values.all { it is StageOutcome.Success })
    }

    @Test
    fun `failed Distro stage leaves Service, Packages, and Tools PENDING waiting for Distro`() = runTest {
        val executionOrder = mutableListOf<SetupStepStage>()
        val stages =
            listOf(
                createMockStage(
                    SetupStepStage.WSL_DETECTION,
                    StageOutcome.Failed("WSL not available"),
                    executionOrder,
                ),
                createMockStage(
                    SetupStepStage.SERVER_CONNECTIVITY,
                    StageOutcome.Success("Should not run"),
                    executionOrder,
                ),
                createMockStage(
                    SetupStepStage.SYSTEM_PACKAGES,
                    StageOutcome.Success("Should not run"),
                    executionOrder,
                ),
                createMockStage(
                    SetupStepStage.TOOLCHAIN_COMPILATION,
                    StageOutcome.Success("Should not run"),
                    executionOrder,
                ),
            )

        val check = EnvironmentCheck(stages)
        val (ctx, scope) = createTestContext()

        val results = check.run(ctx, scope)

        assertEquals(listOf(SetupStepStage.WSL_DETECTION), executionOrder)
        assertTrue(results[SetupStepStage.WSL_DETECTION] is StageOutcome.Failed)
        assertEquals("Waiting for Ubuntu (WSL)", results[SetupStepStage.SERVER_CONNECTIVITY]?.description)
        assertEquals("Waiting for Ubuntu (WSL)", results[SetupStepStage.SYSTEM_PACKAGES]?.description)
        assertEquals("Waiting for Ubuntu (WSL)", results[SetupStepStage.TOOLCHAIN_COMPILATION]?.description)
        assertTrue(results[SetupStepStage.SERVER_CONNECTIVITY] is StageOutcome.Pending)
        assertTrue(results[SetupStepStage.SYSTEM_PACKAGES] is StageOutcome.Pending)
        assertTrue(results[SetupStepStage.TOOLCHAIN_COMPILATION] is StageOutcome.Pending)
    }

    @Test
    fun `failed Service stage leaves Packages and Tools PENDING waiting for Build service`() = runTest {
        val executionOrder = mutableListOf<SetupStepStage>()
        val stages =
            listOf(
                createMockStage(SetupStepStage.WSL_DETECTION, StageOutcome.Success("Ubuntu 24.04"), executionOrder),
                createMockStage(
                    SetupStepStage.SERVER_CONNECTIVITY,
                    StageOutcome.Failed("Daemon connection refused"),
                    executionOrder,
                ),
                createMockStage(
                    SetupStepStage.SYSTEM_PACKAGES,
                    StageOutcome.Success("Should not run"),
                    executionOrder,
                ),
                createMockStage(
                    SetupStepStage.TOOLCHAIN_COMPILATION,
                    StageOutcome.Success("Should not run"),
                    executionOrder,
                ),
            )

        val check = EnvironmentCheck(stages)
        val (ctx, scope) = createTestContext()

        val results = check.run(ctx, scope)

        assertEquals(
            listOf(SetupStepStage.WSL_DETECTION, SetupStepStage.SERVER_CONNECTIVITY),
            executionOrder,
        )
        assertTrue(results[SetupStepStage.WSL_DETECTION] is StageOutcome.Success)
        assertTrue(results[SetupStepStage.SERVER_CONNECTIVITY] is StageOutcome.Failed)
        assertEquals("Waiting for Build service", results[SetupStepStage.SYSTEM_PACKAGES]?.description)
        assertEquals("Waiting for Build service", results[SetupStepStage.TOOLCHAIN_COMPILATION]?.description)
        assertTrue(results[SetupStepStage.SYSTEM_PACKAGES] is StageOutcome.Pending)
        assertTrue(results[SetupStepStage.TOOLCHAIN_COMPILATION] is StageOutcome.Pending)
    }

    @Test
    fun `failed Packages stage leaves Tools PENDING waiting for System packages`() = runTest {
        val executionOrder = mutableListOf<SetupStepStage>()
        val stages =
            listOf(
                createMockStage(SetupStepStage.WSL_DETECTION, StageOutcome.Success("Ubuntu 24.04"), executionOrder),
                createMockStage(
                    SetupStepStage.SERVER_CONNECTIVITY,
                    StageOutcome.Success("Daemon healthy"),
                    executionOrder,
                ),
                createMockStage(
                    SetupStepStage.SYSTEM_PACKAGES,
                    StageOutcome.Failed("Missing packages"),
                    executionOrder,
                ),
                createMockStage(
                    SetupStepStage.TOOLCHAIN_COMPILATION,
                    StageOutcome.Success("Should not run"),
                    executionOrder,
                ),
            )

        val check = EnvironmentCheck(stages)
        val (ctx, scope) = createTestContext()

        val results = check.run(ctx, scope)

        assertEquals(
            listOf(
                SetupStepStage.WSL_DETECTION,
                SetupStepStage.SERVER_CONNECTIVITY,
                SetupStepStage.SYSTEM_PACKAGES,
            ),
            executionOrder,
        )
        assertTrue(results[SetupStepStage.SYSTEM_PACKAGES] is StageOutcome.Failed)
        assertEquals("Waiting for System packages", results[SetupStepStage.TOOLCHAIN_COMPILATION]?.description)
        assertTrue(results[SetupStepStage.TOOLCHAIN_COMPILATION] is StageOutcome.Pending)
    }

    @Test
    fun `each stage logs started and result with duration`() = runTest {
        val logs = mutableListOf<String>()
        val stages =
            listOf(
                createMockStage(SetupStepStage.WSL_DETECTION, StageOutcome.Success("Ubuntu ready")),
            )

        val check = EnvironmentCheck(stages)
        val (ctx, scope) = createTestContext { msg, _ -> logs.add(msg) }

        check.run(ctx, scope)

        assertTrue(logs.any { it.startsWith("[check] Ubuntu (WSL): started") }, "Must log stage started")
        assertTrue(
            logs.any { it.matches(Regex("""\[check\] Ubuntu \(WSL\): Ubuntu ready \(\d+ ms\)""")) },
            "Must log stage result with ms",
        )
    }
}
