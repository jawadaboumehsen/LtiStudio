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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.actions.GlassSecondaryButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassHorizontalDivider
import org.ide.lti.core.designsystem.component.feedback.GlassBanner
import org.ide.lti.core.designsystem.component.feedback.GlassBannerSeverity
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.domain.setup.UserRepairHandoff
import org.ide.lti.feature.setup.TerminalHandoffState

/**
 * Modal sheet presenting a safe terminal handoff for privileged package installation.
 * Implements the contract in contracts/ui-states.md §4:
 * - Scrim click does NOT dismiss (only Escape or "Close for now").
 * - Numbered 4-step sequence: 1 Copy -> 2 Open -> 3 Paste & Run -> 4 Check again.
 * - Actions in FlowRow, with header, content, and actions sharing a single scroll.
 * - Confirm before abandon.
 * - Zero stored passwords, zero password fields, zero sudoers/NOPASSWD references.
 */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun TerminalHandoffSheet(
    handoff: UserRepairHandoff,
    onOpenTerminal: (String) -> Unit,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: TerminalHandoffState = TerminalHandoffState.Idle,
    errorMessage: String? = null,
    onAbandon: (() -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val firstActionFocusRequester = remember { FocusRequester() }
    val clipboardManager = LocalClipboardManager.current
    var copiedToClipboard by remember { mutableStateOf(false) }
    var isConfirmingAbandon by remember { mutableStateOf(false) }

    val effectivePackages =
        when (sheetState) {
            is TerminalHandoffState.StillMissing -> sheetState.packages
            else -> handoff.packages
        }
    val effectiveCommand =
        when (sheetState) {
            is TerminalHandoffState.StillMissing -> sheetState.command
            else -> handoff.terminalCommand
        }

    LaunchedEffect(effectiveCommand) {
        // Reset copy feedback when the command changes
        copiedToClipboard = false
    }

    LaunchedEffect(Unit) {
        firstActionFocusRequester.requestFocus()
    }

    Box(
        modifier =
        modifier
            .fillMaxSize()
            .testTag("terminal_handoff_scrim")
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = AlphaTokens.Scrim))
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    if (event.key == Key.Escape) {
                        onDismiss()
                        true
                    } else if (event.key == Key.Tab) {
                        val direction = if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next
                        focusManager.moveFocus(direction)
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        GlassCard(
            modifier =
            Modifier
                .widthIn(max = ComponentSize.MaxPanelWidth)
                .fillMaxWidth()
                .padding(Spacing.Medium)
                .semantics {
                    paneTitle = "External Terminal Authorization"
                },
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = ComponentSize.MaxPanelHeight)
                    .verticalScroll(scrollState)
                    .padding(Spacing.Large),
                verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                // Header: title, distro, close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                        val titleText =
                            if (effectivePackages.isNotEmpty()) {
                                "Install ${effectivePackages.size} packages"
                            } else {
                                "Install packages"
                            }
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "WSL Environment: ${handoff.distro}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    GlassIconButton(
                        onClick = onDismiss,
                    ) {
                        Icon(
                            painter = AppIcons.ClosePainterResource(),
                            contentDescription = "Close handoff dialog",
                            modifier = Modifier.size(IconSize.Small),
                        )
                    }
                }

                GlassHorizontalDivider()

                // State Banners
                val bannerLiveRegion = if (
                    sheetState is TerminalHandoffState.TerminalLaunchFailed ||
                    !errorMessage.isNullOrBlank()
                ) {
                    LiveRegionMode.Assertive
                } else {
                    LiveRegionMode.Polite
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = bannerLiveRegion },
                ) {
                    when (sheetState) {
                        is TerminalHandoffState.TerminalLaunchFailed -> {
                            GlassBanner(
                                title = "Terminal Launch Failed",
                                description = "Couldn't open a terminal: ${sheetState.reason}. " +
                                    "Copy the command and run it in ${handoff.distro} yourself.",
                                severity = GlassBannerSeverity.ERROR,
                            )
                        }
                        is TerminalHandoffState.Verifying -> {
                            GlassBanner(
                                title = "Checking installation…",
                                description = "Re-probing required packages in ${handoff.distro}.",
                                severity = GlassBannerSeverity.INFO,
                            )
                        }
                        is TerminalHandoffState.StillMissing -> {
                            GlassBanner(
                                title = "Packages Still Missing",
                                description = "Still missing: ${sheetState.packages.joinToString(", ")}",
                                severity = GlassBannerSeverity.WARNING,
                            )
                        }
                        is TerminalHandoffState.AnotherOperationRunning -> {
                            GlassBanner(
                                title = "Operation Busy",
                                description = "Another setup operation is running. Try again when it finishes.",
                                severity = GlassBannerSeverity.WARNING,
                            )
                        }
                        TerminalHandoffState.Idle, TerminalHandoffState.Done -> {
                            if (!errorMessage.isNullOrBlank()) {
                                GlassBanner(
                                    title = "Verification Failed",
                                    description = errorMessage,
                                    severity = GlassBannerSeverity.ERROR,
                                )
                            }
                        }
                    }
                }

                // Numbered sequence: 1 Copy -> 2 Open -> 3 Paste & Run -> 4 Check again
                Box(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(GlassShapes.Card)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = AlphaTokens.Half))
                        .border(
                            width = StrokeWidth.Standard,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = AlphaTokens.Muted),
                            shape = GlassShapes.Card,
                        ).padding(Spacing.Medium),
                ) {
                    Text(
                        text =
                        "1 Copy the command → 2 Open the terminal → " +
                            "3 Paste it and press Enter, then type your password → " +
                            "4 Come back and select \"I've run it — check again\".",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Packages Section
                if (effectivePackages.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                        Text(
                            text = "Packages to Install (${effectivePackages.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                        ) {
                            for (pkg in effectivePackages) {
                                Box(
                                    modifier =
                                    Modifier
                                        .clip(GlassShapes.Pill)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant
                                                .copy(alpha = AlphaTokens.Half),
                                        ).border(
                                            width = StrokeWidth.Standard,
                                            color =
                                            MaterialTheme.colorScheme.outlineVariant
                                                .copy(alpha = AlphaTokens.Muted),
                                            shape = GlassShapes.Pill,
                                        ).padding(
                                            horizontal = Spacing.Small,
                                            vertical = Spacing.ExtraExtraSmall,
                                        ),
                                ) {
                                    Text(
                                        text = pkg,
                                        style =
                                        MaterialTheme.typography.bodySmall
                                            .copy(fontFamily = codeFontFamily()),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                // Terminal Command Box
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                    Text(
                        text = "Terminal Command",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Box(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(GlassShapes.Card)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHighest
                                    .copy(alpha = AlphaTokens.Prominent),
                            ).border(
                                width = StrokeWidth.Standard,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = AlphaTokens.Hover),
                                shape = GlassShapes.Card,
                            ).padding(Spacing.Medium),
                    ) {
                        Text(
                            text = effectiveCommand,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = codeFontFamily()),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                if (copiedToClipboard) {
                    Text(
                        text = "Command copied to clipboard. Paste into your WSL terminal to run.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }

                GlassHorizontalDivider()

                // Actions, in order: Copy, Open terminal, I've run it — check again, Close for now, Abandon
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    // 1. Copy command
                    GlassSecondaryButton(
                        modifier = Modifier.focusRequester(firstActionFocusRequester),
                        onClick = {
                            clipboardManager.setText(AnnotatedString(effectiveCommand))
                            copiedToClipboard = true
                        },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                        ) {
                            Icon(
                                painter = AppIcons.CopyPainterResource(),
                                contentDescription = null,
                                modifier = Modifier.size(IconSize.Small),
                            )
                            Text(if (copiedToClipboard) "Copied!" else "Copy command")
                        }
                    }

                    // 2. Open the terminal
                    GlassSecondaryButton(
                        onClick = { onOpenTerminal(handoff.distro) },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                        ) {
                            Icon(
                                painter = AppIcons.PlayPainterResource(),
                                contentDescription = null,
                                modifier = Modifier.size(IconSize.Small),
                            )
                            val terminalLabel =
                                if (sheetState is TerminalHandoffState.TerminalLaunchFailed) {
                                    "Try again"
                                } else {
                                    "Open the terminal"
                                }
                            Text(terminalLabel)
                        }
                    }

                    // 3. I've run it — check again
                    val isVerifying = sheetState == TerminalHandoffState.Verifying
                    GlassPrimaryButton(
                        onClick = onResume,
                        enabled = !isVerifying,
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
                            val resumeLabel =
                                when (sheetState) {
                                    is TerminalHandoffState.AnotherOperationRunning -> "Try again"
                                    else -> "I've run it — check again"
                                }
                            Text(resumeLabel)
                        }
                    }

                    // 4. Close for now
                    GlassSecondaryButton(
                        onClick = onDismiss,
                    ) {
                        Text("Close for now")
                    }

                    // 5. Abandon setup (asks for confirmation)
                    if (onAbandon != null) {
                        if (!isConfirmingAbandon) {
                            GlassSecondaryButton(
                                onClick = { isConfirmingAbandon = true },
                            ) {
                                Text("Abandon setup")
                            }
                        } else {
                            GlassSecondaryButton(
                                onClick = { isConfirmingAbandon = false },
                            ) {
                                Text("Cancel")
                            }
                            GlassPrimaryButton(
                                onClick = {
                                    isConfirmingAbandon = false
                                    onAbandon()
                                },
                            ) {
                                Text("Confirm")
                            }
                        }
                    }
                }
            }
        }
    }
}
