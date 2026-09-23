/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.layout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.hazeSource
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.GlassTheme

/**
 * Standard Liquid Glass application backdrop canvas.
 *
 * Renders a fixed cosmic-aurora gradient field as the sampled source for Haze's real-time
 * blur/refraction glass rendering.
 *
 * @param modifier Modifier to be applied to the canvas.
 */
@Composable
fun GlassBackdrop(
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    Canvas(
        modifier = modifier
            .hazeSource(GlassTheme.hazeState)
            .fillMaxSize(),
    ) {
        // 1. Deep space / page base
        drawRect(colorScheme.surface)

        // 2. Primary nebula (Top-Center) - Vibrant Glow
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    colorScheme.primary.copy(alpha = AlphaTokens.Elevated),
                    colorScheme.primaryContainer.copy(alpha = AlphaTokens.Ambient),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.40f, size.height * 0.15f),
                radius = size.width * 0.60f,
            ),
        )

        // 3. Secondary aurora glow (Top-Right)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    colorScheme.secondary.copy(alpha = AlphaTokens.Elevated),
                    colorScheme.secondary.copy(alpha = AlphaTokens.Ambient),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.85f, size.height * 0.20f),
                radius = size.width * 0.55f,
            ),
        )

        // 4. Tertiary shimmer (Bottom-Left)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    colorScheme.tertiary.copy(alpha = AlphaTokens.Ambient),
                    colorScheme.tertiaryContainer.copy(alpha = AlphaTokens.Hover),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.15f, size.height * 0.85f),
                radius = size.width * 0.50f,
            ),
        )

        // 5. Inverse-primary accent (Bottom-Right)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    colorScheme.inversePrimary.copy(alpha = AlphaTokens.Faded),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.90f, size.height * 0.90f),
                radius = size.width * 0.45f,
            ),
        )

        // 6. Smooth Vignette Overlay
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    colorScheme.scrim.copy(alpha = AlphaTokens.Scrim),
                    Color.Transparent,
                    colorScheme.scrim.copy(alpha = AlphaTokens.Half),
                ),
            ),
        )
    }
}
