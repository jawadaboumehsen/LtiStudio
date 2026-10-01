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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.StrokeWidth

/**
 * Standardized Liquid Glass Horizontal Divider.
 *
 * @param modifier Optional modifier.
 * @param thickness Divider thickness (defaults to [StrokeWidth.Hairline]).
 * @param specular Whether to apply a subtle horizontal specular light gradient beam.
 * @param color Override color (defaults to [MaterialTheme.colorScheme.outline] with faint alpha).
 */
@Composable
fun GlassHorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = StrokeWidth.Hairline,
    specular: Boolean = false,
    color: Color = Color.Unspecified,
) {
    val effectsEnabled = GlassTheme.effectsEnabled
    val dividerModifier = if (specular && effectsEnabled) {
        modifier
            .fillMaxWidth()
            .height(thickness)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Hover),
                        MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Prominent),
                        MaterialTheme.colorScheme.outline.copy(alpha = AlphaTokens.Hover),
                    ),
                ),
            )
    } else {
        val finalColor = when {
            color != Color.Unspecified -> color
            effectsEnabled -> MaterialTheme.colorScheme.outline.copy(alpha = AlphaTokens.Faint)
            else -> MaterialTheme.colorScheme.outlineVariant
        }
        modifier
            .fillMaxWidth()
            .height(thickness)
            .background(finalColor)
    }

    Box(modifier = dividerModifier)
}

/**
 * Standardized Liquid Glass Vertical Divider.
 *
 * @param modifier Optional modifier.
 * @param thickness Divider thickness (defaults to [StrokeWidth.Hairline]).
 * @param specular Whether to apply a subtle vertical specular light gradient beam.
 * @param color Override color (defaults to [MaterialTheme.colorScheme.outline] with faint alpha).
 */
@Composable
fun GlassVerticalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = StrokeWidth.Hairline,
    specular: Boolean = false,
    color: Color = Color.Unspecified,
) {
    val effectsEnabled = GlassTheme.effectsEnabled
    val dividerModifier = if (specular && effectsEnabled) {
        modifier
            .fillMaxHeight()
            .width(thickness)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Hover),
                        MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Prominent),
                        MaterialTheme.colorScheme.outline.copy(alpha = AlphaTokens.Hover),
                    ),
                ),
            )
    } else {
        val finalColor = when {
            color != Color.Unspecified -> color
            effectsEnabled -> MaterialTheme.colorScheme.outline.copy(alpha = AlphaTokens.Faint)
            else -> MaterialTheme.colorScheme.outlineVariant
        }
        modifier
            .fillMaxHeight()
            .width(thickness)
            .background(finalColor)
    }

    Box(modifier = dividerModifier)
}
