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

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import org.ide.lti.core.designsystem.component.layout.ideShellSurface
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.HazeShape
import org.ide.lti.core.designsystem.theme.Spacing

/**
 * Surface styling modifier for cards hosted within the IDE workspace shell - built on Haze's
 * real `Modifier.hazeGlass` via [ideShellSurface].
 *
 * Cards render real glass across Blue, Dark, and Light themes, falling back to flat solid
 * surfaces when glass effects are disabled.
 */
@Composable
fun Modifier.ideCardSurface(
    shape: HazeShape = GlassShapes.HazeCard,
    borderWidth: Dp = GlassDimens.HairlineBorder,
    isMuted: Boolean = false,
): Modifier {
    val colors = MaterialTheme.colorScheme
    val surfaceColor = if (isMuted) colors.surfaceContainer else colors.surfaceContainerHigh
    val borderColor = if (isMuted) colors.outlineVariant else colors.outline

    return this.ideShellSurface(
        shape = shape,
        surfaceColor = surfaceColor,
        drawBorder = true,
        borderColor = borderColor,
        borderWidth = borderWidth,
    )
}

/**
 * Surface styling modifier for shell cards, applying clipping to [shape], glass card surface,
 * and standard internal [padding].
 */
@Composable
fun Modifier.ideShellCardSurface(
    shape: HazeShape = GlassShapes.ShellCard,
    borderWidth: Dp = GlassDimens.HairlineBorder,
    isMuted: Boolean = false,
    padding: Dp = Spacing.Medium,
): Modifier = this
    .clip(shape)
    .ideCardSurface(shape = shape, borderWidth = borderWidth, isMuted = isMuted)
    .padding(padding)
