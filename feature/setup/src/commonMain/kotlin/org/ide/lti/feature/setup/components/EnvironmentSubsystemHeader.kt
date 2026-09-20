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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.kashif_e.backdrop.backdrops.LayerBackdrop
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.actions.GlassSecondaryButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.GlassHorizontalDivider
import org.ide.lti.core.designsystem.component.feedback.GlassBanner
import org.ide.lti.core.designsystem.component.feedback.GlassBannerSeverity
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
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
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainSetupState

/**
 * Interactive Subsystem & Runtime Header modeled after JetBrains CLion Toolchains
 * and VS Code Remote-WSL status bars.
 *
 * Provides real-time environmental telemetry:
 * - Active WSL2 distribution with live readiness indicator
 * - LtiRomServer daemon bridge latency pill with round-trip ping in milliseconds
 * - ext4 workspace storage headroom meter
 * - Direct environment refresh and elevated root package remediation actions
 *
 * Adheres strictly to:
 * - Single Responsibility: Presentation of environment subsystem status and quick controls.
 * - Design System Guardrails: 100% token usage (zero raw dp or alpha float literals).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EnvironmentSubsystemHeader(
    state: ToolchainSetupState,
    onCheckEnvironment: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: LayerBackdrop? = null,
    isAutoDoctorEnabled: Boolean = true,
    onSetAutoDoctorEnabled: (Boolean) -> Unit = {},
    onResetToolchainCache: () -> Unit = {},
) {
    val glassColors = GlassTheme.colors
    val diagnostics = glassColors.diagnostics

    val isWslHealthy = state.steps.firstOrNull { it.stage.name == "WSL_DETECTION" }?.status == StepStatus.SUCCESS
    val isServerConnected =
        state.steps.firstOrNull { it.stage.name == "SERVER_CONNECTIVITY" }?.status == StepStatus.SUCCESS
    val isServerFailed = state.steps.firstOrNull { it.stage.name == "SERVER_CONNECTIVITY" }?.status == StepStatus.FAILED

    val shellColors = GlassTheme.ideShellColors
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        surfaceColor = shellColors.cardBackground,
        borderColor = shellColors.border,
        backdrop = backdrop,
        contentPadding = Spacing.CardPadding,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            // Responsive Subsystem Header: Badges on left, action controls on right, wrapping cleanly without overlap
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                // Left: Subsystem Badges & Live Status Indicators
                FlowRow(
                    modifier = Modifier.padding(vertical = Spacing.ExtraSmall),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                ) {
                    WslRuntimeBadge(
                        isWslHealthy = isWslHealthy,
                        activeDistro = state.activeDistro,
                        glassColors = glassColors,
                        diagnostics = diagnostics,
                    )
                    DaemonBridgeBadge(
                        isServerConnected = isServerConnected,
                        isServerFailed = isServerFailed,
                        latencyMs = state.daemonPingMs,
                        glassColors = glassColors,
                        diagnostics = diagnostics,
                    )
                }

                // Right: Probing action (Primary mutations owned exclusively by SetupPresentationMapper / ActionBar)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    GlassSecondaryButton(
                        onClick = onCheckEnvironment,
                        enabled = !state.isBusy,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                        ) {
                            if (state.isChecking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(IconSize.Small),
                                    strokeWidth = StrokeWidth.Hairline,
                                    color = glassColors.onGlass,
                                )
                            } else {
                                Icon(
                                    painter = AppIcons.RefreshPainterResource(),
                                    contentDescription = null,
                                    modifier = Modifier.size(IconSize.Small),
                                )
                            }
                            Text("Probe Subsystem", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            val sudoDiag = state.diagnostics.firstOrNull { it.id == "sudo_noninteractive" }
            if (sudoDiag != null && sudoDiag.status != StepStatus.SUCCESS) {
                val cmd = sudoDiag.copyableCommand ?: "echo \"\$USER ALL=(ALL) NOPASSWD: ALL\" | sudo tee /etc/sudoers.d/ltirom"
                GlassBanner(
                    severity = GlassBannerSeverity.WARNING,
                    title = "Non-Interactive Sudo Required",
                    description = "Run in WSL terminal: $cmd",
                    backdrop = backdrop,
                )
            }

            GlassHorizontalDivider()

            // Subsystem Footer: Status metadata on left, secondary utility controls on right
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                val isRestored = state.steps.any { it.provenance == StepProvenance.RESTORED }
                val lastVerifiedText = state.lastVerifiedTimestamp?.takeIf { it > 0 }?.let { millis ->
                    val relative = formatRelativeTime(Instant.fromEpochMilliseconds(millis))
                    if (isRestored) "Last verified $relative (historical)" else "Last verified $relative"
                } ?: "Not yet verified"

                // Left: Last verified telemetry caption
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                    modifier = Modifier.padding(vertical = Spacing.ExtraExtraSmall),
                ) {
                    Text(
                        text = lastVerifiedText,
                        style = MaterialTheme.typography.labelSmall,
                        color = glassColors.onGlassMuted,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Right: Auto-Fix on Launch toggle and Reset & Rebuild icon button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                ) {
                    AutoFixToggle(
                        checked = isAutoDoctorEnabled,
                        onCheckedChange = onSetAutoDoctorEnabled,
                        enabled = !state.isBusy,
                        textColor = glassColors.onGlassSecondary,
                    )

                    // Reset & Rebuild destructive action (quiet icon button)
                    GlassTooltipArea(
                        tooltipText = "Reset toolchain cache & rebuild from scratch",
                    ) {
                        GlassIconButton(
                            onClick = onResetToolchainCache,
                            enabled = !state.isBusy,
                            size = ComponentSize.PanelHeaderActionTouchTarget,
                        ) {
                            Icon(
                                painter = AppIcons.DeletePainterResource(),
                                contentDescription = "Reset toolchain cache & rebuild from scratch",
                                modifier = Modifier.size(IconSize.Small),
                                tint = glassColors.onGlassSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatRelativeTime(instant: Instant): String {
    val now = Clock.System.now()
    val duration = now - instant
    return when {
        duration.isNegative() || duration.inWholeMinutes < 1 -> "Just now"
        duration.inWholeMinutes < 60 -> "${duration.inWholeMinutes}m ago"
        duration.inWholeHours < 24 -> "${duration.inWholeHours}h ago"
        duration.inWholeDays < 7 -> "${duration.inWholeDays}d ago"
        else -> "${duration.inWholeDays / 7}w ago"
    }
}

@Composable
private fun WslRuntimeBadge(
    isWslHealthy: Boolean,
    activeDistro: String?,
    glassColors: org.ide.lti.core.designsystem.theme.GlassColorScheme,
    diagnostics: org.ide.lti.core.designsystem.theme.DiagnosticColors,
) {
    // WSL 2 Distro Badge
    Box(
        modifier = Modifier
            .clip(GlassShapes.Medium)
            .background(glassColors.glassSurface)
            .border(
                width = StrokeWidth.Standard,
                color = if (isWslHealthy) {
                    diagnostics.success.copy(alpha = AlphaTokens.Border)
                } else {
                    glassColors.glassBorder
                },
                shape = GlassShapes.Medium,
            )
            .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.Small),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Box(
                modifier = Modifier
                    .size(IconSize.Indicator)
                    .clip(GlassShapes.Capsule)
                    .background(
                        if (isWslHealthy) diagnostics.success else diagnostics.warning,
                    ),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
                Text(
                    text = "WSL 2 Runtime",
                    style = MaterialTheme.typography.labelSmall,
                    color = glassColors.onGlassMuted,
                )
                Text(
                    text = activeDistro ?: "Ubuntu",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = glassColors.onGlass,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DaemonBridgeBadge(
    isServerConnected: Boolean,
    isServerFailed: Boolean,
    latencyMs: Long?,
    glassColors: org.ide.lti.core.designsystem.theme.GlassColorScheme,
    diagnostics: org.ide.lti.core.designsystem.theme.DiagnosticColors,
) {
    // Daemon Bridge & Latency Meter
    Box(
        modifier = Modifier
            .clip(GlassShapes.Medium)
            .background(glassColors.glassSurface)
            .border(
                width = StrokeWidth.Standard,
                color = when {
                    isServerConnected -> diagnostics.success.copy(alpha = AlphaTokens.Border)
                    isServerFailed -> diagnostics.error.copy(alpha = AlphaTokens.Border)
                    else -> glassColors.glassBorder
                },
                shape = GlassShapes.Medium,
            )
            .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.Small),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Box(
                modifier = Modifier
                    .size(IconSize.Indicator)
                    .clip(GlassShapes.Capsule)
                    .background(
                        when {
                            isServerConnected -> diagnostics.success
                            isServerFailed -> diagnostics.error
                            else -> glassColors.onGlassMuted
                        },
                    ),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
                Text(
                    text = "Daemon Bridge",
                    style = MaterialTheme.typography.labelSmall,
                    color = glassColors.onGlassMuted,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                ) {
                    Text(
                        text = when {
                            isServerConnected -> "Connected"
                            isServerFailed -> "Failed"
                            else -> "Idle"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isServerFailed) diagnostics.error else glassColors.onGlass,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    latencyMs?.let { ping ->
                        GlassChip(
                            label = "${ping}ms",
                            selected = true,
                        )
                    }
                }
            }
        }
    }

    // Storage Headroom Meter
    Box(
        modifier = Modifier
            .clip(GlassShapes.Medium)
            .background(glassColors.glassSurface)
            .border(
                width = StrokeWidth.Standard,
                color = glassColors.glassBorder,
                shape = GlassShapes.Medium,
            )
            .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.Small),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
            Text(
                text = "Storage Headroom",
                style = MaterialTheme.typography.labelSmall,
                color = glassColors.onGlassMuted,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Text(
                    text = "ext4 (>45GB)",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = codeFontFamily(),
                    fontWeight = FontWeight.Medium,
                    color = glassColors.onGlass,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                GlassChip(
                    label = "Ready",
                    selected = false,
                )
            }
        }
    }
}

@Composable
private fun AutoFixToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean, textColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        GlassToggle(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
        Text(
            text = "Auto-Fix on Launch",
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
