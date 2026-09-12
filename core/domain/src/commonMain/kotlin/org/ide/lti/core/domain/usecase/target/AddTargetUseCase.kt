package org.ide.lti.core.domain.usecase.target

import org.ide.lti.core.domain.repository.target.TargetRepository
import org.ide.lti.core.model.target.TargetDevice

/**
 * Use case to register a new target device profile.
 */
class AddTargetUseCase(
    private val repository: TargetRepository,
) {
    suspend operator fun invoke(target: TargetDevice): Result<TargetDevice> = repository.addTarget(target)
}
