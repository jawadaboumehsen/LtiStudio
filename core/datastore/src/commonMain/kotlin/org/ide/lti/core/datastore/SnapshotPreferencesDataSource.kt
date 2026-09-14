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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ide.lti.core.model.workspace.ConfigurationSnapshot

@Serializable
public data class SnapshotList(
    val schemaVersion: Int = 1,
    val items: List<ConfigurationSnapshot> = emptyList(),
)

/**
 * Thread-safe multiplatform persistent preferences data source for configuration snapshots.
 *
 * Implements strict immutability: saving a snapshot whose ID is already persisted throws
 * [IllegalArgumentException].
 */
public class SnapshotPreferencesDataSource(
    private val settings: Settings = Settings(),
    private val json: Json = defaultJson(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()

    private val _snapshots = MutableStateFlow(readSnapshotsSafe())
    public val snapshots: Flow<List<ConfigurationSnapshot>> = _snapshots.asStateFlow()
    public val currentSnapshots: List<ConfigurationSnapshot> get() = _snapshots.value

    public fun snapshotsForWorkspace(workspaceId: String): Flow<List<ConfigurationSnapshot>> {
        return _snapshots.map { list -> list.filter { it.workspaceId == workspaceId } }
    }

    public suspend fun getSnapshot(id: String): ConfigurationSnapshot? = withContext(ioDispatcher) {
        mutex.withLock {
            _snapshots.value.firstOrNull { it.id == id }
        }
    }

    /**
     * Atomically saves an immutable configuration snapshot.
     * Throws [IllegalArgumentException] if a snapshot with the given ID already exists.
     */
    public suspend fun saveSnapshot(snapshot: ConfigurationSnapshot): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            val current = _snapshots.value
            require(current.none { it.id == snapshot.id }) {
                "ConfigurationSnapshot with id '${snapshot.id}' already exists and is immutable"
            }
            val updated = current + snapshot
            persistSnapshotsInternal(updated)
            _snapshots.value = updated
        }
    }

    public suspend fun clear(): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            settings.remove(KEY_SNAPSHOTS)
            _snapshots.value = emptyList()
        }
    }

    private fun readSnapshotsSafe(): List<ConfigurationSnapshot> {
        val raw = settings.getStringOrNull(KEY_SNAPSHOTS)
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return emptyList()

        return try {
            if (trimmed.startsWith("[")) {
                json.decodeFromString<List<ConfigurationSnapshot>>(trimmed)
            } else {
                try {
                    val wrapper = json.decodeFromString<SnapshotList>(trimmed)
                    wrapper.items
                } catch (_: Exception) {
                    json.decodeFromString<List<ConfigurationSnapshot>>(trimmed)
                }
            }
        } catch (e: Exception) {
            co.touchlab.kermit.Logger.w(e) { "Failed to decode persisted ConfigurationSnapshot store. Discarding." }
            settings.remove(KEY_SNAPSHOTS)
            emptyList()
        }
    }

    private fun persistSnapshotsInternal(snapshots: List<ConfigurationSnapshot>) {
        try {
            val wrapper = SnapshotList(schemaVersion = 1, items = snapshots)
            val encoded = json.encodeToString(wrapper)
            settings[KEY_SNAPSHOTS] = encoded
        } catch (_: Exception) {
            // Keep in-memory
        }
    }

    public companion object {
        public const val KEY_SNAPSHOTS: String = "lti_snapshots_v1"

        public fun defaultJson(): Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
            prettyPrint = false
        }
    }
}
