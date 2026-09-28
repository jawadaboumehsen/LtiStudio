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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.hazeGlass
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.HazeShape
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.sceneHazeInput

/**
 * Base glass surface component - built on Haze's real `Modifier.hazeGlass`.
 *
 * @param modifier Modifier to be applied to the surface
 * @param shape Shape of the glass surface
 * @param surfaceColor Optional surface color overlay (default: [MaterialTheme.colorScheme.surfaceContainer])
 * @param tint Optional tint color
 * @param borderColor Optional border color
 * @param borderWidth Width of the border (default: [StrokeWidth.Standard])
 * @param content Content to display inside the glass surface
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: HazeShape = GlassShapes.HazePanel,
    surfaceColor: Color = Color.Unspecified,
    tint: Color = Color.Unspecified,
    captureBacking: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
    borderWidth: Dp = StrokeWidth.Standard,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val theme = GlassTheme.appTheme
    val resolvedSurface = if (surfaceColor.isSpecified) surfaceColor else colors.surfaceContainer

    val borderModifier = if (borderColor.isSpecified) {
        Modifier.glassOutlineBorder(color = borderColor, width = borderWidth, shape = shape)
    } else {
        Modifier
    }

    val glassOrSolidModifier = if (GlassTheme.effectsEnabled) {
        Modifier.hazeGlass(
            input = sceneHazeInput(GlassTheme.hazeState),
            style = remember(theme, shape, tint, captureBacking) {
                org.ide.lti.core.designsystem.theme.GlassMaterialStyles.baseStyle(
                    theme = theme,
                    opticalTint = tint,
                    captureBacking = captureBacking,
                ).then {
                    this.shape(shape)
                }
            },
        )
    } else {
        Modifier.background(resolvedSurface, shape = shape)
    }

    Box(
        modifier = modifier
            .then(glassOrSolidModifier)
            .clip(shape)
            .then(borderModifier),
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
            content()
        }
    }
}
