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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.kashif_e.backdrop.backdrops.layerBackdrop
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.PreviewSampleColors
import org.ide.lti.core.designsystem.theme.config.BackdropStyle
import org.ide.lti.core.designsystem.theme.glass.GlassSceneState

/**
 * Standard Liquid Glass application backdrop canvas.
 *
 * Renders the active [GlassTheme.backdropConfig] polymorphically onto a canvas captured
 * by [backdrop] for real-time GPU glass refraction and specular sampling.
 *
 * @param backdrop The [GlassSceneState] instance capturing the rendered canvas for glass sampling.
 * @param modifier Modifier to be applied to the canvas.
 */
@Composable
fun GlassBackdrop(
    backdrop: GlassSceneState,
    modifier: Modifier = Modifier,
) {
    val backdropConfig = GlassTheme.backdropConfig
    val colorScheme = MaterialTheme.colorScheme

    // Runs once per successful recomposition of THIS composable (i.e. once per genuine content
    // change - a theme flip, etc.) - never per draw/frame. Tells every consumer sampling this
    // backdrop (every glass panel) to redraw and pick up the fresh content, even though the
    // shared GlassSceneState instance's own identity never changes.
    SideEffect {
        backdrop.engineBackdrop.notifyContentChanged()
    }

    Canvas(
        modifier = modifier
            .layerBackdrop(backdrop.engineBackdrop)
            .fillMaxSize(),
    ) {
        when (val style = backdropConfig.style) {
            is BackdropStyle.CosmicAurora -> {
                // 1. Deep space / page base
                drawRect(backdropConfig.baseColor)

                // 2. Primary nebula (Top-Center) - Vibrant Glow
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            style.nebulaTint.copy(alpha = AlphaTokens.Prominent),
                            colorScheme.primary.copy(alpha = AlphaTokens.Elevated),
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
                            style.secondaryTint.copy(alpha = AlphaTokens.Elevated),
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
            is BackdropStyle.Solid -> {
                drawRect(style.color)
            }
            is BackdropStyle.FrostedLight -> {
                // 1. Light space base
                drawRect(backdropConfig.baseColor)

                // 2. Primary nebula (Top-Center) - Saturated Azure & Cyan
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            style.nebulaTint.copy(alpha = AlphaTokens.Ambient),
                            style.secondaryTint.copy(alpha = AlphaTokens.Faded),
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
                            style.secondaryTint.copy(alpha = AlphaTokens.Ambient),
                            style.nebulaTint.copy(alpha = AlphaTokens.Hover),
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
                            style.nebulaTint.copy(alpha = AlphaTokens.Hover),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.90f, size.height * 0.90f),
                        radius = size.width * 0.45f,
                    ),
                )
            }
        }
    }
}
