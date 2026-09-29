/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup

import io.ltirom.tooling.client.WslDaemonConfig
import io.ltirom.tooling.client.WslDaemonManager
import io.ltirom.tooling.client.wsl.CliExecutionResult
import io.ltirom.tooling.client.wsl.DaemonHealthProber
import io.ltirom.tooling.client.wsl.ServerArtifactInstaller
import io.ltirom.tooling.client.wsl.WslCliExecutor
import io.ltirom.tooling.client.wsl.WslProcessLaunchResult
import io.ltirom.tooling.client.wsl.WslProcessLauncher
import io.ltirom.tooling.core.ports.ServerConnectionDescriptor
import io.ltirom.tooling.core.remote.WslServerInfo
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.model.setup.InstallState
import org.ide.lti.core.model.setup.SetupEnvironment
import java.io.File
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerArtifactAdapterTest {

    private class FakeCli : WslCliExecutor() {
        override fun execute(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            charset: Charset,
            user: String?,
        ): CliExecutionResult {
            val cmd = command.joinToString(" ")
            if (cmd.contains("readlink")) {
                return CliExecutionResult(0, "/home/testuser/.ltirom/server/versions/1.0.0", "")
            }
            return CliExecutionResult(0, "", "")
        }
    }

    private class RecordingLauncher : WslProcessLauncher(startupTimeoutMs = 1000L) {
        var lastLaunchedJavaHome: String? = null

        override suspend fun launch(distro: String, binaryPath: String, javaHome: String?): WslProcessLaunchResult {
            lastLaunchedJavaHome = javaHome
            val p = ProcessBuilder("cmd.exe", "/c", "echo LTI_WSL_SERVER_READY port=9999 token=fake pid=1234").start()
            return WslProcessLaunchResult(p, 9999, "fake", 1234L)
        }
    }

    private class FakeProber : DaemonHealthProber(null) {
        override suspend fun isHealthy(info: ServerConnectionDescriptor, timeoutMs: Long): Boolean = true
        override suspend fun getServerInfo(info: ServerConnectionDescriptor): WslServerInfo? = WslServerInfo(
            status = "UP",
            distro = "Ubuntu-24.04",
            kernelRelease = "6.6.0",
            architecture = "x86_64",
            javaVersion = "21",
            serverUptimeMs = 1000L,
            serverVersion = "1.0.0",
            activeRuns = 0,
        )
    }

    private val testEnv = SetupEnvironment(
        distro = "Ubuntu-24.04",
        wslVersion = 2,
        osId = "ubuntu",
        osVersionId = "24.04",
        user = "testuser",
        home = "/home/testuser",
    )

    @Test
    fun `javaHome reaches launcher exactly as passed when calling ensureRunningCurrent`() = runTest {
        val launcher = RecordingLauncher()
        val prober = FakeProber()
        val daemonManager = WslDaemonManager(
            config = WslDaemonConfig(
                distroName = "Ubuntu-24.04",
                serverBinaryWslPath = "/home/testuser/.ltirom/server/current/bin/ltirom-server",
            ),
            launcher = launcher,
            customProber = prober,
        )
        val installer = ServerArtifactInstaller(
            cli = FakeCli(),
            bundleDirProvider = { null },
        )
        val adapter = ServerArtifactAdapter(
            installer = installer,
            daemonManager = daemonManager,
        )

        val targetJavaHome = "/usr/lib/jvm/java-21-openjdk-amd64"
        val result = adapter.ensureRunningCurrent(testEnv, targetJavaHome)

        assertEquals(targetJavaHome, launcher.lastLaunchedJavaHome, "javaHome must reach launcher exactly")
        assertEquals(targetJavaHome, result.javaHome, "result must contain javaHome")
    }

    @Test
    fun `null bundle returns bundledVersion null and Failed install state`() = runTest {
        val installer = ServerArtifactInstaller(
            cli = FakeCli(),
            bundleDirProvider = { null },
        )
        val adapter = ServerArtifactAdapter(
            installer = installer,
            daemonManager = WslDaemonManager(),
        )

        val result = adapter.ensureInstalled(testEnv)

        assertNull(result.bundledVersion, "bundledVersion must be null when bundle is null")
        assertTrue(result.installState is InstallState.Failed, "installState must be Failed")
    }

    @Test
    fun `status delegates to installer and merges daemon serverInfo`() = runTest {
        val tempDir = File.createTempFile("server_bundle_", "").apply {
            delete()
            mkdirs()
        }
        try {
            File(tempDir, "server-version.txt").writeText("1.0.0\n")
            val prober = FakeProber()
            val daemonManager = WslDaemonManager(
                config = WslDaemonConfig(distroName = "Ubuntu-24.04"),
                customProber = prober,
            )
            val installer = ServerArtifactInstaller(
                cli = FakeCli(),
                bundleDirProvider = { tempDir },
            )
            val adapter = ServerArtifactAdapter(
                installer = installer,
                daemonManager = daemonManager,
            )

            val status = adapter.status(testEnv)

            assertEquals("1.0.0", status.bundledVersion)
            assertEquals("1.0.0", status.activeVersion)
            assertEquals(InstallState.Current, status.installState)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
