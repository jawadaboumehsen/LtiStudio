/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.remote

import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.RunStatusValue
import io.ltirom.tooling.core.remote.StartRunRequest
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.ide.lti.core.domain.pipeline.ToolResult
import org.ide.lti.core.domain.ports.FileStat
import org.ide.lti.core.domain.ports.MAX_TRANSFER_CHUNK_BYTES
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.ports.RunHandle
import org.ide.lti.core.domain.ports.StepEvent
import org.ide.lti.core.domain.ports.StepStatus
import org.ide.lti.core.model.run.StepCommand
import org.ide.lti.core.model.workspace.Workspace
import io.ltirom.tooling.core.remote.StreamEvent as CoreStreamEvent

public class StepExecutionAdapter(
    private val transport: RemoteTransportPort,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
) : PipelineExecutionPort {

    private companion object {
        const val DEFAULT_POLL_INTERVAL_MS = 500L
        val TERMINAL = setOf(
            RunStatusValue.COMPLETED,
            RunStatusValue.FAILED,
            RunStatusValue.CANCELLED,
            RunStatusValue.INTERRUPTED,
        )
    }

    override suspend fun startStep(ws: Workspace, step: StepCommand, idempotencyKey: String): RunHandle {
        val workingDir = step.workingDir ?: ws.linuxPath ?: ws.path
        val toolRequest = ToolExecutionRequest(
            toolId = step.toolId,
            arguments = step.args,
            workingDirectory = workingDir,
            timeoutMs = step.timeoutMs,
            streamOutput = true,
        )
        val startReq = StartRunRequest(
            request = toolRequest,
            idempotencyKey = idempotencyKey,
            workspaceLock = workingDir,
        )
        val handle = transport.startRun(startReq)
        return RunHandle(
            runId = handle.runId,
            status = handle.status.name,
            startedAtEpochMs = handle.startedAtEpochMs,
        )
    }

    /**
     * Attaches to the durable run via WebSocket: streams past replayed events and live events,
     * emits heartbeats, and terminates with [StepEvent.Finished]. Maps INTERRUPTED status correctly.
     */
    override fun observe(serverRunId: String, fromSeq: Long): Flow<StepEvent> = flow {
        val startSeq = fromSeq.coerceAtLeast(0L)
        transport.attachRun(serverRunId, startSeq).collect { sequenced ->
            when (val event = sequenced.event) {
                is CoreStreamEvent.OutputChunk -> {
                    emit(StepEvent.Output(sequenced.seq, event.text, event.isError))
                }
                is CoreStreamEvent.Heartbeat -> {
                    emit(StepEvent.Heartbeat)
                }
                is CoreStreamEvent.ExecutionFinished -> {
                    val run = runCatching { transport.getRun(serverRunId) }.getOrNull()
                    val exitCode = if (run?.status == RunStatusValue.INTERRUPTED) {
                        -1
                    } else {
                        event.exitCode
                    }
                    emit(StepEvent.Finished(exitCode))
                }
                is CoreStreamEvent.ProgressUpdate -> {
                    // Ignored for pipeline steps
                }
            }
        }
    }

    override suspend fun status(serverRunId: String): StepStatus? {
        val status = transport.getRun(serverRunId) ?: return null
        return StepStatus(
            runId = status.runId,
            status = status.status.name,
            exitCode = status.exitCode,
            pid = status.pid,
            startedAtEpochMs = status.startedAtEpochMs,
            endedAtEpochMs = status.endedAtEpochMs,
            lastSeq = status.lastSeq,
        )
    }

    override suspend fun cancel(serverRunId: String): Boolean {
        val resp = transport.cancelRun(serverRunId)
        return resp?.accepted ?: false
    }

    override suspend fun uploadFile(ws: Workspace, relPath: String, bytes: ByteArray): Boolean {
        require(bytes.size <= MAX_TRANSFER_CHUNK_BYTES) {
            "uploadFile is for small host-generated metadata only; size ${bytes.size} exceeds $MAX_TRANSFER_CHUNK_BYTES"
        }
        val wsPath = ws.linuxPath ?: ws.path
        val fullPath = "$wsPath/$relPath"
        return transport.uploadFile(fullPath, bytes)
    }

    override suspend fun readFile(ws: Workspace, relPath: String): String? {
        val wsPath = ws.linuxPath ?: ws.path
        val fullPath = "$wsPath/$relPath"
        val bytes = transport.downloadFile(fullPath) ?: return null
        return bytes.decodeToString()
    }

    override suspend fun stat(ws: Workspace, relPath: String): FileStat {
        val wsPath = ws.linuxPath ?: ws.path
        val fullPath = "$wsPath/$relPath"
        val response = transport.execute(
            ToolExecutionRequest(
                toolId = "stat",
                arguments = listOf("-c", "%s", fullPath),
                workingDirectory = wsPath,
            ),
        )
        return if (response.exitCode == 0) {
            val size = response.stdout.trim().toLongOrNull() ?: 0L
            FileStat(path = fullPath, exists = true, sizeBytes = size)
        } else {
            FileStat(path = fullPath, exists = false, sizeBytes = 0L)
        }
    }

    override suspend fun execute(ws: Workspace, command: StepCommand): ToolResult {
        val response = transport.execute(
            ToolExecutionRequest(
                toolId = command.toolId,
                arguments = command.args,
                workingDirectory = command.workingDir ?: ws.linuxPath ?: ws.path,
                timeoutMs = command.timeoutMs,
            ),
        )
        return ToolResult(exitCode = response.exitCode, stdout = response.stdout, stderr = response.stderr)
    }

    override suspend fun checkAvailableDiskSpace(ws: Workspace): Long? {
        val wsPath = ws.linuxPath ?: ws.path
        val response = transport.execute(
            ToolExecutionRequest(
                toolId = "df",
                arguments = listOf("-B1", "--output=avail", wsPath),
                workingDirectory = wsPath,
            ),
        )
        if (response.exitCode != 0) return null
        return response.stdout.trim().lines().lastOrNull()?.trim()?.toLongOrNull()
    }
}
