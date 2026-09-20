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
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.drawBackdrop
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.theme.GlassEffects
import org.ide.lti.core.designsystem.theme.GlassHighlights
import org.ide.lti.core.designsystem.theme.GlassShadows
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.IdeShellDimens
import org.ide.lti.core.designsystem.theme.IdeShellShapes
import org.ide.lti.core.designsystem.theme.config.AppTheme

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
    shape: Shape = IdeShellShapes.Card,
    borderWidth: Dp = IdeShellDimens.HairlineBorder,
    isMuted: Boolean = false,
    backdrop: Backdrop? = null,
): Modifier {
    val isBlueGlass = GlassTheme.appTheme == AppTheme.Blue && GlassTheme.effectsEnabled
    val actualBackdrop = backdrop ?: GlassTheme.backdrop
    val surfaceColor = if (isMuted) colors.cardBackgroundMuted else colors.cardBackground
    val borderColor = if (isMuted) colors.borderMuted else colors.border

    return if (isBlueGlass && actualBackdrop != null) {
        this.drawBackdrop(
            backdrop = actualBackdrop,
            shape = { shape },
            effects = GlassEffects.Card,
            highlight = GlassHighlights.Default,
            shadow = GlassShadows.Card,
            onDrawSurface = {
                drawRect(surfaceColor)
            },
        ).glassOutlineBorder(
            color = borderColor,
            width = borderWidth,
            shape = shape,
        )
    } else {
        this
            .background(surfaceColor, shape)
            .border(borderWidth, borderColor, shape)
    }
}
