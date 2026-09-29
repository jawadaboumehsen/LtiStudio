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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ide.lti.core.model.workspace.DecodeWarning
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace

@Serializable
public data class WorkspaceList(
    val schemaVersion: Int = 2,
    val items: List<Workspace> = emptyList(),
)

@Serializable
public data class RecentProjectList(
    val schemaVersion: Int = 2,
    val items: List<RecentProject> = emptyList(),
)

/**
 * Thread-safe multiplatform persistent preferences data source for workspace state and recents.
 *
 * Backed by [Settings] and tolerant [Json] serialization.
 *
 * Principles:
 * - Single Responsibility: Exclusively manages persistent serialization, loading, and
 *   reactive state streams for workspaces and recent projects.
 * - Thread Safety: Employs coroutine [Mutex] to eliminate race conditions across I/O threads.
 * - Error Resilience: Recovers automatically from corrupted storage payloads without crashing.
 */
public class WorkspacePreferencesDataSource(
    private val settings: Settings = Settings(),
    private val json: Json = defaultJson(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()

    private val _decodeWarnings = MutableSharedFlow<DecodeWarning>(replay = 64, extraBufferCapacity = 64)
    public val decodeWarnings: Flow<DecodeWarning> = _decodeWarnings.asSharedFlow()

    private val _recentProjects = MutableStateFlow(readRecentProjectsSafe())
    public val recentProjects: Flow<List<RecentProject>> = _recentProjects.asStateFlow()
    public val currentRecentProjects: List<RecentProject> get() = _recentProjects.value

    private val _workspaces = MutableStateFlow(readWorkspacesSafe())
    public val workspaces: Flow<List<Workspace>> = _workspaces.asStateFlow()
    public val currentWorkspaces: List<Workspace> get() = _workspaces.value

    /**
     * Atomically adds or promotes a project in the recent list.
     */
    public suspend fun addRecentProject(
        project: RecentProject,
        maxItems: Int = 20,
    ): Unit = withContext(ioDispatcher) {
        if (isSystemStaging(project)) return@withContext
        mutex.withLock {
            val current = _recentProjects.value.toMutableList()
            current.removeAll { it.workspaceId == project.workspaceId || it.path == project.path }
            current.add(0, project)
            val trimmed = if (current.size > maxItems) current.take(maxItems) else current
            persistRecentProjectsInternal(trimmed)
            _recentProjects.value = trimmed
        }
    }

    /**
     * Atomically removes a project from the recent projects list by workspaceId.
     */
    public suspend fun removeRecentProject(workspaceId: String): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            val current = _recentProjects.value.filterNot { it.workspaceId == workspaceId }
            persistRecentProjectsInternal(current)
            _recentProjects.value = current
        }
    }

    /**
     * Atomically saves or replaces the complete list of recent projects.
     */
    public suspend fun saveRecentProjects(projects: List<RecentProject>): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            persistRecentProjectsInternal(projects)
            _recentProjects.value = projects
        }
    }

    /**
     * Atomically clears all recent projects.
     */
    public suspend fun clearRecentProjects(): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            settings.remove(KEY_RECENT_PROJECTS)
            _recentProjects.value = emptyList()
        }
    }

    /**
     * Retrieves a workspace by its unique ID.
     */
    public suspend fun getWorkspace(id: String): Workspace? = withContext(ioDispatcher) {
        mutex.withLock {
            _workspaces.value.firstOrNull { it.id == id }
        }
    }

    /**
     * Atomically saves or updates a workspace record.
     */
    public suspend fun saveWorkspace(workspace: Workspace): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            val current = _workspaces.value.toMutableList()
            current.removeAll { it.id == workspace.id }
            current.add(0, workspace)
            persistWorkspacesInternal(current)
            _workspaces.value = current
        }
    }

    /**
     * Atomically deletes a workspace by its ID.
     */
    public suspend fun deleteWorkspace(id: String): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            val current = _workspaces.value.filterNot { it.id == id }
            persistWorkspacesInternal(current)
            _workspaces.value = current
        }
    }

    /**
     * Atomically clears all stored workspaces.
     */
    public suspend fun clearWorkspaces(): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            settings.remove(KEY_WORKSPACES)
            _workspaces.value = emptyList()
        }
    }

    private fun handleDecodeFailure(key: String, raw: String, message: String) {
        val timestamp = System.currentTimeMillis()
        val quarantineKey = "$key.quarantine.$timestamp"
        try {
            settings[quarantineKey] = raw
        } catch (_: Exception) {}
        val warning = DecodeWarning(
            key = key,
            quarantineKey = quarantineKey,
            message = message,
            timestampEpochMs = timestamp,
        )
        _decodeWarnings.tryEmit(warning)
    }

    private fun readRecentProjectsSafe(): List<RecentProject> {
        val raw = settings.getStringOrNull(KEY_RECENT_PROJECTS)
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return emptyList()

        return try {
            val items = if (trimmed.startsWith("[")) {
                json.decodeFromString<List<RecentProject>>(trimmed)
            } else {
                try {
                    val wrapper = json.decodeFromString<RecentProjectList>(trimmed)
                    wrapper.items
                } catch (_: Exception) {
                    json.decodeFromString<List<RecentProject>>(trimmed)
                }
            }
            val filtered = items.filterNot { isSystemStaging(it) }
            if (filtered.size != items.size) {
                persistRecentProjectsInternal(filtered)
            }
            filtered
        } catch (_: Exception) {
            handleDecodeFailure(
                key = KEY_RECENT_PROJECTS,
                raw = raw.orEmpty(),
                message = "Recent projects could not be loaded; a backup was kept",
            )
            emptyList()
        }
    }

    private fun persistRecentProjectsInternal(projects: List<RecentProject>) {
        try {
            val wrapper = RecentProjectList(schemaVersion = 2, items = projects)
            val encoded = json.encodeToString(wrapper)
            settings[KEY_RECENT_PROJECTS] = encoded
        } catch (_: Exception) {
            // Keep in-memory
        }
    }

    private fun readWorkspacesSafe(): List<Workspace> {
        val raw = settings.getStringOrNull(KEY_WORKSPACES)
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return emptyList()

        return try {
            if (trimmed.startsWith("[")) {
                json.decodeFromString<List<Workspace>>(trimmed)
            } else {
                try {
                    val wrapper = json.decodeFromString<WorkspaceList>(trimmed)
                    wrapper.items
                } catch (_: Exception) {
                    json.decodeFromString<List<Workspace>>(trimmed)
                }
            }
        } catch (_: Exception) {
            handleDecodeFailure(
                key = KEY_WORKSPACES,
                raw = raw.orEmpty(),
                message = "Workspaces could not be loaded; a backup was kept",
            )
            emptyList()
        }
    }

    private fun persistWorkspacesInternal(workspaces: List<Workspace>) {
        try {
            val wrapper = WorkspaceList(schemaVersion = 2, items = workspaces)
            val encoded = json.encodeToString(wrapper)
            settings[KEY_WORKSPACES] = encoded
        } catch (_: Exception) {
            // Keep in-memory
        }
    }

    public companion object {
        public const val KEY_RECENT_PROJECTS: String = "lti_recent_projects_v1"
        public const val KEY_WORKSPACES: String = "lti_workspaces_v1"

        public fun defaultJson(): Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
            prettyPrint = false
        }

        public fun isSystemStaging(project: RecentProject): Boolean {
            if (project.workspaceId in listOf("ltirom_staging_workdir", "ltirom_workdir")) return true
            return isSystemStagingPath(project.path)
        }

        public fun isSystemStagingPath(path: String): Boolean {
            val normalized = path.trim().trimEnd('/', '\\').replace('\\', '/')
            if (normalized in listOf("~/LtiRomWorkDir", "/home/lti/LtiRomWorkDir", "LtiRomWorkDir")) return true
            val isWorkDirRoot = normalized.endsWith("/LtiRomWorkDir") || normalized.endsWith("LtiRomWorkDir")
            val isSubTargetOrProject = normalized.contains("/target/") || normalized.contains("/projects/")
            return isWorkDirRoot && !isSubTargetOrProject
        }
    }
}
