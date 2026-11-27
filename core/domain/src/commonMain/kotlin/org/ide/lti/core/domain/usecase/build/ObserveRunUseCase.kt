/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.usecase.build

import kotlinx.coroutines.flow.Flow
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.model.run.BuildRun

class ObserveRunUseCase(
    private val runRepository: RunRepository
) {
    fun observeRuns(workspaceId: String): Flow<List<BuildRun>> {
        return runRepository.observeRuns(workspaceId)
    }

    suspend fun getRun(runId: String): BuildRun? {
        return runRepository.getRun(runId)
    }

    suspend fun latestActive(workspaceId: String): BuildRun? {
        return runRepository.latestActive(workspaceId)
    }
}
