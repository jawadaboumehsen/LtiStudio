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

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyleScope
import dev.chrisbanes.haze.glass.hazeGlass
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassMaterialStyles
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.HazeShape
import org.ide.lti.core.designsystem.theme.sceneHazeInput

/**
 * Unified design system modifier governing all glass and solid fallback surfaces.
 *
 * Enforces Haze-aligned source layering, optical parity across themes (no theme-branching on optics),
 * and monotonic accessibility.
 *
 * @param role The semantic role determining material optics and fallback elevation.
 * @param shape Corner clipping and refraction boundary shape.
 * @param sourceInput Haze input sources (defaults to wallpaper source [GlassTheme.hazeState]).
 * @param surfaceColor Explicit override for effects-off solid fallback (defaults to role-calibrated container).
 * @param tint Optical glass tint override.
 * @param captureBacking Backdrop backing color composited behind captured content before blur.
 * @param drawBorder Whether to render a hairline boundary border.
 * @param borderColor Border stroke color.
 * @param borderWidth Border stroke thickness.
 * @param styleOverride Optional styling hook to customize or override glass optics.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.glassSurface(
    role: GlassRole = GlassRole.Shell,
    shape: HazeShape = GlassShapes.HazeFlat,
    hazeState: HazeState? = null,
    surfaceColor: Color = Color.Unspecified,
    tint: Color = Color.Unspecified,
    captureBacking: Color = Color.Unspecified,
    drawBorder: Boolean = false,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    borderWidth: Dp = GlassDimens.HairlineBorder,
    styleOverride: (GlassStyleScope.() -> Unit)? = null,
): Modifier {
    val theme = GlassTheme.appTheme
    val effectsEnabled = GlassTheme.effectsEnabled
    val colors = MaterialTheme.colorScheme
    val resolvedHazeState = hazeState ?: GlassTheme.hazeStateOrNull

    val resolvedSurface = when {
        surfaceColor.isSpecified -> surfaceColor
        role == GlassRole.Shell -> colors.surfaceContainer
        role == GlassRole.TextCard -> colors.surfaceContainerHigh
        role == GlassRole.FloatingControl -> colors.surfaceContainerHighest
        else -> colors.surfaceContainer
    }

    val glassModifier = if (effectsEnabled && resolvedHazeState != null) {
        val baseStyle = when (role) {
            GlassRole.FloatingControl -> GlassMaterialStyles.clearStyle(
                theme = theme,
                opticalTint = tint,
                captureBacking = captureBacking,
            )
            else -> GlassMaterialStyles.baseStyle(
                theme = theme,
                opticalTint = tint,
                captureBacking = captureBacking,
            )
        }

        Modifier.hazeGlass(
            input = sceneHazeInput(resolvedHazeState),
            style = remember(theme, shape, tint, captureBacking, role, styleOverride) {
                baseStyle.then {
                    this.shape(shape)
                    styleOverride?.invoke(this)
                }
            },
        )
    } else {
        Modifier.background(resolvedSurface, shape = shape)
    }

    val borderModifier = if (drawBorder) {
        Modifier.glassOutlineBorder(color = borderColor, width = borderWidth, shape = shape)
    } else {
        Modifier
    }

    return this
        .then(glassModifier)
        .clip(shape)
        .then(borderModifier)
}

/**
 * Reusable surface modifier for IDE shell chrome (top bar, status bar,
 * pipeline rail, workspace navigator, card surfaces, and auxiliary sidebar panels).
 *
 * Delegates to the unified [glassSurface] modifier with [GlassRole.Shell].
 */
@Composable
fun Modifier.ideShellSurface(
    shape: HazeShape = GlassShapes.HazeFlat,
    surfaceColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    tint: Color = Color.Unspecified,
    captureBacking: Color = Color.Unspecified,
    drawBorder: Boolean = false,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    borderWidth: Dp = GlassDimens.HairlineBorder,
    hazeState: HazeState? = null,
    styleOverride: (GlassStyleScope.() -> Unit)? = null,
): Modifier = glassSurface(
    role = GlassRole.Shell,
    shape = shape,
    hazeState = hazeState,
    surfaceColor = surfaceColor,
    tint = tint,
    captureBacking = captureBacking,
    drawBorder = drawBorder,
    borderColor = borderColor,
    borderWidth = borderWidth,
    styleOverride = styleOverride,
)
