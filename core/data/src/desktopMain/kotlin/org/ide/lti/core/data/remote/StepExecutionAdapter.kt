package org.ide.lti.core.data.remote

import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.StartRunRequest
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import io.ltirom.tooling.core.remote.StreamEvent as CoreStreamEvent
import io.ltirom.tooling.core.remote.RunStatusValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import org.ide.lti.core.domain.pipeline.ToolResult
import org.ide.lti.core.domain.ports.FileStat
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.ports.RunHandle
import org.ide.lti.core.domain.ports.StepEvent
import org.ide.lti.core.domain.ports.StepStatus
import org.ide.lti.core.model.run.StepCommand
import org.ide.lti.core.model.workspace.Workspace

public class StepExecutionAdapter(
    private val transport: RemoteTransportPort,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
) : PipelineExecutionPort {

    private companion object {
        const val DEFAULT_POLL_INTERVAL_MS = 500L
        val TERMINAL = setOf(
            RunStatusValue.COMPLETED, RunStatusValue.FAILED, RunStatusValue.CANCELLED, RunStatusValue.INTERRUPTED,
        )
    }

    override suspend fun startStep(
        ws: Workspace,
        step: StepCommand,
        idempotencyKey: String
    ): RunHandle {
        val workingDir = step.workingDir ?: ws.linuxPath ?: ws.path
        val toolRequest = ToolExecutionRequest(
            toolId = step.toolId,
            arguments = step.args,
            workingDirectory = workingDir,
            timeoutMs = step.timeoutMs,
            streamOutput = true
        )
        val startReq = StartRunRequest(
            request = toolRequest,
            idempotencyKey = idempotencyKey,
            workspaceLock = workingDir
        )
        val handle = transport.startRun(startReq)
        return RunHandle(
            runId = handle.runId,
            status = handle.status.name,
            startedAtEpochMs = handle.startedAtEpochMs
        )
    }

    /**
     * Polls the durable run: drains new sequenced events, then checks the run status. Emits exactly
     * one [StepEvent.Finished] once the run is terminal; a still-running step yields heartbeats.
     */
    override fun observe(serverRunId: String, fromSeq: Long): Flow<StepEvent> = flow {
        var nextSeq = fromSeq
        while (true) {
            nextSeq = drain(serverRunId, nextSeq)
            val run = transport.getRun(serverRunId)
            if (run != null && run.status in TERMINAL) {
                drain(serverRunId, nextSeq)
                emit(StepEvent.Finished(run.exitCode ?: -1))
                return@flow
            }
            emit(StepEvent.Heartbeat)
            delay(pollIntervalMs)
        }
    }

    /** Emits every output chunk with `seq >= from` and returns the next sequence to ask for. */
    private suspend fun FlowCollector<StepEvent>.drain(serverRunId: String, from: Long): Long {
        var next = from
        val events = runCatching { transport.replaySession(serverRunId, from) }.getOrDefault(emptyList())
        for (sequenced in events) {
            if (sequenced.seq < next) continue
            next = sequenced.seq + 1
            val event = sequenced.event
            if (event is CoreStreamEvent.OutputChunk) emit(StepEvent.Output(sequenced.seq, event.text, event.isError))
        }
        return next
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
            lastSeq = status.lastSeq
        )
    }

    override suspend fun cancel(serverRunId: String): Boolean {
        val resp = transport.cancelRun(serverRunId)
        return resp?.accepted ?: false
    }

    override suspend fun uploadFile(ws: Workspace, relPath: String, bytes: ByteArray): Boolean {
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
                workingDirectory = wsPath
            )
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
                timeoutMs = command.timeoutMs
            )
        )
        return ToolResult(exitCode = response.exitCode, stdout = response.stdout, stderr = response.stderr)
    }

    override suspend fun checkAvailableDiskSpace(ws: Workspace): Long? {
        val wsPath = ws.linuxPath ?: ws.path
        val response = transport.execute(
            ToolExecutionRequest(
                toolId = "df",
                arguments = listOf("-B1", "--output=avail", wsPath),
                workingDirectory = wsPath
            )
        )
        if (response.exitCode != 0) return null
        return response.stdout.trim().lines().lastOrNull()?.trim()?.toLongOrNull()
    }
}

