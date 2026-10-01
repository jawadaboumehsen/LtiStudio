/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.component.progress.GlassStepStatus
import org.ide.lti.core.designsystem.component.tooltip.GlassTooltipArea
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.DiagnosticColors
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily

/**
 * Data item representing an individual diagnostic inspection item.
 *
 * @property id Unique identifier for the diagnostic check.
 * @property title Primary title describing what was checked.
 * @property detail Informational message or remediation guidance.
 * @property status Current diagnostic state (success, warning, failed, running, pending).
 * @property category Optional display category label rendered as a chip.
 * @property copyableCommand Optional bash/terminal command rendered with a one-click copy button.
 */
data class GlassDiagnosticItem(
    val id: String,
    val title: String,
    val detail: String,
    val status: GlassStepStatus,
    val category: String? = null,
    val copyableCommand: String? = null,
)

/**
 * Default constants and helpers for diagnostic components.
 */
@Suppress("PropertyName") // Design token uses PascalCase per CLAUDE.md token catalog
object GlassDiagnosticDefaults {
    /** Duration in milliseconds that the "Copied!" feedback indicator persists. */
    const val CopiedFeedbackDurationMs: Long = 2000L

    /**
     * Resolves a sensible default status label when rendering diagnostic status text.
     */
    fun defaultStatusLabel(status: GlassStepStatus): String = when (status) {
        GlassStepStatus.SUCCESS -> "Verified"
        GlassStepStatus.WARNING -> "Notice"
        GlassStepStatus.FAILED -> "Missing"
        GlassStepStatus.RUNNING -> "Running"
        GlassStepStatus.PENDING -> "Pending"
        GlassStepStatus.SKIPPED -> "Skipped"
        GlassStepStatus.CANCELLED -> "Cancelled"
        GlassStepStatus.INTERRUPTED -> "Interrupted"
        GlassStepStatus.RESTORED -> "Restored"
    }
}

/**
 * Reusable Liquid Glass tile displaying a diagnostic inspection check.
 *
 * Generalizes the Doctor Diagnostics drawer check tile: includes status indicator dot,
 * title, category chip, status label, descriptive detail, and an optional copyable command pill.
 *
 * @param item The diagnostic item to display.
 * @param modifier Optional layout modifier.
 */
@Composable
fun GlassDiagnosticTile(item: GlassDiagnosticItem, modifier: Modifier = Modifier) {
    val diagnostics = GlassTheme.diagnosticColors

    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    var isCopied by remember(item.id) { mutableStateOf(false) }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(GlassDiagnosticDefaults.CopiedFeedbackDurationMs)
            isCopied = false
        }
    }

    val statusColor = resolveStatusColor(item.status, diagnostics, MaterialTheme.colorScheme)

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(GlassShapes.Small)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .glassOutlineBorder(
                    width = StrokeWidth.Hairline,
                    color = MaterialTheme.colorScheme.outline,
                    shape = GlassShapes.Small,
                )
                .padding(Spacing.SmallMedium),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(end = ComponentSize.ButtonMinWidth),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(IconSize.Indicator)
                                .clip(GlassShapes.Capsule)
                                .background(statusColor),
                        )

                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )

                        if (item.category != null) {
                            GlassChip(
                                label = item.category,
                                selected = false,
                            )
                        }
                    }

                    Text(
                        text = GlassDiagnosticDefaults.defaultStatusLabel(item.status),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = statusColor,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }

                Text(
                    text = item.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                item.copyableCommand?.let { cmd ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(GlassShapes.Small)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = AlphaTokens.UltraFaint))
                            .glassOutlineBorder(
                                width = StrokeWidth.Hairline,
                                color = MaterialTheme.colorScheme.outline,
                                shape = GlassShapes.Small,
                            )
                            .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraExtraSmall),
                    ) {
                        Text(
                            text = cmd,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = codeFontFamily()),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(end = ComponentSize.PanelHeaderAction + Spacing.Small),
                        )

                        GlassTooltipArea(
                            tooltipText = if (isCopied) "Copied to clipboard!" else "Copy bash command",
                            modifier = Modifier.align(Alignment.CenterEnd),
                        ) {
                            GlassIconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(cmd))
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
                                    contentDescription = "Copy",
                                    tint = if (isCopied) {
                                        diagnostics.success
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(IconSize.Small),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Resolves the status color for a diagnostic step status.
 */
private fun resolveStatusColor(status: GlassStepStatus, diagnostics: DiagnosticColors, colors: ColorScheme): Color =
    when (status) {
        GlassStepStatus.SUCCESS -> diagnostics.success
        GlassStepStatus.WARNING -> diagnostics.warning
        GlassStepStatus.FAILED -> diagnostics.error
        GlassStepStatus.RUNNING -> colors.primary
        else -> colors.onSurfaceVariant
    }
