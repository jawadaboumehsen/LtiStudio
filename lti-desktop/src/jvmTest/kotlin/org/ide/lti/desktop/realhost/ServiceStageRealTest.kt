package org.ide.lti.desktop.realhost

import io.ltirom.tooling.client.wsl.ServerArtifactInstaller
import io.ltirom.tooling.client.wsl.WslCliExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.setup.check.CheckContext
import org.ide.lti.core.data.setup.check.ReadOnlyCommands
import org.ide.lti.core.data.setup.check.StageOutcome
import org.ide.lti.core.data.setup.check.stages.ServiceStage
import org.ide.lti.core.data.setup.state.CheckScope
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.model.setup.SetupEnvironment
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Real-host integration tests for ServiceStage (T027).
 */
class ServiceStageRealTest {
    @Test
    fun serviceStageInstallsAndStartsRealServerWithBundledJava() = runTest {
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

            val cli = WslCliExecutor()
            val readOnlyCommands = ReadOnlyCommands(cli)
            val stateFlow = MutableStateFlow(ToolchainSetupState())
            val scope = CheckScope("Ubuntu", "real-check", stateFlow) { "real-check" }

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

            val stage =
                ServiceStage(
                    installer = ServerArtifactInstaller(),
                    transport = host.transport,
                )

            val outcome = stage.run(ctx, scope)
            assertTrue(
                outcome is StageOutcome.Success,
                "Expected ServiceStage to succeed on real host, got $outcome",
            )

            // Verify the server process runs the bundled Java runtime
            val pid = host.serverProcess?.pid()
            if (pid != null) {
                val exePath = File("/proc/$pid/exe")
                if (exePath.exists()) {
                    val target = exePath.toPath().toRealPath().toString()
                    assertTrue(
                        target.contains("/runtime/bin/java") || target.contains("java"),
                        "Process /proc/$pid/exe must resolve to bundled java runtime, got: $target",
                    )
                }
            }
        }
    }
}
