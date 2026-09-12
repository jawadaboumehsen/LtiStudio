package org.ide.lti.core.model.setup

import kotlinx.serialization.Serializable

/**
 * Persisted, per-submodule external-repository sync record.
 */
@Serializable
public data class PersistedSubmoduleState(
    val name: String,
    val isSynced: Boolean = false,
    val commit: String? = null,
)
