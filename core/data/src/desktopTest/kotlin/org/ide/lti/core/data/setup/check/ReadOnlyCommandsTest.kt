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
import kotlinx.coroutines.test.runTest
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReadOnlyCommandsTest {

    private class RecordingCli : WslCliExecutor() {
        val executed = mutableListOf<List<String>>()

        override fun execute(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            charset: Charset,
            user: String?,
        ): CliExecutionResult {
            executed.add(command)
            return CliExecutionResult(0, "ok", "")
        }
    }

    @Test
    fun safeReadCommandsPassValidation() = runTest {
        val cli = RecordingCli()
        val runner = ReadOnlyCommands(cli)

        runner.run("Ubuntu", listOf("test", "-f", "/path/to/file"))
        runner.run("Ubuntu", listOf("stat", "/path/to/file"))
        runner.run("Ubuntu", listOf("readlink", "-f", "/path/to/link"))
        runner.run("Ubuntu", listOf("sha256sum", "/path/to/file"))
        runner.run("Ubuntu", listOf("df", "-P", "/path"))

        assertEquals(5, cli.executed.size)
    }

    @Test
    fun allowedFindCommandsPass() = runTest {
        val cli = RecordingCli()
        val runner = ReadOnlyCommands(cli)

        runner.run("Ubuntu", listOf("find", "/path", "-maxdepth", "1", "-type", "f", "-name", "*.txt"))
        assertEquals(1, cli.executed.size)
    }

    @Test
    fun forbiddenFindOptionsThrow() = runTest {
        val runner = ReadOnlyCommands(RecordingCli())

        val forbidden = listOf(
            listOf("find", "/path", "-exec", "rm", "{}", ";"),
            listOf("find", "/path", "-execdir", "rm", "{}", ";"),
            listOf("find", "/path", "-ok", "rm", "{}", ";"),
            listOf("find", "/path", "-okdir", "rm", "{}", ";"),
            listOf("find", "/path", "-delete"),
            listOf("find", "/path", "-fprint", "/tmp/out"),
            listOf("find", "/path", "-fprintf", "/tmp/out", "%p"),
            listOf("find", "/path", "-fprint0", "/tmp/out"),
            listOf("find", "/path", "-fls", "/tmp/out"),
        )

        for (cmd in forbidden) {
            assertFailsWith<IllegalArgumentException>("Expected $cmd to throw") {
                runner.run("Ubuntu", cmd)
            }
        }
    }

    @Test
    fun dpkgQueryRequiresDashW() = runTest {
        val runner = ReadOnlyCommands(RecordingCli())

        // -W passes
        val cli = RecordingCli()
        ReadOnlyCommands(cli).run("Ubuntu", listOf("dpkg-query", "-W", "-f=\${Status}", "cmake"))
        assertEquals(1, cli.executed.size)

        // Without -W throws
        assertFailsWith<IllegalArgumentException> {
            runner.run("Ubuntu", listOf("dpkg-query", "-s", "cmake"))
        }
        assertFailsWith<IllegalArgumentException> {
            runner.run("Ubuntu", listOf("dpkg-query", "-l", "cmake"))
        }
    }

    @Test
    fun gitAllowedSubcommandsPass() = runTest {
        val cli = RecordingCli()
        val runner = ReadOnlyCommands(cli)

        runner.run("Ubuntu", listOf("git", "-C", "/repo", "rev-parse", "HEAD"))
        runner.run("Ubuntu", listOf("git", "-C", "/repo", "ls-tree", "HEAD"))
        runner.run("Ubuntu", listOf("git", "ls-remote", "https://github.com/foo/bar.git"))
        runner.run("Ubuntu", listOf("git", "-C", "/repo", "remote", "get-url", "origin"))
        runner.run("Ubuntu", listOf("git", "-C", "/repo", "status", "--porcelain"))

        assertEquals(5, cli.executed.size)
    }

    @Test
    fun gitForbiddenSubcommandsAndFlagsThrow() = runTest {
        val runner = ReadOnlyCommands(RecordingCli())

        val forbidden = listOf(
            listOf("git", "-c", "core.editor=echo", "status"),
            listOf("git", "--config-env", "foo=bar", "status"),
            listOf("git", "fetch"),
            listOf("git", "-C", "/repo", "push"),
            listOf("git", "-C", "/repo", "commit", "-m", "msg"),
            listOf("git", "-C", "/repo", "checkout", "main"),
            listOf("git", "-C", "/repo", "reset", "--hard"),
            listOf("git", "-C", "/repo", "clean", "-fdx"),
        )

        for (cmd in forbidden) {
            assertFailsWith<IllegalArgumentException>("Expected $cmd to throw") {
                runner.run("Ubuntu", cmd)
            }
        }
    }

    @Test
    fun catalogProbesWithExactArgsPass() = runTest {
        val cli = RecordingCli()
        val runner = ReadOnlyCommands(cli)

        runner.run("Ubuntu", listOf("/bin/adb", "version"))
        runner.run("Ubuntu", listOf("/bin/fastboot", "--version"))
        runner.run("Ubuntu", listOf("/bin/mke2fs", "-V"))

        assertEquals(3, cli.executed.size)
    }

    @Test
    fun probesWithNonCatalogArgsThrow() = runTest {
        val runner = ReadOnlyCommands(RecordingCli())

        assertFailsWith<IllegalArgumentException> {
            runner.run("Ubuntu", listOf("/bin/adb", "shell", "rm", "-rf", "/"))
        }
        assertFailsWith<IllegalArgumentException> {
            runner.run("Ubuntu", listOf("/bin/fastboot", "flash", "boot", "boot.img"))
        }
    }

    @Test
    fun fixedFormCommandsPassAndDeviationsThrow() = runTest {
        val cli = RecordingCli()
        val runner = ReadOnlyCommands(cli)

        runner.run("Ubuntu", listOf("java", "-version"))
        runner.run("Ubuntu", listOf("python3", "-c", "import sys; print(sys.version)"))

        assertEquals(2, cli.executed.size)

        assertFailsWith<IllegalArgumentException> {
            runner.run("Ubuntu", listOf("java", "-jar", "malicious.jar"))
        }
        assertFailsWith<IllegalArgumentException> {
            runner.run("Ubuntu", listOf("python3", "-m", "http.server"))
        }
    }
}
