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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import org.ide.lti.core.designsystem.component.primitives.glass
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.config.AppTheme
import org.ide.lti.core.designsystem.theme.glass.GlassSceneState
import org.ide.lti.core.designsystem.theme.glass.GlassStyles

/**
 * Surface styling modifier for cards hosted within the IDE workspace shell.
 *
 * When [AppTheme.Blue] is active with effects enabled, applies the liquid glass effect
 * stack with backdrop blur, lens refraction, specular highlights, and card elevation shadow.
 * In [AppTheme.Dark] and [AppTheme.Light], renders the opaque design system surface background
 * and hairline border.
 */
@Composable
fun Modifier.ideCardSurface(
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
    shape: Shape = GlassShapes.ShellCard,
    borderWidth: Dp = GlassDimens.HairlineBorder,
    isMuted: Boolean = false,
    backdrop: GlassSceneState? = null,
): Modifier {
    val isBlueGlass = GlassTheme.appTheme == AppTheme.Blue && GlassTheme.effectsEnabled
    val surfaceColor = if (isMuted) colors.cardBackgroundMuted else colors.cardBackground
    val borderColor = if (isMuted) colors.borderMuted else colors.border
    val style = GlassStyles.card.then {
        copy(shape = shape, surface = surfaceColor, border = borderColor, borderWidth = borderWidth)
    }

    return this.glass(style, enabled = isBlueGlass, backdrop = backdrop)
}
