/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.run

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class StageState {
    PENDING,
    RUNNING,
    EXECUTED,
    SKIPPED,
    FAILED,
    NOT_RUN,
    CANCELLED,
    INTERRUPTED,
}

@Serializable
data class StageOutcome(
    val stageId: StageId,
    val state: StageState = StageState.PENDING,
    val attempt: Int = 1,
    val startedAt: Instant? = null,
    val endedAt: Instant? = null,
    val durationMs: Long? = null,
    val message: String? = null,
    val cacheKey: String? = null,
)

@Serializable
enum class StepState {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    INTERRUPTED,
}

@Serializable
data class StepCommand(
    val toolId: String,
    val args: List<String> = emptyList(),
    val workingDir: String? = null,
    val timeoutMs: Long = 0L,
)

@Serializable
data class StageStep(
    val stageId: StageId,
    val index: Int,
    val serverRunId: String? = null,
    val idempotencyKey: String,
    val command: StepCommand,
    val state: StepState = StepState.PENDING,
    val exitCode: Int? = null,
    val lastSeq: Long = 0L,
) {
    companion object {
        fun deriveIdempotencyKey(runId: String, stageId: Any, stepIndex: Int): String {
            val stageName = when (stageId) {
                is StageId -> stageId.name
                else -> stageId.toString()
            }
            val raw = "$runId/$stageName/$stepIndex"
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val hash = md.digest(raw.toByteArray(Charsets.UTF_8))
            return hash.joinToString("") { "%02x".format(it) }
        }
    }
}

@Serializable
enum class ArtifactKind {
    FLASHABLE_ZIP,
    OTA_MANIFEST,
    CHECKSUM,
    IMAGE,
}

@Serializable
enum class VerificationState {
    VERIFIED,
    FAILED,
    NOT_VERIFIED,
}

@Serializable
enum class Presence {
    PRESENT,
    MISSING,
}

@Serializable
data class Artifact(
    val kind: ArtifactKind,
    val linuxPath: String,
    val sizeBytes: Long? = null,
    val sha256: String? = null,
    val verification: VerificationState = VerificationState.NOT_VERIFIED,
    val presence: Presence = Presence.MISSING,
)

@Serializable
data class BuildRun(
    val id: String,
    val workspaceId: String,
    val snapshotId: String,
    val state: RunState = RunState.QUEUED,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val stages: List<StageOutcome> = emptyList(),
    val steps: List<StageStep> = emptyList(),
    val artifacts: List<Artifact> = emptyList(),
    val lastSeqByStep: Map<String, Long> = emptyMap(),
    val driverInstanceId: String? = null,
    val driverHeartbeatEpochMs: Long? = null,
    /** Small captured step results (digests, sizes, dates) so a resumed driver can re-plan. */
    val runtimeValues: Map<String, String> = emptyMap(),
)

@Serializable
public data class BuildRunSummary(
    val id: String,
    val workspaceId: String,
    val snapshotId: String,
    val state: RunState,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val exitCode: Int? = null,
    val artifacts: List<Artifact> = emptyList(),
    val steps: List<StageStep> = emptyList(),
)

@Serializable
public data class RunIndex(
    val schemaVersion: Int = 1,
    val items: List<BuildRunSummary> = emptyList(),
)

public fun BuildRun.toSummary(): BuildRunSummary = BuildRunSummary(
    id = id,
    workspaceId = workspaceId,
    snapshotId = snapshotId,
    state = state,
    startedAt = startedAt,
    endedAt = endedAt,
    exitCode = steps.lastOrNull()?.exitCode ?: if (state == RunState.SUCCEEDED) 0 else null,
    artifacts = artifacts,
    steps = steps,
)
