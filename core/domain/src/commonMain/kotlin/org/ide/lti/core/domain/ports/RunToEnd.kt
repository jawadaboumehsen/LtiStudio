/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.ports

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.ide.lti.core.domain.pipeline.ToolResult
import org.ide.lti.core.domain.pipeline.stepCommand
import org.ide.lti.core.model.workspace.Workspace
import java.util.UUID

/**
 * Runs a command as a service step and reads it to its end. For long work outside the pipeline (publishing
 * gigabytes, apktool): the quick [PipelineExecutionPort.execute] call times out after 30 s while the command goes on.
 * Like the pipeline, a stream that ends without its result asks the service for the step's status. Cancelling the
 * caller, or a read that fails, cancels the command on the service too: the caller's job lock is released next, so
 * nothing may go on changing the workspace behind it.
 */
public suspend fun PipelineExecutionPort.runToEnd(ws: Workspace, tool: String, args: List<String>): ToolResult {
    val root = checkNotNull(ws.linuxPath) { "workspace ${ws.id} has no WSL folder" }
    val handle = startStep(ws, stepCommand(tool, args, root), "host-${UUID.randomUUID()}")
    val stdout = StringBuilder()
    val stderr = StringBuilder()
    val exitCode = try {
        readToEnd(handle.runId, stdout, stderr)
    } catch (e: Exception) {
        withContext(NonCancellable) { runCatching { cancel(handle.runId) } }
        throw e
    }
    return ToolResult(exitCode, stdout.toString(), stderr.toString())
}

private suspend fun PipelineExecutionPort.readToEnd(runId: String, stdout: StringBuilder, stderr: StringBuilder): Int {
    var exitCode: Int? = null
    var lastSeq = 0L
    while (exitCode == null) {
        observe(runId, if (lastSeq == 0L) 0L else lastSeq + 1).collect { event ->
            when (event) {
                is StepEvent.Output -> {
                    lastSeq = maxOf(lastSeq, event.seq)
                    (if (event.isError) stderr else stdout).appendCaptured(event.text)
                }

                is StepEvent.Finished -> exitCode = event.exitCode

                StepEvent.Heartbeat -> Unit
            }
        }
        if (exitCode == null) {
            val status = status(runId)
            if (status == null || status.status !in STILL_RUNNING) {
                exitCode = status?.exitCode ?: -1
            } else {
                delay(RETRY_MS)
            }
        }
    }
    return checkNotNull(exitCode)
}

/**
 * One output line of a command whose result is used. Past [MAX_CAPTURED_CHARS] it fails: a result that large (a
 * listing of millions of files) would exhaust the app's memory, so it says so instead.
 */
internal fun StringBuilder.appendCaptured(line: String) {
    check(length + line.length < MAX_CAPTURED_CHARS) {
        "The step's output is over ${MAX_CAPTURED_CHARS / CHARS_PER_MEGA}M characters; it is not kept."
    }
    append(line).append('\n')
}

private val STILL_RUNNING = setOf("RUNNING", "QUEUED")
private const val RETRY_MS = 1_000L

// ponytail: one cap for every captured result; stream to a file if a real step ever needs more.
private const val MAX_CAPTURED_CHARS = 64 * 1024 * 1024
private const val CHARS_PER_MEGA = 1024 * 1024
