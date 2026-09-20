/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.progress

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.kashif_e.backdrop.Backdrop
import kotlinx.coroutines.delay
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.tooltip.GlassTooltipArea
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily

/**
 * Minimalist, dark glass terminal console drawer displaying real-time execution logs.
 *
 * Implements Progressive Disclosure:
 * - Hidden by default during idle inspection.
 * - Auto-expands and streams auto-scrolling terminal output when builds or remediation execute.
 * - Monospaced code typography with syntax highlighting for error and stage prefixes.
 *
 * Adheres strictly to:
 * - Single Responsibility: Execution log visualization and clipboard export.
 * - Design System Guardrails: 100% token usage (zero raw dp or alpha float literals).
 */
@Composable
fun GlassConsoleDrawer(
    logs: List<String>,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
) {
    val glassColors = GlassTheme.colors
    val diagnostics = glassColors.diagnostics

    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    var isExpanded by remember { mutableStateOf(false) }
    var isCopied by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(2000)
            isCopied = false
        }
    }

    // Automatically expand and scroll to bottom while running
    LaunchedEffect(isRunning) {
        if (isRunning) {
            isExpanded = true
        }
    }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty() && isExpanded) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        backdrop = backdrop,
        contentPadding = Spacing.CardPadding,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium)) {
            // Header Row
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(GlassShapes.Small)
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = Spacing.ExtraSmall),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
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
                        contentDescription = "Toggle Terminal Logs",
                        tint = glassColors.onGlassSecondary,
                        modifier = Modifier.size(IconSize.Small),
                    )

                    Text(
                        text = "Execution Terminal Logs",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = glassColors.onGlass,
                        maxLines = 1,
                        softWrap = false,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )

                    GlassChip(
                        label = "${logs.size} lines",
                        selected = false,
                    )

                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(IconSize.Small),
                            strokeWidth = StrokeWidth.Hairline,
                            color = glassColors.glassAccent,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    if (logs.isNotEmpty()) {
                        GlassTooltipArea(tooltipText = if (isCopied) "Copied all logs!" else "Copy all logs") {
                            GlassIconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(logs.joinToString("\n")))
                                    isCopied = true
                                },
                                size = ComponentSize.PanelHeaderAction,
                            ) {
                                Icon(
                                    painter = if (isCopied) {
                                        AppIcons.CheckPainterResource()
                                    } else {
                                        AppIcons.CopyPainterResource()
                                    },
                                    contentDescription = "Copy all",
                                    tint = if (isCopied) diagnostics.success else glassColors.onGlassSecondary,
                                    modifier = Modifier.size(IconSize.Small),
                                )
                            }
                        }
                    }

                    Text(
                        text = if (isExpanded) "Hide Terminal" else "View Output",
                        style = MaterialTheme.typography.labelSmall,
                        color = glassColors.glassAccent,
                        maxLines = 1,
                        softWrap = false,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }

            // Expandable Monospaced Terminal Output
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
                    if (logs.isEmpty()) {
                        Text(
                            text = "No execution output generated yet. " +
                                "Run setup or diagnostics to capture terminal streams.",
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
                            items(
                                items = logs,
                                contentType = { "log_line" },
                            ) { line ->
                                val lineTint = when {
                                    line.contains("ERROR", ignoreCase = true) ||
                                        line.contains("FAILED", ignoreCase = true) -> diagnostics.error
                                    line.contains("WARNING", ignoreCase = true) -> diagnostics.warning
                                    line.contains("Step", ignoreCase = true) ||
                                        line.contains("Complete", ignoreCase = true) -> glassColors.glassAccent
                                    else -> glassColors.onGlassSecondary
                                }

                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = codeFontFamily(),
                                    ),
                                    color = lineTint,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
