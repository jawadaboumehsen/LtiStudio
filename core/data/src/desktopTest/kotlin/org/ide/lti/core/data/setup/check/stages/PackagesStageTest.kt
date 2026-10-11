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

import io.ltirom.tooling.client.wsl.WslCliExecutor
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.StreamEvent
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import io.ltirom.tooling.core.remote.ToolExecutionResponse
import io.ltirom.tooling.core.remote.ToolListResult
import io.ltirom.tooling.core.remote.WslServerInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.setup.RequirementCatalog
import org.ide.lti.core.data.setup.check.CheckContext
import org.ide.lti.core.data.setup.check.ReadOnlyCommands
import org.ide.lti.core.data.setup.check.StageOutcome
import org.ide.lti.core.data.setup.state.CheckScope
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.model.setup.SetupEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PackagesStageTest {
    private class FakeTransport(var stdout: String = "", var stderr: String = "", var exitCode: Int = 0) :
        RemoteTransportPort {
        override suspend fun checkHealth(): WslServerInfo? = WslServerInfo(
            status = "UP",
            distro = "Ubuntu-24.04",
            kernelRelease = "6.6",
            architecture = "x86_64",
            javaVersion = "21.0.2",
            serverUptimeMs = 1000L,
            serverVersion = "1.0.0",
        )

        override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse =
            ToolExecutionResponse(exitCode = exitCode, stdout = stdout, stderr = stderr, durationMs = 10L)

        override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> = emptyFlow()

        override suspend fun listTools(): ToolListResult = ToolListResult.Tools(emptyList())

        override suspend fun refreshTools(): ToolListResult = ToolListResult.Tools(emptyList())

        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean = true

        override suspend fun downloadFile(remotePath: String): ByteArray? = null

        override suspend fun shutdown(): Boolean = true

        override suspend fun sendHeartbeat(): Boolean = true
    }

    private fun createTestContext(transport: RemoteTransportPort?): Pair<CheckContext, CheckScope> {
        val cli = WslCliExecutor()
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
                transport = transport,
            )
        return ctx to scope
    }

    @Test
    fun packagesStageReturnsSuccessWhenAllPackagesPresent() = runTest {
        val stdout =
            buildString {
                appendLine("PACKAGES_FRESH=1")
                for (req in RequirementCatalog.REQUIREMENTS) {
                    appendLine("${req.id}=present")
                }
            }
        val transport = FakeTransport(stdout = stdout)
        val (ctx, scope) = createTestContext(transport)

        val stage = PackagesStage()
        val outcome = stage.run(ctx, scope)

        assertTrue(outcome is StageOutcome.Success, "Expected Success, got $outcome")
        assertEquals("All packages present", outcome.description)
    }

    @Test
    fun packagesStageReturnsFailedWhenPackagesMissing() = runTest {
        val stdout =
            buildString {
                appendLine("PACKAGES_FRESH=1")
                for (req in RequirementCatalog.REQUIREMENTS) {
                    if (req.id == "build-essential") {
                        appendLine("${req.id}=missing")
                    } else {
                        appendLine("${req.id}=present")
                    }
                }
            }
        val transport = FakeTransport(stdout = stdout)
        val (ctx, scope) = createTestContext(transport)

        val stage = PackagesStage()
        val outcome = stage.run(ctx, scope)

        assertTrue(outcome is StageOutcome.Failed, "Expected Failed, got $outcome")
        assertTrue(
            outcome.description.contains("package(s) missing"),
            "Description should note missing packages: ${outcome.description}",
        )
        assertTrue(
            outcome.error?.contains("apt-get install") == true,
            "Error should contain apt-get install: ${outcome.error}",
        )
    }

    @Test
    fun packagesStageReturnsFailedWhenTransportFails() = runTest {
        val transport = FakeTransport(exitCode = 1, stderr = "Remote probe execution failed")
        val (ctx, scope) = createTestContext(transport)

        val stage = PackagesStage()
        val outcome = stage.run(ctx, scope)

        assertTrue(outcome is StageOutcome.Failed, "Expected Failed, got $outcome")
    }

    @Test
    fun packagesStageReturnsFailedWhenTransportUnavailable() = runTest {
        val (ctx, scope) = createTestContext(null)

        val stage = PackagesStage()
        val outcome = stage.run(ctx, scope)

        assertTrue(outcome is StageOutcome.Failed, "Expected Failed, got $outcome")
        assertTrue(outcome.error?.contains("unavailable") == true, "Expected unavailable transport error")
    }
}
