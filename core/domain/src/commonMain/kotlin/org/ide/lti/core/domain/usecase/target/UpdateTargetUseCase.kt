package org.ide.lti.core.domain.usecase.target

import org.ide.lti.core.domain.repository.target.TargetRepository
import org.ide.lti.core.model.target.TargetDevice

/**
 * Use case to update an existing target device profile.
 */
class UpdateTargetUseCase(
    private val repository: TargetRepository,
) {
    suspend operator fun invoke(target: TargetDevice): Result<TargetDevice> = repository.updateTarget(target)
}
