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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.actions.GlassSecondaryButton
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.icon.ideStatusError
import org.ide.lti.core.designsystem.icon.ideStatusInfo
import org.ide.lti.core.designsystem.icon.ideStatusSuccess
import org.ide.lti.core.designsystem.icon.ideStatusWarning
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.DiagnosticColors
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth

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
data class GlassBannerAction(val label: String, val onClick: () -> Unit)

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
) {
    val diagnostics = GlassTheme.diagnosticColors
    val severityColor = bannerSeverityColor(severity, diagnostics, MaterialTheme.colorScheme.primary)
    val hasActions = secondaryAction != null || primaryAction != null || onDismiss != null

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        val backgroundModifier = if (GlassTheme.effectsEnabled) {
            Modifier.background(severityColor.copy(alpha = AlphaTokens.UltraFaint))
        } else {
            Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .background(severityColor.copy(alpha = AlphaTokens.Subtle))
        }
        val borderColor = if (GlassTheme.effectsEnabled) {
            severityColor.copy(alpha = AlphaTokens.Border)
        } else {
            severityColor
        }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(GlassShapes.Small)
                .then(backgroundModifier)
                .glassOutlineBorder(
                    width = StrokeWidth.Hairline,
                    color = borderColor,
                    shape = GlassShapes.Small,
                )
                .padding(horizontal = Spacing.Medium, vertical = Spacing.SmallMedium),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isNarrow = maxWidth < ComponentSize.PanelHeaderStackBreakpoint

                if (isNarrow) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                        ) {
                            BannerIcon(icon = icon, severity = severity, severityColor = severityColor)
                            Box(modifier = Modifier.weight(1f)) {
                                BannerText(title = title, description = description)
                            }
                        }
                        if (hasActions) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                BannerActions(
                                    primaryAction = primaryAction,
                                    secondaryAction = secondaryAction,
                                    onDismiss = onDismiss,
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                    ) {
                        BannerIcon(icon = icon, severity = severity, severityColor = severityColor)
                        Box(modifier = Modifier.weight(1f)) {
                            BannerText(title = title, description = description)
                        }
                        if (hasActions) {
                            BannerActions(
                                primaryAction = primaryAction,
                                secondaryAction = secondaryAction,
                                onDismiss = onDismiss,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Resolves the primary accent/border color for a banner severity.
 */
private fun bannerSeverityColor(severity: GlassBannerSeverity, diagnostics: DiagnosticColors, primary: Color): Color =
    when (severity) {
        GlassBannerSeverity.INFO -> primary
        GlassBannerSeverity.WARNING -> diagnostics.warning
        GlassBannerSeverity.ERROR -> diagnostics.error
        GlassBannerSeverity.SUCCESS -> diagnostics.success
    }

/**
 * Resolves the default status icon painter for a banner severity.
 */
private fun bannerDefaultPainter(severity: GlassBannerSeverity): @Composable () -> Painter = when (severity) {
    GlassBannerSeverity.INFO -> AppIcons.ideStatusInfo
    GlassBannerSeverity.WARNING -> AppIcons.ideStatusWarning
    GlassBannerSeverity.ERROR -> AppIcons.ideStatusError
    GlassBannerSeverity.SUCCESS -> AppIcons.ideStatusSuccess
}

/**
 * Renders the leading icon or default status badge for a banner.
 */
@Composable
private fun BannerIcon(
    icon: (@Composable () -> Unit)?,
    severity: GlassBannerSeverity,
    severityColor: Color,
    modifier: Modifier = Modifier,
) {
    if (icon != null) {
        icon()
    } else {
        val painter = bannerDefaultPainter(severity)
        Box(
            modifier = modifier
                .size(IconSize.Large)
                .clip(GlassShapes.Capsule)
                .background(severityColor.copy(alpha = AlphaTokens.GlassSecondaryTint)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painter(),
                contentDescription = null,
                tint = severityColor,
                modifier = Modifier.size(IconSize.Medium),
            )
        }
    }
}

/**
 * Renders the title and optional description text for a banner.
 */
@Composable
private fun BannerText(title: String, description: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Renders the actions row (secondary, primary, dismiss) for a banner.
 */
@Composable
private fun BannerActions(
    primaryAction: GlassBannerAction?,
    secondaryAction: GlassBannerAction?,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.Small),
                )
            }
        }
    }
}
