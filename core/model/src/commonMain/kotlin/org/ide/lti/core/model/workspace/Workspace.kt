/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.workspace

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

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
)

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
