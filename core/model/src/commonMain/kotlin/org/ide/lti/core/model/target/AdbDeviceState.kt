package org.ide.lti.core.model.target

/**
 * Connectivity state of a physical or emulated ADB/Fastboot device.
 */
public enum class AdbDeviceState {
    ONLINE,
    OFFLINE,
    UNAUTHORIZED,
    FASTBOOT,
    RECOVERY,
    SIDELOAD,
}
