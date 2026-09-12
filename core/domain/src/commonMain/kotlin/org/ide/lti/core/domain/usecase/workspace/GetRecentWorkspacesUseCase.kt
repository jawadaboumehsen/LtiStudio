package org.ide.lti.core.domain.usecase.workspace

import kotlinx.coroutines.flow.Flow
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.model.workspace.RecentProject

/**
 * Use case to observe recently opened workspace projects.
 */
class GetRecentWorkspacesUseCase(
    private val repository: WorkspaceRepository,
) {
    operator fun invoke(): Flow<List<RecentProject>> = repository.getRecentProjects()
}
