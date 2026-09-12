package org.ide.lti.core.domain.usecase.device

import org.ide.lti.core.domain.repository.device.ConnectedDeviceRepository
import org.ide.lti.core.model.target.DeviceOperationResult
import org.ide.lti.core.model.target.WirelessPairingRequest

/**
 * Use case to pair with a device over wireless ADB.
 */
class PairWirelessDeviceUseCase(
    private val repository: ConnectedDeviceRepository,
) {
    suspend operator fun invoke(request: WirelessPairingRequest): Result<DeviceOperationResult> =
        repository.pairWirelessDevice(request)
}
