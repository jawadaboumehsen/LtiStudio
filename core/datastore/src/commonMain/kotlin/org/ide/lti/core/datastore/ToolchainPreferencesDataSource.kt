/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.datastore

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ide.lti.core.model.setup.PersistedSubmoduleState
import org.ide.lti.core.model.setup.PersistedToolState
import org.ide.lti.core.model.setup.ToolchainPersistenceState

/**
 * Thread-safe multiplatform persistent preferences data source for toolchain state.
 *
 * Backed by [Settings] and [Json] serialization.
 *
 * Adheres to:
 * - Single Responsibility Principle: Exclusively manages persistence, serialization,
 *   and reactive streams for toolchain state.
 * - Robustness / Error Resilience: Automatically recovers from corrupted JSON payloads
 *   without throwing fatal exceptions.
 * - Thread Safety: Uses [Mutex] to guard concurrent state updates across background coroutines.
 */
public class ToolchainPreferencesDataSource(
    private val settings: Settings = Settings(),
    private val json: Json = defaultJson(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private val _toolchainState = MutableStateFlow(readCurrentStateSafe())

    /**
     * Continuous reactive stream of persisted toolchain state.
     */
    public val toolchainState: Flow<ToolchainPersistenceState> = _toolchainState.asStateFlow()

    /**
     * Synchronous snapshot of the current persisted toolchain state.
     */
    public val currentToolchainState: ToolchainPersistenceState get() = _toolchainState.value

    /**
     * Atomically saves or replaces the complete [ToolchainPersistenceState].
     */
    public suspend fun saveToolchainState(state: ToolchainPersistenceState): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            persistStateInternal(state)
            _toolchainState.value = state
        }
    }

    /**
     * Atomically mutates an individual tool's persisted state.
     */
    public suspend fun updateTool(
        toolId: String,
        transform: (PersistedToolState) -> PersistedToolState,
    ): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            val current = _toolchainState.value
            val existingTool = current.tools[toolId] ?: PersistedToolState(id = toolId, binaryName = toolId)
            val updatedTool = transform(existingTool)
            val updated = current.copy(
                tools = current.tools + (toolId to updatedTool),
                lastVerifiedTimestamp = updatedTool.lastVerifiedTimestamp ?: current.lastVerifiedTimestamp,
            )
            persistStateInternal(updated)
            _toolchainState.value = updated
        }
    }

    /**
     * Atomically mutates an external git submodule's persisted state.
     */
    public suspend fun updateSubmodule(
        name: String,
        transform: (PersistedSubmoduleState) -> PersistedSubmoduleState,
    ): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            val current = _toolchainState.value
            val existing = current.submodules[name] ?: PersistedSubmoduleState(name = name)
            val updatedSub = transform(existing)
            val updated = current.copy(
                submodules = current.submodules + (name to updatedSub),
            )
            persistStateInternal(updated)
            _toolchainState.value = updated
        }
    }

    /**
     * Records a setup stage as completed or pending.
     */
    public suspend fun markStageCompleted(stageName: String, completed: Boolean): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            val current = _toolchainState.value
            val updatedStages = if (completed) {
                current.completedStages + stageName
            } else {
                current.completedStages - stageName
            }
            val updated = current.copy(completedStages = updatedStages)
            persistStateInternal(updated)
            _toolchainState.value = updated
        }
    }

    /**
     * Records overall toolchain setup completion status with timestamp.
     */
    public suspend fun markSetupCompleted(completed: Boolean, timestamp: Long? = null): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            val current = _toolchainState.value
            val updated = current.copy(
                isSetupCompleted = completed,
                lastCompletedTimestamp = if (completed) (timestamp ?: current.lastCompletedTimestamp) else null,
            )
            persistStateInternal(updated)
            _toolchainState.value = updated
        }
    }

    /**
     * Records passing doctor diagnostic item IDs.
     */
    public suspend fun updateDiagnosticsSnapshot(passedIds: Set<String>): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            val current = _toolchainState.value
            val updated = current.copy(passedDiagnosticIds = passedIds)
            persistStateInternal(updated)
            _toolchainState.value = updated
        }
    }

    /**
     * Records AVB 2.0 key provisioning status and path.
     */
    public suspend fun updateAvbKeyState(provisioned: Boolean, keyPath: String?): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            val current = _toolchainState.value
            val updated = current.copy(
                isAvbKeyProvisioned = provisioned,
                avbKeyPath = keyPath,
            )
            persistStateInternal(updated)
            _toolchainState.value = updated
        }
    }

    /**
     * Atomically clears all persisted toolchain state and resets to empty default.
     */
    public suspend fun clearToolchainState(): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            settings.remove(KEY_TOOLCHAIN_PERSISTENCE_STATE)
            val resetState = ToolchainPersistenceState()
            _toolchainState.value = resetState
        }
    }

    private fun readCurrentStateSafe(): ToolchainPersistenceState {
        val raw = settings.getStringOrNull(KEY_TOOLCHAIN_PERSISTENCE_STATE) ?: return ToolchainPersistenceState()
        return try {
            json.decodeFromString<ToolchainPersistenceState>(raw)
        } catch (e: Exception) {
            // Corruption or schema evolution fallback
            ToolchainPersistenceState()
        }
    }

    private fun persistStateInternal(state: ToolchainPersistenceState) {
        try {
            val encoded = json.encodeToString(state)
            settings[KEY_TOOLCHAIN_PERSISTENCE_STATE] = encoded
        } catch (_: Exception) {
            // Logically silent; state will remain in memory
        }
    }

    public companion object {
        public const val KEY_TOOLCHAIN_PERSISTENCE_STATE: String = "lti_toolchain_persistence_state_v1"

        public fun defaultJson(): Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
            prettyPrint = false
        }
    }
}
