package org.ide.lti.core.data.setup.install

import org.ide.lti.core.data.setup.FakeWslCliExecutor
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class InstallationManagerTest {

    private lateinit var cli: FakeWslCliExecutor
    private lateinit var manager: InstallationManager

    @BeforeTest
    fun setUp() {
        cli = FakeWslCliExecutor()
        manager = InstallationManager(cli, "Ubuntu")
    }

    @Test
    fun `installId calculation is deterministic and changes with content`() {
        val groups1 = mapOf("erofs-utils" to "art-1", "android-tools" to "art-2")
        val outputs1 = listOf(
            InstallOutputDescriptor("mkfs.erofs", "mkfs.erofs"),
            InstallOutputDescriptor("fastboot", "fastboot"),
        )

        val id1 = InstallationManager.computeInstallId(
            schema = 1,
            distro = "Ubuntu",
            arch = "x86_64",
            groups = groups1,
            outputs = outputs1,
            catalogRevision = 1,
        )

        val id2 = InstallationManager.computeInstallId(
            schema = 1,
            distro = "Ubuntu",
            arch = "x86_64",
            groups = groups1,
            outputs = outputs1,
            catalogRevision = 1,
        )

        assertEquals(id1, id2, "Identical inputs must yield identical installId")
        assertTrue(id1.startsWith("i-"), "installId must start with i-")

        val idDiffRevision = InstallationManager.computeInstallId(
            schema = 1,
            distro = "Ubuntu",
            arch = "x86_64",
            groups = groups1,
            outputs = outputs1,
            catalogRevision = 2,
        )
        assertNotEquals(id1, idDiffRevision, "Changed catalogRevision must yield new installId")
    }

    @Test
    fun `assembleCandidate throws when a required catalog group is missing`() {
        val incompleteGroups = mapOf("erofs-utils" to "art-1")
        val outputs = listOf(InstallOutputDescriptor("mkfs.erofs", "mkfs.erofs"))

        assertFailsWith<IllegalArgumentException> {
            manager.assembleCandidate(
                distro = "Ubuntu",
                arch = "x86_64",
                groups = incompleteGroups,
                outputs = outputs,
                catalogRevision = 1,
            )
        }
    }

    @Test
    fun `cleanup aborts on failed directory listing`() {
        cli.commandInterceptor = { cmd ->
            val cmdStr = cmd.joinToString(" ")
            when {
                cmdStr.contains("test -d") -> io.ltirom.tooling.client.wsl.CliExecutionResult(0, "", "")
                cmdStr.contains("ls -1") -> io.ltirom.tooling.client.wsl.CliExecutionResult(
                    exitCode = 1,
                    output = "",
                    error = "ls: cannot access: I/O error",
                )
                else -> null
            }
        }
        assertFailsWith<IllegalStateException> {
            manager.cleanup(activeInstallId = "i-1", previousInstallId = null)
        }
    }

    @Test
    fun `cleanup aborts on corrupted reference manifest`() {
        cli.commandInterceptor = { cmd ->
            val cmdStr = cmd.joinToString(" ")
            when {
                cmdStr.contains("ls -1") ->
                    io.ltirom.tooling.client.wsl.CliExecutionResult(0, "i-active\ni-old\n", "")
                cmdStr.contains("cat") && cmdStr.contains("install.json") ->
                    io.ltirom.tooling.client.wsl.CliExecutionResult(0, "{ corrupt json", "")
                else -> null
            }
        }
        assertFailsWith<IllegalStateException> {
            manager.cleanup(activeInstallId = "i-active", previousInstallId = null)
        }
    }

    @Test
    fun `cleanup preserves active and previous installs and deletes unreferenced`() {
        val deleted = mutableListOf<String>()
        val activeManifest = """{"schema":1,"installId":"i-active","distro":"Ubuntu","arch":"x86_64",""" +
            """"groups":{"android-tools":"art-1"}}"""
        val prevManifest = """{"schema":1,"installId":"i-prev","distro":"Ubuntu","arch":"x86_64",""" +
            """"groups":{"android-tools":"art-0"}}"""

        cli.commandInterceptor = { cmd ->
            val cmdStr = cmd.joinToString(" ")
            when {
                cmdStr.contains("ls -1") && cmdStr.contains("installs") ->
                    io.ltirom.tooling.client.wsl.CliExecutionResult(0, "i-active\ni-prev\ni-stale\n", "")
                cmdStr.contains("cat") && cmdStr.contains("i-active") ->
                    io.ltirom.tooling.client.wsl.CliExecutionResult(0, activeManifest, "")
                cmdStr.contains("cat") && cmdStr.contains("i-prev") ->
                    io.ltirom.tooling.client.wsl.CliExecutionResult(0, prevManifest, "")
                cmdStr.contains("rm -rf") -> {
                    deleted.add(cmdStr)
                    io.ltirom.tooling.client.wsl.CliExecutionResult(0, "", "")
                }
                cmdStr.contains("ls -1") && cmdStr.contains("artifacts") ->
                    io.ltirom.tooling.client.wsl.CliExecutionResult(0, "", "")
                cmdStr.contains("test -L") ->
                    io.ltirom.tooling.client.wsl.CliExecutionResult(1, "", "")
                else -> null
            }
        }

        val result = manager.cleanup(activeInstallId = "i-active", previousInstallId = "i-prev")
        assertEquals(listOf("i-stale"), result.deletedInstalls)
        assertTrue(deleted.any { it.contains("i-stale") }, "i-stale must be deleted")
        assertTrue(
            deleted.none { it.contains("i-active") || it.contains("i-prev") },
            "Active and prev must not be deleted",
        )
    }

    @Test
    fun `cleanup aborts when intermediate group directory is a symlink`() {
        val activeManifest = """{"schema":1,"installId":"i-active","distro":"Ubuntu","arch":"x86_64",""" +
            """"groups":{"android-tools":"art-1"}}"""
        cli.commandInterceptor = { cmd ->
            val cmdStr = cmd.joinToString(" ")
            when {
                cmdStr.contains("ls -1") && cmdStr.contains("installs") ->
                    io.ltirom.tooling.client.wsl.CliExecutionResult(0, "i-active\n", "")
                cmdStr.contains("cat") && cmdStr.contains("install.json") ->
                    io.ltirom.tooling.client.wsl.CliExecutionResult(0, activeManifest, "")
                cmdStr.contains("ls -1") && cmdStr.contains("artifacts") ->
                    io.ltirom.tooling.client.wsl.CliExecutionResult(0, "android-tools\n", "")
                cmdStr.contains("test -L") && cmdStr.contains("artifacts/android-tools") ->
                    io.ltirom.tooling.client.wsl.CliExecutionResult(0, "", "")
                cmdStr.contains("test -L") ->
                    io.ltirom.tooling.client.wsl.CliExecutionResult(1, "", "")
                else -> null
            }
        }
        val ex = assertFailsWith<IllegalStateException> {
            manager.cleanup(activeInstallId = "i-active", previousInstallId = null)
        }
        assertTrue(
            ex.message!!.contains("Security violation: intermediate directory"),
            "Must abort on symlink group directory",
        )
    }

    @Test
    fun `cleanup refuses symlinked managed root before deleting anything`() {
        val deleted = mutableListOf<String>()
        cli.commandInterceptor = { cmd ->
            val cmdStr = cmd.joinToString(" ")
            when {
                cmdStr.contains("test -L") && cmdStr.endsWith("/LtiRomTools") ->
                    io.ltirom.tooling.client.wsl.CliExecutionResult(0, "", "")
                cmdStr.contains("rm -rf") -> {
                    deleted += cmdStr
                    io.ltirom.tooling.client.wsl.CliExecutionResult(0, "", "")
                }
                else -> null
            }
        }

        assertFailsWith<IllegalStateException> {
            manager.cleanup(activeInstallId = null, previousInstallId = null)
        }
        assertEquals(emptyList(), deleted, "Cleanup must fail closed before deleting through a symlink root")
    }

    private fun assertTrue(b: Boolean, msg: String) {
        kotlin.test.assertTrue(b, msg)
    }
}
