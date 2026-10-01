/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.setup

/**
 * Status of the connection to the execution environment / daemon bridge.
 */
public enum class EnvironmentConnectionStatus {
    CONNECTED,
    DISCONNECTED,
    UNAVAILABLE,
}

/**
 * Evidence and verification status for an individual tool binary.
 */
public data class ToolEvidence(
    val toolId: String,
    val recipeGroup: String? = null,
    val sourceRevision: String? = null,
    val probeArgv: List<String> = emptyList(),
    val acceptedExitCodes: Set<Int> = setOf(0),
    val isInstalled: Boolean = false,
    val isVerified: Boolean = false,
    val isFailedExecution: Boolean = false,
    val publishedIdentity: String? = null,
    val lastVerifiedTimestamp: Long? = null,
)

/**
 * Capability descriptor for a recipe group and its owned tools.
 */
public data class RecipeCapability(
    val recipeGroup: String,
    val toolIds: Set<String>,
    val sourceRevision: String? = null,
    val isSupported: Boolean = true,
    val unavailableReason: String? = null,
) {
    init {
        // Unknown recipe is unavailable, not optimistically supported.
        if (!isSupported) {
            require(!unavailableReason.isNullOrBlank()) {
                "Unavailable recipe must specify a reason."
            }
        }
    }
}

/**
 * Enforces: "Repair invalidates the owning recipe group only and verifies all affected required tools."
 */
public fun invalidateForRepair(
    targetToolId: String,
    allTools: Map<String, ToolEvidence>,
    recipes: List<RecipeCapability>,
): Map<String, ToolEvidence> {
    val owningRecipe = recipes.firstOrNull { targetToolId in it.toolIds }
    val affectedToolIds = owningRecipe?.toolIds ?: setOf(targetToolId)

    return allTools.mapValues { (id, evidence) ->
        if (id in affectedToolIds) {
            evidence.copy(isVerified = false)
        } else {
            evidence
        }
    }
}

/**
 * Authoritative point-in-time environment snapshot.
 */
public data class CheckSnapshot(
    val environmentKey: String,
    val checkedAt: Long?,
    val lastReadyAt: Long?,
    val connection: EnvironmentConnectionStatus,
    val requiredToolEvidence: Map<String, ToolEvidence> = emptyMap(),
    val publicationEvidence: Set<String> = emptySet(),
    val storageAvailableBytes: Long? = null,
    val storageMeasuredAt: Long? = null,
    val reason: String? = null,
) {
    init {
        // Invariant: "Only a completed live check updates checkedAt;
        // only fully ready live evidence updates lastReadyAt."
        if (lastReadyAt != null) {
            check(checkedAt != null && lastReadyAt <= checkedAt) {
                "Only a completed live check updates checkedAt; only fully ready live evidence updates lastReadyAt."
            }
        }
    }

    /**
     * True if the environment has fully verified live evidence and is ready for workspace execution.
     * Absent service and empty required inventory cannot become Ready.
     */
    val isReady: Boolean
        get() {
            if (connection != EnvironmentConnectionStatus.CONNECTED) return false
            if (requiredToolEvidence.isEmpty()) return false
            val allToolsReady = requiredToolEvidence.values.all {
                it.isInstalled && it.isVerified && !it.isFailedExecution
            }
            val allPublished = publicationEvidence.isNotEmpty() &&
                requiredToolEvidence.keys.all { it in publicationEvidence }
            return allToolsReady && allPublished
        }

    /**
     * Enforces: "Failed execution verification is preserved until a successful execution probe replaces it."
     */
    public fun withUpdatedToolEvidence(evidence: ToolEvidence): CheckSnapshot {
        val current = requiredToolEvidence[evidence.toolId]
        val updated = if (current != null && current.isFailedExecution && !evidence.isVerified) {
            evidence.copy(isFailedExecution = true)
        } else {
            evidence
        }
        return copy(requiredToolEvidence = requiredToolEvidence + (evidence.toolId to updated))
    }

    public companion object {
        public fun create(
            environmentKey: String,
            checkedAt: Long?,
            lastReadyAt: Long?,
            connection: EnvironmentConnectionStatus,
            requiredToolEvidence: Map<String, ToolEvidence> = emptyMap(),
            publicationEvidence: Set<String> = emptySet(),
            storageAvailableBytes: Long? = null,
            storageMeasuredAt: Long? = null,
            reason: String? = null,
        ): CheckSnapshot {
            if (lastReadyAt != null) {
                check(checkedAt != null) {
                    "Only a completed live check updates checkedAt; only fully ready live evidence updates lastReadyAt."
                }
            }
            val candidate = CheckSnapshot(
                environmentKey = environmentKey,
                checkedAt = checkedAt,
                lastReadyAt = lastReadyAt,
                connection = connection,
                requiredToolEvidence = requiredToolEvidence,
                publicationEvidence = publicationEvidence,
                storageAvailableBytes = storageAvailableBytes,
                storageMeasuredAt = storageMeasuredAt,
                reason = reason,
            )
            if (lastReadyAt != null) {
                check(candidate.isReady) {
                    "Only a completed live check updates checkedAt; only fully ready live evidence updates lastReadyAt."
                }
            }
            return candidate
        }
    }
}

/**
 * Kind of setup plan. Every environment mutation (including cache reset) is previewed as one of these;
 * a tool test acquires operation ownership without a mutation preview and therefore has no plan kind.
 */
public enum class SetupPlanKind {
    FULL_SETUP,
    STAGE_RETRY,
    REPAIR_TOOL,
    CACHE_RESET,
    BOOTSTRAP_PACKAGES,
}

/**
 * Concrete action in an immutable setup plan.
 * Actions identify package installs, source sync, recipe builds and publication exactly; no global signing action.
 */
public sealed interface SetupPlanAction {
    public val description: String

    /**
     * System package remediation. [packages] are apt packages installed through non-interactive sudo,
     * [pipPackages] are Python packages installed through pip, and [configureLoopMountElevation] records
     * the sudoers mount/umount rule that loop-device mounting needs. Preview and execution both read this
     * one action, so the elevation summary can never differ from what runs.
     */
    public data class InstallPackages(
        val packages: List<String>,
        val pipPackages: List<String> = emptyList(),
        val configureLoopMountElevation: Boolean = false,
    ) : SetupPlanAction {
        public val requiresElevation: Boolean
            get() = packages.isNotEmpty() || configureLoopMountElevation

        override val description: String = buildString {
            append("Install packages: ")
            append((packages + pipPackages.map { "$it (pip)" }).joinToString())
            if (configureLoopMountElevation) append("; configure loop-mount elevation")
        }
    }

    public data class SyncSources(val submodules: List<String>) : SetupPlanAction {
        override val description: String = "Sync submodules: ${submodules.joinToString()}"
    }

    public data class BuildRecipes(val recipeGroups: List<String>) : SetupPlanAction {
        override val description: String = "Build recipes: ${recipeGroups.joinToString()}"
    }

    public data class PublishTools(val toolIds: List<String>) : SetupPlanAction {
        override val description: String = "Publish tools: ${toolIds.joinToString()}"
    }

    public data class SwitchToolchain(
        val inputs: Map<ToolGroupId, ResolvedInput>,
        val selectionRevision: Long,
        val forceRebuildGroups: Set<ToolGroupId> = emptySet(),
    ) : SetupPlanAction {
        override val description: String = "Switch toolchain to revision $selectionRevision"
    }
}

/**
 * Immutable domain setup plan driving preview and confirmed execution.
 */
public data class SetupPlan(
    val planId: String,
    val revisionHash: String,
    val environmentKey: String,
    val kind: SetupPlanKind,
    val targetStageOrToolId: String? = null,
    val autoDoctorEnabled: Boolean = true,
    val orderedActions: List<SetupPlanAction> = emptyList(),
    val prerequisiteEvidenceRevision: String? = null,
) {
    /**
     * Enforces: "Confirmation requires the displayed plan ID and unchanged revision hash."
     */
    public fun validateConfirmation(confirmedPlanId: String, confirmedRevisionHash: String) {
        require(confirmedPlanId == planId && confirmedRevisionHash == revisionHash) {
            "Confirmation requires the displayed plan ID and unchanged revision hash."
        }
    }

    /**
     * Enforces: "Auto Doctor policy is immutable for the confirmed attempt."
     */
    public fun validateAttemptPolicy(attemptAutoDoctorPolicy: Boolean) {
        require(attemptAutoDoctorPolicy == autoDoctorEnabled) {
            "Auto Doctor policy is immutable for the confirmed attempt."
        }
    }
}

/**
 * Exhaustive typed outcome for all environment operations.
 */
public sealed interface SetupOutcome {
    public val isSuccess: Boolean
        get() = this is Succeeded

    /**
     * Enforces: "A normal return is not success unless the typed outcome is Succeeded."
     */
    public fun requireSuccess(): Succeeded {
        check(this is Succeeded) {
            "A normal return is not success unless the typed outcome is Succeeded."
        }
        return this
    }

    public data class Succeeded(val evidence: Map<String, ToolEvidence> = emptyMap()) : SetupOutcome
    public data class Failed(val stage: String?, val reason: String) : SetupOutcome
    public data object Cancelled : SetupOutcome
    public data class Busy(val ownerId: String) : SetupOutcome
    public data class Interrupted(val reason: String) : SetupOutcome

    /**
     * The confirmed plan paused because an action requires interactive user authorization.
     * Subsequent build and sync actions are paused. Recheck via resume() continues the plan.
     */
    public data class AwaitingUserAction(
        val pendingPlanId: String,
        val stage: SetupStepStage,
        val reason: String,
        val handoff: UserRepairHandoff,
    ) : SetupOutcome
}

/**
 * Diagnostic repair handoff details for actions requiring user terminal authorization.
 */
public data class UserRepairHandoff(
    val actionId: String,
    val description: String,
    val terminalCommand: String,
    val packages: List<String>,
    val distro: String,
)
