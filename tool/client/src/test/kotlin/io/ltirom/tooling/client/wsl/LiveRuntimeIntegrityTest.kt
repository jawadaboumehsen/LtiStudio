package io.ltirom.tooling.client.wsl

import io.ltirom.tooling.client.WslPathTranslator
import org.ide.lti.core.model.setup.InstallState
import org.ide.lti.core.model.setup.SetupEnvironment
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Opt-in (`LTI_LIVE_WSL=1`): a tiny bundle with a fake JRE is installed into the distro for real. The bundle's
 * checksums only cover `jre.tar.gz`, so damage to a file extracted from it - a library `java -version` never
 * loads - must still make the installation count as damaged.
 */
class LiveRuntimeIntegrityTest {
    private val distro = System.getenv("LTI_TEST_DISTRO") ?: "Ubuntu"
    private val cli = WslCliExecutor()
    private val translator = WslPathTranslator()

    private fun sh(script: String) = cli.execute(distro, listOf("sh", "-c", script), timeoutSeconds = 60)

    private fun wsl(file: File) = translator.toWslPath(file.absolutePath)

    @Test
    fun `a damaged extracted runtime file makes the installation damaged`() {
        org.junit.Assume.assumeTrue("set LTI_LIVE_WSL=1", System.getenv("LTI_LIVE_WSL") == "1")
        val work = Files.createTempDirectory("ltirom-jre").toFile()
        val home = "/tmp/ltirom-jre-home-${System.nanoTime()}"
        try {
            val bundle = File(work, "ltirom-server").also { File(it, "bin").mkdirs(); File(it, "runtime").mkdirs() }
            File(bundle, "bin/ltirom-server").writeText("#!/bin/sh\nexit 0\n")
            File(bundle, "server-version.txt").writeText("9.9.1")
            val stage = File(work, "stage/jre").also { File(it, "bin").mkdirs(); File(it, "lib").mkdirs() }
            File(stage, "bin/java").writeText("#!/bin/sh\necho 'openjdk version \"21\"' >&2\nexit 0\n")
            File(stage, "lib/libnet.so").writeText("native networking code")
            val make = sh(
                "cd '${wsl(File(work, "stage"))}' && tar -czf '${wsl(File(bundle, "runtime/jre.tar.gz"))}' jre && " +
                    "cd '${wsl(bundle)}' && sha256sum bin/ltirom-server runtime/jre.tar.gz > checksums.txt",
            )
            assertEquals(0, make.exitCode, make.error)

            val environment = SetupEnvironment(distro, 2, "ubuntu", "24.04", "user", home)
            val installer = ServerArtifactInstaller(cli, bundleDirProvider = { bundle })

            val installed = installer.ensureInstalled(environment)
            assertEquals(InstallState.Current, installed.installState, "install: ${installed.installState}")
            assertEquals(InstallState.Current, installer.status(environment).installState)

            val damage = sh("printf 'x' >> '$home/.ltirom/server/current/runtime/lib/libnet.so'")
            assertEquals(0, damage.exitCode, damage.error)

            val state = installer.status(environment).installState
            assertIs<InstallState.Failed>(state, "java -version still works, yet the runtime is damaged: $state")

            // An install over the damaged one repairs it.
            assertEquals(InstallState.Current, installer.ensureInstalled(environment).installState)
            assertEquals(InstallState.Current, installer.status(environment).installState)

            val missingManifest = sh("rm '$home/.ltirom/server/current/runtime/.extracted.sha256'")
            assertEquals(0, missingManifest.exitCode, missingManifest.error)
            assertTrue(installer.status(environment).installState is InstallState.Failed, "no manifest, no proof")
        } finally {
            sh("rm -rf '$home'")
            work.deleteRecursively()
        }
    }
}
