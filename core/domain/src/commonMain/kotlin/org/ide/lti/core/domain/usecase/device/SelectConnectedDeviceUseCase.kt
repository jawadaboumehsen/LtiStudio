package org.ide.lti.core.domain.usecase.device

import org.ide.lti.core.domain.repository.device.ConnectedDeviceRepository

/**
 * Use case to select the active connected device by serial (or clear the selection with null).
 */
class SelectConnectedDeviceUseCase(
    private val repository: ConnectedDeviceRepository,
) {
    suspend operator fun invoke(serial: String?) = repository.selectConnectedDevice(serial)
}
