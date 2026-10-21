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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.feature.workspace.studio.components.OverviewBreadcrumbHeader
import org.ide.lti.feature.workspace.studio.components.OverviewCallToAction
import org.ide.lti.feature.workspace.studio.components.ReadinessChecklistSection
import org.ide.lti.feature.workspace.studio.components.RecentActivitySection
import org.ide.lti.feature.workspace.studio.components.TargetProfileCard

/**
 * Workspace Overview pane shown when no stage editor is active.
 *
 * Implements the normative Blue Glass Mockup 09 layout:
 * 1. Content Header with title, target summary context, and actions.
 * 2. Two-column body: Left = Build Readiness matrix, Right = Target Profile summary card.
 * 3. Primary CTA row (Configure firmware + status).
 * 4. Recent activity card.
 */
@Composable
public fun WorkspaceOverview(
    state: WorkspaceOverviewUiState,
    modifier: Modifier = Modifier,
    onAction: (WorkspaceActionUi) -> Unit = {},
    onReload: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
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
                .widthIn(max = GlassDimens.OverviewMaxContentWidth)
                .verticalScroll(scrollState)
                .padding(horizontal = Spacing.ExtraLarge, vertical = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Large),
        ) {
            // 1. Content Header: Title, target context pill, and action buttons
            val summary = state.targetSummary
            OverviewBreadcrumbHeader(
                title = state.workspaceTitle,
                subtitle = state.subtitle,
                onReload = onReload,
                onAction = onAction,
                targetLine = listOf(summary.deviceId, summary.channel)
                    .filterIsInstance<DisplayValue.Available>().joinToString(" / ") { it.text },
                targetDetail = (summary.revision as? DisplayValue.Available)?.text
                    ?.let { "Device ${it.replace("rev ", "revision ")}" }.orEmpty(),
            )

            // 2. Two-column body: Build Readiness (left) and Target Card (right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                verticalAlignment = Alignment.Top,
            ) {
                ReadinessChecklistSection(
                    items = state.readinessItems,
                    reviewCount = state.reviewCount,
                    onAction = onAction,
                    modifier = Modifier.weight(1f),
                )

                TargetProfileCard(
                    summary = state.targetSummary,
                    onEditProfile = onEditProfile,
                    modifier = Modifier.width(GlassDimens.OverviewTargetCardWidth),
                )
            }

            // 3. Prominent CTA bar
            OverviewCallToAction(
                buildState = state.buildState,
                nextAction = state.nextAction,
                onAction = onAction,
            )

            // 4. Recent activity feed
            RecentActivitySection(
                activities = state.recentActivity,
                onOpenHistory = onOpenHistory,
                onAction = onAction,
            )
        }
    }
}
