/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.plugins.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth

/**
 * 3-step progress stepper matching Concept 14 (Review package installation).
 */
@Composable
public fun PluginStepper(currentStep: ImportStep, modifier: Modifier = Modifier) {
    val steps = ImportStep.entries

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Large, vertical = Spacing.ExtraSmall),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.Center,
    ) {
        steps.forEachIndexed { index, step ->
            val isCompleted = step.stepNumber < currentStep.stepNumber ||
                (step == ImportStep.INSPECT && currentStep == ImportStep.REVIEW)
            val isCurrent = step == currentStep

            StepperStepNode(
                step = step,
                isCompleted = isCompleted,
                isCurrent = isCurrent,
            )

            if (index < steps.size - 1) {
                val isLineActive = step.stepNumber < currentStep.stepNumber
                StepperConnector(
                    isLineActive = isLineActive,
                    modifier = Modifier.padding(top = Spacing.MediumSmall),
                )
            }
        }
    }
}

@Composable
private fun StepperStepNode(
    step: ImportStep,
    isCompleted: Boolean,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
) {
    val circleColor = when {
        isCompleted -> GlassTheme.diagnosticColors.success
        isCurrent -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }

    val contentColor = when {
        isCompleted || isCurrent -> BrandColors.OnLogoBadge
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusLabel = when {
        isCompleted -> "Completed"
        isCurrent -> "Current"
        else -> "Pending"
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
    ) {
        Box(
            modifier = Modifier
                .size(ComponentSize.PluginStepperNodeSize)
                .clip(GlassShapes.Circle)
                .background(circleColor)
                .glassOutlineBorder(
                    width = StrokeWidth.Hairline,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = GlassShapes.Circle,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isCompleted) {
                Icon(
                    painter = AppIcons.CheckPainterResource(),
                    contentDescription = "Completed",
                    tint = BrandColors.OnLogoBadge,
                    modifier = Modifier.size(IconSize.Small),
                )
            } else {
                Text(
                    text = step.stepNumber.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
            }
        }

        Text(
            text = step.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
            color = if (isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelSmall,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StepperConnector(isLineActive: Boolean, modifier: Modifier = Modifier) {
    val lineColor = if (isLineActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    Box(
        modifier = modifier
            .width(ComponentSize.PluginStepperConnectorWidth)
            .padding(horizontal = Spacing.Small)
            .height(StrokeWidth.Focused)
            .background(lineColor),
    )
}
