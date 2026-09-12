package org.ide.lti.core.domain.usecase.target

import org.ide.lti.core.domain.repository.target.TargetRepository
import org.ide.lti.core.model.target.TargetDevice

/**
 * Use case to select the active target device profile by ID.
 */
class SelectTargetUseCase(
    private val repository: TargetRepository,
) {
    suspend operator fun invoke(targetId: String): Result<TargetDevice> = repository.selectTarget(targetId)
}
