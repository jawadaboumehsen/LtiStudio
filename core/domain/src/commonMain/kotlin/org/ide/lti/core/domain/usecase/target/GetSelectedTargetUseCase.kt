package org.ide.lti.core.domain.usecase.target

import kotlinx.coroutines.flow.Flow
import org.ide.lti.core.domain.repository.target.TargetRepository
import org.ide.lti.core.model.target.TargetDevice

/**
 * Use case to observe the currently selected target device profile.
 */
class GetSelectedTargetUseCase(
    private val repository: TargetRepository,
) {
    operator fun invoke(): Flow<TargetDevice> = repository.getSelectedTarget()
}
