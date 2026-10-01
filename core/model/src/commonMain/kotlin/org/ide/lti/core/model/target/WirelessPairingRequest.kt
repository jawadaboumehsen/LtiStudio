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
 * Request to pair with a device over wireless ADB (Android 11+ pairing code flow).
 */
public data class WirelessPairingRequest(
    val host: String,
    val port: Int,
    val pairingCode: String,
)
