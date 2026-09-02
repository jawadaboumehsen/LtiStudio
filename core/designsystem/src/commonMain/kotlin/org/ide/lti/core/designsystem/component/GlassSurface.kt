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
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.BackdropEffectScope
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.vibrancy
import com.kashif_e.backdrop.highlight.Highlight
import com.kashif_e.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousRoundedRectangle
import org.ide.lti.core.designsystem.theme.BackdropBlur
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.LensRefraction
import org.ide.lti.core.designsystem.theme.StrokeWidth

/**
 * Default glass effect stack ("Standard" look): vibrancy + moderate blur + lens refraction.
 * Matches the kmp-liquid-glass skill's canonical iOS-style liquid stack.
 */
val StandardGlassEffects: BackdropEffectScope.() -> Unit = {
    vibrancy()
    blur(BackdropBlur.Standard.toPx())
    lens(LensRefraction.Surface.height.toPx(), LensRefraction.Surface.amount.toPx())
}

/**
 * Base glass surface component. Thin wrapper over [com.kashif_e.backdrop.drawBackdrop] —
 * callers supply their own tuned [effects] block (see the kmp-liquid-glass skill's
 * per-component effect ordering guidance) instead of a shared preset object.
 *
 * @param modifier Modifier to be applied to the surface
 * @param shape Shape of the glass surface
 * @param backdrop Backdrop instance to sample (default: from [GlassTheme] or a new layer)
 * @param surfaceColor Optional surface color overlay (default: from [GlassTheme] colors)
 * @param tint Optional tint color (default: from [GlassTheme] colors)
 * @param borderColor Optional border color
 * @param borderWidth Width of the border (default: [StrokeWidth.Standard])
 * @param effects Backdrop effect chain, e.g. `{ vibrancy(); blur(4.dp.toPx()); lens(16.dp.toPx(), 32.dp.toPx()) }`
 * @param highlight Optional highlight decorator (default: [Highlight.Plain])
 * @param content Content to display inside the glass surface
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = ContinuousRoundedRectangle(CornerRadius.Medium),
    backdrop: Backdrop? = null,
    surfaceColor: Color = Color.Unspecified,
    tint: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
    borderWidth: Dp = StrokeWidth.Standard,
    effects: BackdropEffectScope.() -> Unit = StandardGlassEffects,
    highlight: () -> Highlight? = { Highlight.Plain },
    shadow: (() -> Shadow?)? = null,
    content: @Composable () -> Unit,
) {
    val glassColors = GlassTheme.colors
    val effectsEnabled = GlassTheme.effectsEnabled
    val actualBackdrop = backdrop ?: GlassTheme.backdrop

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
            backdrop = actualBackdrop,
            shape = { shape },
            effects = effects,
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
