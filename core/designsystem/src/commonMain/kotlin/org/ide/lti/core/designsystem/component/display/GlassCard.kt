/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.display

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import org.ide.lti.core.designsystem.component.actions.*
import org.ide.lti.core.designsystem.component.devtools.*
import org.ide.lti.core.designsystem.component.feedback.*
import org.ide.lti.core.designsystem.component.inputs.*
import org.ide.lti.core.designsystem.component.layout.*
import org.ide.lti.core.designsystem.component.navigation.*
import org.ide.lti.core.designsystem.component.primitives.*
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.glass.GlassSceneState
import org.ide.lti.core.designsystem.theme.glass.GlassStyles

/**
 * Glass card component with rounded corners and padding.
 *
 * Built on [Modifier.glass] + [GlassStyles.card], adds card-specific defaults:
 * - Rounded rectangle shape
 * - Default padding
 * - Optimized for content display
 *
 * @param modifier Modifier to be applied to the card
 * @param shape Shape of the card (default: [ContinuousRoundedRectangle] with 16dp radius)
 * @param surfaceColor Optional surface color overlay (default: [GlassTheme.colors.glassSurfaceElevated])
 * @param tint Optional tint color
 * @param borderColor Optional border color (default: [GlassTheme.colors.glassBorder])
 * @param contentPadding Padding inside the card (default: 16dp)
 * @param backdrop GlassSceneState to sample (default: the ambient [GlassTheme.scene]) - an override
 * stays available for screens that capture their own distinct backdrop (e.g. Setup's Blue Glass
 * mode), matching [org.ide.lti.core.designsystem.component.primitives.glass]'s own `backdrop` param.
 * @param content Content to display inside the card
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = GlassShapes.Card,
    surfaceColor: Color = Color.Unspecified,
    tint: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
    contentPadding: Dp = Spacing.Medium,
    backdrop: GlassSceneState? = null,
    content: @Composable () -> Unit,
) {
    val glassColors = GlassTheme.colors
    val style = GlassStyles.card.then {
        copy(
            shape = shape,
            surface = if (surfaceColor.isSpecified) surfaceColor else this.surface,
            tint = if (tint.isSpecified) tint else this.tint,
            border = if (borderColor.isSpecified) borderColor else glassColors.glassBorder,
            borderWidth = StrokeWidth.Hairline,
        )
    }
    Box(modifier = modifier.glass(style, backdrop = backdrop)) {
        CompositionLocalProvider(LocalContentColor provides glassColors.onGlass) {
            Box(modifier = Modifier.padding(contentPadding)) {
                content()
            }
        }
    }
}
