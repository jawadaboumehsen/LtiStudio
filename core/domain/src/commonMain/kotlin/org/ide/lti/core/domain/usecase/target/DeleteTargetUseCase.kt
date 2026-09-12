package org.ide.lti.core.domain.usecase.target

import org.ide.lti.core.domain.repository.target.TargetRepository

/**
 * Use case to delete a target device profile.
 */
class DeleteTargetUseCase(
    private val repository: TargetRepository,
) {
    suspend operator fun invoke(targetId: String): Result<Unit> = repository.deleteTarget(targetId)
}
