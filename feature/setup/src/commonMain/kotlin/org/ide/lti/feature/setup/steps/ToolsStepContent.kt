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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.feature.setup.SetupActivityState
import org.ide.lti.feature.setup.SetupUiState
import org.ide.lti.feature.setup.components.SetupActivityPanel
import org.ide.lti.feature.setup.tools.ToolInspector
import org.ide.lti.feature.setup.tools.ToolsPageState
import org.ide.lti.feature.setup.tools.ToolsTable

/**
 * Tools destination content for Setup (US3 — Tools and activity, P2).
 *
 * Capabilities:
 * - Unified 009 Tools page hosting [ToolsTable] and [ToolInspector] as bounded siblings, driven by [ToolsPageState].
 * - Bounded activity stream drawer anchored to bottom of tools list.
 * - Strict design-system token discipline (zero raw dp, color, or alpha literals).
 */
@Composable
fun ToolsStepContent(
    state: SetupUiState,
    modifier: Modifier = Modifier,
    activityState: SetupActivityState? = null,
    toolsPageState: ToolsPageState? = null,
    scrollState: ScrollState = rememberScrollState(),
) {
    val coroutineScope = rememberCoroutineScope()
    val fallbackActivityState = remember { SetupActivityState(scope = coroutineScope) }
    val resolvedActivityState = activityState ?: fallbackActivityState

    if (toolsPageState != null) {
        val rows by toolsPageState.rows.collectAsState()
        val selectedGroup by toolsPageState.selectedGroup.collectAsState()
        val isInspectorOpen by toolsPageState.isInspectorOpen.collectAsState()

        Row(
            modifier =
            modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Column(
                modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(scrollState)
                    .padding(
                        start = Spacing.ScreenHorizontal,
                        end = if (isInspectorOpen) Spacing.None else Spacing.ScreenHorizontal,
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
                        text = "Browse, configure, and rebuild native Android ROM engineering tools",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Unified Tools Table (bounded LazyColumn)
                ToolsTable(
                    rows = rows,
                    selectedGroupId = selectedGroup,
                    onSelectGroup = { toolsPageState.selectGroup(it) },
                    onMoveUp = { toolsPageState.selectPreviousGroup() },
                    onMoveDown = { toolsPageState.selectNextGroup() },
                    onOpenInspector = { toolsPageState.openInspector() },
                    onCloseInspector = { toolsPageState.closeInspector() },
                    modifier = Modifier.fillMaxWidth(),
                )

                // Bounded Activity & Execution Terminal Output
                SetupActivityPanel(
                    activityState = resolvedActivityState,
                    title = "Activity (live log)",
                    isRunning = state.toolchainSetupState.isRunning,
                    defaultExpanded = false,
                )
            }

            if (isInspectorOpen) {
                ToolInspector(
                    state = toolsPageState,
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
}
