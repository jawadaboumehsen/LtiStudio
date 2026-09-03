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

import org.ide.lti.core.designsystem.component.primitives.*
import org.ide.lti.core.designsystem.component.actions.*
import org.ide.lti.core.designsystem.component.inputs.*
import org.ide.lti.core.designsystem.component.display.*
import org.ide.lti.core.designsystem.component.navigation.*
import org.ide.lti.core.designsystem.component.layout.*
import org.ide.lti.core.designsystem.component.feedback.*
import org.ide.lti.core.designsystem.component.devtools.*

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.BackdropEffectScope
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.vibrancy
import com.kashif_e.backdrop.highlight.Highlight
import com.kashif_e.backdrop.shadow.Shadow
import org.ide.lti.core.designsystem.theme.BackdropBlur
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.GlassEffects
import org.ide.lti.core.designsystem.theme.GlassHighlights
import org.ide.lti.core.designsystem.theme.GlassShadows
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.Spacing

/**
 * Card glass effect stack.
 * Delegated to canonical [GlassEffects.Card].
 */
val GlassCardEffects: BackdropEffectScope.() -> Unit = GlassEffects.Card

/**
 * Glass card component with rounded corners and padding.
 *
 * Built on top of GlassSurface, adds card-specific defaults:
 * - Rounded rectangle shape
 * - Default padding
 * - Optimized for content display
 *
 * Follows Open/Closed Principle: extends GlassSurface without modifying it.
 *
 * @param modifier Modifier to be applied to the card
 * @param shape Shape of the card (default: [ContinuousRoundedRectangle] with 16dp radius)
 * @param effects Backdrop effect chain (default: [GlassEffects.Card])
 * @param backdrop Backdrop instance to use (default: from GlassTheme or create new)
 * @param surfaceColor Optional surface color overlay
 * @param tint Optional tint color
 * @param borderColor Optional border color
 * @param contentPadding Padding inside the card (default: 16dp)
 * @param highlight Optional highlight decorator (default: [GlassHighlights.Default])
 * @param shadow Optional shadow decorator (default: [GlassShadows.None])
 * @param content Content to display inside the card
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = GlassShapes.Card,
    effects: BackdropEffectScope.() -> Unit = GlassEffects.Card,
    backdrop: Backdrop? = null,
    surfaceColor: Color = Color.Unspecified,
    tint: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
    contentPadding: Dp = Spacing.Medium,
    highlight: () -> Highlight? = GlassHighlights.Default,
    shadow: (() -> Shadow?)? = GlassShadows.None,
    content: @Composable () -> Unit,
) {
    GlassSurface(
        modifier = modifier,
        shape = shape,
        effects = effects,
        backdrop = backdrop,
        surfaceColor = surfaceColor,
        tint = tint,
        borderColor = borderColor,
        highlight = highlight,
        shadow = shadow,
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}
