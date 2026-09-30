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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.component.tooltip.GlassTooltipArea
import org.ide.lti.core.designsystem.icon.AppIcons
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
 * Execution status for an individual stage in a [GlassStepper].
 */
enum class GlassStepStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    WARNING,
    FAILED,
    SKIPPED,
    CANCELLED,
    INTERRUPTED,
    RESTORED,
    ;

    val isComplete: Boolean
        get() = this == SUCCESS || this == SKIPPED
}

/**
 * Orientation layout for [GlassStepper].
 */
enum class GlassStepperOrientation {
    Horizontal,
    Vertical,
}

/**
 * Data model representing an individual step in [GlassStepper].
 *
 * @property id Unique identifier for this step.
 * @property shortTitle Concise title of the step shown on the stage node.
 * @property status Current execution status of the step.
 * @property statusLabel Optional descriptive status label. If null, derived automatically from [status].
 */
data class GlassStepInfo(
    val id: String,
    val shortTitle: String,
    val status: GlassStepStatus,
    val statusLabel: String? = null,
)

/**
 * Default constants and helpers for [GlassStepper].
 */
object GlassStepperDefaults {
    /**
     * Resolves a sensible default status label when [GlassStepInfo.statusLabel] is null.
     */
    fun defaultStatusLabel(status: GlassStepStatus): String = when (status) {
        GlassStepStatus.PENDING -> "Pending"
        GlassStepStatus.RUNNING -> "Working..."
        GlassStepStatus.SUCCESS -> "Ready"
        GlassStepStatus.WARNING -> "Notice"
        GlassStepStatus.FAILED -> "Failed"
        GlassStepStatus.SKIPPED -> "Skipped"
        GlassStepStatus.CANCELLED -> "Cancelled"
        GlassStepStatus.INTERRUPTED -> "Interrupted"
        GlassStepStatus.RESTORED -> "Restored"
    }
}

/**
 * Reusable Liquid Glass stepper visualizing a sequence of pipeline stages.
 *
 * Supports horizontal and vertical orientations, dynamic stage status icons (spinner, checkmark,
 * warning, error, or numeric step index), and retry actions for failed stages.
 *
 * @param steps List of step details to visualize.
 * @param modifier Optional layout modifier.
 * @param orientation Visual direction for the stepper (Horizontal or Vertical).
 * @param isBusy Whether a global pipeline operation is running (disables retry triggers).
 * @param onRetryStep Callback invoked when a failed stage's retry action is clicked.
 * @param onStepClick Optional callback invoked when any stage node is clicked.
 * @param backdrop Optional backdrop instance for glass sampling.
 */
@Suppress("UnusedParameter")
@Composable
fun GlassStepper(
    steps: List<GlassStepInfo>,
    modifier: Modifier = Modifier,
    orientation: GlassStepperOrientation = GlassStepperOrientation.Horizontal,
    isBusy: Boolean = false,
    onRetryStep: ((stepId: String) -> Unit)? = null,
    onStepClick: ((stepId: String) -> Unit)? = null,
) {
    val diagnostics = GlassTheme.diagnosticColors

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        when (orientation) {
            GlassStepperOrientation.Horizontal -> {
                Row(
                    modifier = modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    steps.forEachIndexed { index, step ->
                        GlassStepNode(
                            step = step,
                            index = index + 1,
                            isBusy = isBusy,
                            onRetry = onRetryStep?.let { retry -> { retry(step.id) } },
                            onClick = onStepClick?.let { click -> { click(step.id) } },
                            // Fixed floor, no weight(): weight() cannot combine with the
                            // Row's horizontalScroll (weight needs bounded max width, a
                            // scrollable row measures children unbounded). Below this floor
                            // the row scrolls instead of squeezing nodes into illegible
                            // slivers; above it, nodes simply keep their natural width.
                            modifier = Modifier.widthIn(min = ComponentSize.StepNodeMinWidth),
                        )

                        if (index < steps.size - 1) {
                            val isPreviousComplete = step.status.isComplete
                            Icon(
                                painter = AppIcons.ChevronRightPainterResource(),
                                contentDescription = null,
                                tint = if (isPreviousComplete) {
                                    diagnostics.success.copy(alpha = AlphaTokens.Border)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AlphaTokens.Faint)
                                },
                                modifier = Modifier.size(IconSize.Small),
                            )
                        }
                    }
                }
            }

            GlassStepperOrientation.Vertical -> {
                Column(
                    modifier = modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    steps.forEachIndexed { index, step ->
                        GlassStepNode(
                            step = step,
                            index = index + 1,
                            isBusy = isBusy,
                            onRetry = onRetryStep?.let { retry -> { retry(step.id) } },
                            onClick = onStepClick?.let { click -> { click(step.id) } },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        if (index < steps.size - 1) {
                            val isPreviousComplete = step.status.isComplete
                            Icon(
                                painter = AppIcons.ChevronDownPainterResource(),
                                contentDescription = null,
                                tint = if (isPreviousComplete) {
                                    diagnostics.success.copy(alpha = AlphaTokens.Border)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AlphaTokens.Faint)
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

@Composable
private fun GlassStepNode(
    step: GlassStepInfo,
    index: Int,
    isBusy: Boolean,
    onRetry: (() -> Unit)?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val diagnostics = GlassTheme.diagnosticColors
    val isRunningOrSuccess = step.status == GlassStepStatus.RUNNING || step.status == GlassStepStatus.SUCCESS
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    val effectsEnabled = GlassTheme.effectsEnabled

    Box(
        modifier = modifier
            .clip(GlassShapes.Small)
            .background(stepBackgroundColor(step.status, diagnostics, colors, effectsEnabled))
            .glassOutlineBorder(
                width = StrokeWidth.Hairline,
                color = stepBorderColor(step.status, diagnostics, colors, effectsEnabled),
                shape = GlassShapes.Small,
            )
            .then(clickModifier)
            .padding(Spacing.Small),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
            horizontalAlignment = Alignment.Start,
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(IconSize.Medium)
                        .clip(GlassShapes.Capsule)
                        .background(stepIconBgColor(step.status, diagnostics, colors, effectsEnabled)),
                    contentAlignment = Alignment.Center,
                ) {
                    StepStatusIndicator(
                        status = step.status,
                        index = index,
                        diagnostics = diagnostics,
                    )
                }

                if (step.status == GlassStepStatus.FAILED && onRetry != null) {
                    StepRetryButton(
                        onRetry = onRetry,
                        isBusy = isBusy,
                        diagnostics = diagnostics,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }

            StepTextDetails(
                title = step.shortTitle,
                statusText = step.statusLabel ?: GlassStepperDefaults.defaultStatusLabel(step.status),
                statusColor = stepStatusColor(step.status, diagnostics, colors),
                isHighEmphasis = isRunningOrSuccess,
            )
        }
    }
}

/**
 * Resolves the background surface color for a step node based on its status.
 */
private fun stepBackgroundColor(
    status: GlassStepStatus,
    diagnostics: DiagnosticColors,
    colors: ColorScheme,
    effectsEnabled: Boolean,
): Color = when {
    !effectsEnabled -> when (status) {
        GlassStepStatus.RUNNING -> colors.primaryContainer
        GlassStepStatus.SUCCESS -> colors.surfaceContainerHigh
        GlassStepStatus.FAILED -> colors.surfaceContainerHigh
        GlassStepStatus.WARNING -> colors.surfaceContainerHigh
        else -> colors.surfaceContainer
    }
    status == GlassStepStatus.RUNNING -> colors.primary.copy(alpha = AlphaTokens.GlassSecondaryTint)
    status == GlassStepStatus.SUCCESS -> diagnostics.success.copy(alpha = AlphaTokens.Faint)
    status == GlassStepStatus.FAILED -> diagnostics.error.copy(alpha = AlphaTokens.Faint)
    status == GlassStepStatus.WARNING -> diagnostics.warning.copy(alpha = AlphaTokens.Faint)
    else -> colors.surfaceContainer
}

/**
 * Resolves the border color for a step node based on its status.
 */
private fun stepBorderColor(
    status: GlassStepStatus,
    diagnostics: DiagnosticColors,
    colors: ColorScheme,
    effectsEnabled: Boolean,
): Color = when {
    !effectsEnabled -> when (status) {
        GlassStepStatus.RUNNING -> colors.primary
        GlassStepStatus.SUCCESS -> diagnostics.success
        GlassStepStatus.FAILED -> diagnostics.error
        GlassStepStatus.WARNING -> diagnostics.warning
        else -> colors.outlineVariant
    }
    status == GlassStepStatus.RUNNING -> colors.primary
    status == GlassStepStatus.SUCCESS -> diagnostics.success.copy(alpha = AlphaTokens.Border)
    status == GlassStepStatus.FAILED -> diagnostics.error.copy(alpha = AlphaTokens.Border)
    status == GlassStepStatus.WARNING -> diagnostics.warning.copy(alpha = AlphaTokens.Border)
    else -> colors.outline
}

/**
 * Resolves the icon background color for a step node based on its status.
 */
private fun stepIconBgColor(
    status: GlassStepStatus,
    diagnostics: DiagnosticColors,
    colors: ColorScheme,
    effectsEnabled: Boolean,
): Color = when {
    !effectsEnabled -> when (status) {
        GlassStepStatus.RUNNING -> colors.primaryContainer
        GlassStepStatus.SUCCESS -> diagnostics.success.copy(alpha = AlphaTokens.Hover)
        GlassStepStatus.FAILED -> diagnostics.error.copy(alpha = AlphaTokens.Hover)
        GlassStepStatus.WARNING -> diagnostics.warning.copy(alpha = AlphaTokens.Hover)
        else -> colors.surfaceContainerHighest
    }
    status == GlassStepStatus.RUNNING -> colors.primary.copy(alpha = AlphaTokens.GlassSecondaryTint)
    status == GlassStepStatus.SUCCESS -> diagnostics.success.copy(alpha = AlphaTokens.GlassSecondaryTint)
    status == GlassStepStatus.FAILED -> diagnostics.error.copy(alpha = AlphaTokens.GlassSecondaryTint)
    status == GlassStepStatus.WARNING -> diagnostics.warning.copy(alpha = AlphaTokens.GlassSecondaryTint)
    else -> colors.onSurfaceVariant.copy(alpha = AlphaTokens.Faint)
}

/**
 * Resolves the primary semantic color for a step status.
 */
private fun stepStatusColor(status: GlassStepStatus, diagnostics: DiagnosticColors, colors: ColorScheme): Color =
    when (status) {
        GlassStepStatus.RUNNING -> colors.primary
        GlassStepStatus.SUCCESS -> diagnostics.success
        GlassStepStatus.WARNING -> diagnostics.warning
        GlassStepStatus.FAILED -> diagnostics.error
        else -> colors.onSurfaceVariant
    }

/**
 * Icon or step number indicator for a step node.
 */
@Composable
private fun StepStatusIndicator(
    status: GlassStepStatus,
    index: Int,
    diagnostics: DiagnosticColors,
    modifier: Modifier = Modifier,
) {
    when (status) {
        GlassStepStatus.RUNNING -> CircularProgressIndicator(
            modifier = modifier.size(IconSize.Small),
            strokeWidth = StrokeWidth.Hairline,
            color = MaterialTheme.colorScheme.primary,
        )
        GlassStepStatus.SUCCESS -> Icon(
            painter = AppIcons.CheckPainterResource(),
            contentDescription = null,
            tint = diagnostics.success,
            modifier = modifier.size(IconSize.Small),
        )
        GlassStepStatus.FAILED -> Icon(
            painter = AppIcons.ClearPainterResource(),
            contentDescription = null,
            tint = diagnostics.error,
            modifier = modifier.size(IconSize.Small),
        )
        GlassStepStatus.WARNING -> Icon(
            painter = AppIcons.ideStatusWarning(),
            contentDescription = null,
            tint = diagnostics.warning,
            modifier = modifier.size(IconSize.Small),
        )
        GlassStepStatus.PENDING -> Text(
            text = "$index",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        GlassStepStatus.SKIPPED -> Icon(
            painter = AppIcons.ChevronRightPainterResource(),
            contentDescription = "Skipped",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.size(IconSize.Small),
        )
        GlassStepStatus.CANCELLED -> Icon(
            painter = AppIcons.ClearPainterResource(),
            contentDescription = "Cancelled",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.size(IconSize.Small),
        )
        GlassStepStatus.INTERRUPTED -> Icon(
            painter = AppIcons.ideStatusWarning(),
            contentDescription = "Interrupted",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.size(IconSize.Small),
        )
        GlassStepStatus.RESTORED -> Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(IconSize.Small),
                strokeWidth = StrokeWidth.Hairline,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                painter = AppIcons.CheckPainterResource(),
                contentDescription = "Restored",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AlphaTokens.Muted),
                modifier = Modifier.size(IconSize.Indicator),
            )
        }
    }
}

/**
 * Optional retry button displayed on a failed step node.
 */
@Composable
private fun StepRetryButton(
    onRetry: () -> Unit,
    isBusy: Boolean,
    diagnostics: DiagnosticColors,
    modifier: Modifier = Modifier,
) {
    GlassTooltipArea(
        tooltipText = if (isBusy) "Pipeline operation in progress..." else "Retry this stage",
        modifier = modifier,
    ) {
        GlassIconButton(
            onClick = onRetry,
            enabled = !isBusy,
            size = ComponentSize.PanelHeaderAction,
        ) {
            Icon(
                painter = AppIcons.RefreshPainterResource(),
                contentDescription = "Retry",
                tint = if (!isBusy) diagnostics.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.Small),
            )
        }
    }
}

/**
 * Title and status label text for a step node.
 */
@Composable
private fun StepTextDetails(
    title: String,
    statusText: String,
    statusColor: Color,
    isHighEmphasis: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = if (isHighEmphasis) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )

    Text(
        text = statusText,
        style = MaterialTheme.typography.labelSmall,
        color = statusColor,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}
