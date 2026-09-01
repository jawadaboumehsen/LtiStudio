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

import kotlinx.serialization.Serializable

/**
 * Represents the current session state of a workspace.
 * This includes open files, panel states, and layout configuration.
 * Persisted to .idrworkspace file.
 */
@Serializable
data class WorkspaceSession(
    val workspaceId: String,
    val panels: PanelStates = PanelStates(),
    val openFiles: List<OpenFile> = emptyList(),
    val activeFileIndex: Int = -1,
    val layout: LayoutState = LayoutState(),
)

@Serializable
data class PanelStates(
    val leftPanelVisible: Boolean = true,
    val rightPanelVisible: Boolean = true,
    val bottomPanelVisible: Boolean = false,
    val leftPanelWidth: Float = 0.2f,
    val rightPanelWidth: Float = 0.25f,
    val bottomPanelHeight: Float = 0.3f,
    val leftActiveTool: String = "explorer",
    val rightActiveTool: String = "ai-assistant",
    val bottomActiveTool: String = "terminal",
)

@Serializable
data class OpenFile(
    val path: String,
    val cursorLine: Int = 0,
    val cursorColumn: Int = 0,
    val scrollPosition: Int = 0,
)

@Serializable
data class LayoutState(
    val profile: String = "default",
    val customLayouts: Map<String, String> = emptyMap(),
)
