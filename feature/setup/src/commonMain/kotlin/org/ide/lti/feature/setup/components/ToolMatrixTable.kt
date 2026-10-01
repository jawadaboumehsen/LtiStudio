/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import org.ide.lti.core.designsystem.component.actions.GlassSecondaryButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.inputs.TextFieldTrailingSlot
import org.ide.lti.core.designsystem.component.navigation.GlassFilterChipItem
import org.ide.lti.core.designsystem.component.navigation.GlassFilterChipRow
import org.ide.lti.core.designsystem.component.tooltip.GlassTooltipArea
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolCategory
import org.ide.lti.core.domain.setup.ToolComponentItem
import org.ide.lti.feature.setup.ToolBrowserState
import org.ide.lti.feature.setup.ToolSelection

/**
 * 35-Tool Interactive Matrix Table modeled after Android Studio SDK Tools & JetBrains Toolchains.
 *
 * Capabilities:
 * - Category filter chips ("All", "Dynamic Partitions", "Filesystem & Images", etc.)
 * - Instant debounced search filtering by name, binary, and capabilities
 * - Status pills (Ready, Missing, Compiling, Testing)
 * - Per-tool verification (`[ Test ]`) and compilation (`[ Recompile ]`) actions
 * - Monospaced binary paths and capabilities badges
 *
 * Adheres strictly to:
 * - Single Responsibility: Presentation and interactive testing of toolchain components.
 * - Design System Guardrails: 100% token usage (zero raw dp or alpha float literals).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolMatrixTable(
    tools: List<ToolComponentItem>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    toolSelection: ToolSelection,
    onSelectToolCategory: (ToolCategory?) -> Unit,
    onTestTool: (String) -> Unit,
    onRecompileTool: (String) -> Unit,
    modifier: Modifier = Modifier,
    isBusy: Boolean = false,
    searchFocusRequester: FocusRequester? = null,
    publishedToolIds: Set<String> = emptySet(),
) {
    val diagnostics = GlassTheme.diagnosticColors

    val categoryChipItems = remember(toolSelection.navPackage) {
        listOf(GlassFilterChipItem(label = "All", count = -1, value = "ALL")) +
            toolSelection.availableCategories.map { cat ->
                GlassFilterChipItem(
                    label = cat.displayName,
                    count = -1,
                    value = cat.name,
                )
            }
    }

    val readyCount = tools.count { it.status == StepStatus.SUCCESS }
    val totalCount = tools.size

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = Spacing.CardPadding,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            // Top Section: Title, Stats Pill, and Search Filter
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .padding(end = ComponentSize.CommandPaletteMaxWidth + Spacing.Medium),
                    verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    ) {
                        Text(
                            text = "Toolchain Binaries Matrix",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        GlassChip(
                            label = "$readyCount / $totalCount Ready",
                            selected = readyCount == totalCount && totalCount > 0,
                        )
                    }
                    Text(
                        text = "Native Android ROM engineering tools compiled and staged in ~/LtiRomTools/bin",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Search Input Box
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .widthIn(max = ComponentSize.CommandPaletteMaxWidth),
                ) {
                    GlassTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = "Filter tools...",
                        trailing = if (searchQuery.isNotEmpty()) {
                            TextFieldTrailingSlot.Clear { onSearchQueryChange("") }
                        } else {
                            TextFieldTrailingSlot.None
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (searchFocusRequester != null) {
                                    Modifier.focusRequester(searchFocusRequester)
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }

            // Category Filter Strip
            if (toolSelection.shouldShowCategoryChips) {
                GlassFilterChipRow(
                    items = categoryChipItems,
                    selectedValue = toolSelection.category?.name ?: "ALL",
                    onSelect = { stringVal ->
                        val cat = ToolCategory.entries.firstOrNull { it.name == stringVal }
                        onSelectToolCategory(cat)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Tools Item List / Grid
            if (tools.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.ExtraLarge),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No toolchain binaries matching criteria.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = ComponentSize.MaxPanelHeight),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    items(tools, key = { it.id }) { tool ->
                        ToolMatrixItemRow(
                            tool = tool,
                            isBusy = isBusy,
                            isPublished = publishedToolIds.contains(tool.id),
                            onTest = { onTestTool(tool.id) },
                            onRecompile = { onRecompileTool(tool.id) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToolMatrixItemRow(
    tool: ToolComponentItem,
    isBusy: Boolean,
    isPublished: Boolean,
    onTest: () -> Unit,
    onRecompile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val diagnostics = GlassTheme.diagnosticColors

    val isRecipeSupported = ToolBrowserState.isRecipeSupported(tool.id)
    val isRunning = tool.status == StepStatus.RUNNING || isBusy
    // Installed/tested are measured facts, not derived from pipeline status alone -- must match
    // ToolBrowserState.ToolPresentation's definitions exactly so the two never disagree.
    val isInstalled = tool.path != null
    val isTested = tool.status == StepStatus.SUCCESS && tool.lastTested != null

    val statusColor = when (tool.status) {
        StepStatus.SUCCESS -> diagnostics.success
        StepStatus.RUNNING -> MaterialTheme.colorScheme.primary
        StepStatus.WARNING -> diagnostics.warning
        StepStatus.FAILED -> diagnostics.error
        StepStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(GlassShapes.Medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = StrokeWidth.Standard,
                color = if (tool.status == StepStatus.SUCCESS) {
                    diagnostics.success.copy(alpha = AlphaTokens.UltraFaint)
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = GlassShapes.Medium,
            )
            .padding(horizontal = Spacing.Medium, vertical = Spacing.SmallMedium),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .padding(end = ComponentSize.ButtonMinWidth * 3 + Spacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                ToolStatusIndicator(
                    status = tool.status,
                    statusColor = statusColor,
                )

                ToolItemDetails(
                    tool = tool,
                    isInstalled = isInstalled,
                    isTested = isTested,
                    isPublished = isPublished,
                    isRecipeSupported = isRecipeSupported,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }

            ToolItemActionButtons(
                isRunning = isRunning,
                isRecipeSupported = isRecipeSupported,
                onTest = onTest,
                onRecompile = onRecompile,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun ToolStatusIndicator(
    status: StepStatus,
    statusColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(ComponentSize.SegmentedButtonSize)
            .clip(GlassShapes.Small)
            .background(statusColor.copy(alpha = AlphaTokens.Hover))
            .border(
                width = StrokeWidth.Standard,
                color = statusColor.copy(alpha = AlphaTokens.Border),
                shape = GlassShapes.Small,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (status == StepStatus.RUNNING) {
            val isReducedMotion = GlassTheme.reducedMotionPolicy == GlassReducedMotionPolicy.Reduced
            if (isReducedMotion) {
                Text(
                    text = "Checking…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(IconSize.Small),
                    strokeWidth = StrokeWidth.Hairline,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(IconSize.Indicator)
                    .clip(GlassShapes.Capsule)
                    .background(statusColor),
            )
        }
    }
}

@Composable
private fun ToolItemDetails(
    tool: ToolComponentItem,
    isInstalled: Boolean,
    isTested: Boolean,
    isPublished: Boolean,
    isRecipeSupported: Boolean,
    modifier: Modifier = Modifier,
) {
    val diagnostics = GlassTheme.diagnosticColors

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
    ) {
        FlowRow(
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Text(
                text = tool.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = tool.binaryName,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = codeFontFamily(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            if (tool.isCore) {
                GlassChip(
                    label = "Core",
                    selected = false,
                )
            }
            GlassChip(
                label = tool.category.displayName,
                selected = false,
            )
            GlassChip(
                label = if (isInstalled) "Installed" else "Missing",
                selected = isInstalled,
            )
            GlassChip(
                label = if (isTested) "Tested" else "Untested",
                selected = isTested,
            )
            GlassChip(
                label = if (isPublished) "Published" else "Unpublished",
                selected = isPublished,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Text(
                text = tool.path ?: "~/LtiRomTools/bin/${tool.binaryName}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = codeFontFamily(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )

            tool.lastTested?.let { last ->
                Text(
                    text = "· $last",
                    style = MaterialTheme.typography.labelSmall,
                    color = diagnostics.success,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (tool.capabilities.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
            ) {
                tool.capabilities.take(4).forEach { cap ->
                    Text(
                        text = "#$cap",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (!isRecipeSupported) {
            Text(
                text = ToolBrowserState.REPAIR_UNAVAILABLE_REASON,
                style = MaterialTheme.typography.labelSmall,
                color = diagnostics.warning,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ToolItemActionButtons(
    isRunning: Boolean,
    isRecipeSupported: Boolean,
    onTest: () -> Unit,
    onRecompile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        GlassSecondaryButton(
            onClick = onTest,
            enabled = !isRunning,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Icon(
                    painter = AppIcons.CheckPainterResource(),
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.Small),
                )
                Text("Test", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        }

        val rebuildButton = @Composable {
            GlassSecondaryButton(
                onClick = onRecompile,
                enabled = !isRunning && isRecipeSupported,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                ) {
                    Icon(
                        painter = AppIcons.RefreshPainterResource(),
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.Small),
                    )
                    Text("Rebuild", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        if (!isRecipeSupported) {
            GlassTooltipArea(tooltipText = ToolBrowserState.REPAIR_UNAVAILABLE_REASON) {
                rebuildButton()
            }
        } else {
            rebuildButton()
        }
    }
}
