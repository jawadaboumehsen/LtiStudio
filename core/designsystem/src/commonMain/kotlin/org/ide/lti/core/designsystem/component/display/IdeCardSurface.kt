/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.display

import androidx.compose.foundation.background
import org.ide.lti.core.designsystem.theme.HazeShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme

/**
 * Surface styling modifier for cards hosted within the IDE workspace shell - built on Haze's
 * real `Modifier.hazeGlass`.
 *
 * When [AppTheme.Blue] is active, applies the real glass material. In [AppTheme.Dark] and
 * [AppTheme.Light], renders the opaque design system surface background and hairline border only
 * (matching the rest of the shell chrome, which stays flat outside Blue).
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.ideCardSurface(
    shape: HazeShape = GlassShapes.HazeCard,
    borderWidth: Dp = GlassDimens.HairlineBorder,
    isMuted: Boolean = false,
): Modifier {
    val isBlueGlass = GlassTheme.appTheme == AppTheme.Blue
    val colors = MaterialTheme.colorScheme
    val surfaceColor = if (isMuted) colors.surfaceContainer else colors.surfaceContainerHigh
    val borderColor = if (isMuted) colors.outlineVariant else colors.outline

    return this
        .clip(shape)
        .then(
            if (isBlueGlass) {
                Modifier.hazeGlass(
                    input = HazeInput.Backdrop(GlassTheme.hazeState),
                    style = remember(shape, surfaceColor) {
                        GlassStyle.regular.then {
                            this.shape(shape)
                            backgroundColor(surfaceColor)
                        }
                    },
                )
            } else {
                Modifier.background(surfaceColor)
            },
        )
        .glassOutlineBorder(color = borderColor, width = borderWidth, shape = shape)
}
