package org.ide.lti.core.domain.repository.setup

import kotlinx.coroutines.flow.Flow
import org.ide.lti.core.model.setup.PersistedSubmoduleState
import org.ide.lti.core.model.setup.PersistedToolState
import org.ide.lti.core.model.setup.ToolchainPersistenceState

/**
 * Repository for persisted toolchain setup progress (history only - see
 * contracts/setup-environment.md; never a substitute for a live verification probe).
 */
public interface ToolchainSetupRepository {
    public val toolchainState: Flow<ToolchainPersistenceState>

    public val currentState: ToolchainPersistenceState

    public suspend fun saveToolchainState(state: ToolchainPersistenceState)

    public suspend fun updateTool(toolId: String, transform: (PersistedToolState) -> PersistedToolState)

    public suspend fun updateSubmodule(name: String, transform: (PersistedSubmoduleState) -> PersistedSubmoduleState)

    public suspend fun markStageCompleted(stageName: String, completed: Boolean)

    public suspend fun markSetupCompleted(completed: Boolean, timestamp: Long? = null)

    public suspend fun updateDiagnosticsSnapshot(passedIds: Set<String>)

    public suspend fun updateAvbKeyState(provisioned: Boolean, keyPath: String?)

    public suspend fun clearToolchainState()
}
