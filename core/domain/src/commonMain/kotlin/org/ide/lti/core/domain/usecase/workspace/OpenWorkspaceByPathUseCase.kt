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

import kotlinx.datetime.Clock
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceType

/**
 * Domain Use Case to open a workspace given an absolute or relative directory path.
 *
 * Adheres to:
 * - Single Responsibility Principle (SRP): Resolves workspace by path, registers persistence,
 *   and synchronizes active workspace session in WorkspaceManager.
 */
class OpenWorkspaceByPathUseCase(
    private val repository: WorkspaceRepository,
    private val workspaceManager: WorkspaceManager? = null,
) {
    suspend operator fun invoke(
        path: String,
        type: WorkspaceType = WorkspaceType.LOCAL,
    ): Result<Workspace> {
        return try {
            val normalized = path.trimEnd('/', '\\')
            val workspaceId = path.hashCode().toString()
            val name = normalized.substringAfterLast('/').substringAfterLast('\\').ifEmpty { path }

            val workspace = Workspace(
                id = workspaceId,
                name = name,
                path = path,
                type = type,
                lastOpened = Clock.System.now(),
            )
            repository.saveWorkspace(workspace)

            val openUseCase = OpenWorkspaceUseCase(repository, workspaceManager)
            val result = openUseCase(workspaceId)
            if (result.isSuccess) {
                result
            } else {
                workspaceManager?.setCurrentWorkspace(workspace)
                Result.success(workspace)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
