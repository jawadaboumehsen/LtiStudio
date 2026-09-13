/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.kashif_e.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.launch
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.feature.setup.SetupActivityState
import org.ide.lti.feature.setup.SetupUiActions
import org.ide.lti.feature.setup.SetupUiState
import org.ide.lti.feature.setup.ToolBrowserState
import org.ide.lti.feature.setup.components.SetupActivityPanel
import org.ide.lti.feature.setup.components.ToolMatrixTable

/**
 * Tools destination content for Setup (US3 — Tools and activity, P2).
 *
 * Capabilities:
 * - Full available width toolchain matrix with debounced search and category filtering.
 * - Virtualized tool rows displaying binary paths, capability tags, core badges, and status indicators.
 * - Per-tool verification (`[ Test ]`) and real recipe rebuild (`[ Rebuild ]`) actions.
 * - Unsupported repair explanations with disabled rebuild button and accessible reasons.
 * - Bounded live execution terminal logs (at most 10,000 lines) with visible dropped-line count,
 *   pause-follow, and Jump to latest transitions.
 * - Keyboard navigation and Ctrl+F search focus integration.
 * - Strict design-system token discipline (zero raw dp, color, or alpha literals).
 */
@Composable
fun ToolsStepContent(
    state: SetupUiState,
    actions: SetupUiActions,
    resolvedBackdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    toolBrowserState: ToolBrowserState? = null,
    activityState: SetupActivityState? = null,
    searchFocusRequester: FocusRequester = remember { FocusRequester() },
    focusSearchOnMount: Boolean = false,
    scrollState: ScrollState = rememberScrollState(),
) {
    val glassColors = GlassTheme.colors
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (focusSearchOnMount) {
            searchFocusRequester.requestFocus()
        }
    }

    val toolsList = if (toolBrowserState != null) {
        val presentations by toolBrowserState.filteredTools.collectAsState()
        presentations.map { it.tool }
    } else {
        state.filteredToolsMatrix.ifEmpty { state.toolchainSetupState.toolsMatrix }
    }

    val currentSearch = if (toolBrowserState != null) {
        val query by toolBrowserState.searchQuery.collectAsState()
        query
    } else {
        state.toolSearchQuery
    }

    val currentCategory = if (toolBrowserState != null) {
        val category by toolBrowserState.selectedCategory.collectAsState()
        category
    } else {
        state.selectedToolCategory
    }

    val fallbackActivityState = remember { SetupActivityState(scope = coroutineScope) }
    val resolvedActivityState = activityState ?: fallbackActivityState


    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .verticalScroll(scrollState)
                .padding(
                    start = Spacing.ScreenHorizontal,
                    end = Spacing.ScreenHorizontal,
                    top = Spacing.Medium,
                    bottom = Spacing.ScreenVertical,
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.SectionGap),
        ) {
            // Header: Title and Description
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Text(
                    text = "Toolchain & Native Binaries",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = glassColors.onGlass,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Browse, test, and rebuild native Android ROM engineering tools compiled for your subsystem",
                    style = MaterialTheme.typography.bodyMedium,
                    color = glassColors.onGlassSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // 35-Tool Interactive Matrix Table
            ToolMatrixTable(
                tools = toolsList,
                searchQuery = currentSearch,
                onSearchQueryChange = { query ->
                    toolBrowserState?.onSearchQueryChange(query)
                    actions.onToolSearchQueryChange(query)
                },
                selectedCategory = currentCategory,
                onSelectCategory = { category ->
                    toolBrowserState?.selectCategory(category)
                    actions.onSelectToolCategory(category)
                },
                onTestTool = { toolId ->
                    if (toolBrowserState != null) {
                        toolBrowserState.launchTestTool(toolId)
                    } else {
                        actions.onTestTool(toolId)
                    }
                },
                onRecompileTool = { toolId ->
                    if (toolBrowserState != null) {
                        toolBrowserState.launchRepairTool(toolId)
                    } else {
                        actions.onRecompileTool(toolId)
                    }
                },
                isBusy = state.toolchainSetupState.isBusy,
                searchFocusRequester = searchFocusRequester,
                backdrop = resolvedBackdrop,
                publishedToolIds = state.toolchainSetupState.publishedToolIds,
            )

            // Bounded Activity & Execution Terminal Output
            SetupActivityPanel(
                activityState = resolvedActivityState,
                title = "Tool Execution & Activity",
                isRunning = state.toolchainSetupState.isRunning,
                defaultExpanded = true,
                backdrop = resolvedBackdrop,
            )
        }
    }
}
