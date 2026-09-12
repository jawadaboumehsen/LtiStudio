package org.ide.lti.core.model.target

/**
 * Outcome of a device-directed ADB/Fastboot operation (reboot, pair, connect).
 */
public data class DeviceOperationResult(
    val isSuccess: Boolean,
    val message: String,
    val output: String = "",
)
