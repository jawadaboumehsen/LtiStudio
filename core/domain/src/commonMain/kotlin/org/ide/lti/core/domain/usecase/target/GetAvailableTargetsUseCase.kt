package org.ide.lti.core.domain.usecase.target

import kotlinx.coroutines.flow.Flow
import org.ide.lti.core.domain.repository.target.TargetRepository
import org.ide.lti.core.model.target.TargetDevice

/**
 * Use case to observe all available target device profiles.
 */
class GetAvailableTargetsUseCase(
    private val repository: TargetRepository,
) {
    operator fun invoke(): Flow<List<TargetDevice>> = repository.getAvailableTargets()
}
