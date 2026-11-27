/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.repository.device

import kotlinx.coroutines.flow.StateFlow
import org.ide.lti.core.model.target.ConnectedTargetDevice
import org.ide.lti.core.model.target.DeviceOperationResult
import org.ide.lti.core.model.target.DeviceRebootMode
import org.ide.lti.core.model.target.WirelessPairingRequest

/**
 * Repository for physical/emulated ADB & Fastboot device connectivity.
 */
interface ConnectedDeviceRepository {
    fun getConnectedDevices(): StateFlow<List<ConnectedTargetDevice>>

    fun getSelectedConnectedDevice(): StateFlow<ConnectedTargetDevice?>

    suspend fun selectConnectedDevice(serial: String?)

    suspend fun refreshConnectedDevices()

    suspend fun rebootConnectedDevice(serial: String, mode: DeviceRebootMode): Result<DeviceOperationResult>

    suspend fun pairWirelessDevice(request: WirelessPairingRequest): Result<DeviceOperationResult>

    suspend fun connectWirelessDevice(host: String, port: Int): Result<DeviceOperationResult>

    suspend fun disconnectDevice(target: String)
}
