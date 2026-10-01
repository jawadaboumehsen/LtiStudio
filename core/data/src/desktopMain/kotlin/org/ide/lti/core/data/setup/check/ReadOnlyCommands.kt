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
import org.ide.lti.core.domain.setup.ToolGroupCatalog

/**
 * Runner for environment inspection stages that enforces argument-level read-only
 * allowlisting (FR-003). Any command or argument pattern not explicitly permitted
 * throws [IllegalArgumentException].
 */
public class ReadOnlyCommands(private val cli: WslCliExecutor = WslCliExecutor()) : WslCliExecutor() {

    public companion object {
        private const val DEFAULT_TIMEOUT_SECONDS = 10L
        private val UNRESTRICTED_READ_COMMANDS = setOf("test", "stat", "readlink", "sha256sum", "df")

        private val FORBIDDEN_FIND_FLAGS = setOf(
            "-exec",
            "-execdir",
            "-ok",
            "-okdir",
            "-delete",
            "-fprint",
            "-fprintf",
            "-fprint0",
            "-fls",
        )

        private val ALLOWED_FIND_FLAGS = setOf(
            "-L",
            "-maxdepth",
            "-mindepth",
            "-type",
            "-name",
            "-perm",
            "-printf",
        )

        private val ALLOWED_GIT_SUBCOMMANDS = setOf(
            "rev-parse",
            "ls-tree",
            "ls-remote",
            "remote",
            "status",
        )
    }

    override fun execute(
        distro: String,
        command: List<String>,
        timeoutSeconds: Long,
        charset: java.nio.charset.Charset,
        user: String?,
    ): CliExecutionResult {
        validate(command)
        return cli.execute(distro, command, timeoutSeconds, charset, user)
    }

    override fun execute(
        distro: String,
        command: List<String>,
        timeoutSeconds: Long,
        charset: java.nio.charset.Charset,
        user: String?,
        stdin: String?,
    ): CliExecutionResult {
        validate(command)
        return cli.execute(distro, command, timeoutSeconds, charset, user, stdin)
    }

    override suspend fun run(
        distro: String,
        command: List<String>,
        timeoutSeconds: Long,
        user: String?,
    ): CliExecutionResult {
        validate(command)
        return cli.run(distro, command, timeoutSeconds, user)
    }

    override suspend fun run(
        distro: String,
        command: List<String>,
        timeoutSeconds: Long,
        user: String?,
        stdin: String?,
    ): CliExecutionResult {
        validate(command)
        return cli.run(distro, command, timeoutSeconds, user, stdin)
    }

    public fun validate(command: List<String>) {
        require(command.isNotEmpty()) { "Command must not be empty" }
        val rawBinary = command.first()
        val binary = rawBinary.substringAfterLast('/')
        val args = command.drop(1)

        val matchingOutput = ToolGroupCatalog.allOutputs.firstOrNull { it.file == binary || it.toolId == binary }
        when {
            matchingOutput != null -> validateProbeArgs(binary, args, matchingOutput.probe.args)
            binary in UNRESTRICTED_READ_COMMANDS -> Unit
            binary == "find" -> validateFind(args)
            binary == "dpkg-query" -> validateDpkgQuery(args)
            binary == "git" -> validateGit(args)
            binary == "java" -> validateJava(args)
            binary == "python3" || binary == "python" -> validatePython(args)
            else -> throw IllegalArgumentException("Forbidden read-only command: $binary")
        }
    }

    private fun validateProbeArgs(binary: String, actualArgs: List<String>, expectedArgs: List<String>) {
        if (actualArgs != expectedArgs) {
            throw IllegalArgumentException(
                "Forbidden probe arguments for $binary: $actualArgs (expected $expectedArgs)",
            )
        }
    }

    private fun validateJava(args: List<String>) {
        if (args != listOf("-version")) {
            throw IllegalArgumentException("Forbidden java command: $args (only 'java -version' allowed)")
        }
    }

    private fun validatePython(args: List<String>) {
        if (args.size != 2 || args[0] != "-c") {
            throw IllegalArgumentException("Forbidden python command: $args (only 'python3 -c <script>' allowed)")
        }
    }

    private fun validateFind(args: List<String>) {
        for (arg in args) {
            if (arg in FORBIDDEN_FIND_FLAGS) {
                throw IllegalArgumentException("Forbidden find flag: $arg")
            }
        }
        for (arg in args) {
            if (arg.startsWith("-") && arg !in ALLOWED_FIND_FLAGS && !arg.startsWith("-perm")) {
                throw IllegalArgumentException("Unknown or unallowed find flag: $arg")
            }
        }
    }

    private fun validateDpkgQuery(args: List<String>) {
        if ("-W" !in args && "--show" !in args) {
            throw IllegalArgumentException("dpkg-query requires -W flag")
        }
        for (arg in args) {
            if (isForbiddenDpkgFlag(arg)) {
                throw IllegalArgumentException("Forbidden dpkg-query flag: $arg")
            }
        }
    }

    private fun isForbiddenDpkgFlag(arg: String): Boolean = arg.startsWith("-") &&
        arg != "-W" &&
        arg != "--show" &&
        !arg.startsWith("-f=") &&
        !arg.startsWith("--showformat=")

    private fun validateGit(args: List<String>) {
        val error = checkGitError(args)
        if (error != null) {
            throw IllegalArgumentException(error)
        }
    }

    private fun checkGitError(args: List<String>): String? {
        val subIndex = if (args.getOrNull(0) == "-C") 2 else 0
        val subcommand = args.getOrNull(subIndex)
        return when {
            args.any { it == "-c" || it == "--config-env" || it == "--exec-path" } ->
                "Forbidden git configuration flags"
            subcommand == null ->
                "Missing git subcommand"
            subcommand !in ALLOWED_GIT_SUBCOMMANDS ->
                "Forbidden git subcommand: $subcommand"
            subcommand == "remote" && args.getOrNull(subIndex + 1) != "get-url" ->
                "Only 'git remote get-url' is allowed"
            subcommand == "status" && args.getOrNull(subIndex + 1) != "--porcelain" ->
                "Only 'git status --porcelain' is allowed"
            else -> null
        }
    }
}
