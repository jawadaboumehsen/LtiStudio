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
