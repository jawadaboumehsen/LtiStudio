package org.ide.lti.core.model.setup

import kotlinx.serialization.Serializable

/**
 * Aggregate persisted record of toolchain setup progress, surfaced as history
 * (never as a live-verification shortcut — see contracts/setup-environment.md).
 *
 * Includes versioned optional ledger fields for durable attempt tracking and recovery (003 US3).
 * Defaults ensure legacy JSON records without ledger fields decode cleanly to no active attempt.
 */
@Serializable
public data class ToolchainPersistenceState(
    val isSetupCompleted: Boolean = false,
    val completedStages: Set<String> = emptySet(),
    val tools: Map<String, PersistedToolState> = emptyMap(),
    val submodules: Map<String, PersistedSubmoduleState> = emptyMap(),
    val lastVerifiedTimestamp: Long? = null,
    val lastCompletedTimestamp: Long? = null,
    val passedDiagnosticIds: Set<String> = emptySet(),
    val isAvbKeyProvisioned: Boolean = false,
    val avbKeyPath: String? = null,
    val activeDistro: String? = null,
    val activeAttempt: SetupAttemptRecord? = null,
    val attemptHistory: List<SetupAttemptRecord> = emptyList(),
    val recoveryBlocked: Boolean = false,
    val recoveryBlockReason: String? = null,
) {
    public fun isStageCompleted(stageName: String): Boolean = stageName in completedStages
    public fun isSubmoduleSynced(name: String): Boolean = submodules[name]?.isSynced == true
    public fun isToolCompiled(name: String): Boolean = tools[name]?.isCompiled == true
}
