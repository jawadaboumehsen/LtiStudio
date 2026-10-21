/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.ide.lti.core.domain.workspace

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import java.util.concurrent.ConcurrentHashMap

/** Pending editor values survive the debounce and can be committed by Save or Run's caller. */
public class PendingWorkspaceEdits(
    private val save:
    suspend (
        String,
        (ConfigurationSnapshot) -> ConfigurationSnapshot,
    ) -> Result<ConfigurationSnapshot>,
) {
    private class Edit(val workspaceId: String, val change: (ConfigurationSnapshot) -> ConfigurationSnapshot)

    private val pending = ConcurrentHashMap<Any, Edit>()
    private val writes = Mutex()

    public fun enqueue(owner: Any, workspaceId: String, change: (ConfigurationSnapshot) -> ConfigurationSnapshot) {
        pending[owner] = Edit(workspaceId, change)
    }

    public fun applyTo(owner: Any, snapshot: ConfigurationSnapshot): ConfigurationSnapshot =
        pending[owner]?.change?.invoke(snapshot) ?: snapshot

    public suspend fun save(owner: Any): Result<Unit> = writes.withLock {
        pending[owner]?.let { commit(owner, it) } ?: Result.success(Unit)
    }

    public suspend fun flush(workspaceId: String): Result<Unit> = writes.withLock {
        var result = Result.success(Unit)
        while (result.isSuccess) {
            val entry = pending.entries.firstOrNull { it.value.workspaceId == workspaceId } ?: break
            result = commit(entry.key, entry.value)
        }
        result
    }

    private suspend fun commit(owner: Any, edit: Edit): Result<Unit> {
        val result = save(edit.workspaceId, edit.change)
        result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        if (result.isSuccess) pending.remove(owner, edit)
        return result.fold({ Result.success(Unit) }, { Result.failure(it) })
    }
}
