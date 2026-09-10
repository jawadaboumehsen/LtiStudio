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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.kashif_e.backdrop.Backdrop
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.tooltip.GlassTooltipArea
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.icon.ideStatusWarning
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
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
    backdrop: Backdrop? = null,
) {
    val glassColors = GlassTheme.colors
    val diagnostics = glassColors.diagnostics

    CompositionLocalProvider(LocalContentColor provides glassColors.onGlass) {
        when (orientation) {
            GlassStepperOrientation.Horizontal -> {
                Row(
                    modifier = modifier.fillMaxWidth(),
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
                            modifier = Modifier.weight(1f),
                        )

                        if (index < steps.size - 1) {
                            val isPreviousComplete = step.status == GlassStepStatus.SUCCESS
                            Icon(
                                painter = AppIcons.ChevronRightPainterResource(),
                                contentDescription = null,
                                tint = if (isPreviousComplete) {
                                    diagnostics.success.copy(alpha = AlphaTokens.Border)
                                } else {
                                    glassColors.onGlassMuted.copy(alpha = AlphaTokens.Faint)
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
                            val isPreviousComplete = step.status == GlassStepStatus.SUCCESS
                            Icon(
                                painter = AppIcons.ChevronDownPainterResource(),
                                contentDescription = null,
                                tint = if (isPreviousComplete) {
                                    diagnostics.success.copy(alpha = AlphaTokens.Border)
                                } else {
                                    glassColors.onGlassMuted.copy(alpha = AlphaTokens.Faint)
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
    val glassColors = GlassTheme.colors
    val diagnostics = glassColors.diagnostics

    val isSuccess = step.status == GlassStepStatus.SUCCESS
    val isRunning = step.status == GlassStepStatus.RUNNING
    val isFailed = step.status == GlassStepStatus.FAILED
    val isWarning = step.status == GlassStepStatus.WARNING

    val statusColor = when (step.status) {
        GlassStepStatus.RUNNING -> glassColors.glassAccent
        GlassStepStatus.SUCCESS -> diagnostics.success
        GlassStepStatus.WARNING -> diagnostics.warning
        GlassStepStatus.FAILED -> diagnostics.error
        GlassStepStatus.PENDING -> glassColors.onGlassMuted
    }

    val backgroundColor = when {
        isRunning -> glassColors.glassAccent.copy(alpha = AlphaTokens.GlassSecondaryTint)
        isSuccess -> diagnostics.success.copy(alpha = AlphaTokens.Faint)
        isFailed -> diagnostics.error.copy(alpha = AlphaTokens.Faint)
        isWarning -> diagnostics.warning.copy(alpha = AlphaTokens.Faint)
        else -> glassColors.glassSurface
    }

    val borderColor = when {
        isRunning -> glassColors.glassAccent
        isSuccess -> diagnostics.success.copy(alpha = AlphaTokens.Border)
        isFailed -> diagnostics.error.copy(alpha = AlphaTokens.Border)
        isWarning -> diagnostics.warning.copy(alpha = AlphaTokens.Border)
        else -> glassColors.glassBorder
    }

    val iconBgColor = when {
        isRunning -> glassColors.glassAccent.copy(alpha = AlphaTokens.GlassSecondaryTint)
        isSuccess -> diagnostics.success.copy(alpha = AlphaTokens.GlassSecondaryTint)
        isFailed -> diagnostics.error.copy(alpha = AlphaTokens.GlassSecondaryTint)
        isWarning -> diagnostics.warning.copy(alpha = AlphaTokens.GlassSecondaryTint)
        else -> glassColors.onGlassMuted.copy(alpha = AlphaTokens.Faint)
    }

    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(GlassShapes.Small)
            .background(backgroundColor)
            .border(
                width = StrokeWidth.Hairline,
                color = borderColor,
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
                // Status Icon or Index Indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(IconSize.Medium)
                        .clip(GlassShapes.Capsule)
                        .background(iconBgColor),
                    contentAlignment = Alignment.Center,
                ) {
                    when (step.status) {
                        GlassStepStatus.RUNNING -> CircularProgressIndicator(
                            modifier = Modifier.size(IconSize.Small),
                            strokeWidth = StrokeWidth.Hairline,
                            color = glassColors.glassAccent,
                        )
                        GlassStepStatus.SUCCESS -> Icon(
                            painter = AppIcons.CheckPainterResource(),
                            contentDescription = null,
                            tint = diagnostics.success,
                            modifier = Modifier.size(IconSize.Small),
                        )
                        GlassStepStatus.FAILED -> Icon(
                            painter = AppIcons.ClearPainterResource(),
                            contentDescription = null,
                            tint = diagnostics.error,
                            modifier = Modifier.size(IconSize.Small),
                        )
                        GlassStepStatus.WARNING -> Icon(
                            painter = AppIcons.ideStatusWarning(),
                            contentDescription = null,
                            tint = diagnostics.warning,
                            modifier = Modifier.size(IconSize.Small),
                        )
                        GlassStepStatus.PENDING -> Text(
                            text = "$index",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = glassColors.onGlassMuted,
                        )
                    }
                }

                if (isFailed && onRetry != null) {
                    GlassTooltipArea(
                        tooltipText = if (isBusy) "Pipeline operation in progress..." else "Retry this stage",
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        GlassIconButton(
                            onClick = onRetry,
                            enabled = !isBusy,
                            size = ComponentSize.PanelHeaderAction,
                        ) {
                            Icon(
                                painter = AppIcons.RefreshPainterResource(),
                                contentDescription = "Retry",
                                tint = if (!isBusy) diagnostics.error else glassColors.onGlassMuted,
                                modifier = Modifier.size(IconSize.Small),
                            )
                        }
                    }
                }
            }

            Text(
                text = step.shortTitle,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (isRunning || isSuccess) glassColors.onGlass else glassColors.onGlassSecondary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = step.statusLabel ?: GlassStepperDefaults.defaultStatusLabel(step.status),
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
