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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import kotlinx.coroutines.delay
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.actions.GlassSecondaryButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.navigation.GlassFilterChipItem
import org.ide.lti.core.designsystem.component.navigation.GlassFilterChipRow
import org.ide.lti.core.designsystem.component.tooltip.GlassTooltipArea
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.icon.ideGeneralExport
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.domain.setup.SetupLogEvent
import org.ide.lti.feature.setup.LogFilter
import org.ide.lti.feature.setup.SetupActivityState

/**
 * Evaluates whether user scroll activity should pause auto-follow.
 */
private fun shouldPauseFollow(
    isScrollInProgress: Boolean,
    isProgrammaticScroll: Boolean,
    canScrollForward: Boolean,
    isFollowing: Boolean,
): Boolean {
    if (!isFollowing || isProgrammaticScroll) return false
    return isScrollInProgress && canScrollForward
}

/**
 * Live Activity and Execution Terminal Panel (FR-010, FR-011, FR-012, SC-004).
 *
 * Responsibilities:
 * - Displays live streaming activity logs with bounded memory (holds at most 10,000 lines, 8 MiB payload).
 * - Exposes a visible dropped-line badge when the buffer cap is exceeded.
 * - Virtualized LazyColumn with stable keys (event.id).
 * - Auto-follow latest logs keyed to latest event ID (not list size).
 * - Distinguishes user-initiated scroll from programmatic scroll: pauses follow only on user scroll up.
 * - Per-destination scroll/follow persistence across tab switches.
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
    listState: LazyListState = rememberLazyListState(),
    destination: String? = null,
    onExport: (() -> Unit)? = null,
) {
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    val visibleEvents by activityState.visibleEvents.collectAsState()
    val droppedCount by activityState.droppedLineCount.collectAsState()
    val followingLatest by activityState.followingLatest.collectAsState()
    val selectedEventIds by activityState.selectedEventIds.collectAsState()
    val currentFilter by activityState.filter.collectAsState()

    var isExpanded by remember { mutableStateOf(defaultExpanded) }
    var isCopiedAll by remember { mutableStateOf(false) }
    var isCopiedSelection by remember { mutableStateOf(false) }
    var isProgrammaticScroll by remember { mutableStateOf(false) }

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

    LaunchedEffect(isRunning) {
        if (isRunning) isExpanded = true
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }
            .collect { (inProgress, canScrollForward) ->
                if (shouldPauseFollow(inProgress, isProgrammaticScroll, canScrollForward, followingLatest)) {
                    activityState.pauseFollow()
                }
            }
    }

    val targetDestination = destination ?: title.lowercase().replace(" ", "_")

    LaunchedEffect(targetDestination) {
        val saved = activityState.getDestinationState(targetDestination)
        if (saved != null) {
            activityState.setFollowingLatest(saved.following)
            if (!saved.following && visibleEvents.isNotEmpty()) {
                val safeIndex = saved.scrollIndex.coerceIn(0, visibleEvents.size - 1)
                listState.scrollToItem(safeIndex, saved.scrollOffset)
            }
        }
    }

    DisposableEffect(targetDestination) {
        onDispose {
            activityState.saveDestinationState(
                destination = targetDestination,
                scrollIndex = listState.firstVisibleItemIndex,
                scrollOffset = listState.firstVisibleItemScrollOffset,
                following = activityState.followingLatest.value,
            )
        }
    }

    val lastEventId = visibleEvents.lastOrNull()?.id
    LaunchedEffect(lastEventId, followingLatest, isExpanded) {
        if (followingLatest && visibleEvents.isNotEmpty() && isExpanded) {
            isProgrammaticScroll = true
            try {
                listState.animateScrollToItem(visibleEvents.size - 1)
            } finally {
                isProgrammaticScroll = false
            }
        }
    }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics { paneTitle = title },
        surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = Spacing.CardPadding,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium)) {
            ActivityPanelHeader(
                title = title,
                isExpanded = isExpanded,
                onToggleExpand = { isExpanded = !isExpanded },
                linesCount = visibleEvents.size,
                droppedCount = droppedCount,
                isRunning = isRunning,
                hasEvents = visibleEvents.isNotEmpty(),
                followingLatest = followingLatest,
                onPauseFollow = { activityState.pauseFollow() },
                onJumpToLatest = { activityState.jumpToLatest() },
                selectedCount = selectedEventIds.size,
                isCopiedSelection = isCopiedSelection,
                onCopySelection = {
                    clipboardManager.setText(AnnotatedString(activityState.copySelected()))
                    isCopiedSelection = true
                },
                onClearSelection = { activityState.clearSelection() },
                isCopiedAll = isCopiedAll,
                onCopyAll = {
                    clipboardManager.setText(AnnotatedString(activityState.copyAll()))
                    isCopiedAll = true
                },
                onExport = onExport,
            )

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                    ActivityFilterAndDisclosureBar(
                        currentFilter = currentFilter,
                        onSelectFilter = { activityState.setFilter(it) },
                        droppedCount = droppedCount,
                        visibleLinesCount = visibleEvents.size,
                        onExport = onExport,
                    )

                    ActivityPanelViewport(
                        visibleEvents = visibleEvents,
                        selectedEventIds = selectedEventIds,
                        listState = listState,
                        onSelectEvent = { eventId ->
                            activityState.selectEvent(eventId, isMultiSelect = true)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityPanelHeader(
    title: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    linesCount: Int,
    droppedCount: Int,
    isRunning: Boolean,
    hasEvents: Boolean,
    followingLatest: Boolean,
    onPauseFollow: () -> Unit,
    onJumpToLatest: () -> Unit,
    selectedCount: Int,
    isCopiedSelection: Boolean,
    onCopySelection: () -> Unit,
    onClearSelection: () -> Unit,
    isCopiedAll: Boolean,
    onCopyAll: () -> Unit,
    onExport: (() -> Unit)? = null,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.Small)
            .clickable(onClick = onToggleExpand)
            .padding(vertical = Spacing.ExtraSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        ActivityHeaderLeftBadges(
            title = title,
            isExpanded = isExpanded,
            linesCount = linesCount,
            droppedCount = droppedCount,
            isRunning = isRunning,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            if (isExpanded && hasEvents) {
                ActivityFollowButton(
                    followingLatest = followingLatest,
                    onPauseFollow = onPauseFollow,
                    onJumpToLatest = onJumpToLatest,
                )
            }

            if (isExpanded && selectedCount > 0) {
                ActivitySelectionActions(
                    selectedCount = selectedCount,
                    isCopiedSelection = isCopiedSelection,
                    onCopySelection = onCopySelection,
                    onClearSelection = onClearSelection,
                )
            }

            if (hasEvents) {
                ActivityCopyAllButton(
                    isCopiedAll = isCopiedAll,
                    onCopyAll = onCopyAll,
                )
            }

            if (onExport != null) {
                ActivityExportButton(onExport = onExport)
            }

            Text(
                text = if (isExpanded) "Hide Output" else "View Output",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ActivityHeaderLeftBadges(
    title: String,
    isExpanded: Boolean,
    linesCount: Int,
    droppedCount: Int,
    isRunning: Boolean,
) {
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
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSize.Small),
        )

        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )

        GlassChip(label = "$linesCount lines", selected = false)

        if (droppedCount > 0) {
            GlassTooltipArea(
                tooltipText = "The in-memory activity buffer holds at most 10,000 lines. " +
                    "$droppedCount lines dropped.",
            ) {
                GlassChip(label = "$droppedCount dropped", selected = true)
            }
        }

        if (isRunning) {
            val isReducedMotion = GlassTheme.reducedMotionPolicy == GlassReducedMotionPolicy.Reduced
            if (isReducedMotion) {
                Text(
                    text = "Checking…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(IconSize.Small),
                    strokeWidth = StrokeWidth.Hairline,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ActivityFollowButton(
    followingLatest: Boolean,
    onPauseFollow: () -> Unit,
    onJumpToLatest: () -> Unit,
) {
    if (followingLatest) {
        GlassSecondaryButton(onClick = onPauseFollow) {
            Text(
                text = "Pause follow",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        GlassSecondaryButton(onClick = onJumpToLatest) {
            Text(
                text = "Jump to latest",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ActivitySelectionActions(
    selectedCount: Int,
    isCopiedSelection: Boolean,
    onCopySelection: () -> Unit,
    onClearSelection: () -> Unit,
) {
    val diagnostics = GlassTheme.diagnosticColors

    GlassSecondaryButton(onClick = onCopySelection) {
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
                tint = if (isCopiedSelection) diagnostics.success else MaterialTheme.colorScheme.onSurface,
            )
            val label = if (isCopiedSelection) "Copied!" else "Copy selection ($selectedCount)"
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    GlassTooltipArea(tooltipText = "Clear selection") {
        GlassIconButton(
            onClick = onClearSelection,
            size = ComponentSize.PanelHeaderAction,
        ) {
            Icon(
                painter = AppIcons.ClearPainterResource(),
                contentDescription = "Clear selection",
                modifier = Modifier.size(IconSize.Small),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActivityCopyAllButton(
    isCopiedAll: Boolean,
    onCopyAll: () -> Unit,
) {
    val diagnostics = GlassTheme.diagnosticColors

    GlassTooltipArea(tooltipText = if (isCopiedAll) "Copied all logs!" else "Copy all") {
        GlassIconButton(
            onClick = onCopyAll,
            size = ComponentSize.PanelHeaderAction,
        ) {
            Icon(
                painter = if (isCopiedAll) {
                    AppIcons.CheckPainterResource()
                } else {
                    AppIcons.CopyPainterResource()
                },
                contentDescription = "Copy all",
                tint = if (isCopiedAll) diagnostics.success else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.Small),
            )
        }
    }
}

@Composable
private fun ActivityPanelViewport(
    visibleEvents: List<SetupLogEvent>,
    selectedEventIds: Set<String>,
    listState: LazyListState,
    onSelectEvent: (String) -> Unit,
) {
    val horizontalScrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = ComponentSize.DialogDefaultHeight)
            .clip(GlassShapes.Small)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = StrokeWidth.Hairline,
                color = MaterialTheme.colorScheme.outline,
                shape = GlassShapes.Small,
            )
            .padding(Spacing.SmallMedium),
    ) {
        if (visibleEvents.isEmpty()) {
            Text(
                text = "No execution output generated yet. " +
                    "Run setup, diagnostics, or tool verification to capture terminal streams.",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = codeFontFamily()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(horizontalScrollState),
            ) {
                items(
                    items = visibleEvents,
                    key = { it.id },
                    contentType = { "log_line" },
                ) { event ->
                    ActivityLogItemRow(
                        event = event,
                        isSelected = event.id in selectedEventIds,
                        onClick = { onSelectEvent(event.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityLogItemRow(
    event: SetupLogEvent,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val diagnostics = GlassTheme.diagnosticColors
    val line = event.text

    val lineTint = when {
        line.contains("ERROR", ignoreCase = true) ||
            line.contains("FAILED", ignoreCase = true) -> diagnostics.error
        line.contains("WARNING", ignoreCase = true) -> diagnostics.warning
        line.contains("Step", ignoreCase = true) ||
            line.contains("Complete", ignoreCase = true) -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.Small)
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Subtle)
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
            )
            .border(
                width = StrokeWidth.Hairline,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Border)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = AlphaTokens.UltraFaint)
                },
                shape = GlassShapes.Small,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.ExtraSmall, vertical = Spacing.ExtraExtraSmall),
    ) {
        Text(
            text = line,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = codeFontFamily(),
            ),
            color = lineTint,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActivityFilterAndDisclosureBar(
    currentFilter: LogFilter,
    onSelectFilter: (LogFilter) -> Unit,
    droppedCount: Int,
    visibleLinesCount: Int,
    onExport: (() -> Unit)?,
) {
    val filterItems = remember {
        listOf(
            GlassFilterChipItem(label = "All", count = -1, value = LogFilter.ALL.name),
            GlassFilterChipItem(label = "Checks", count = -1, value = LogFilter.CHECKS.name),
            GlassFilterChipItem(label = "Commands", count = -1, value = LogFilter.COMMANDS.name),
            GlassFilterChipItem(label = "Errors", count = -1, value = LogFilter.ERRORS.name),
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            GlassFilterChipRow(
                items = filterItems,
                selectedValue = currentFilter.name,
                onSelect = { name -> onSelectFilter(LogFilter.valueOf(name)) },
                modifier = Modifier.weight(1f, fill = false),
            )
        }

        if (droppedCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(GlassShapes.Small)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(
                        width = StrokeWidth.Hairline,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = GlassShapes.Small,
                    )
                    .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.ExtraSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Showing the last $visibleLinesCount lines — export for the full log",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (onExport != null) {
                    GlassSecondaryButton(onClick = onExport) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                        ) {
                            Icon(
                                painter = AppIcons.ideGeneralExport(),
                                contentDescription = "Export full log",
                                modifier = Modifier.size(IconSize.Small),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Export",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityExportButton(onExport: () -> Unit) {
    GlassTooltipArea(tooltipText = "Export full log") {
        GlassIconButton(
            onClick = onExport,
            size = ComponentSize.PanelHeaderAction,
        ) {
            Icon(
                painter = AppIcons.ideGeneralExport(),
                contentDescription = "Export full log",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.Small),
            )
        }
    }
}
