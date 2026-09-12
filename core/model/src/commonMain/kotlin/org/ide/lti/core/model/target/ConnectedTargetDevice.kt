package org.ide.lti.core.model.target

/**
 * A physical or emulated device currently reachable over ADB/Fastboot.
 */
public data class ConnectedTargetDevice(
    val serial: String,
    val modelName: String,
    val state: AdbDeviceState,
    val connectionType: DeviceConnectionType,
    val androidVersion: String? = null,
) {
    /** Compact label for space-constrained pills, e.g. "REDMAGIC Astra (USB)". */
    public val shortDisplayName: String
        get() = "$modelName (${connectionType.displayName})"

    /** Short state label for a trailing badge, e.g. "online". */
    public val stateBadge: String
        get() = state.displayName.lowercase()
}
