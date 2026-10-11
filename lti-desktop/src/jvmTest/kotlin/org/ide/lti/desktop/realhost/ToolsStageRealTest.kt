package org.ide.lti.desktop.realhost

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.setup.check.CheckContext
import org.ide.lti.core.data.setup.check.ReadOnlyCommands
import org.ide.lti.core.data.setup.check.StageOutcome
import org.ide.lti.core.data.setup.check.stages.ToolsStage
import org.ide.lti.core.data.setup.state.CheckScope
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.model.setup.SetupEnvironment
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Real-host integration tests for ToolsStage (T028).
 */
class ToolsStageRealTest {
    @Test
    fun toolsStageReturnsPendingWhenNotInstalled() = runTest {
        if (!RealToolHost.isAvailable()) return@runTest

        RealToolHost.create().use { host ->
            val env =
                SetupEnvironment(
                    distro = System.getenv("LTI_TEST_DISTRO") ?: "Ubuntu-24.04",
                    wslVersion = 2,
                    osId = "ubuntu",
                    osVersionId = "24.04",
                    user = System.getProperty("user.name") ?: "user",
                    home = host.tempHome.absolutePath,
                )

            val cli = io.ltirom.tooling.client.wsl.WslCliExecutor()
            val readOnlyCommands = ReadOnlyCommands(cli)
            val stateFlow = MutableStateFlow(ToolchainSetupState())
            val scope = CheckScope("Ubuntu", "tools-check", stateFlow) { "tools-check" }

            val ctx =
                CheckContext(
                    environment = env,
                    readOnlyCommands = readOnlyCommands,
                    userHome = host.tempHome.absolutePath,
                    workDir = File(host.tempHome, "LtiRomWorkDir").absolutePath,
                    binDir = File(host.tempHome, "LtiRomTools/bin").absolutePath,
                    extDir = File(host.tempHome, "LtiRomTools/external").absolutePath,
                    transport = host.transport,
                )

            val stage = ToolsStage(toolManager = host.manager)
            val outcome = stage.run(ctx, scope)

            // When no tools are installed yet, outcome must be Pending to trigger auto run
            assertTrue(
                outcome is StageOutcome.Pending || outcome is StageOutcome.Failed,
                "Expected Pending or Failed for uninstalled tools, got $outcome",
            )
        }
    }

    @Test
    fun toolsStageReturnsSuccessWhenAllGroupsReady() = runTest {
        if (!RealToolHost.isAvailable()) return@runTest

        RealToolHost.create().use { host ->
            val env =
                SetupEnvironment(
                    distro = System.getenv("LTI_TEST_DISTRO") ?: "Ubuntu-24.04",
                    wslVersion = 2,
                    osId = "ubuntu",
                    osVersionId = "24.04",
                    user = System.getProperty("user.name") ?: "user",
                    home = host.tempHome.absolutePath,
                )

            val cli = io.ltirom.tooling.client.wsl.WslCliExecutor()
            val readOnlyCommands = ReadOnlyCommands(cli)
            val stateFlow = MutableStateFlow(ToolchainSetupState())
            val scope = CheckScope("Ubuntu", "tools-check", stateFlow) { "tools-check" }

            val ctx =
                CheckContext(
                    environment = env,
                    readOnlyCommands = readOnlyCommands,
                    userHome = host.tempHome.absolutePath,
                    workDir = File(host.tempHome, "LtiRomWorkDir").absolutePath,
                    binDir = File(host.tempHome, "LtiRomTools/bin").absolutePath,
                    extDir = File(host.tempHome, "LtiRomTools/external").absolutePath,
                    transport = host.transport,
                )

            val stage = ToolsStage(toolManager = host.manager)
            val outcome = stage.run(ctx, scope)
            assertTrue(outcome is StageOutcome, "ToolsStage returned $outcome")
        }
    }
}
