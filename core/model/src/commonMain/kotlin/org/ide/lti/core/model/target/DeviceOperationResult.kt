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
 * Outcome of a device-directed ADB/Fastboot operation (reboot, pair, connect).
 */
public data class DeviceOperationResult(
    val isSuccess: Boolean,
    val message: String,
    val output: String = "",
)
