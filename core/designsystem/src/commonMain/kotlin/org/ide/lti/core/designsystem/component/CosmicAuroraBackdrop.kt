/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.kashif_e.backdrop.backdrops.LayerBackdrop
import com.kashif_e.backdrop.backdrops.layerBackdrop
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.ColorContrast
import org.ide.lti.core.designsystem.theme.LtiSeedColor
import org.ide.lti.core.designsystem.theme.PreviewSampleColors

/**
 * Luminous multi-point cosmic aurora backdrop canvas for Liquid Glass refraction.
 *
 * Renders dynamically adapted aurora gradients for both Dark and Light theme modes:
 * - In dark mode: AMOLED true-black base with deep-space nebula glow and soft vignette.
 * - In light mode: clean light base with vibrant Azure, Electric Cyan, and violet nebulae
 *   that preserve saturation and luminous refraction through frosted glass surfaces without
 *   washing out or graying the theme.
 *
 * @param backdrop The [LayerBackdrop] instance to capture the rendered canvas.
 * @param modifier Modifier to be applied to the canvas.
 */
@Composable
fun CosmicAuroraBackdrop(
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = ColorContrast.relativeLuminance(colorScheme.background) < 0.5

    // Runs once per successful recomposition of THIS composable (i.e. once per genuine content
    // change - a theme flip, etc.) - never per draw/frame. Tells every consumer sampling this
    // backdrop (every glass panel) to redraw and pick up the fresh content, even though the
    // shared LayerBackdrop instance's own identity never changes. See notifyContentChanged()'s
    // doc for why this must NOT be called from a draw phase.
    SideEffect {
        backdrop.notifyContentChanged()
    }

    Canvas(
        modifier = modifier
            .layerBackdrop(backdrop)
            .fillMaxSize(),
    ) {
        if (isDark) {
            // 1. Deep space / page base
            drawRect(colorScheme.background)

            // 2. Primary nebula (Top-Center)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        colorScheme.primary.copy(alpha = AlphaTokens.Soft),
                        colorScheme.primaryContainer.copy(alpha = AlphaTokens.Faded),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.40f, size.height * 0.15f),
                    radius = size.width * 0.55f,
                ),
            )

            // 3. Secondary aurora glow (Top-Right)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        colorScheme.secondary.copy(alpha = AlphaTokens.Ambient),
                        colorScheme.secondaryContainer.copy(alpha = AlphaTokens.Hover),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.85f, size.height * 0.20f),
                    radius = size.width * 0.50f,
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
        } else {
            // 1. Light space base
            drawRect(colorScheme.background)

            // 2. Primary nebula (Top-Center) - Saturated Azure & Cyan
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        LtiSeedColor.copy(alpha = AlphaTokens.Ambient),
                        BrandColors.LogoGradientEnd.copy(alpha = AlphaTokens.Faded),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.40f, size.height * 0.15f),
                    radius = size.width * 0.55f,
                ),
            )

            // 3. Secondary aurora glow (Top-Right) - Electric Cyan
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        BrandColors.LogoGradientEnd.copy(alpha = AlphaTokens.Ambient),
                        LtiSeedColor.copy(alpha = AlphaTokens.Hover),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.85f, size.height * 0.20f),
                    radius = size.width * 0.50f,
                ),
            )

            // 4. Tertiary shimmer (Bottom-Left) - Vibrant violet
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        PreviewSampleColors.Purple.copy(alpha = AlphaTokens.Faded),
                        colorScheme.tertiaryContainer.copy(alpha = AlphaTokens.Hover),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.15f, size.height * 0.85f),
                    radius = size.width * 0.50f,
                ),
            )

            // 5. Inverse-primary accent (Bottom-Right) - Soft Azure
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        LtiSeedColor.copy(alpha = AlphaTokens.Hover),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.90f, size.height * 0.90f),
                    radius = size.width * 0.45f,
                ),
            )
        }
    }
}
