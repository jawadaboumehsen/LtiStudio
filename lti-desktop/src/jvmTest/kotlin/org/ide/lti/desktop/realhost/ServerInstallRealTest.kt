package org.ide.lti.desktop.realhost

import io.ltirom.tooling.client.wsl.ServerArtifactInstaller
import io.ltirom.tooling.client.wsl.WslCliExecutor
import org.ide.lti.core.model.setup.InstallState
import org.ide.lti.core.model.setup.SetupEnvironment
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Real-host tests for server and runtime bundle installation (T022).
 */
class ServerInstallRealTest {
    @Test
    fun runtimeIsExtractedAndJavaRuns() {
        if (!RealToolHost.isAvailable()) return
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
            val installer = ServerArtifactInstaller()
            val result = installer.ensureInstalled(env)
            assertEquals(InstallState.Current, result.installState)

            val javaBin = File(host.tempHome, ".ltirom/server/current/runtime/bin/java")
            val cli = WslCliExecutor()
            val wslJavaBin = io.ltirom.tooling.client.WslPathTranslator().toWslPath(javaBin.absolutePath)
            val exists =
                if (host.mode == "wsl") {
                    cli.execute(env.distro, listOf("test", "-x", wslJavaBin)).exitCode == 0
                } else {
                    javaBin.exists()
                }
            assertTrue(exists, "runtime/bin/java must exist after extraction")

            val javaVerResult = cli.execute(env.distro, listOf(wslJavaBin, "-version"))
            assertEquals(0, javaVerResult.exitCode, "runtime/bin/java -version must exit 0")
        }
    }

    @Test
    fun tamperedJreFailsChecksumAndKeepsPreviousCurrent() {
        if (!RealToolHost.isAvailable()) return
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
            val installer = ServerArtifactInstaller()
            val initialResult = installer.ensureInstalled(env)
            assertEquals(InstallState.Current, initialResult.installState)

            // Prepare a tampered bundle
            val stagedBundle =
                File("lti-desktop/build/bundledServer/ltirom-server").takeIf { it.isDirectory }
                    ?: File("build/bundledServer/ltirom-server")
            val tamperedDir = File(host.tempHome, "tampered-bundle")
            stagedBundle.copyRecursively(tamperedDir)
            val jreFile = File(tamperedDir, "runtime/jre.tar.gz")
            if (jreFile.exists()) {
                val bytes = jreFile.readBytes()
                bytes[0] = (bytes[0].toInt() xor 0xFF).toByte()
                jreFile.writeBytes(bytes)
                File(tamperedDir, "server-version.txt").writeText("9.9.9-tampered")
                val tamperedInstaller = ServerArtifactInstaller(bundleDirProvider = { tamperedDir })
                val tamperedResult = tamperedInstaller.ensureInstalled(env)
                assertTrue(tamperedResult.installState is InstallState.Failed, "Tampered JRE must fail installation")
                assertEquals("verify", (tamperedResult.installState as InstallState.Failed).step)
                assertEquals(initialResult.activeVersion, installer.getActiveVersion(env))
            }
        }
    }

    @Test
    fun unexecutableJavaCausesIsIntactToBeFalse() {
        if (!RealToolHost.isAvailable()) return
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
            val installer = ServerArtifactInstaller()
            val result = installer.ensureInstalled(env)
            assertEquals(InstallState.Current, result.installState)

            val javaBin = File(host.tempHome, ".ltirom/server/current/runtime/bin/java")
            val cli = WslCliExecutor()
            val wslJavaBin = io.ltirom.tooling.client.WslPathTranslator().toWslPath(javaBin.absolutePath)
            val exists =
                if (host.mode == "wsl") {
                    cli.execute(env.distro, listOf("test", "-f", wslJavaBin)).exitCode == 0
                } else {
                    javaBin.exists()
                }
            if (exists) {
                cli.execute(env.distro, listOf("chmod", "-x", wslJavaBin))
                val status = installer.status(env)
                assertFalse(status.installState == InstallState.Current, "Damaged java must not be Current")
            }
        }
    }
}
