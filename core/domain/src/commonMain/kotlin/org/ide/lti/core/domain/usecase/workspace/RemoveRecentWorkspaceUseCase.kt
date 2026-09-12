package org.ide.lti.core.domain.usecase.workspace

import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository

/**
 * Use case to remove a workspace from the recent projects list.
 */
class RemoveRecentWorkspaceUseCase(
    private val repository: WorkspaceRepository,
) {
    suspend operator fun invoke(workspaceId: String) = repository.removeRecentProject(workspaceId)
}
