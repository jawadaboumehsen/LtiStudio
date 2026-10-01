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
