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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.primitives.GlassSurface
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.model.run.StageId

/**
 * Bounded Activity / Problems / Artifacts Dock.
 */
@Composable
internal fun StudioDock(
    activeTab: StudioDockTab,
    isExpanded: Boolean,
    validationReport: ValidationReport,
    onTabSelect: (StudioDockTab) -> Unit,
    onToggleExpand: () -> Unit,
    onSelectIssue: (StageId?, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        DockTabStrip(
            activeTab = activeTab,
            isExpanded = isExpanded,
            problemCount = validationReport.errors.size,
            onTabSelect = onTabSelect,
            onToggleExpand = onToggleExpand,
        )

        // Bounded Dock Body with independent scrollable LazyColumn
        if (isExpanded) {
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ComponentSize.StudioDockDefaultHeight)
                    .heightIn(min = ComponentSize.StudioDockMinHeight, max = ComponentSize.StudioDockMaxHeight),
                surfaceColor = GlassTheme.colors.canvasSurface,
            ) {
                when (activeTab) {
                    StudioDockTab.PROBLEMS -> DockProblemsTab(validationReport.errors, onSelectIssue)
                    StudioDockTab.ACTIVITY -> DockNoticeTab(
                        text = "ROM Setup Studio session active. Ready for configuration changes.",
                        color = GlassTheme.colors.onGlassSecondary,
                    )
                    StudioDockTab.ARTIFACTS -> DockNoticeTab(
                        text = "Pipeline build artifacts are generated during execution in the Run view.",
                        color = GlassTheme.colors.onGlassMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun DockTabStrip(
    activeTab: StudioDockTab,
    isExpanded: Boolean,
    problemCount: Int,
    onTabSelect: (StudioDockTab) -> Unit,
    onToggleExpand: () -> Unit,
) {
    val glassColors = GlassTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ComponentSize.TabBarHeight)
            .background(glassColors.chromeSurface)
            .padding(horizontal = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            StudioDockTab.entries.forEach { tab ->
                val isSelected = tab == activeTab
                val label = if (tab == StudioDockTab.PROBLEMS) "${tab.title} ($problemCount)" else tab.title

                GlassButton(
                    onClick = { onTabSelect(tab) },
                    variant = if (isSelected) GlassButtonVariant.Primary else GlassButtonVariant.Standard,
                    shape = GlassShapes.Compact,
                    minWidth = Spacing.None,
                    minHeight = ComponentSize.TopBarButtonSize,
                    contentPadding = PaddingValues(horizontal = Spacing.Small, vertical = Spacing.ExtraExtraSmall),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) glassColors.onGlass else glassColors.onGlassSecondary,
                    )
                }
            }
        }

        GlassIconButton(onClick = onToggleExpand, size = ComponentSize.TopBarButtonSize) {
            Icon(
                painter = if (isExpanded) {
                    AppIcons.ChevronDownPainterResource()
                } else {
                    AppIcons.ChevronUpPainterResource()
                },
                contentDescription = if (isExpanded) "Collapse Dock" else "Expand Dock",
                modifier = Modifier.size(IconSize.Small),
                tint = glassColors.onGlassSecondary,
            )
        }
    }
}

@Composable
private fun DockProblemsTab(errors: List<ValidationError>, onSelectIssue: (StageId?, String?) -> Unit) {
    val glassColors = GlassTheme.colors
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(Spacing.Small),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        if (errors.isEmpty()) {
            item(key = "no-problems") {
                Text(
                    text = "No configuration validation problems detected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = glassColors.onGlassMuted,
                )
            }
        } else {
            items(items = errors, key = { "${it.stageId}_${it.objectId}_${it.fieldPath}" }) { error ->
                DockProblemRow(error = error, onSelectIssue = onSelectIssue)
            }
        }
    }
}

@Composable
private fun DockProblemRow(error: ValidationError, onSelectIssue: (StageId?, String?) -> Unit) {
    val glassColors = GlassTheme.colors
    val isError = error.severity == Severity.ERROR
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.Compact)
            .background(glassColors.glassSurface.copy(alpha = AlphaTokens.Faint))
            .clickable { onSelectIssue(error.stageId, error.objectId) }
            .padding(Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Icon(
            painter = if (isError) AppIcons.ClearPainterResource() else AppIcons.InfoPainterResource(),
            contentDescription = null,
            modifier = Modifier.size(IconSize.Small),
            tint = if (isError) glassColors.diagnostics.error else glassColors.diagnostics.warning,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = error.message, style = MaterialTheme.typography.bodySmall, color = glassColors.onGlass)
            Text(
                text = "${error.stageId ?: "Global"} > ${error.objectId ?: "general"} [${error.fieldPath}]",
                style = MaterialTheme.typography.labelSmall,
                color = glassColors.onGlassMuted,
            )
        }
    }
}

@Composable
private fun DockNoticeTab(text: String, color: Color) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(Spacing.Small),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        item(key = "dock-notice") {
            Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
        }
    }
}
