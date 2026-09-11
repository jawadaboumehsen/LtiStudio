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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ide.lti.core.model.target.TargetDevice

@Serializable
public data class TargetList(
    val schemaVersion: Int = 2,
    val items: List<TargetDevice> = emptyList(),
)

/**
 * Thread-safe multiplatform persistent preferences data source for target device profiles
 * and reactive selection persistence.
 *
 * Backed by [Settings] and tolerant [Json] serialization.
 *
 * Principles:
 * - Single Responsibility: Exclusively manages persistent serialization, loading, and
 *   reactive state streams for target profiles and the selected target ID.
 * - Thread Safety: Employs coroutine [Mutex] to eliminate race conditions across I/O threads.
 * - Error Resilience: Recovers automatically from corrupted storage payloads without crashing.
 */
public class TargetPreferencesDataSource(
    private val settings: Settings = Settings(),
    private val json: Json = defaultJson(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()

    private val _targets = MutableStateFlow(readTargetsSafe())
    public val targets: Flow<List<TargetDevice>> = _targets.asStateFlow()
    public val currentTargets: List<TargetDevice> get() = _targets.value

    private val _selectedTargetId = MutableStateFlow(readSelectedTargetIdSafe())
    public val selectedTargetId: Flow<String?> = _selectedTargetId.asStateFlow()
    public val currentSelectedTargetId: String? get() = _selectedTargetId.value

    /**
     * Atomically saves or replaces the complete list of target device profiles.
     */
    public suspend fun saveTargets(targets: List<TargetDevice>): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            persistTargetsInternal(targets)
            _targets.value = targets
        }
    }

    /**
     * Atomically saves the selected target ID.
     */
    public suspend fun saveSelectedTargetId(id: String): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            persistSelectedTargetIdInternal(id)
            _selectedTargetId.value = id
        }
    }

    /**
     * Atomically clears all stored targets and selected target ID.
     */
    public suspend fun clear(): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            settings.remove(KEY_TARGETS)
            settings.remove(KEY_SELECTED_TARGET_ID)
            _targets.value = emptyList()
            _selectedTargetId.value = null
        }
    }

    private fun readTargetsSafe(): List<TargetDevice> {
        val raw = settings.getStringOrNull(KEY_TARGETS)
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return emptyList()

        return try {
            if (trimmed.startsWith("[")) {
                json.decodeFromString<List<TargetDevice>>(trimmed)
            } else {
                try {
                    val wrapper = json.decodeFromString<TargetList>(trimmed)
                    wrapper.items
                } catch (_: Exception) {
                    json.decodeFromString<List<TargetDevice>>(trimmed)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persistTargetsInternal(targets: List<TargetDevice>) {
        try {
            val wrapper = TargetList(schemaVersion = 2, items = targets)
            val encoded = json.encodeToString(wrapper)
            settings[KEY_TARGETS] = encoded
        } catch (_: Exception) {
            // Keep in-memory
        }
    }

    private fun readSelectedTargetIdSafe(): String? {
        val raw = settings.getStringOrNull(KEY_SELECTED_TARGET_ID) ?: return null
        return try {
            json.decodeFromString<String>(raw)
        } catch (_: Exception) {
            null
        }
    }

    private fun persistSelectedTargetIdInternal(id: String) {
        try {
            val encoded = json.encodeToString(id)
            settings[KEY_SELECTED_TARGET_ID] = encoded
        } catch (_: Exception) {
            // Keep in-memory
        }
    }

    public companion object {
        public const val KEY_TARGETS: String = "lti_targets_v1"
        public const val KEY_SELECTED_TARGET_ID: String = "lti_selected_target_id_v1"

        public fun defaultJson(): Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
            prettyPrint = false
        }
    }
}
