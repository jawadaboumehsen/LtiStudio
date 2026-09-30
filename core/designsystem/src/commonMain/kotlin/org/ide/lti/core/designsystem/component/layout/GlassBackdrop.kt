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
import androidx.compose.ui.graphics.drawscope.DrawScope
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
fun GlassBackdrop(modifier: Modifier = Modifier) {
    val appTheme = GlassTheme.appTheme

    Canvas(
        modifier = modifier
            .hazeSource(GlassTheme.hazeState)
            .fillMaxSize(),
    ) {
        when (appTheme) {
            AppTheme.Blue -> drawBlueBackdrop()
            AppTheme.Dark -> drawDarkBackdrop()
            AppTheme.Light -> drawLightBackdrop()
        }
    }
}

/**
 * Draws the celestial blue atmospheric gradient canvas backdrop.
 */
private fun DrawScope.drawBlueBackdrop() {
    // 1. Deep Midnight Space Base
    drawRect(StudioBackdropColors.Base)

    // 2. Royal Sapphire Radiant Hub (Behind TopAppBar & Upper Frame)
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                StudioBackdropColors.PrimaryAura.copy(alpha = AlphaTokens.Vibrant),
                StudioBackdropColors.PrimaryAuraSubtle.copy(alpha = AlphaTokens.Medium),
                Color.Transparent,
            ),
            center = Offset(size.width * 0.50f, size.height * 0.04f),
            radius = size.width * 0.70f,
        ),
    )

    // 3. Luminous Cyan / Teal Field (Smooth Continuous Left Frame & Navigator)
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                StudioBackdropColors.SidebarAura.copy(alpha = AlphaTokens.Strong),
                StudioBackdropColors.CyanAuraSubtle.copy(alpha = AlphaTokens.Ambient),
                Color.Transparent,
            ),
            center = Offset(size.width * 0.04f, size.height * 0.45f),
            radius = size.width * 0.25f,
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

    // 7. Ambient Obsidian Vignette (soft baseline grounding, keeping status bar luminous)
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                StudioBackdropColors.Vignette.copy(alpha = AlphaTokens.Muted),
            ),
        ),
    )
}

/**
 * Draws the dark cosmic indigo gradient canvas backdrop.
 */
private fun DrawScope.drawDarkBackdrop() {
    // 1. Deep Obsidian Space Base
    drawRect(DarkBackdropColors.Base)

    // 2. Cosmic Indigo Radiant Hub (Behind TopAppBar & Upper Frame)
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                DarkBackdropColors.IndigoAura.copy(alpha = AlphaTokens.Vibrant),
                DarkBackdropColors.IndigoAuraSubtle.copy(alpha = AlphaTokens.Medium),
                Color.Transparent,
            ),
            center = Offset(size.width * 0.50f, size.height * 0.04f),
            radius = size.width * 0.70f,
        ),
    )

    // 3. Deep Celestial Cyan/Teal (Smooth Continuous Left Frame & Navigator)
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                DarkBackdropColors.CyanAura.copy(alpha = AlphaTokens.Strong),
                DarkBackdropColors.CyanAuraSubtle.copy(alpha = AlphaTokens.Ambient),
                Color.Transparent,
            ),
            center = Offset(size.width * 0.04f, size.height * 0.45f),
            radius = size.width * 0.25f,
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

    // 7. Ambient Vignette
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                DarkBackdropColors.Vignette.copy(alpha = AlphaTokens.Muted),
            ),
        ),
    )
}

/**
 * Draws the light frosted porcelain and prismatic gradient canvas backdrop.
 */
private fun DrawScope.drawLightBackdrop() {
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
