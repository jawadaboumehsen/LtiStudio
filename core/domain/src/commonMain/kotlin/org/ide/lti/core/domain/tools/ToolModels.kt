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

public data class RecipeSummary(val type: String, val revision: Int)

public data class InstalledOutput(val file: String, val size: Long, val mode: String)

public sealed interface ResolvedSource {
    public data class Git(
        val repoUrl: String,
        val ref: ToolRef,
        val commit: String,
        val submodules: Map<String, String> = emptyMap(),
    ) : ResolvedSource

    public data class Release(val version: String, val url: String, val sha256: String, val member: String) :
        ResolvedSource
}

public data class InstalledVersion(
    val group: ToolGroupId,
    val id: String,
    val label: String,
    val source: ResolvedSource,
    val recipe: RecipeSummary,
    val outputs: List<InstalledOutput>,
    val builtAt: String,
    val intact: Boolean,
)

public enum class ChoiceUpdate {
    Record,
    Clear,
    Keep,
}

public data class Selection(val id: String, val source: ResolvedSource)

public data class InterruptedOp(val opId: String, val versionId: String, val startedAt: String)

public enum class GroupStatus {
    NotInstalled,
    Ready,
    NeedsRepair,
    Installing,
    Failed,
    Unknown,
    Interrupted,
}

public enum class OperationKind {
    Install,
    Repair,
    Use,
    Remove,
}

public enum class OperationPhase {
    Fetching,
    Building,
    Verifying,
    Publishing,
    Switching,
}

public class ToolErrorException(public val toolError: ToolError) : Exception(toolError.message)

public sealed interface ToolError {
    public val message: String

    public data class MissingPackages(val packages: List<String>) : ToolError {
        override val message: String get() = "Missing system packages: ${packages.joinToString(", ")}"
    }

    public data class Network(val reason: String) : ToolError {
        override val message: String get() = "Network error: $reason"
    }

    public data class Timeout(val detail: String) : ToolError {
        override val message: String get() = "Timed out: $detail"
    }

    /** The new version is in place, but the service doesn't offer its tools. */
    public data class RegistrationFailed(val detail: String) : ToolError {
        override val message: String get() = "Switched, but tools are not registered: $detail"
    }

    public data class RefNotFound(val ref: String) : ToolError {
        override val message: String get() = "Reference not found: $ref"
    }

    public data class UnsupportedLayout(val missingPaths: List<String>) : ToolError {
        override val message: String get() = "Missing required paths: ${missingPaths.joinToString(", ")}"
    }

    public data class PatchFailed(val detail: String) : ToolError {
        override val message: String get() = "Patch failed: $detail"
    }

    public data class BuildFailed(val detail: String) : ToolError {
        override val message: String get() = "Build failed: $detail"
    }

    public data class VerifyFailed(val toolId: String) : ToolError {
        override val message: String get() = "Tool verification failed for $toolId"
    }

    public data class ChecksumMismatch(val detail: String) : ToolError {
        override val message: String get() = "Checksum mismatch: $detail"
    }

    public data class DiskFull(val detail: String) : ToolError {
        override val message: String get() = "Insufficient disk space: $detail"
    }

    public data class Busy(val detail: String) : ToolError {
        override val message: String get() = "Operation busy: $detail"
    }

    public data class InUse(val detail: String) : ToolError {
        override val message: String get() = "Version in use: $detail"
    }

    public data class NotFound(val detail: String) : ToolError {
        override val message: String get() = "Not found: $detail"
    }

    public data class ConnectionLost(val detail: String) : ToolError {
        override val message: String get() = "Connection to build service lost: $detail"
    }

    public data class AdmissionRequired(val detail: String) : ToolError {
        override val message: String get() = "Command admission closed: $detail"
    }

    public data class Internal(val detail: String) : ToolError {
        override val message: String get() = "Internal error: $detail"
    }
}

public sealed interface OperationResult {
    public data class Reused(val versionId: String) : OperationResult

    public data class Installed(val versionId: String) : OperationResult

    public data class Used(val versionId: String) : OperationResult

    public data class Failed(val error: ToolError, val message: String, val logRef: String? = null) : OperationResult

    public data object Cancelled : OperationResult

    public data class Interrupted(val reason: String) : OperationResult

    public data class TrustRequired(val url: String) : OperationResult
}

public data class ToolOperation(
    val opId: String,
    val group: ToolGroupId,
    val kind: OperationKind,
    val source: ResolvedSource? = null,
    val phase: OperationPhase,
    val serverRunId: String? = null,
    /** Bounded live output tail for the setup/tools console; never a fabricated progress message. */
    val outputTail: List<String> = emptyList(),
    val result: OperationResult? = null,
)

public data class GroupState(
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

public data class ToolInventory(val groups: Map<ToolGroupId, GroupState>)

public sealed interface GroupSetupResult {
    public data class Reused(val versionId: String) : GroupSetupResult

    public data class Installed(val versionId: String) : GroupSetupResult

    public data class Failed(val error: ToolError) : GroupSetupResult

    public data class NotAttempted(val blocker: ToolError) : GroupSetupResult

    public data object Cancelled : GroupSetupResult
}

public data class SetupToolsSummary(val groupResults: Map<ToolGroupId, GroupSetupResult>) {
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

public data class PinnedTools(val paths: Map<String, String>, val versions: Map<String, String>)
