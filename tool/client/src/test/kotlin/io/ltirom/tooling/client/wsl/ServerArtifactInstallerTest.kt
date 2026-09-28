package io.ltirom.tooling.client.wsl

import org.ide.lti.core.model.setup.InstallState
import org.ide.lti.core.model.setup.SetupEnvironment
import java.io.File
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerArtifactInstallerTest {

    private class RecordingFakeCli(
        var failOnCommandSubstring: String? = null,
        var stderrToEmit: String = "simulated error",
        var activeVersionSymlinkTarget: String? = null,
    ) : WslCliExecutor() {
        val executedCommands = mutableListOf<List<String>>()

        override fun execute(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            charset: Charset,
            user: String?,
        ): CliExecutionResult {
            executedCommands.add(command)
            val cmdStr = command.joinToString(" ")

            if (failOnCommandSubstring != null && cmdStr.contains(failOnCommandSubstring!!)) {
                return CliExecutionResult(1, "", stderrToEmit)
            }

            if (cmdStr.contains("readlink")) {
                return if (activeVersionSymlinkTarget != null) {
                    CliExecutionResult(0, activeVersionSymlinkTarget!!, "")
                } else {
                    CliExecutionResult(1, "", "No such file")
                }
            }

            if (cmdStr.contains("test -x")) {
                return CliExecutionResult(0, "", "")
            }

            if (cmdStr.contains("test -d")) {
                return CliExecutionResult(0, "", "")
            }

            return CliExecutionResult(0, "", "")
        }
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
    fun `fresh install performs staged install through partial, verify, rename, and atomic swap`() {
        val tempDir = File.createTempFile("server_bundle_", "").apply {
            delete()
            mkdirs()
        }
        try {
            File(tempDir, "server-version.txt").writeText("1.2.0\n")
            val binDir = File(tempDir, "bin").apply { mkdirs() }
            File(binDir, "ltirom-server").writeText("#!/bin/sh\n")

            val cli = RecordingFakeCli()
            val installer = ServerArtifactInstaller(cli = cli, bundleDirProvider = { tempDir })

            val result = installer.ensureInstalled(testEnv)

            assertEquals("1.2.0", result.bundledVersion)
            assertEquals("1.2.0", result.activeVersion)
            assertEquals(InstallState.Current, result.installState)

            // Verify execution sequence contains:
            // 1. cleanup of partial
            // 2. copy to versions/1.2.0.partial
            // 3. rename to versions/1.2.0
            // 4. atomic symlink swap to current
            val allCommands = cli.executedCommands.map { it.joinToString(" ") }
            assertTrue(
                allCommands.any {
                    it.contains("rm -rf") && it.contains("1.2.0.partial")
                },
                "Should clean up partial directory",
            )
            assertTrue(
                allCommands.any {
                    it.contains("1.2.0.partial") && (it.contains("cp") || it.contains("mkdir"))
                },
                "Should stage into partial directory",
            )
            assertTrue(
                allCommands.any {
                    it.contains("mv") && it.contains("1.2.0.partial") && it.contains("1.2.0")
                },
                "Should rename partial to final",
            )
            assertTrue(
                allCommands.any {
                    it.contains("current") && (it.contains("ln") || it.contains("mv"))
                },
                "Should atomically update current symlink",
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `failure at staging leaves previous current untouched and returns Failed state`() {
        val tempDir = File.createTempFile("server_bundle_", "").apply {
            delete()
            mkdirs()
        }
        try {
            File(tempDir, "server-version.txt").writeText("2.0.0\n")

            val cli = RecordingFakeCli(
                failOnCommandSubstring = "mv",
                stderrToEmit = "Permission denied",
                activeVersionSymlinkTarget = "/home/testuser/.ltirom/server/versions/1.0.0",
            )
            val installer = ServerArtifactInstaller(cli = cli, bundleDirProvider = { tempDir })

            val result = installer.ensureInstalled(testEnv)

            assertTrue(
                result.installState is InstallState.Failed,
                "Expected Failed installState, was ${result.installState}",
            )
            val failed = result.installState as InstallState.Failed
            assertTrue(failed.stderr.contains("Permission denied"))
            assertEquals("1.0.0", result.activeVersion, "Previous active version must be retained")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `subsequent attempt deletes existing partial directory before staging`() {
        val tempDir = bundle("1.3.0")
        try {
            val cli = RecordingFakeCli()
            ServerArtifactInstaller(cli = cli, bundleDirProvider = { tempDir }).ensureInstalled(testEnv)

            val commands = cli.executedCommands.map { it.joinToString(" ") }
            val cleanup = commands.indexOfFirst { it.startsWith("rm -rf") && it.contains("1.3.0.partial") }
            val copy = commands.indexOfFirst { it.startsWith("cp -r") }
            assertTrue(cleanup in 0 until copy, "Stale .partial must be removed before copying: $commands")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `already current version is not reinstalled`() {
        val tempDir = bundle("1.4.0")
        try {
            val cli = RecordingFakeCli(activeVersionSymlinkTarget = "/home/testuser/.ltirom/server/versions/1.4.0")
            val result = ServerArtifactInstaller(cli = cli, bundleDirProvider = { tempDir }).ensureInstalled(testEnv)

            assertEquals(InstallState.Current, result.installState)
            val commands = cli.executedCommands.map { it.joinToString(" ") }
            assertTrue(commands.none { it.startsWith("cp") || it.startsWith("mv") || it.startsWith("rm") }, "$commands")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `checksum mismatch fails at verify and never activates`() {
        val tempDir = bundle("1.5.0")
        try {
            val cli = RecordingFakeCli(
                failOnCommandSubstring = "sha256sum",
                stderrToEmit = "lib/app.jar: FAILED",
                activeVersionSymlinkTarget = "/home/testuser/.ltirom/server/versions/1.0.0",
            )
            val result = ServerArtifactInstaller(cli = cli, bundleDirProvider = { tempDir }).ensureInstalled(testEnv)

            val failed = result.installState as InstallState.Failed
            assertEquals("verify", failed.step)
            assertEquals("1.0.0", result.activeVersion)
            val commands = cli.executedCommands.map { it.joinToString(" ") }
            assertTrue(commands.none { it.startsWith("ln") || it.contains("mv -T") }, "$commands")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `failed link swap keeps the previous version active`() {
        val tempDir = bundle("1.6.0")
        try {
            val cli = RecordingFakeCli(
                failOnCommandSubstring = "mv -T",
                activeVersionSymlinkTarget = "/home/testuser/.ltirom/server/versions/1.0.0",
            )
            val result = ServerArtifactInstaller(cli = cli, bundleDirProvider = { tempDir }).ensureInstalled(testEnv)

            assertEquals("activate", (result.installState as InstallState.Failed).step)
            assertEquals("1.0.0", result.activeVersion)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun bundle(version: String): File = File.createTempFile("server_bundle_", "").apply {
        delete()
        mkdirs()
        File(this, "server-version.txt").writeText(version)
    }

    @Test
    fun `missing bundle returns bundledVersion null and no guessed path`() {
        val cli = RecordingFakeCli()
        val installer = ServerArtifactInstaller(cli = cli, bundleDirProvider = { null })

        val result = installer.ensureInstalled(testEnv)

        assertNull(result.bundledVersion, "bundledVersion must be null when bundle is missing")
        assertTrue(result.installState is InstallState.Failed)
    }

    @Test
    fun `a same-version install with a corrupted jar is repaired, not accepted`() {
        val tempDir = bundle("1.7.0")
        try {
            // The active copy is 1.7.0 and its launcher runs, but a JAR no longer matches its checksum.
            val cli = object : WslCliExecutor() {
                val commands = mutableListOf<String>()
                override fun execute(
                    distro: String,
                    command: List<String>,
                    timeoutSeconds: Long,
                    charset: Charset,
                    user: String?,
                ): CliExecutionResult {
                    val cmd = command.joinToString(" ")
                    commands += cmd
                    return when {
                        cmd.startsWith("readlink") -> CliExecutionResult(0, "/home/testuser/.ltirom/server/versions/1.7.0", "")
                        cmd.contains("/current") && cmd.contains("sha256sum") ->
                            CliExecutionResult(1, "", "lib/ltirom-server.jar: FAILED")
                        else -> CliExecutionResult(0, "", "")
                    }
                }
            }
            val result = ServerArtifactInstaller(cli = cli, bundleDirProvider = { tempDir }).ensureInstalled(testEnv)

            assertEquals(InstallState.Current, result.installState)
            assertTrue(cli.commands.any { it.startsWith("cp -r") }, "A damaged install must be re-copied: ${cli.commands}")
            // The damaged copy is moved aside (not deleted) before the verified one replaces it.
            assertTrue(cli.commands.any { it.contains("mv '/home/testuser/.ltirom/server/versions/1.7.0' ") })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `a failed swap puts the set-aside install back`() {
        val tempDir = bundle("1.8.0")
        try {
            val cli = RecordingFakeCli(
                failOnCommandSubstring = "mv /home/testuser/.ltirom/server/versions/1.8.0.partial",
                activeVersionSymlinkTarget = "/home/testuser/.ltirom/server/versions/1.0.0",
            )
            ServerArtifactInstaller(cli = cli, bundleDirProvider = { tempDir }).ensureInstalled(testEnv)

            val commands = cli.executedCommands.map { it.joinToString(" ") }
            assertTrue(commands.any { it.contains("mv '/home/testuser/.ltirom/server/versions/1.8.0.old'") }, "$commands")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `installing v3 keeps v1 while a deferred update leaves the v1 service running`() {
        // v1 still runs (update deferred for its active work), `current` already points at v2.
        val tempDir = bundle("3.0.0")
        try {
            val cli = RecordingFakeCli(activeVersionSymlinkTarget = "/home/testuser/.ltirom/server/versions/2.0.0")
            ServerArtifactInstaller(cli = cli, bundleDirProvider = { tempDir })
                .ensureInstalled(testEnv, keepVersions = setOf("1.0.0"))

            val prune = cli.executedCommands.map { it.joinToString(" ") }.single { it.contains("find ") }
            assertTrue("! -name '1.0.0'" in prune, "The running v1 must survive pruning: $prune")
            assertTrue("! -name '2.0.0'" in prune && "! -name '3.0.0'" in prune, prune)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `nothing is pruned when the running version is unknown`() {
        val tempDir = bundle("3.1.0")
        try {
            val cli = RecordingFakeCli(activeVersionSymlinkTarget = "/home/testuser/.ltirom/server/versions/2.0.0")
            ServerArtifactInstaller(cli = cli, bundleDirProvider = { tempDir }).ensureInstalled(testEnv, prune = false)

            assertTrue(cli.executedCommands.none { it.joinToString(" ").contains("find ") })
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
