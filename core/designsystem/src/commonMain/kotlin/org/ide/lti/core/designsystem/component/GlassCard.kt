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
import com.kashif_e.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousRoundedRectangle
import org.ide.lti.core.designsystem.theme.BackdropBlur
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.LensRefraction
import org.ide.lti.core.designsystem.theme.Spacing

/**
 * Card glass effect stack, adapted from the kmp-liquid-glass skill's "Glass card" recipe
 * (vibrancy + lens refraction): a blur pass is added and the lens amount reduced from the
 * doc's defaults, tuned for legibility over the denser IDE content this app's cards host.
 */
val GlassCardEffects: BackdropEffectScope.() -> Unit = {
    vibrancy()
    blur(BackdropBlur.Card.toPx())
    lens(LensRefraction.Card.height.toPx(), LensRefraction.Card.amount.toPx())
}

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
 * @param effects Backdrop effect chain (default: [GlassCardEffects])
 * @param backdrop Backdrop instance to use (default: from GlassTheme or create new)
 * @param surfaceColor Optional surface color overlay
 * @param tint Optional tint color
 * @param borderColor Optional border color
 * @param contentPadding Padding inside the card (default: 16dp)
 * @param content Content to display inside the card
 *
 * Example:
 * ```kotlin
 * GlassCard {
 *     Column {
 *         Text("Title", style = MaterialTheme.typography.titleLarge)
 *         Text("Description")
 *     }
 * }
 * ```
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = ContinuousRoundedRectangle(CornerRadius.Card),
    effects: BackdropEffectScope.() -> Unit = GlassCardEffects,
    backdrop: Backdrop? = null,
    surfaceColor: Color = Color.Unspecified,
    tint: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
    contentPadding: Dp = Spacing.Medium,
    shadow: (() -> Shadow?)? = null,
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
        shadow = shadow,
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}
