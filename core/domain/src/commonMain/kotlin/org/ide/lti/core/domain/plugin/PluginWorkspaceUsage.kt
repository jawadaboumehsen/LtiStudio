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
    /**
     * `publisher:id@version` → the names of the workspaces whose builds use that version; [ANY_VERSION] → those
     * whose settings could not be read, which may use any version (so none is removed while they are unknown).
     */
    suspend fun usersOf(): Map<String, List<String>> {
        val users = mutableMapOf<String, MutableList<String>>()
        for (workspace in workspaces.getWorkspaces().first()) {
            val snapshot = runCatching { store.current(workspace.id) }.getOrNull()
            if (snapshot == null) {
                users.getOrPut(ANY_VERSION) { mutableListOf() } += workspace.name
                continue
            }
            snapshot.customization.lockfile.entries.forEach { e ->
                users.getOrPut("${e.publisher}:${e.id}@${e.version}") { mutableListOf() } += workspace.name
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
            if (relocked(snapshot, versions, packages) != null) {
                // Worked out again on the snapshot being replaced: an edit made meanwhile is kept, not overwritten.
                var changed = false
                store.update(id) { current ->
                    relocked(current, versions, packages)?.also { changed = true } ?: current
                }.onSuccess { if (changed) moved += name }
            }
        }
        return moved
    }

    /** [snapshot] locked onto [packages], or null when it uses no older version or cannot be locked. */
    private fun relocked(
        snapshot: ConfigurationSnapshot,
        versions: Map<String, String>,
        packages: List<InstalledPackage>,
    ): ConfigurationSnapshot? {
        val customization = snapshot.customization
        val stale = customization.lockfile.entries.any { e ->
            versions["${e.publisher}:${e.id}"]?.let { it != e.version } ?: false
        }
        if (!stale) return null
        // The workspace's own order is kept (independent mods that touch the same files apply as chosen),
        // unless the new versions' dependencies no longer allow it.
        val locked = PluginDependencyResolver.lock(
            customization.enabledPackages,
            packages,
            customization.enabledPackages,
        ).takeIf { it is LockResult.Locked }
            ?: PluginDependencyResolver.lock(customization.enabledPackages, packages, emptyList())
        return (locked as? LockResult.Locked)?.let {
            val ordered = it.lockfile.entries.map { e -> "${e.publisher}:${e.id}" }
            snapshot.copy(customization = customization.copy(enabledPackages = ordered, lockfile = it.lockfile))
        }
    }

    companion object {
        /** [usersOf]'s key for workspaces whose settings could not be read. */
        const val ANY_VERSION: String = "*"
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
