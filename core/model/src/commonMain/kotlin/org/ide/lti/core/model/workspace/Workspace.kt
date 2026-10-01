/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.workspace

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.ide.lti.core.model.target.TargetBinding

/**
 * Identifier for the remote execution environment.
 */
@Serializable
data class EnvironmentId(
    val distro: String,
    val user: String,
    val daemonId: String,
)

/**
 * Represents a workspace/project in the IDR system.
 * A workspace is a directory containing files, configuration, and session state.
 */
@Serializable
data class Workspace(
    val id: String,
    val name: String,
    val path: String,
    val type: WorkspaceType = WorkspaceType.LOCAL,
    val lastOpened: Instant? = null,
    val settings: WorkspaceSettings = WorkspaceSettings(),
    val linuxPath: String? = null,
    val targetBinding: TargetBinding? = null,
    val environmentId: EnvironmentId? = null,
    val layoutVersion: Int = 1,
    val effectiveSnapshotId: String? = null,
) {
    init {
        require(layoutVersion >= 1) { "layoutVersion must be >= 1, but was $layoutVersion" }
    }
}

@Serializable
enum class WorkspaceType {
    LOCAL,
    REMOTE_SSH,
    REMOTE_WSL,
    REMOTE_DOCKER,
    REMOTE_CODECANVAS,
}

@Serializable
data class WorkspaceSettings(
    val theme: String = "dark",
    val fontSize: Int = 14,
    val autoSave: Boolean = true,
    val customSettings: Map<String, String> = emptyMap(),
)
