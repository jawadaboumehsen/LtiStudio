/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.PasswordStrengthColors
import org.ide.lti.core.designsystem.theme.Spacing
import org.jetbrains.compose.ui.tooling.preview.Preview

@Suppress("LongMethod", "CyclomaticComplexMethod", "MagicNumber")
@Composable
fun PasswordStrengthIndicator(
    state: PasswordStrengthState,
    currentCharacterCount: Int,
    modifier: Modifier = Modifier,
    minimumCharacterCount: Int? = null,
) {
    val widthPercent by animateFloatAsState(
        targetValue = when (state) {
            PasswordStrengthState.NONE -> 0f
            PasswordStrengthState.WEAK_1 -> .25f
            PasswordStrengthState.WEAK_2 -> .5f
            PasswordStrengthState.WEAK_3 -> .66f
            PasswordStrengthState.GOOD -> .82f
            PasswordStrengthState.STRONG -> 1f
            PasswordStrengthState.VERY_STRONG -> 1f
        },
        label = "Width Percent State",
    )
    val indicatorColor = when (state) {
        PasswordStrengthState.NONE -> MaterialTheme.colorScheme.error
        PasswordStrengthState.WEAK_1 -> MaterialTheme.colorScheme.error
        PasswordStrengthState.WEAK_2 -> MaterialTheme.colorScheme.error
        PasswordStrengthState.WEAK_3 -> PasswordStrengthColors.Weak
        PasswordStrengthState.GOOD -> MaterialTheme.colorScheme.primary
        PasswordStrengthState.STRONG -> PasswordStrengthColors.Strong
        PasswordStrengthState.VERY_STRONG -> PasswordStrengthColors.VeryStrong
    }
    val animatedIndicatorColor by animateColorAsState(
        targetValue = indicatorColor,
        label = "Indicator Color State",
    )
    val label = when (state) {
        PasswordStrengthState.NONE -> ""
        PasswordStrengthState.WEAK_1 -> "Weak"
        PasswordStrengthState.WEAK_2 -> "Weak"
        PasswordStrengthState.WEAK_3 -> "Weak"
        PasswordStrengthState.GOOD -> "Good"
        PasswordStrengthState.STRONG -> "Strong"
        PasswordStrengthState.VERY_STRONG -> "Very Strong"
    }
    Column(
        modifier = modifier,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(ComponentSize.ProgressBarHeight)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(pivotFractionX = 0f, pivotFractionY = 0f)
                        scaleX = widthPercent
                    }
                    .drawBehind {
                        drawRect(animatedIndicatorColor)
                    },
            )
        }
        Spacer(Modifier.height(Spacing.ExtraSmall))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            minimumCharacterCount?.let { minCount ->
                MinimumCharacterCount(
                    minimumRequirementMet = currentCharacterCount >= minCount,
                    minimumCharacterCount = minCount,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = indicatorColor,
            )
        }
    }
}

@Composable
private fun MinimumCharacterCount(
    minimumRequirementMet: Boolean,
    minimumCharacterCount: Int,
    modifier: Modifier = Modifier,
) {
    val characterCountColor by animateColorAsState(
        targetValue = if (minimumRequirementMet) {
            PasswordStrengthColors.Strong
        } else {
            MaterialTheme.colorScheme.surfaceDim
        },
        label = "minmumCharacterCountColor",
    )
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = if (minimumRequirementMet) {
                Icons.Default.CheckCircle
            } else {
                Icons.Default.Close
            },
            label = "iconForMinimumCharacterCount",
        ) {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = characterCountColor,
                modifier = Modifier.size(IconSize.Small),
            )
        }
        Spacer(modifier = Modifier.width(Spacing.ExtraExtraSmall))
        Text(
            text = "$minimumCharacterCount characters",
            color = characterCountColor,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

enum class PasswordStrengthState {
    NONE,
    WEAK_1,
    WEAK_2,
    WEAK_3,
    GOOD,
    STRONG,
    VERY_STRONG,
}

@Preview
@Composable
private fun PasswordStrengthIndicatorPreview_minCharMet() {
    LtiTheme {
        PasswordStrengthIndicator(
            state = PasswordStrengthState.WEAK_3,
            currentCharacterCount = 12,
            minimumCharacterCount = 12,
        )
    }
}

@Preview
@Composable
private fun PasswordStrengthIndicatorPreview_minCharNotMet() {
    LtiTheme {
        PasswordStrengthIndicator(
            state = PasswordStrengthState.WEAK_3,
            currentCharacterCount = 11,
            minimumCharacterCount = 12,
        )
    }
}

@Preview
@Composable
private fun PasswordStrengthIndicatorPreview_noMinChar() {
    LtiTheme {
        PasswordStrengthIndicator(
            state = PasswordStrengthState.WEAK_3,
            currentCharacterCount = 12,
        )
    }
}
