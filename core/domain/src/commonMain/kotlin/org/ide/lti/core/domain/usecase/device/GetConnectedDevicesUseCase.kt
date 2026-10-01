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

import kotlinx.coroutines.flow.StateFlow
import org.ide.lti.core.domain.repository.device.ConnectedDeviceRepository
import org.ide.lti.core.model.target.ConnectedTargetDevice

/**
 * Use case to observe currently connected ADB/Fastboot devices.
 */
class GetConnectedDevicesUseCase(
    private val repository: ConnectedDeviceRepository,
) {
    operator fun invoke(): StateFlow<List<ConnectedTargetDevice>> = repository.getConnectedDevices()
}
