package org.ide.lti.core.model.target

/**
 * Target boot mode for an ADB/Fastboot reboot operation.
 */
public enum class DeviceRebootMode(public val displayName: String) {
    SYSTEM("System"),
    BOOTLOADER("Bootloader"),
    FASTBOOTD("Fastbootd"),
    RECOVERY("Recovery"),
    SIDELOAD("Sideload"),
    ;

    public companion object {
        public fun fromString(value: String): DeviceRebootMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SYSTEM
    }
}
