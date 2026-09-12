package org.ide.lti.core.model.target

/**
 * How a connected device is reachable (USB ADB/Fastboot vs. wireless ADB).
 */
public enum class DeviceConnectionType(public val displayName: String, public val iconEmoji: String) {
    USB("USB", "🔌"),
    WIRELESS("Wireless", "📶"),
}
