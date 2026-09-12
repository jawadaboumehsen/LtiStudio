package org.ide.lti.core.domain.usecase.device

import kotlinx.coroutines.flow.StateFlow
import org.ide.lti.core.domain.repository.device.ConnectedDeviceRepository
import org.ide.lti.core.model.target.ConnectedTargetDevice

/**
 * Use case to observe the currently selected connected device.
 */
class GetSelectedConnectedDeviceUseCase(
    private val repository: ConnectedDeviceRepository,
) {
    operator fun invoke(): StateFlow<ConnectedTargetDevice?> = repository.getSelectedConnectedDevice()
}
