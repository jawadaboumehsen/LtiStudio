/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.repository.target

import kotlinx.coroutines.flow.Flow
import org.ide.lti.core.model.target.TargetDevice

/**
 * The device configuration of a workspace, stored in the workspace folder itself ([FILE_NAME], next to
 * `workspace.json` and `config.json`) and never in the app. One workspace has exactly one; it travels with the
 * folder, so a workspace found on disk or opened after a reinstall still has it. A new workspace starts from a
 * *copy* of a catalog template, never a link to it.
 */
public interface WorkspaceDeviceRepository {
    /**
     * The device of [workspaceId], then every later change. Emits null when the folder has no device file. Fails
     * (like [get]) when the file cannot be read, so a caller never mistakes an unreachable file for a missing one.
     */
    public fun observe(workspaceId: String): Flow<TargetDevice?>

    /**
     * The device stored in the folder of [workspaceId], or null when it has none. Throws when the file exists but
     * cannot be read or parsed, or when the build service cannot be reached: that is not "no device".
     */
    public suspend fun get(workspaceId: String): TargetDevice?

    /**
     * Writes [device] into the folder of [workspaceId] and returns what was written. The revision is bumped when
     * the content differs from the stored one; identical content keeps it.
     */
    public suspend fun save(workspaceId: String, device: TargetDevice): TargetDevice

    public companion object {
        public const val FILE_NAME: String = "device.json"
    }
}
