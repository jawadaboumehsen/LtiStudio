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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.kashif_e.backdrop.backdrops.layerBackdrop
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.StudioBackdropColors
import org.ide.lti.core.designsystem.theme.glass.GlassSceneState

/**
 * Saturated, multi-depth canvas for the professional IDE shell.
 *
 * The canvas remains the sampled source for real backdrop blur/refraction. Its concentrated
 * blue light fields deliberately sit behind chrome and navigation so translucent surfaces read
 * as glass instead of flat charcoal.
 */
@Composable
fun StudioGlassBackdrop(backdrop: GlassSceneState, modifier: Modifier = Modifier) {
    SideEffect { backdrop.engineBackdrop.notifyContentChanged() }

    Canvas(
        modifier = modifier
            .layerBackdrop(backdrop.engineBackdrop)
            .fillMaxSize(),
    ) {
        drawRect(StudioBackdropColors.Base)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    StudioBackdropColors.PrimaryAura.copy(alpha = AlphaTokens.Prominent),
                    StudioBackdropColors.CyanAura.copy(alpha = AlphaTokens.Muted),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.48f, size.height * 0.05f),
                radius = size.width * 0.68f,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    StudioBackdropColors.SidebarAura.copy(alpha = AlphaTokens.Strong),
                    StudioBackdropColors.SidebarAura.copy(alpha = AlphaTokens.Hover),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.04f, size.height * 0.58f),
                radius = size.width * 0.34f,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    StudioBackdropColors.CyanAura.copy(alpha = AlphaTokens.Ambient),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.90f, size.height * 0.18f),
                radius = size.width * 0.42f,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    StudioBackdropColors.VioletAura.copy(alpha = AlphaTokens.Faded),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.20f, size.height * 0.94f),
                radius = size.width * 0.46f,
            ),
        )
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
}
