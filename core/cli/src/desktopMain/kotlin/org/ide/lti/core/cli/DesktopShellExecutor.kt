/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.cli

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.terminal.ShellExecutor
import org.ide.lti.core.domain.terminal.ShellOutputEvent
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader

class DesktopShellExecutor : ShellExecutor {

    internal var lastStartedProcess: Process? = null

    override fun run(command: String, workingDirectory: String): Flow<ShellOutputEvent> = callbackFlow {
        val shellCommand = buildShellCommand(command)
        val workDir = resolveWorkingDirectory(workingDirectory)

        val process = ProcessBuilder(shellCommand)
            .directory(workDir)
            .redirectErrorStream(false)
            .start()

        lastStartedProcess = process

        val stdoutJob = launchStreamReader(process.inputStream, isError = false) {
            trySend(it)
        }
        val stderrJob = launchStreamReader(process.errorStream, isError = true) {
            trySend(it)
        }

        val processJob = launch(Dispatchers.IO) {
            try {
                stdoutJob.join()
                stderrJob.join()
                val exitCode = process.waitFor()
                trySend(ShellOutputEvent.Finished(exitCode))
                close()
            } catch (_: Exception) {
                // cancelled or interrupted
            }
        }

        awaitClose {
            stdoutJob.cancel()
            stderrJob.cancel()
            processJob.cancel()
            killProcessTree(process)
        }
    }

    private fun CoroutineScope.launchStreamReader(
        inputStream: InputStream,
        isError: Boolean,
        onLine: (ShellOutputEvent.Line) -> Unit,
    ): Job = launch(Dispatchers.IO) {
        try {
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                while (isActive) {
                    val line = reader.readLine() ?: break
                    onLine(ShellOutputEvent.Line(line, isError = isError))
                }
            }
        } catch (_: Exception) {
            // stream closed or cancelled
        }
    }

    private fun buildShellCommand(command: String): List<String> {
        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
        return if (isWindows) {
            listOf("cmd.exe", "/c", command)
        } else {
            listOf("/bin/sh", "-c", command)
        }
    }

    private fun resolveWorkingDirectory(directory: String): File {
        val dir = File(directory)
        return if (dir.exists() && dir.isDirectory) {
            dir
        } else {
            File(System.getProperty("user.home"))
        }
    }

    private fun killProcessTree(process: Process) {
        try {
            process.descendants().forEach { it.destroyForcibly() }
        } catch (_: Exception) {
            // Best-effort descendant termination
        }
        if (process.isAlive) {
            process.destroyForcibly()
        }
    }
}
