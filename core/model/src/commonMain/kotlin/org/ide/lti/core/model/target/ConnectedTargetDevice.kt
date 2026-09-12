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
)
