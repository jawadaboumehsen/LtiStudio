package org.ide.lti.core.domain.usecase.target

import org.ide.lti.core.domain.repository.target.TargetRepository
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetFirmware

/**
 * Use case to select the active firmware build for the selected target device.
 */
class SelectTargetFirmwareUseCase(
    private val repository: TargetRepository,
) {
    suspend operator fun invoke(firmware: TargetFirmware): Result<TargetDevice> = repository.selectFirmware(firmware)
}
