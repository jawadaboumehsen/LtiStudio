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
