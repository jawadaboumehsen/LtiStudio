/*
 * Copyright 2026 Mifos Initiative
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.hazeSource
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.DarkBackdropColors
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.LightBackdropColors
import org.ide.lti.core.designsystem.theme.StudioBackdropColors

/**
 * Standard Liquid Glass application backdrop canvas.
 *
 * Renders a theme-adaptive canvas as the sampled source for Haze's real-time
 * blur/refraction glass rendering.
 *
 * For [AppTheme.Blue], preserves verbatim the saturated cosmic-aurora light fields of the
 * professional IDE shell so translucent surfaces read as rich glass instead of flat charcoal.
 * For [AppTheme.Dark] and [AppTheme.Light], adapts dynamically to the active MaterialTheme palette.
 *
 * @param modifier Modifier to be applied to the canvas.
 */
@Composable
fun GlassBackdrop(
    modifier: Modifier = Modifier,
) {
    val appTheme = GlassTheme.appTheme

    Canvas(
        modifier = modifier
            .hazeSource(GlassTheme.hazeState)
            .fillMaxSize(),
    ) {
        when (appTheme) {
            AppTheme.Blue -> {
                // 1. Deep Midnight Space Base
                drawRect(StudioBackdropColors.Base)

                // 2. Royal Sapphire Radiant Hub (Behind TopAppBar)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            StudioBackdropColors.PrimaryAura.copy(alpha = AlphaTokens.Vibrant),
                            StudioBackdropColors.PrimaryAuraSubtle.copy(alpha = AlphaTokens.Medium),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.50f, size.height * 0.04f),
                        radius = size.width * 0.65f,
                    ),
                )

                // 3. Luminous Cyan / Teal Field (Behind Rail / Navigator)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            StudioBackdropColors.SidebarAura.copy(alpha = AlphaTokens.Prominent),
                            StudioBackdropColors.CyanAuraSubtle.copy(alpha = AlphaTokens.Muted),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.05f, size.height * 0.50f),
                        radius = size.width * 0.38f,
                    ),
                )

                // 4. Cosmic Violet / Deep Indigo Glow (Bottom-Right)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            StudioBackdropColors.VioletAura.copy(alpha = AlphaTokens.Strong),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.85f, size.height * 0.75f),
                        radius = size.width * 0.50f,
                    ),
                )

                // 5. Subtle Warm Amber Star Core (Bottom-Left)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            StudioBackdropColors.AmberAura.copy(alpha = AlphaTokens.Ambient),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.22f, size.height * 0.90f),
                        radius = size.width * 0.40f,
                    ),
                )

                // 6. Central Workspace Celestial Aurora (provides organic spatial variation beneath center cards)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            StudioBackdropColors.PrimaryAuraSubtle.copy(alpha = AlphaTokens.Ambient),
                            StudioBackdropColors.CyanAuraSubtle.copy(alpha = AlphaTokens.Subtle),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.52f, size.height * 0.48f),
                        radius = size.width * 0.45f,
                    ),
                )

                // 7. Deep Obsidian Vignette
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            StudioBackdropColors.Vignette.copy(alpha = AlphaTokens.Intense),
                        ),
                    ),
                )
            }
            AppTheme.Dark -> {
                // 1. Deep Obsidian Space Base
                drawRect(DarkBackdropColors.Base)

                // 2. Cosmic Indigo Radiant Hub (Behind TopAppBar)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            DarkBackdropColors.IndigoAura.copy(alpha = AlphaTokens.Vibrant),
                            DarkBackdropColors.IndigoAuraSubtle.copy(alpha = AlphaTokens.Medium),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.50f, size.height * 0.04f),
                        radius = size.width * 0.65f,
                    ),
                )

                // 3. Deep Celestial Cyan/Teal (Behind Rail/Navigator)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            DarkBackdropColors.CyanAura.copy(alpha = AlphaTokens.Prominent),
                            DarkBackdropColors.CyanAuraSubtle.copy(alpha = AlphaTokens.Muted),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.05f, size.height * 0.50f),
                        radius = size.width * 0.38f,
                    ),
                )

                // 4. Amethyst/Cosmic Violet (Bottom-Right glow)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            DarkBackdropColors.AmethystAura.copy(alpha = AlphaTokens.Strong),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.85f, size.height * 0.75f),
                        radius = size.width * 0.50f,
                    ),
                )

                // 5. Subtle Warm Amber Star Core (Bottom-Left)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            DarkBackdropColors.AmberAura.copy(alpha = AlphaTokens.Ambient),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.22f, size.height * 0.90f),
                        radius = size.width * 0.40f,
                    ),
                )

                // 6. Central Workspace Celestial Aurora (provides organic spatial variation beneath center cards)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            DarkBackdropColors.IndigoAuraSubtle.copy(alpha = AlphaTokens.Ambient),
                            DarkBackdropColors.CyanAuraSubtle.copy(alpha = AlphaTokens.Subtle),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.52f, size.height * 0.48f),
                        radius = size.width * 0.45f,
                    ),
                )

                // 7. Vignette
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            DarkBackdropColors.Vignette.copy(alpha = AlphaTokens.Intense),
                        ),
                    ),
                )
            }
            AppTheme.Light -> {
                // 1. Frosted Architectural Porcelain Base
                drawRect(LightBackdropColors.Base)

                // 2. Prismatic Sky Cyan Bloom (Behind TopAppBar / Header)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            LightBackdropColors.SkyBloom.copy(alpha = AlphaTokens.Intense),
                            LightBackdropColors.SkyBloomSubtle.copy(alpha = AlphaTokens.Medium),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.50f, size.height * 0.05f),
                        radius = size.width * 0.65f,
                    ),
                )

                // 3. Prismatic Lilac Bloom (Behind Rail / Navigator)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            LightBackdropColors.LilacBloom.copy(alpha = AlphaTokens.Vibrant),
                            LightBackdropColors.LilacBloomSubtle.copy(alpha = AlphaTokens.Ambient),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.05f, size.height * 0.52f),
                        radius = size.width * 0.38f,
                    ),
                )

                // 4. Soft Champagne/Peach Bloom (Bottom-Right)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            LightBackdropColors.PeachBloom.copy(alpha = AlphaTokens.Prominent),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.88f, size.height * 0.80f),
                        radius = size.width * 0.48f,
                    ),
                )

                // 5. Central Workspace Soft Prismatic Bloom
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            LightBackdropColors.SkyBloomSubtle.copy(alpha = AlphaTokens.Muted),
                            LightBackdropColors.LilacBloomSubtle.copy(alpha = AlphaTokens.Subtle),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.52f, size.height * 0.48f),
                        radius = size.width * 0.45f,
                    ),
                )

                // 6. Subtle Soft Light Vignette
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            LightBackdropColors.Vignette.copy(alpha = AlphaTokens.Medium),
                        ),
                    ),
                )
            }
        }
    }
}
