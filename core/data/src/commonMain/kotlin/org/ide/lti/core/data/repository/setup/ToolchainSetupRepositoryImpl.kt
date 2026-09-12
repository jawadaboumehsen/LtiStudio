package org.ide.lti.core.data.repository.setup

import kotlinx.coroutines.flow.Flow
import org.ide.lti.core.datastore.ToolchainPreferencesDataSource
import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
import org.ide.lti.core.model.setup.PersistedSubmoduleState
import org.ide.lti.core.model.setup.PersistedToolState
import org.ide.lti.core.model.setup.ToolchainPersistenceState

/**
 * Thin [ToolchainSetupRepository] wrapper over [ToolchainPreferencesDataSource].
 */
public class ToolchainSetupRepositoryImpl(
    private val preferencesDataSource: ToolchainPreferencesDataSource,
) : ToolchainSetupRepository {
    override val toolchainState: Flow<ToolchainPersistenceState> = preferencesDataSource.toolchainState

    override val currentState: ToolchainPersistenceState get() = preferencesDataSource.currentToolchainState

    override suspend fun saveToolchainState(state: ToolchainPersistenceState) =
        preferencesDataSource.saveToolchainState(state)

    override suspend fun updateTool(toolId: String, transform: (PersistedToolState) -> PersistedToolState) =
        preferencesDataSource.updateTool(toolId, transform)

    override suspend fun updateSubmodule(
        name: String,
        transform: (PersistedSubmoduleState) -> PersistedSubmoduleState,
    ) = preferencesDataSource.updateSubmodule(name, transform)

    override suspend fun markStageCompleted(stageName: String, completed: Boolean) =
        preferencesDataSource.markStageCompleted(stageName, completed)

    override suspend fun markSetupCompleted(completed: Boolean, timestamp: Long?) =
        preferencesDataSource.markSetupCompleted(completed, timestamp)

    override suspend fun updateDiagnosticsSnapshot(passedIds: Set<String>) =
        preferencesDataSource.updateDiagnosticsSnapshot(passedIds)

    override suspend fun updateAvbKeyState(provisioned: Boolean, keyPath: String?) =
        preferencesDataSource.updateAvbKeyState(provisioned, keyPath)

    override suspend fun clearToolchainState() = preferencesDataSource.clearToolchainState()
}
