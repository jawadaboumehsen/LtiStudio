/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
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
