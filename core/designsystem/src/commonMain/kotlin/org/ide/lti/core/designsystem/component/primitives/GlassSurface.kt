/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.primitives

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import com.kashif_e.backdrop.ResolvedGlassEffect
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.highlight.Highlight
import com.kashif_e.backdrop.shadow.Shadow
import org.ide.lti.core.designsystem.theme.GlassEffects
import org.ide.lti.core.designsystem.theme.GlassHighlights
import org.ide.lti.core.designsystem.theme.GlassShadows
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.glass.GlassSceneState

/**
 * Default glass effect stack ("Standard" look): vibrancy + moderate blur + lens refraction.
 * Delegated to canonical [GlassEffects.Standard].
 */
val StandardGlassEffects: ResolvedGlassEffect = GlassEffects.Standard

/**
 * Base glass surface component. Thin wrapper over [com.kashif_e.backdrop.drawBackdrop].
 *
 * @param modifier Modifier to be applied to the surface
 * @param shape Shape of the glass surface
 * @param backdrop GlassSceneState instance to sample (default: from [GlassTheme] or a new layer)
 * @param surfaceColor Optional surface color overlay (default: from [GlassTheme] colors)
 * @param tint Optional tint color (default: from [GlassTheme] colors)
 * @param borderColor Optional border color
 * @param borderWidth Width of the border (default: [StrokeWidth.Standard])
 * @param effect Effect stack to apply (default: [GlassEffects.Standard])
 * @param highlight Optional highlight decorator (default: [GlassHighlights.Plain])
 * @param shadow Optional shadow decorator (default: [GlassShadows.None])
 * @param content Content to display inside the glass surface
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = GlassShapes.Medium,
    backdrop: GlassSceneState? = null,
    surfaceColor: Color = Color.Unspecified,
    tint: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
    borderWidth: Dp = StrokeWidth.Standard,
    effect: ResolvedGlassEffect = GlassEffects.Standard,
    highlight: () -> Highlight? = GlassHighlights.Plain,
    shadow: (() -> Shadow?)? = GlassShadows.None,
    content: @Composable () -> Unit,
) {
    val glassColors = GlassTheme.colors
    val effectsEnabled = GlassTheme.effectsEnabled
    val actualBackdrop = backdrop ?: GlassTheme.scene

    val finalSurfaceColor = if (surfaceColor.isSpecified) surfaceColor else glassColors.glassSurface
    val finalTint = if (tint.isSpecified) tint else glassColors.glassTint
    val finalBorderColor = if (borderColor.isSpecified) borderColor else Color.Unspecified

    val borderModifier = if (finalBorderColor.isSpecified) {
        Modifier.glassOutlineBorder(
            color = finalBorderColor,
            width = borderWidth,
            shape = shape,
        )
    } else {
        Modifier
    }

    val backgroundModifier = if (effectsEnabled && actualBackdrop != null) {
        Modifier.drawBackdrop(
            backdrop = actualBackdrop.engineBackdrop,
            shape = { shape },
            resolvedEffect = effect,
            highlight = highlight,
            shadow = shadow,
            onDrawSurface = {
                drawRect(finalSurfaceColor)

                if (finalTint.isSpecified && finalTint.alpha > 0f) {
                    drawRect(finalTint, blendMode = BlendMode.SrcOver)
                }
            },
        )
    } else {
        Modifier
            .clip(shape)
            .drawBehind {
                drawRect(finalSurfaceColor)

                if (finalTint.isSpecified && finalTint.alpha > 0f) {
                    drawRect(finalTint, blendMode = BlendMode.SrcOver)
                }
            }
    }

    Box(
        modifier = modifier
            .then(backgroundModifier)
            .then(borderModifier),
    ) {
        CompositionLocalProvider(LocalContentColor provides glassColors.onGlass) {
            content()
        }
    }
}
