/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.panels

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ide.lti.core.common.event.PanelEvent
import org.ide.lti.core.common.event.PanelEventBus
import org.ide.lti.core.common.event.PanelId
import org.ide.lti.core.model.panels.PanelState
import org.ide.lti.core.model.panels.TabState

/**
 * Manages the state and operations of all panels in the workspace.
 * Handles visibility, tabs, layout, and event communication.
 */
class PanelManager(
    private val eventBus: PanelEventBus,
    private val coroutineScope: CoroutineScope,
) {
    private val _panels = MutableStateFlow(initializeDefaultPanels())
    val panels: StateFlow<Map<PanelId, PanelState>> = _panels.asStateFlow()

    private val _focusedPanel = MutableStateFlow<PanelId?>(PanelId.MAIN)
    val focusedPanel: StateFlow<PanelId?> = _focusedPanel.asStateFlow()

    /**
     * Get state of a specific panel.
     */
    fun getPanelState(panelId: PanelId): PanelState? {
        return _panels.value[panelId]
    }

    /**
     * Set panel visibility.
     */
    fun setPanelVisibility(panelId: PanelId, visible: Boolean) {
        _panels.update { panels ->
            val panel = panels[panelId] ?: return@update panels
            panels + (panelId to panel.copy(visible = visible))
        }
        coroutineScope.launch {
            eventBus.publish(PanelEvent.PanelVisibilityChanged(panelId, visible))
        }
    }

    /**
     * Toggle panel visibility.
     */
    fun togglePanelVisibility(panelId: PanelId) {
        val current = getPanelState(panelId)?.visible ?: true
        setPanelVisibility(panelId, !current)
    }

    /**
     * Set panel size (0.0 to 1.0).
     */
    fun setPanelSize(panelId: PanelId, size: Float) {
        _panels.update { panels ->
            val panel = panels[panelId] ?: return@update panels
            panels + (panelId to panel.copy(size = size.coerceIn(0.1f, 0.9f)))
        }
        coroutineScope.launch {
            eventBus.publish(PanelEvent.LayoutChanged(panelId, size))
        }
    }

    /**
     * Add a tab to a panel.
     */
    fun addTab(panelId: PanelId, tab: TabState) {
        _panels.update { panels ->
            val panel = panels[panelId] ?: return@update panels
            val newTabs = panel.tabs + tab
            panels + (
                panelId to panel.copy(
                    tabs = newTabs,
                    // Select newly added tab
                    activeTabIndex = newTabs.size - 1,
                )
                )
        }
        coroutineScope.launch {
            eventBus.publish(PanelEvent.TabAdded(panelId, tab.id))
        }
    }

    /**
     * Remove a tab from a panel.
     */
    fun removeTab(panelId: PanelId, tabId: String) {
        _panels.update { panels ->
            val panel = panels[panelId] ?: return@update panels
            val newTabs = panel.tabs.filterNot { it.id == tabId }
            panels + (
                panelId to panel.copy(
                    tabs = newTabs,
                    activeTabIndex = (panel.activeTabIndex).coerceIn(0, (newTabs.size - 1).coerceAtLeast(0)),
                )
                )
        }
        coroutineScope.launch {
            eventBus.publish(PanelEvent.TabClosed(panelId, tabId))
        }
    }

    /**
     * Set active tab in a panel.
     */
    fun setActiveTab(panelId: PanelId, tabIndex: Int) {
        _panels.update { panels ->
            val panel = panels[panelId] ?: return@update panels
            if (tabIndex !in panel.tabs.indices) return@update panels
            panels + (panelId to panel.copy(activeTabIndex = tabIndex))
        }
        coroutineScope.launch {
            eventBus.publish(PanelEvent.TabChanged(panelId, tabIndex))
        }
    }

    /**
     * Set focused panel (user clicked on it).
     */
    fun setFocusedPanel(panelId: PanelId) {
        _focusedPanel.value = panelId
        coroutineScope.launch {
            eventBus.publish(PanelEvent.PanelFocusChanged(panelId))
        }
    }

    /**
     * Restore panels state from saved configuration.
     */
    fun restorePanelsState(panelsState: Map<PanelId, PanelState>) {
        _panels.value = panelsState
    }

    private fun initializeDefaultPanels(): Map<PanelId, PanelState> {
        return mapOf(
            PanelId.LEFT to PanelState(
                id = PanelId.LEFT,
                visible = true,
                tabs = listOf(
                    TabState("explorer", "Explorer", "explorer"),
                    TabState("search", "Search", "search"),
                ),
                activeTabIndex = 0,
            ),
            PanelId.MAIN to PanelState(
                id = PanelId.MAIN,
                visible = true,
                tabs = emptyList(),
            ),
            PanelId.RIGHT to PanelState(
                id = PanelId.RIGHT,
                visible = true,
                tabs = listOf(
                    TabState("ai-assistant", "AI Assistant", "ai-assistant"),
                    TabState("inspector", "Inspector", "inspector"),
                ),
                activeTabIndex = 0,
            ),
            PanelId.BOTTOM to PanelState(
                id = PanelId.BOTTOM,
                visible = false,
                tabs = listOf(
                    TabState("terminal", "Terminal", "terminal"),
                    TabState("logs", "Logs", "logs"),
                ),
                activeTabIndex = 0,
            ),
        )
    }
}
