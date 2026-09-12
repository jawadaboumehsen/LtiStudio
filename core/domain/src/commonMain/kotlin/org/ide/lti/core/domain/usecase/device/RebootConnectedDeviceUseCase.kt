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
