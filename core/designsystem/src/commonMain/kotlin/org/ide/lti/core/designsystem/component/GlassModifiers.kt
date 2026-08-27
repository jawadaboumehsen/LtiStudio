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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.BackdropEffectScope
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.vibrancy
import com.kashif_e.backdrop.highlight.Highlight
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BackdropBlur
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth

/**
 * Applies glass morphism effect to any composable.
 *
 * Convenience modifier that wraps [com.kashif_e.backdrop.drawBackdrop] with the
 * standard iOS-style liquid stack (vibrancy + blur + lens), or a custom [effects] block.
 *
 * @param backdrop Backdrop instance (default: from GlassTheme or create new)
 * @param shape Shape of the glass surface (default: [ContinuousRoundedRectangle] with 16dp radius)
 * @param surfaceColor Optional surface color (default: from GlassTheme)
 * @param tint Optional tint color (default: from GlassTheme)
 * @param effects Backdrop effect chain (default: [StandardGlassEffects])
 *
 * Example:
 * ```kotlin
 * Box(
 *     Modifier
 *         .size(200.dp)
 *         .glassEffect()
 * ) {
 *     Text("Glass Content")
 * }
 * ```
 *
 * Example with a custom effect chain:
 * ```kotlin
 * Box(
 *     Modifier.glassEffect(effects = { vibrancy(); blur(8.dp.toPx()); lens(24.dp.toPx(), 48.dp.toPx()) })
 * ) {
 *     Text("Intense Glass")
 * }
 * ```
 */
fun Modifier.glassEffect(
    backdrop: Backdrop? = null,
    shape: Shape = ContinuousRoundedRectangle(CornerRadius.Medium),
    surfaceColor: Color = Color.Unspecified,
    tint: Color = Color.Unspecified,
    effects: BackdropEffectScope.() -> Unit = StandardGlassEffects
): Modifier = composed {
    val glassColors = GlassTheme.colors
    val effectsEnabled = GlassTheme.effectsEnabled
    val actualBackdrop = backdrop ?: GlassTheme.backdrop ?: rememberLayerBackdrop()

    val finalSurfaceColor = if (surfaceColor.isSpecified) surfaceColor else glassColors.glassSurface
    val finalTint = if (tint.isSpecified) tint else glassColors.glassTint

    if (effectsEnabled) {
        this.drawBackdrop(
            backdrop = actualBackdrop,
            shape = { shape },
            effects = effects,
            highlight = { Highlight.Plain },
            onDrawSurface = {
                drawRect(finalSurfaceColor)

                if (finalTint.isSpecified && finalTint.alpha > 0f) {
                    drawRect(finalTint, blendMode = BlendMode.SrcOver)
                }
            }
        )
    } else {
        this
    }
}

/**
 * Applies a simple glass background color without full effects.
 *
 * Useful for performance-critical scenarios where you want the glass aesthetic
 * but don't need the full effect stack.
 *
 * @param color Background color (default: from GlassTheme)
 *
 * Example:
 * ```kotlin
 * Box(
 *     Modifier
 *         .size(100.dp)
 *         .glassBackground()
 * ) {
 *     Text("Simple Glass")
 * }
 * ```
 */
fun Modifier.glassBackground(
    color: Color = Color.Unspecified
): Modifier = composed {
    val glassColors = GlassTheme.colors
    val finalColor = if (color.isSpecified) color else glassColors.glassSurface

    this.drawBehind {
        drawRect(finalColor)
    }
}

/**
 * Applies a glass border to a composable.
 *
 * @param color Border color (default: from GlassTheme)
 * @param width Border width (default: 1.dp)
 * @param shape Shape of the border (default: [ContinuousRoundedRectangle] with 16dp radius)
 *
 * Example:
 * ```kotlin
 * Box(
 *     Modifier
 *         .size(100.dp)
 *         .glassBackground()
 *         .glassBorder()
 * ) {
 *     Text("Glass with Border")
 * }
 * ```
 */
fun Modifier.glassBorder(
    color: Color? = null,
    width: Dp = StrokeWidth.Standard,
    shape: Shape = ContinuousRoundedRectangle(CornerRadius.Medium)
): Modifier = composed {
    val glassColors = GlassTheme.colors
    val finalColor = color ?: glassColors.glassBorder

    this.border(
        border = BorderStroke(width, finalColor),
        shape = shape
    )
}

/**
 * Makes a composable clickable with glass-aware styling.
 *
 * Combines clickable behavior with automatic glass color application
 * for content.
 *
 * @param onClick Callback when clicked
 * @param enabled Whether the element is enabled (default: true)
 * @param role Semantic role for accessibility (default: Button)
 *
 * Example:
 * ```kotlin
 * Box(
 *     Modifier
 *         .glassEffect()
 *         .glassInteractive(onClick = { })
 * ) {
 *     Text("Clickable Glass")
 * }
 * ```
 */
fun Modifier.glassInteractive(
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role? = Role.Button
): Modifier {
    return this.clickable(
        enabled = enabled,
        onClick = onClick,
        role = role
    )
}

/**
 * Applies a subtle glass effect optimized for overlays and dialogs.
 *
 * Uses enhanced blur and reduced refraction for scrim-like surfaces.
 *
 * @param backdrop Backdrop instance (default: from GlassTheme or create new)
 * @param blurRadius Custom blur radius (default: 16.dp)
 * @param surfaceColor Surface color (default: semi-transparent black)
 *
 * Example:
 * ```kotlin
 * Box(
 *     Modifier
 *         .fillMaxSize()
 *         .glassScrim()
 * ) {
 *     // Dialog content
 * }
 * ```
 */
fun Modifier.glassScrim(
    backdrop: Backdrop? = null,
    blurRadius: Dp = BackdropBlur.Scrim,
    surfaceColor: Color = Color.Unspecified
): Modifier = composed {
    val actualBackdrop = backdrop ?: GlassTheme.backdrop ?: rememberLayerBackdrop()
    val effectsEnabled = GlassTheme.effectsEnabled
    val resolvedSurfaceColor = if (surfaceColor.isSpecified) {
        surfaceColor
    } else {
        GlassTheme.colors.glassShadow.copy(alpha = AlphaTokens.Scrim)
    }

    if (effectsEnabled) {
        this.drawBackdrop(
            backdrop = actualBackdrop,
            shape = { androidx.compose.ui.graphics.RectangleShape },
            effects = {
                blur(blurRadius.toPx())
                vibrancy()
            },
            highlight = { Highlight.Plain },
            onDrawSurface = {
                drawRect(resolvedSurfaceColor)
            }
        )
    } else {
        this.drawBehind {
            drawRect(resolvedSurfaceColor)
        }
    }
}
