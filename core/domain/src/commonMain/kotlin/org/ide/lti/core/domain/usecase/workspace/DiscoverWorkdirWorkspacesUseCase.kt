package org.ide.lti.core.domain.usecase.workspace

import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.model.workspace.RecentProject

/**
 * Use case to scan the WSL work directory for physical workspaces not yet in the recent list.
 */
class DiscoverWorkdirWorkspacesUseCase(
    private val repository: WorkspaceRepository,
) {
    suspend operator fun invoke(basePath: String? = null): List<RecentProject> =
        repository.discoverWorkspacesInWorkDir(basePath)
}
