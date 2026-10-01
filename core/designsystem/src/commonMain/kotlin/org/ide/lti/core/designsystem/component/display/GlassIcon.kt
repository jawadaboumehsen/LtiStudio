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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.hazeGlass
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.sceneHazeInput

/**
 * Circular glass icon button - built on Haze's real `Modifier.hazeGlass`, same
 * `hovered { } / pressed { }` pattern as [org.ide.lti.core.designsystem.component.actions.GlassButton].
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun GlassIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    icon: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val theme = GlassTheme.appTheme
    val finalContentColor = if (contentColor.isSpecified) contentColor else colors.onSurface
    val resolvedSurface = if (surfaceColor.isSpecified) surfaceColor else colors.surfaceContainer
    val interactionSource = remember { MutableInteractionSource() }

    val surfaceModifier = if (GlassTheme.effectsEnabled) {
        Modifier.hazeGlass(
            input = sceneHazeInput(GlassTheme.hazeState),
            style = remember(theme, tint) {
                org.ide.lti.core.designsystem.theme.GlassMaterialStyles.baseStyle(
                    theme = theme,
                    opticalTint = tint,
                ).then {
                    shape(GlassShapes.HazeCapsule)
                    hovered { lightingIntensity(0.35f) }
                    pressed { scale(0.94f) }
                }
            },
            interactionSource = interactionSource,
            interactionReducedMotionPolicy = GlassTheme.reducedMotionPolicy,
        )
    } else {
        Modifier.background(resolvedSurface, GlassShapes.HazeCapsule)
    }

    Box(
        modifier = modifier
            .then(surfaceModifier)
            .clip(GlassShapes.HazeCapsule)
            .glassOutlineBorder(
                color = colors.outline,
                width = StrokeWidth.Hairline,
                shape = GlassShapes.HazeCapsule,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides finalContentColor) {
            icon()
        }
    }
}
