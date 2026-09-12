package org.ide.lti.core.domain.usecase.device

import org.ide.lti.core.domain.repository.device.ConnectedDeviceRepository

/**
 * Use case to disconnect a wireless-ADB device.
 */
class DisconnectDeviceUseCase(
    private val repository: ConnectedDeviceRepository,
) {
    suspend operator fun invoke(target: String) = repository.disconnectDevice(target)
}
