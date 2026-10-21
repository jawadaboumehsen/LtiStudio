/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.plugin

import kotlinx.coroutines.flow.first
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.domain.workspace.WorkspaceSettingsStore
import org.ide.lti.core.model.workspace.ConfigurationSnapshot

/**
 * Which workspaces build with which installed plugin versions (their Patches stage's lockfile), and moving them
 * onto the newest installed versions after an update.
 */
class PluginWorkspaceUsage(private val workspaces: WorkspaceRepository, private val store: WorkspaceSettingsStore) {
    /** `publisher:id@version` → the names of the workspaces whose builds use that version. */
    suspend fun usersOf(): Map<String, List<String>> {
        val users = mutableMapOf<String, MutableList<String>>()
        eachWorkspace { _, name, snapshot ->
            snapshot.customization.lockfile.entries.forEach { e ->
                users.getOrPut("${e.publisher}:${e.id}@${e.version}") { mutableListOf() } += name
            }
        }
        return users
    }

    /**
     * Re-locks every workspace that uses an older version of a plugin than [installed] has, onto the newest
     * versions (the ones the Patches stage offers). Returns the names of the workspaces it moved.
     */
    suspend fun moveToNewest(installed: List<InstalledRecord>): List<String> {
        val newest = newestPerPlugin(installed.filter { !it.revoked && !it.archived }, { it.key }) {
            it.identity.version
        }
        val versions = newest.associate { it.key to it.identity.version }
        val packages = newest.map { it.toInstalledPackage() }
        val moved = mutableListOf<String>()
        eachWorkspace { id, name, snapshot ->
            val customization = snapshot.customization
            val stale = customization.lockfile.entries.any { e ->
                versions["${e.publisher}:${e.id}"]?.let { it != e.version } ?: false
            }
            val locked = if (stale) {
                PluginDependencyResolver.lock(customization.enabledPackages, packages, requestedOrder = emptyList())
            } else {
                null
            }
            if (locked is LockResult.Locked) {
                val ordered = locked.lockfile.entries.map { "${it.publisher}:${it.id}" }
                store.update(id) {
                    it.copy(
                        customization = it.customization.copy(enabledPackages = ordered, lockfile = locked.lockfile),
                    )
                }.onSuccess { moved += name }
            }
        }
        return moved
    }

    private suspend fun eachWorkspace(
        action: suspend (id: String, name: String, snapshot: ConfigurationSnapshot) -> Unit,
    ) {
        for (workspace in workspaces.getWorkspaces().first()) {
            // A workspace whose settings cannot be read uses nothing we can see; it is left alone.
            val snapshot = runCatching { store.current(workspace.id) }.getOrNull() ?: continue
            action(workspace.id, workspace.name, snapshot)
        }
    }
}
