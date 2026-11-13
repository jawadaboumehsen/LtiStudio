/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.workspace

import kotlinx.coroutines.flow.first
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.Workspace

/**
 * A phone's setup carried to its next firmware: the other workspaces of the same phone (device codename), and their
 * assembly, debloat, mods and build settings copied into this one. What belongs to the old firmware stays behind:
 * kernel overrides (hashes of its boot images), image-browser edits (its paths) and partitions kept as shipped (its
 * stock trees).
 */
public class SamePhoneSettings(
    private val workspaces: WorkspaceRepository,
    private val settings: WorkspaceSettingsStore,
) {
    /** The other workspaces whose device has [workspaceId]'s codename. */
    public suspend fun others(workspaceId: String): List<Workspace> {
        val codename = runCatching { settings.device(workspaceId)?.codename }.getOrNull() ?: return emptyList()
        return workspaces.getWorkspaces().first().filter { ws ->
            ws.id != workspaceId && runCatching { settings.device(ws.id)?.codename }.getOrNull() == codename
        }
    }

    /** Copies [from]'s settings into [to] (see the class); fails when [from] has none. */
    public suspend fun carry(from: String, to: String): Result<ConfigurationSnapshot> {
        val source = settings.current(from) ?: return Result.failure(IllegalStateException("$from has no settings"))
        return settings.update(to) { mine -> carried(source, mine) }
    }

    public companion object {
        /** [mine] with [source]'s phone settings, without what belongs to [source]'s firmware. */
        public fun carried(source: ConfigurationSnapshot, mine: ConfigurationSnapshot): ConfigurationSnapshot =
            mine.copy(
                assembly = source.assembly.copy(kernelOverrides = emptyMap(), treeEdits = emptyList()),
                debloat = source.debloat,
                customization = source.customization,
                build = source.build.copy(keepStock = emptySet()),
            )
    }
}
