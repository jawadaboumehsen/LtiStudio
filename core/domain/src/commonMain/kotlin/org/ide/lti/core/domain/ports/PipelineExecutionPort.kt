package org.ide.lti.core.domain.ports

import kotlinx.coroutines.flow.Flow
import org.ide.lti.core.model.run.StepCommand
import org.ide.lti.core.domain.pipeline.ToolResult
import org.ide.lti.core.model.workspace.Workspace

public data class RunHandle(
    val runId: String,
    val status: String,
    val startedAtEpochMs: Long
)

public data class StepStatus(
    val runId: String,
    val status: String,
    val exitCode: Int? = null,
    val pid: Long? = null,
    val startedAtEpochMs: Long = 0L,
    val endedAtEpochMs: Long? = null,
    val lastSeq: Long = 0L
)

public data class FileStat(
    val path: String,
    val exists: Boolean,
    val sizeBytes: Long = 0L,
    val isDirectory: Boolean = false
)

public sealed interface StepEvent {
    public data class Output(val seq: Long, val text: String, val isError: Boolean = false) : StepEvent
    public data class Finished(val exitCode: Int) : StepEvent
    public data object Heartbeat : StepEvent
}

public interface PipelineExecutionPort {
    public suspend fun startStep(
        ws: Workspace,
        step: StepCommand,
        idempotencyKey: String
    ): RunHandle

    public fun observe(serverRunId: String, fromSeq: Long = 0L): Flow<StepEvent>

    public suspend fun status(serverRunId: String): StepStatus?

    public suspend fun cancel(serverRunId: String): Boolean

    public suspend fun uploadFile(ws: Workspace, relPath: String, bytes: ByteArray): Boolean

    public suspend fun readFile(ws: Workspace, relPath: String): String?

    public suspend fun stat(ws: Workspace, relPath: String): FileStat

    /** Synchronous tool call for quick queries (`stat`, `sha256sum`, AVB probes); not journaled. */
    public suspend fun execute(ws: Workspace, command: StepCommand): ToolResult

    /** Free bytes on the workspace filesystem, or null when it cannot be determined. */
    public suspend fun checkAvailableDiskSpace(ws: Workspace): Long?
}
