package org.ide.lti.core.domain.usecase.target

import org.ide.lti.core.domain.repository.target.TargetRepository
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetRegion

/**
 * Use case to select the active region for the selected target device.
 */
class SelectTargetRegionUseCase(
    private val repository: TargetRepository,
) {
    suspend operator fun invoke(region: TargetRegion): Result<TargetDevice> = repository.selectRegion(region)
}
