package org.ide.lti.core.domain.usecase.build

import kotlinx.coroutines.flow.Flow
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.model.run.BuildRun

public class ObserveRunUseCase(
    private val runRepository: RunRepository
) {
    public fun observeRuns(workspaceId: String): Flow<List<BuildRun>> {
        return runRepository.observeRuns(workspaceId)
    }

    public suspend fun getRun(runId: String): BuildRun? {
        return runRepository.getRun(runId)
    }

    public suspend fun latestActive(workspaceId: String): BuildRun? {
        return runRepository.latestActive(workspaceId)
    }
}
