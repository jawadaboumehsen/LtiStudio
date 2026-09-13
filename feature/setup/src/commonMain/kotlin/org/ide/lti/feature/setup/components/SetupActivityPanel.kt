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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.kashif_e.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.delay
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.actions.GlassSecondaryButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
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
import org.ide.lti.feature.setup.SetupActivityState

/**
 * Live Activity and Execution Terminal Panel (FR-010, SC-004).
 *
 * Responsibilities:
 * - Displays live streaming activity logs with bounded memory (holds at most 10,000 lines).
 * - Exposes a visible dropped-line badge when the buffer cap is exceeded.
 * - Manages pause-follow, resume, and Jump to latest transitions.
 * - Supports copying all logs or copying selection, with interactive line selection.
 * - Strict design system guardrails compliance (zero raw dp, color, or alpha literals).
 */
@Composable
fun SetupActivityPanel(
    activityState: SetupActivityState,
    modifier: Modifier = Modifier,
    title: String = "Activity",
    isRunning: Boolean = false,
    defaultExpanded: Boolean = true,
    backdrop: LayerBackdrop? = null,
) {
    val glassColors = GlassTheme.colors
    val diagnostics = glassColors.diagnostics
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    val visibleLines by activityState.visibleLines.collectAsState()
    val droppedCount by activityState.droppedLineCount.collectAsState()
    val followingLatest by activityState.followingLatest.collectAsState()
    val selectedIndices by activityState.selectedIndices.collectAsState()

    var isExpanded by remember { mutableStateOf(defaultExpanded) }
    var isCopiedAll by remember { mutableStateOf(false) }
    var isCopiedSelection by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(isCopiedAll) {
        if (isCopiedAll) {
            delay(2000)
            isCopiedAll = false
        }
    }

    LaunchedEffect(isCopiedSelection) {
        if (isCopiedSelection) {
            delay(2000)
            isCopiedSelection = false
        }
    }

    // Auto-expand when operations begin running
    LaunchedEffect(isRunning) {
        if (isRunning) {
            isExpanded = true
        }
    }

    // Auto-follow latest logs
    LaunchedEffect(visibleLines.size, followingLatest, isExpanded) {
        if (followingLatest && visibleLines.isNotEmpty() && isExpanded) {
            listState.animateScrollToItem(visibleLines.size - 1)
        }
    }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics { paneTitle = title },
        backdrop = backdrop,
        contentPadding = Spacing.CardPadding,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium)) {
            // Header Row: Toggle, Title, Line Count, Dropped Count, Progress, and Controls
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(GlassShapes.Small)
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = Spacing.ExtraSmall),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                // Left: Title and Badges
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
                ) {
                    Icon(
                        painter = if (isExpanded) {
                            AppIcons.ChevronDownPainterResource()
                        } else {
                            AppIcons.ChevronRightPainterResource()
                        },
                        contentDescription = "Toggle Activity Panel",
                        tint = glassColors.onGlassSecondary,
                        modifier = Modifier.size(IconSize.Small),
                    )

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = glassColors.onGlass,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )

                    GlassChip(
                        label = "${visibleLines.size} lines",
                        selected = false,
                    )

                    if (droppedCount > 0) {
                        GlassTooltipArea(
                            tooltipText = "The in-memory activity buffer holds at most 10,000 lines. $droppedCount lines dropped.",
                        ) {
                            GlassChip(
                                label = "$droppedCount dropped",
                                selected = true,
                            )
                        }
                    }

                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(IconSize.Small),
                            strokeWidth = StrokeWidth.Hairline,
                            color = glassColors.glassAccent,
                        )
                    }
                }

                // Right: Follow / Pause, Selection Copy, Copy All, and Expand Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    // Follow / Pause Follow / Jump to latest
                    if (isExpanded && visibleLines.isNotEmpty()) {
                        if (followingLatest) {
                            GlassSecondaryButton(
                                onClick = { activityState.pauseFollow() },
                            ) {
                                Text(
                                    text = "Pause follow",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        } else {
                            GlassSecondaryButton(
                                onClick = { activityState.jumpToLatest() },
                            ) {
                                Text(
                                    text = "Jump to latest",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = glassColors.glassAccent,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }

                    // Copy Selection Button
                    if (isExpanded && selectedIndices.isNotEmpty()) {
                        GlassSecondaryButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(activityState.copySelected()))
                                isCopiedSelection = true
                            },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                            ) {
                                Icon(
                                    painter = if (isCopiedSelection) {
                                        AppIcons.CheckPainterResource()
                                    } else {
                                        AppIcons.CopyPainterResource()
                                    },
                                    contentDescription = "Copy selection",
                                    modifier = Modifier.size(IconSize.Small),
                                    tint = if (isCopiedSelection) diagnostics.success else glassColors.onGlass,
                                )
                                Text(
                                    text = if (isCopiedSelection) "Copied!" else "Copy selection (${selectedIndices.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }

                        GlassTooltipArea(tooltipText = "Clear selection") {
                            GlassIconButton(
                                onClick = { activityState.clearSelection() },
                                size = ComponentSize.PanelHeaderAction,
                            ) {
                                Icon(
                                    painter = AppIcons.ClearPainterResource(),
                                    contentDescription = "Clear selection",
                                    modifier = Modifier.size(IconSize.Small),
                                    tint = glassColors.onGlassSecondary,
                                )
                            }
                        }
                    }

                    // Copy All Action
                    if (visibleLines.isNotEmpty()) {
                        GlassTooltipArea(tooltipText = if (isCopiedAll) "Copied all logs!" else "Copy all") {
                            GlassIconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(activityState.copyAll()))
                                    isCopiedAll = true
                                },
                                size = ComponentSize.PanelHeaderAction,
                            ) {
                                Icon(
                                    painter = if (isCopiedAll) {
                                        AppIcons.CheckPainterResource()
                                    } else {
                                        AppIcons.CopyPainterResource()
                                    },
                                    contentDescription = "Copy all",
                                    tint = if (isCopiedAll) diagnostics.success else glassColors.onGlassSecondary,
                                    modifier = Modifier.size(IconSize.Small),
                                )
                            }
                        }
                    }

                    Text(
                        text = if (isExpanded) "Hide Output" else "View Output",
                        style = MaterialTheme.typography.labelSmall,
                        color = glassColors.glassAccent,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }

            // Bounded Activity Viewport
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                val horizontalScrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = ComponentSize.DialogDefaultHeight)
                        .clip(GlassShapes.Small)
                        .background(glassColors.glassSurface)
                        .border(
                            width = StrokeWidth.Hairline,
                            color = glassColors.glassBorder,
                            shape = GlassShapes.Small,
                        )
                        .padding(Spacing.SmallMedium),
                ) {
                    if (visibleLines.isEmpty()) {
                        Text(
                            text = "No execution output generated yet. " +
                                "Run setup, diagnostics, or tool verification to capture terminal streams.",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = codeFontFamily()),
                            color = glassColors.onGlassMuted,
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(horizontalScrollState),
                        ) {
                            itemsIndexed(
                                items = visibleLines,
                                key = { index, _ -> index },
                                contentType = { _, _ -> "log_line" },
                            ) { index, line ->
                                val isSelected = index in selectedIndices

                                val lineTint = when {
                                    line.contains("ERROR", ignoreCase = true) ||
                                        line.contains("FAILED", ignoreCase = true) -> diagnostics.error
                                    line.contains("WARNING", ignoreCase = true) -> diagnostics.warning
                                    line.contains("Step", ignoreCase = true) ||
                                        line.contains("Complete", ignoreCase = true) -> glassColors.glassAccent
                                    else -> glassColors.onGlassSecondary
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(GlassShapes.Small)
                                        .background(
                                            if (isSelected) {
                                                glassColors.glassAccent.copy(alpha = AlphaTokens.Subtle)
                                            } else {
                                                glassColors.glassSurface
                                            },
                                        )
                                        .border(
                                            width = StrokeWidth.Hairline,
                                            color = if (isSelected) {
                                                glassColors.glassAccent.copy(alpha = AlphaTokens.Border)
                                            } else {
                                                glassColors.glassBorder.copy(alpha = AlphaTokens.UltraFaint)
                                            },
                                            shape = GlassShapes.Small,
                                        )
                                        .clickable { activityState.selectLine(index, isMultiSelect = true) }
                                        .padding(horizontal = Spacing.ExtraSmall, vertical = Spacing.ExtraExtraSmall),
                                ) {
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = codeFontFamily(),
                                        ),
                                        color = lineTint,
                                        softWrap = false,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
