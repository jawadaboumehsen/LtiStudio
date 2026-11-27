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
 * A physical or emulated device currently reachable over ADB/Fastboot.
 */
data class ConnectedTargetDevice(
    val serial: String,
    val modelName: String,
    val state: AdbDeviceState,
    val connectionType: DeviceConnectionType,
    val androidVersion: String? = null,
) {
    /** Compact label for space-constrained pills, e.g. "REDMAGIC Astra (USB)". */
    val shortDisplayName: String
        get() = "$modelName (${connectionType.displayName})"

    /** Short state label for a trailing badge, e.g. "online". */
    val stateBadge: String
        get() = state.displayName.lowercase()
}
