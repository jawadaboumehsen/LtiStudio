/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.IdeShellDimens
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.feature.workspace.studio.components.OverviewBreadcrumbHeader
import org.ide.lti.feature.workspace.studio.components.OverviewCallToAction
import org.ide.lti.feature.workspace.studio.components.ReadinessChecklistSection
import org.ide.lti.feature.workspace.studio.components.RecentActivitySection
import org.ide.lti.feature.workspace.studio.components.TargetProfileCard

/**
 * Workspace Overview pane shown when no stage editor is active.
 *
 * Receives a fully resolved [WorkspaceOverviewUiState] produced by [WorkspaceOverviewMapper]
 * and owned by [StudioViewModel]. The composable is intentionally stateless and reads only
 * from the provided state object.
 */
@Composable
public fun WorkspaceOverview(
    state: WorkspaceOverviewUiState,
    modifier: Modifier = Modifier,
    onAction: (WorkspaceActionUi) -> Unit = {},
    onReload: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("WorkspaceOverview"),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = IdeShellDimens.OverviewMaxContentWidth)
                .verticalScroll(scrollState)
                .padding(horizontal = Spacing.ExtraLarge, vertical = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Large),
        ) {
            // 1. Breadcrumbs, title, and quick reload action
            OverviewBreadcrumbHeader(
                title = state.workspaceTitle,
                subtitle = state.subtitle,
                onReload = onReload,
                colors = colors,
            )

            // 2. Bound Target profile summary card
            TargetProfileCard(
                summary = state.targetSummary,
                onEditProfile = onEditProfile,
                colors = colors,
            )

            // 3. 5-row structured readiness matrix
            ReadinessChecklistSection(
                items = state.readinessItems,
                reviewCount = state.reviewCount,
                onAction = onAction,
                colors = colors,
            )

            // 4. Prominent CTA bar
            OverviewCallToAction(
                buildState = state.buildState,
                nextAction = state.nextAction,
                onAction = onAction,
                colors = colors,
            )

            // 5. Recent activity feed or empty state
            RecentActivitySection(
                activities = state.recentActivity,
                onOpenHistory = onOpenHistory,
                onAction = onAction,
                colors = colors,
            )
        }
    }
}
