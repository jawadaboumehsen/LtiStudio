package org.ide.lti.core.domain.usecase.device

import org.ide.lti.core.domain.repository.device.ConnectedDeviceRepository
import org.ide.lti.core.model.target.DeviceOperationResult

/**
 * Use case to connect to an already-paired device over wireless ADB.
 */
class ConnectWirelessDeviceUseCase(
    private val repository: ConnectedDeviceRepository,
) {
    suspend operator fun invoke(host: String, port: Int): Result<DeviceOperationResult> =
        repository.connectWirelessDevice(host, port)
}
