package org.ide.lti.core.model.target

/**
 * A single target-configuration validation failure.
 */
public data class TargetValidationError(
    val field: String,
    val message: String,
)
