/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.panels

import kotlinx.serialization.Serializable
import org.ide.lti.core.common.event.PanelId

/**
 * Represents the state of a single panel.
 */
@Serializable
data class PanelState(
    val id: PanelId,
    val visible: Boolean = true,
    val tabs: List<TabState> = emptyList(),
    val activeTabIndex: Int = 0,
    // 0.0 to 1.0 (percentage of available space)
    val size: Float = getDefaultSize(id),
) {
    companion object {
        fun getDefaultSize(id: PanelId): Float = when (id) {
            PanelId.LEFT -> 0.2f
            PanelId.RIGHT -> 0.25f
            PanelId.BOTTOM -> 0.3f
            PanelId.MAIN -> 1.0f // Takes remaining space
        }
    }
}

/**
 * Represents a tab within a panel.
 */
@Serializable
data class TabState(
    val id: String,
    val title: String,
    // Identifies which tool/component to display
    val toolId: String,
    val metadata: Map<String, String> = emptyMap(),
)
