package org.ide.lti.core.model.setup

import kotlinx.serialization.Serializable

/**
 * Aggregate persisted record of toolchain setup progress, surfaced as history
 * (never as a live-verification shortcut — see contracts/setup-environment.md).
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
) {
    public fun isStageCompleted(stageName: String): Boolean = stageName in completedStages
}
