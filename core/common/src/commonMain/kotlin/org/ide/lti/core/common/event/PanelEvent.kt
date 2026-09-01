/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.common.event

/**
 * Events that can be published and consumed by panels.
 * Enables inter-panel communication via event bus.
 */
sealed class PanelEvent {
    /**
     * A file was opened in the main panel.
     */
    data class FileOpened(val filePath: String) : PanelEvent()

    /**
     * The active tab changed in a panel.
     */
    data class TabChanged(val panelId: PanelId, val tabIndex: Int) : PanelEvent()

    /**
     * A panel's visibility changed.
     */
    data class PanelVisibilityChanged(val panelId: PanelId, val visible: Boolean) : PanelEvent()

    /**
     * Panel focus changed (user switched to another panel).
     */
    data class PanelFocusChanged(val panelId: PanelId) : PanelEvent()

    /**
     * A new tab was added to a panel.
     */
    data class TabAdded(val panelId: PanelId, val tabId: String) : PanelEvent()

    /**
     * A tab was closed in a panel.
     */
    data class TabClosed(val panelId: PanelId, val tabId: String) : PanelEvent()

    /**
     * Panel size/layout changed.
     */
    data class LayoutChanged(val panelId: PanelId, val newSize: Float) : PanelEvent()
}
