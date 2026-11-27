/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.tools

import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.model.setup.ToolRef

data class RecipeSummary(val type: String, val revision: Int)

data class InstalledOutput(val file: String, val size: Long, val mode: String)

sealed interface ResolvedSource {
    /** Whether [other] is the same code: same repository and commits, or same release entry. */
    fun sameIdentityAs(other: ResolvedSource): Boolean

    data class Git(
        val repoUrl: String,
        val ref: ToolRef,
        val commit: String,
        val submodules: Map<String, String> = emptyMap(),
    ) : ResolvedSource {
        override fun sameIdentityAs(other: ResolvedSource): Boolean =
            other is Git && repoUrl == other.repoUrl && commit == other.commit && submodules == other.submodules
    }

    data class Release(val version: String, val url: String, val sha256: String, val member: String) :
        ResolvedSource {
        override fun sameIdentityAs(other: ResolvedSource): Boolean =
            other is Release && version == other.version && url == other.url && sha256 == other.sha256 &&
                member == other.member
    }
}

data class InstalledVersion(
    val group: ToolGroupId,
    val id: String,
    val label: String,
    val source: ResolvedSource,
    val recipe: RecipeSummary,
    val outputs: List<InstalledOutput>,
    val builtAt: String,
    val intact: Boolean,
)

enum class ChoiceUpdate {
    Record,
    Clear,
    Keep,
}

data class Selection(val id: String, val source: ResolvedSource)

data class InterruptedOp(val opId: String, val versionId: String, val startedAt: String)

enum class GroupStatus {
    NotInstalled,
    Ready,
    NeedsRepair,
    Installing,
    Failed,
    Unknown,
    Interrupted,
}

enum class OperationKind {
    Install,
    Repair,
    Use,
    Remove,
}

enum class OperationPhase {
    Fetching,
    Building,
    Verifying,
    Publishing,
    Switching,
}

class ToolErrorException(val toolError: ToolError) : Exception(toolError.message)

sealed interface ToolError {
    val message: String

    data class MissingPackages(val packages: List<String>) : ToolError {
        override val message: String get() = "Missing system packages: ${packages.joinToString(", ")}"
    }

    data class Network(val reason: String) : ToolError {
        override val message: String get() = "Network error: $reason"
    }

    data class Timeout(val detail: String) : ToolError {
        override val message: String get() = "Timed out: $detail"
    }

    /** The new version is in place, but the service doesn't offer its tools. */
    data class RegistrationFailed(val detail: String) : ToolError {
        override val message: String get() = "Switched, but tools are not registered: $detail"
    }

    data class RefNotFound(val ref: String) : ToolError {
        override val message: String get() = "Reference not found: $ref"
    }

    data class UnsupportedLayout(val missingPaths: List<String>) : ToolError {
        override val message: String get() = "Missing required paths: ${missingPaths.joinToString(", ")}"
    }

    data class PatchFailed(val detail: String) : ToolError {
        override val message: String get() = "Patch failed: $detail"
    }

    data class BuildFailed(val detail: String) : ToolError {
        override val message: String get() = "Build failed: $detail"
    }

    data class VerifyFailed(val toolId: String) : ToolError {
        override val message: String get() = "Tool verification failed for $toolId"
    }

    data class ChecksumMismatch(val detail: String) : ToolError {
        override val message: String get() = "Checksum mismatch: $detail"
    }

    data class DiskFull(val detail: String) : ToolError {
        override val message: String get() = "Insufficient disk space: $detail"
    }

    data class Busy(val detail: String) : ToolError {
        override val message: String get() = "Operation busy: $detail"
    }

    data class InUse(val detail: String) : ToolError {
        override val message: String get() = "Version in use: $detail"
    }

    data class NotFound(val detail: String) : ToolError {
        override val message: String get() = "Not found: $detail"
    }

    data class ConnectionLost(val detail: String) : ToolError {
        override val message: String get() = "Connection to build service lost: $detail"
    }

    data class AdmissionRequired(val detail: String) : ToolError {
        override val message: String get() = "Command admission closed: $detail"
    }

    data class Internal(val detail: String) : ToolError {
        override val message: String get() = "Internal error: $detail"
    }
}

sealed interface OperationResult {
    data class Reused(val versionId: String) : OperationResult

    data class Installed(val versionId: String) : OperationResult

    data class Used(val versionId: String) : OperationResult

    data class Failed(val error: ToolError, val message: String, val logRef: String? = null) : OperationResult

    data object Cancelled : OperationResult

    data class Interrupted(val reason: String) : OperationResult

    data class TrustRequired(val url: String) : OperationResult
}

data class ToolOperation(
    val opId: String,
    val group: ToolGroupId,
    val kind: OperationKind,
    val source: ResolvedSource? = null,
    val phase: OperationPhase,
    val serverRunId: String? = null,
    /** Bounded live output tail for the setup/tools console; never a fabricated progress message. */
    val outputTail: List<String> = emptyList(),
    val result: OperationResult? = null,
    /** 0..100 from what the install really reported (see [OperationProgress]); null when it cannot be measured. */
    val percent: Int? = null,
)

data class GroupState(
    val group: ToolGroupId,
    val current: InstalledVersion?,
    val selection: Selection? = null,
    val installed: List<InstalledVersion>,
    val operation: ToolOperation? = null,
    val currentUnknown: Boolean = false,
    val interrupted: InterruptedOp? = null,
) {
    val status: GroupStatus
        get() =
            when {
                operation != null && operation.result == null -> GroupStatus.Installing
                interrupted != null -> GroupStatus.Interrupted
                currentUnknown -> GroupStatus.Unknown
                current ==
                    null -> if (installed.isNotEmpty() ||
                    selection != null
                ) {
                    GroupStatus.NeedsRepair
                } else {
                    GroupStatus.NotInstalled
                }
                !current.intact -> GroupStatus.NeedsRepair
                operation?.result is OperationResult.Failed -> GroupStatus.Failed
                current.intact -> GroupStatus.Ready
                else -> GroupStatus.Unknown
            }
}

data class ToolInventory(val groups: Map<ToolGroupId, GroupState>)

sealed interface GroupSetupResult {
    data class Reused(val versionId: String) : GroupSetupResult

    data class Installed(val versionId: String) : GroupSetupResult

    data class Failed(val error: ToolError) : GroupSetupResult

    data class NotAttempted(val blocker: ToolError) : GroupSetupResult

    data object Cancelled : GroupSetupResult
}

data class SetupToolsSummary(val groupResults: Map<ToolGroupId, GroupSetupResult>) {
    val isComplete: Boolean get() =
        groupResults.values.all {
            it is GroupSetupResult.Reused || it is GroupSetupResult.Installed
        }
    val readyCount: Int get() =
        groupResults.values.count {
            it is GroupSetupResult.Reused || it is GroupSetupResult.Installed
        }
    val failedCount: Int get() = groupResults.values.count { it is GroupSetupResult.Failed }
    val notAttemptedCount: Int get() = groupResults.values.count { it is GroupSetupResult.NotAttempted }
}

data class PinnedTools(val paths: Map<String, String>, val versions: Map<String, String>)
