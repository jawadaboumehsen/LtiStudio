package org.ide.lti.core.model.target

/**
 * Outcome of saving a target device configuration: the persisted [target],
 * the workspace it was applied to (if any), and whether that workspace is
 * currently in sync with this configuration.
 */
public data class TargetConfigurationResult(
    val target: TargetDevice,
    val workspacePath: String?,
    val isSynced: Boolean,
)
