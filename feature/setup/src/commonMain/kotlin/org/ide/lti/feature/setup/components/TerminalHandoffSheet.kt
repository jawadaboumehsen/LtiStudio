package org.ide.lti.feature.setup.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
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

/**
 * Modal sheet presenting a safe terminal handoff for privileged package installation.
 * Never requests or writes persistent sudoers rules (NOPASSWD: ALL).
 * Offers one-click external terminal launch, command clipboard copying, and resume verification.
 */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("LongMethod")
@Composable
fun TerminalHandoffSheet(
    handoff: UserRepairHandoff,
    onOpenTerminal: (String) -> Unit,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    onAbandon: (() -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val clipboardManager = LocalClipboardManager.current
    var copiedToClipboard by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = AlphaTokens.Scrim))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .focusRequester(focusRequester)
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
            modifier = Modifier
                .widthIn(max = ComponentSize.MaxPanelWidth)
                .fillMaxWidth()
                .padding(Spacing.Medium)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .semantics {
                    paneTitle = "External Terminal Authorization"
                },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.Large),
                verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                        Text(
                            text = "Administrator Authorization Required",
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

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = ComponentSize.DialogDefaultHeight),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
                ) {
                    if (!errorMessage.isNullOrBlank()) {
                        item(key = "error_banner") {
                            GlassBanner(
                                title = "Verification Failed",
                                description = errorMessage,
                                severity = GlassBannerSeverity.ERROR,
                            )
                        }
                    }

                    item(key = "info_banner") {
                        GlassBanner(
                            title = "Safe User-Authorized Installation",
                            description = "Some packages require administrator privileges to install. " +
                                "LtiRom Studio operates strictly unprivileged and does not request or write " +
                                "persistent passwordless sudo rules. Please run the command below in your WSL " +
                                "terminal to authorize the installation.",
                            severity = GlassBannerSeverity.INFO,
                        )
                    }

                    if (handoff.packages.isNotEmpty()) {
                        item(key = "packages_section") {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                                Text(
                                    text = "Packages to Install (${handoff.packages.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                                ) {
                                    for (pkg in handoff.packages) {
                                        Box(
                                            modifier = Modifier
                                                .clip(GlassShapes.Pill)
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant
                                                        .copy(alpha = AlphaTokens.Half),
                                                )
                                                .border(
                                                    width = StrokeWidth.Standard,
                                                    color = MaterialTheme.colorScheme.outlineVariant
                                                        .copy(alpha = AlphaTokens.Muted),
                                                    shape = GlassShapes.Pill,
                                                )
                                                .padding(
                                                    horizontal = Spacing.Small,
                                                    vertical = Spacing.ExtraExtraSmall,
                                                ),
                                        ) {
                                            Text(
                                                text = pkg,
                                                style = MaterialTheme.typography.bodySmall
                                                    .copy(fontFamily = codeFontFamily()),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(key = "terminal_command_box") {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                            Text(
                                text = "Terminal Command",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(GlassShapes.Card)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                            .copy(alpha = AlphaTokens.Prominent),
                                    )
                                    .border(
                                        width = StrokeWidth.Standard,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = AlphaTokens.Hover),
                                        shape = GlassShapes.Card,
                                    )
                                    .padding(Spacing.Medium),
                            ) {
                                Text(
                                    text = handoff.terminalCommand,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = codeFontFamily()),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    if (copiedToClipboard) {
                        item(key = "copied_feedback") {
                            Text(
                                text = "Command copied to clipboard. Paste into your WSL terminal to run.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                GlassHorizontalDivider()

                // Actions row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small, Alignment.End),
                ) {
                    if (onAbandon != null) {
                        GlassSecondaryButton(
                            onClick = onAbandon,
                        ) {
                            Text("Abandon")
                        }
                    }

                    GlassSecondaryButton(
                        onClick = onDismiss,
                    ) {
                        Text("Close for now")
                    }

                    GlassSecondaryButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(handoff.terminalCommand))
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
                            Text(if (copiedToClipboard) "Copied!" else "Copy Command")
                        }
                    }

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
                            Text("Open WSL Terminal")
                        }
                    }

                    GlassPrimaryButton(
                        onClick = onResume,
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
                            Text("Verify & Resume")
                        }
                    }
                }
            }
        }
    }
}
