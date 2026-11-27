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

import kotlinx.coroutines.flow.Flow
import org.ide.lti.core.domain.pipeline.ToolResult
import org.ide.lti.core.model.run.StepCommand
import org.ide.lti.core.model.workspace.Workspace

data class RunHandle(
    val runId: String,
    val status: String,
    val startedAtEpochMs: Long,
)

data class StepStatus(
    val runId: String,
    val status: String,
    val exitCode: Int? = null,
    val pid: Long? = null,
    val startedAtEpochMs: Long = 0L,
    val endedAtEpochMs: Long? = null,
    val lastSeq: Long = 0L,
)

/** The service reports this step as over. Anything else, a null status too, does not say it has stopped. */
val StepStatus.hasFinished: Boolean
    get() = status in FINISHED_STEP_STATES

private val FINISHED_STEP_STATES = setOf("COMPLETED", "FAILED", "CANCELLED", "INTERRUPTED")

data class FileStat(val path: String, val exists: Boolean, val sizeBytes: Long = 0L, val isDirectory: Boolean = false)

sealed interface StepEvent {
    data class Output(val seq: Long, val text: String, val isError: Boolean = false) : StepEvent
    data class Finished(val exitCode: Int) : StepEvent
    data object Heartbeat : StepEvent
}

interface PipelineExecutionPort {
    suspend fun startStep(
        ws: Workspace,
        step: StepCommand,
        idempotencyKey: String,
    ): RunHandle

    fun observe(serverRunId: String, fromSeq: Long = 0L): Flow<StepEvent>

    suspend fun status(serverRunId: String): StepStatus?

    suspend fun cancel(serverRunId: String): Boolean

    /** Small host-generated metadata only. */
    suspend fun uploadFile(ws: Workspace, relPath: String, bytes: ByteArray): Boolean

    suspend fun uploadBounded(
        ws: Workspace,
        relPath: String,
        totalBytes: Long,
        chunks: Flow<ByteArray>,
    ): TransferReceipt = throw UnsupportedTransportCapabilityException(
        "bounded streaming upload is not supported by this transport",
    )

    suspend fun readFile(ws: Workspace, relPath: String): String?

    suspend fun stat(ws: Workspace, relPath: String): FileStat

    /** Synchronous tool call for quick queries (`stat`, `sha256sum`, AVB probes); not journaled. */
    suspend fun execute(ws: Workspace, command: StepCommand): ToolResult

    /** Free bytes on the workspace filesystem, or null when it cannot be determined. */
    suspend fun checkAvailableDiskSpace(ws: Workspace): Long?
}
