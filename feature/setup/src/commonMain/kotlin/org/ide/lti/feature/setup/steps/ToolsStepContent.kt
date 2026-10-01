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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.component.layout.IdeSubMenuItem
import org.ide.lti.core.designsystem.component.layout.IdeSubMenuTabBar
import org.ide.lti.core.designsystem.component.layout.resolveNavIcon
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.feature.setup.SetupActivityState
import org.ide.lti.feature.setup.SetupUiActions
import org.ide.lti.feature.setup.SetupUiState
import org.ide.lti.feature.setup.ToolNavPackage
import org.ide.lti.feature.setup.components.SetupActivityPanel
import org.ide.lti.feature.setup.components.ToolMatrixTable
import org.ide.lti.feature.setup.versions.ToolVersionsState
import org.ide.lti.feature.setup.versions.components.ToolVersionsInspector
import org.ide.lti.feature.setup.versions.components.ToolVersionsSection

/**
 * Tools destination content for Setup (US3 — Tools and activity, P2).
 *
 * Capabilities:
 * - Full available width toolchain matrix with debounced search and category filtering.
 * - Virtualized tool rows displaying binary paths, capability tags, core badges, and status indicators.
 * - Activity stream drawer anchored to bottom of tools list.
 * - Keyboard navigation and Ctrl+F search focus integration.
 * - Strict design-system token discipline (zero raw dp, color, or alpha literals).
 */
@Composable
fun ToolsStepContent(
    state: SetupUiState,
    actions: SetupUiActions,
    modifier: Modifier = Modifier,
    activityState: SetupActivityState? = null,
    toolVersionsState: ToolVersionsState? = null,
    searchFocusRequester: FocusRequester = remember { FocusRequester() },
    focusSearchOnMount: Boolean = false,
    scrollState: ScrollState = rememberScrollState(),
) {
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (focusSearchOnMount) {
            searchFocusRequester.requestFocus()
        }
    }

    val fallbackActivityState = remember { SetupActivityState(scope = coroutineScope) }
    val resolvedActivityState = activityState ?: fallbackActivityState

    val editDraft = toolVersionsState?.draft?.collectAsState()?.value

    // The page and the version inspector are siblings: each gets a bounded height and scrolls on its own.
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .fillMaxHeight(),
    ) {
        Column(
            modifier =
            Modifier
                .weight(1f)
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
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Browse, test, and rebuild native Android ROM engineering tools compiled for your subsystem",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val toolNavItems =
                remember {
                    ToolNavPackage.entries.map { pkg ->
                        IdeSubMenuItem(
                            id = pkg.id,
                            label = pkg.label,
                            icon = resolveNavIcon(pkg.id),
                        )
                    }
                }
            IdeSubMenuTabBar(
                items = toolNavItems,
                selectedItemId = state.toolSelection.navPackage.id,
                onSelectItem = { navId ->
                    ToolNavPackage.find(navId)?.let(actions.onSelectToolPackage)
                },
                modifier = Modifier.testTag("ToolsSubMenu"),
            )

            // 35-Tool Interactive Matrix Table
            ToolMatrixTable(
                tools = state.filteredToolsMatrix,
                searchQuery = state.toolSearchQuery,
                onSearchQueryChange = actions.onToolSearchQueryChange,
                toolSelection = state.toolSelection,
                onSelectToolCategory = actions.onSelectToolCategory,
                onTestTool = actions.onTestTool,
                onRecompileTool = actions.onRecompileTool,
                isBusy = state.toolchainSetupState.isBusy,
                searchFocusRequester = searchFocusRequester,
                publishedToolIds = state.toolchainSetupState.publishedToolIds,
            )

            if (toolVersionsState != null) {
                ToolVersionsSection(state = toolVersionsState)
            }

            // Bounded Activity & Execution Terminal Output
            SetupActivityPanel(
                activityState = resolvedActivityState,
                title = "Tool Execution & Activity",
                isRunning = state.toolchainSetupState.isRunning,
                defaultExpanded = true,
            )
        }

        if (toolVersionsState != null && editDraft != null) {
            ToolVersionsInspector(
                state = toolVersionsState,
                modifier =
                Modifier.padding(
                    end = Spacing.ScreenHorizontal,
                    top = Spacing.Medium,
                    bottom = Spacing.ScreenVertical,
                ),
            )
        }
    }
}
