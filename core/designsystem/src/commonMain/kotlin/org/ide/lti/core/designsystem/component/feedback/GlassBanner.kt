/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.actions.GlassSecondaryButton
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.icon.ideStatusError
import org.ide.lti.core.designsystem.icon.ideStatusInfo
import org.ide.lti.core.designsystem.icon.ideStatusSuccess
import org.ide.lti.core.designsystem.icon.ideStatusWarning
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.glass.GlassSceneState

/**
 * Severity levels for [GlassBanner] alerting and notification messages.
 */
enum class GlassBannerSeverity {
    INFO,
    WARNING,
    ERROR,
    SUCCESS,
}

/**
 * Action specification for buttons inside a [GlassBanner].
 *
 * @property label The text displayed on the action button.
 * @property onClick Callback invoked when the button is clicked.
 */
data class GlassBannerAction(
    val label: String,
    val onClick: () -> Unit,
)

/**
 * Reusable Liquid Glass contextual banner displaying diagnostic, validation, or informational notices.
 *
 * Adheres strictly to the Doctor Diagnostics visual hierarchy: tinted background at [AlphaTokens.UltraFaint],
 * hairline border at [AlphaTokens.Border], and high-contrast text and actions.
 *
 * @param severity Severity category mapping to theme diagnostic colors.
 * @param title Prominent heading text for the banner.
 * @param modifier Optional layout modifier.
 * @param description Optional secondary explanation text.
 * @param icon Optional custom icon composable slot. When null, defaults to a severity-specific status icon badge.
 * @param primaryAction Optional high-emphasis action button.
 * @param secondaryAction Optional medium-emphasis secondary action button.
 * @param onDismiss Optional callback invoked when the dismiss icon button is clicked.
 * @param backdrop Optional backdrop instance for glass sampling.
 */
@Suppress("UnusedParameter")
@Composable
fun GlassBanner(
    severity: GlassBannerSeverity,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: (@Composable () -> Unit)? = null,
    primaryAction: GlassBannerAction? = null,
    secondaryAction: GlassBannerAction? = null,
    onDismiss: (() -> Unit)? = null,
    backdrop: GlassSceneState? = null,
) {
    val glassColors = GlassTheme.colors
    val diagnostics = glassColors.diagnostics

    val severityColor = when (severity) {
        GlassBannerSeverity.INFO -> glassColors.glassAccent
        GlassBannerSeverity.WARNING -> diagnostics.warning
        GlassBannerSeverity.ERROR -> diagnostics.error
        GlassBannerSeverity.SUCCESS -> diagnostics.success
    }

    val defaultIconPainter: @Composable () -> Painter = when (severity) {
        GlassBannerSeverity.INFO -> AppIcons.ideStatusInfo
        GlassBannerSeverity.WARNING -> AppIcons.ideStatusWarning
        GlassBannerSeverity.ERROR -> AppIcons.ideStatusError
        GlassBannerSeverity.SUCCESS -> AppIcons.ideStatusSuccess
    }

    CompositionLocalProvider(LocalContentColor provides glassColors.onGlass) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(GlassShapes.Small)
                .background(severityColor.copy(alpha = AlphaTokens.UltraFaint))
                .border(
                    width = StrokeWidth.Hairline,
                    color = severityColor.copy(alpha = AlphaTokens.Border),
                    shape = GlassShapes.Small,
                )
                .padding(horizontal = Spacing.Medium, vertical = Spacing.SmallMedium),
        ) {
            val iconBlock: @Composable () -> Unit = {
                if (icon != null) {
                    icon()
                } else {
                    Box(
                        modifier = Modifier
                            .size(IconSize.Large)
                            .clip(GlassShapes.Capsule)
                            .background(severityColor.copy(alpha = AlphaTokens.GlassSecondaryTint)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = defaultIconPainter(),
                            contentDescription = null,
                            tint = severityColor,
                            modifier = Modifier.size(IconSize.Medium),
                        )
                    }
                }
            }

            val textBlock: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = glassColors.onGlass,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (description != null) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = glassColors.onGlassSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            val hasActions = secondaryAction != null || primaryAction != null || onDismiss != null
            val actionsBlock: @Composable () -> Unit = {
                if (hasActions) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    ) {
                        if (secondaryAction != null) {
                            GlassSecondaryButton(onClick = secondaryAction.onClick) {
                                Text(
                                    text = secondaryAction.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (primaryAction != null) {
                            GlassPrimaryButton(onClick = primaryAction.onClick) {
                                Text(
                                    text = primaryAction.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (onDismiss != null) {
                            GlassIconButton(
                                onClick = onDismiss,
                                size = ComponentSize.PanelHeaderAction,
                            ) {
                                Icon(
                                    painter = AppIcons.ClosePainterResource(),
                                    contentDescription = "Dismiss",
                                    tint = glassColors.onGlassSecondary,
                                    modifier = Modifier.size(IconSize.Small),
                                )
                            }
                        }
                    }
                }
            }

            // Below PanelHeaderStackBreakpoint, icon + title/description (weight(1f), fill
            // = true) + a fixed-content actions row don't all fit: the weighted text column
            // gets squeezed toward zero while actions still demands its own natural width,
            // clipping the title/description away entirely rather than truncating gracefully.
            // Read the available width once, at this single root, to pick the side-by-side
            // Row vs. a stacked Column — same cheap, non-nested breakpoint pattern as
            // RunPanel's header and SetupScreen's root.
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isNarrow = maxWidth < ComponentSize.PanelHeaderStackBreakpoint

                if (isNarrow) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                        ) {
                            iconBlock()
                            Box(modifier = Modifier.weight(1f)) { textBlock() }
                        }
                        if (hasActions) {
                            Box(modifier = Modifier.fillMaxWidth()) { actionsBlock() }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                    ) {
                        iconBlock()
                        Box(modifier = Modifier.weight(1f)) { textBlock() }
                        actionsBlock()
                    }
                }
            }
        }
    }
}
