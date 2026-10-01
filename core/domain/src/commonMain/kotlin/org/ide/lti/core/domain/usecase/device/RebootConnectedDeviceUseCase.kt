/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.usecase.device

import org.ide.lti.core.domain.repository.device.ConnectedDeviceRepository
import org.ide.lti.core.model.target.DeviceOperationResult
import org.ide.lti.core.model.target.DeviceRebootMode

/**
 * Use case to reboot a connected device into the given mode.
 */
class RebootConnectedDeviceUseCase(
    private val repository: ConnectedDeviceRepository,
) {
    suspend operator fun invoke(serial: String, mode: DeviceRebootMode): Result<DeviceOperationResult> =
        repository.rebootConnectedDevice(serial, mode)
}
