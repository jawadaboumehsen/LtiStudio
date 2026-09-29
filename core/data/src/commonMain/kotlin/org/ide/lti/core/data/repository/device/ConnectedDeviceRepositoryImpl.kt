/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.repository.device

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ide.lti.core.domain.repository.device.ConnectedDeviceRepository
import org.ide.lti.core.model.target.ConnectedTargetDevice
import org.ide.lti.core.model.target.DeviceOperationResult
import org.ide.lti.core.model.target.DeviceRebootMode
import org.ide.lti.core.model.target.WirelessPairingRequest

/**
 * No-op [ConnectedDeviceRepository]: always reports zero connected devices.
 *
 * Real ADB/Fastboot device management (host process invocation, wireless pairing) was never
 * committed to this repository's history and has no surviving spec to reconstruct from. This
 * stub exists so the app compiles and runs; `WorkspaceViewModel`'s own in-memory fallback path
 * (used when these use cases are unavailable/no-op) is what the product actually ships with
 * today. See dynamic-prancing-lake.md "Decisions needed" #3.
 */
public class ConnectedDeviceRepositoryImpl : ConnectedDeviceRepository {
    private val devices = MutableStateFlow<List<ConnectedTargetDevice>>(emptyList())
    private val selected = MutableStateFlow<ConnectedTargetDevice?>(null)

    override fun getConnectedDevices(): StateFlow<List<ConnectedTargetDevice>> = devices.asStateFlow()

    override fun getSelectedConnectedDevice(): StateFlow<ConnectedTargetDevice?> = selected.asStateFlow()

    override suspend fun selectConnectedDevice(serial: String?) {
        selected.value = devices.value.find { it.serial == serial }
    }

    override suspend fun refreshConnectedDevices() {
        // No-op: no real device backend to query.
    }

    override suspend fun rebootConnectedDevice(serial: String, mode: DeviceRebootMode): Result<DeviceOperationResult> =
        Result.failure(UnsupportedOperationException("Device management is not implemented."))

    override suspend fun pairWirelessDevice(request: WirelessPairingRequest): Result<DeviceOperationResult> =
        Result.failure(UnsupportedOperationException("Device management is not implemented."))

    override suspend fun connectWirelessDevice(host: String, port: Int): Result<DeviceOperationResult> =
        Result.failure(UnsupportedOperationException("Device management is not implemented."))

    override suspend fun disconnectDevice(target: String) {
        // No-op: no real device backend to disconnect from.
    }
}
