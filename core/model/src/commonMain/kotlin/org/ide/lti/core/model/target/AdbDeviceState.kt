package org.ide.lti.core.model.target

/**
 * Connectivity state of a physical or emulated ADB/Fastboot device.
 */
public enum class AdbDeviceState(public val displayName: String) {
    ONLINE("Online"),
    OFFLINE("Offline"),
    UNAUTHORIZED("Unauthorized"),
    FASTBOOT("Fastboot"),
    FASTBOOTD("Fastbootd"),
    RECOVERY("Recovery"),
    SIDELOAD("Sideload"),
}
