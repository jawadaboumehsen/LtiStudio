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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.ide.lti.core.domain.terminal.ShellOutputEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopShellExecutorTest {

    @Test
    fun testProcessExecution() = runBlocking(Dispatchers.IO) {
        val executor = DesktopShellExecutor()
        val cwd = System.getProperty("user.home") ?: "."
        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
        val command = if (isWindows) "echo test_output" else "echo test_output"

        val events = executor.run(command, cwd).toList()
        assertTrue(events.isNotEmpty())
        val outputLines = events.filterIsInstance<ShellOutputEvent.Line>().map { it.text.trim() }
        assertTrue(outputLines.any { it.contains("test_output") })
        val finished = events.filterIsInstance<ShellOutputEvent.Finished>().firstOrNull()
        assertNotNull(finished)
        assertEquals(0, finished.exitCode)
    }

    @Test
    fun testProcessCancellationKillsProcess() = runBlocking(Dispatchers.IO) {
        val executor = DesktopShellExecutor()
        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
        val longRunningCommand = if (isWindows) {
            "ping -n 30 127.0.0.1"
        } else {
            "sleep 30"
        }

        val cwd = System.getProperty("user.home") ?: "."

        val job = launch {
            executor.run(longRunningCommand, cwd).collect { }
        }

        // Wait until process has been started by the executor
        var waited = 0
        while (executor.lastStartedProcess == null && waited < 5000) {
            Thread.sleep(50)
            waited += 50
        }

        val process = executor.lastStartedProcess
        assertNotNull(process, "Process should have started")
        assertTrue(process.isAlive, "Process should be alive while running")

        // Cancel the coroutine collecting the flow
        job.cancel()
        job.join()

        // Give process termination up to 3 seconds to complete
        var waitKill = 0
        while (process.isAlive && waitKill < 3000) {
            Thread.sleep(50)
            waitKill += 50
        }

        assertFalse(process.isAlive, "Underlying process must be killed on cancellation")
        val livingDescendants = process.descendants().filter { it.isAlive }.count()
        assertEquals(0L, livingDescendants, "All descendant processes must be killed on cancellation")
    }
}
