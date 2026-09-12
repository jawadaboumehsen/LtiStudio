package org.ide.lti.core.model.setup

import kotlinx.serialization.Serializable

/**
 * Persisted, per-tool compilation/verification record.
 */
@Serializable
public data class PersistedToolState(
    val id: String,
    val binaryName: String,
    val isCompiled: Boolean = false,
    val isVerified: Boolean = false,
    val binaryPath: String? = null,
    val version: String? = null,
    val lastVerifiedTimestamp: Long? = null,
)
