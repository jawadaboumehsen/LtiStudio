/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.usecase.workspace

import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.workspace.Workspace

/**
 * Use case to open an existing workspace by ID.
 *
 * Delegates to [WorkspaceManager.openWorkspace] when a manager is bound (it already handles
 * session restore and recent-project bookkeeping); falls back to a plain repository lookup when
 * running without a manager (e.g. preview/host mode).
 */
class OpenWorkspaceUseCase(
    private val repository: WorkspaceRepository,
    private val workspaceManager: WorkspaceManager? = null,
) {
    suspend operator fun invoke(workspaceId: String): Result<Workspace> {
        workspaceManager?.let { return it.openWorkspace(workspaceId) }
        val workspace = repository.getWorkspace(workspaceId)
            ?: return Result.failure(IllegalArgumentException("Workspace not found: $workspaceId"))
        return Result.success(workspace)
    }
}
