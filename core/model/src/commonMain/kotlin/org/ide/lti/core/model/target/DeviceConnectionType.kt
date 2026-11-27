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
 * How a connected device is reachable (USB ADB/Fastboot vs. wireless ADB).
 */
enum class DeviceConnectionType(val displayName: String, val iconEmoji: String) {
    USB("USB", "🔌"),
    WIRELESS("Wireless", "📶"),
}
