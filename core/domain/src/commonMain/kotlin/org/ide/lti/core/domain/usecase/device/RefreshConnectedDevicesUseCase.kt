package org.ide.lti.core.domain.usecase.device

import org.ide.lti.core.domain.repository.device.ConnectedDeviceRepository

/**
 * Use case to trigger a fresh scan of connected ADB/Fastboot devices.
 */
class RefreshConnectedDevicesUseCase(
    private val repository: ConnectedDeviceRepository,
) {
    suspend operator fun invoke() = repository.refreshConnectedDevices()
}
