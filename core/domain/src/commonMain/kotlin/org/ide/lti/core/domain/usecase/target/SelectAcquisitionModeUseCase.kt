package org.ide.lti.core.domain.usecase.target

import org.ide.lti.core.domain.repository.target.TargetRepository
import org.ide.lti.core.model.target.FirmwareAcquisitionMode
import org.ide.lti.core.model.target.TargetDevice

/**
 * Use case to select the firmware acquisition mode for the selected target device.
 */
class SelectAcquisitionModeUseCase(
    private val repository: TargetRepository,
) {
    suspend operator fun invoke(mode: FirmwareAcquisitionMode): Result<TargetDevice> =
        repository.selectAcquisitionMode(mode)
}
