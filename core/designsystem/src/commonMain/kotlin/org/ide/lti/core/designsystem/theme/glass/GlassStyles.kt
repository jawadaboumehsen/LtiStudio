/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.theme.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.kashif_e.backdrop.OpticalSizeValue
import com.kashif_e.backdrop.shadow.Shadow
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BackdropBlur
import org.ide.lti.core.designsystem.theme.BackdropBrightness
import org.ide.lti.core.designsystem.theme.BackdropSaturation
import org.ide.lti.core.designsystem.theme.Elevation
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.LensRefraction

/**
 * The seven named [GlassStyle] roles from backdrop-refactor-plan-20260920.md §1 - the shared
 * vocabulary that replaces [org.ide.lti.core.designsystem.theme.GlassEffects]' 24 independently
 * drifting stacks. Each role's optics/colors are transcribed from the real component that already
 * owns that visual tier ([org.ide.lti.core.designsystem.theme.GlassEffects.TopBar],
 * `.DockedNav`, `.Panel`, `.Card`, `.Button`, `.dialog()`, `.TabSpotlight`) rather than re-tuned
 * from scratch, so a component migrating from its old hand-picked effect stack to
 * `Modifier.glass(GlassStyles.x)` sees no visual change.
 *
 * Colors are theme-dependent ([GlassTheme.colors]), so these are `@Composable` properties, not
 * static `val`s - a role must re-resolve when the active [org.ide.lti.core.designsystem.theme.AppTheme]
 * or light/dark mode changes.
 *
 * [dialog]'s brightness+saturation boost from `GlassEffects.dialog()` is reproduced via
 * [GlassStyle.brightness]/[GlassStyle.saturation] (the plan's §7 review amendment / external
 * review's Phase 2 `colorAdjustment` gap, closed here once [dialog] became a real consumer).
 */
public object GlassStyles {

    public val bar: GlassStyle
        @Composable
        @ReadOnlyComposable
        get() {
            val colors = GlassTheme.colors
            return GlassStyle(
                surface = colors.chromeSurface,
                shape = GlassShapes.None,
                blur = OpticalSizeValue.Fixed(BackdropBlur.TopBar),
                lens = OpticalSizeValue.Fixed(LensRefraction.TopBar),
                grain = GRAIN_STANDARD,
                rim = Rim(),
            )
        }

    public val rail: GlassStyle
        @Composable
        @ReadOnlyComposable
        get() {
            val colors = GlassTheme.colors
            return GlassStyle(
                surface = colors.chromeSurface,
                shape = GlassShapes.None,
                blur = OpticalSizeValue.Fixed(BackdropBlur.Panel),
                lens = OpticalSizeValue.Fixed(LensRefraction.None),
                grain = GRAIN_STANDARD,
            )
        }

    // Real usage (IdeNavigatorPanel) confirmed this is optically identical to `rail` - both are
    // GlassEffects.DockedNav (RectangleShape, no lens, no rim) in the actual production shell.
    // Kept as a separate named role from `rail` for call-site clarity even though the values
    // match exactly, per the plan's own "rail / navigator" table row.
    public val navigator: GlassStyle
        @Composable
        @ReadOnlyComposable
        get() = rail

    public val selected: GlassStyle
        @Composable
        @ReadOnlyComposable
        get() {
            val colors = GlassTheme.colors
            return GlassStyle(
                surface = colors.glassSurface,
                tint = colors.glassAccent.copy(alpha = AlphaTokens.PrimaryButtonTint),
                shape = GlassShapes.Capsule,
                blur = OpticalSizeValue.Fixed(BackdropBlur.Standard),
                lens = OpticalSizeValue.Fixed(LensRefraction.TabSpotlight),
            )
        }

    public val card: GlassStyle
        @Composable
        @ReadOnlyComposable
        get() {
            val colors = GlassTheme.colors
            return GlassStyle(
                surface = colors.glassSurfaceElevated,
                tint = colors.glassTint,
                shape = GlassShapes.Card,
                blur = OpticalSizeValue.Fixed(BackdropBlur.Card),
                lens = OpticalSizeValue.Fixed(LensRefraction.Card),
                lensDepthEffect = true,
                lensChromaticAberration = true,
                grain = GRAIN_STANDARD,
                rim = Rim(),
                shadow = Shadow(radius = Elevation.Raised, color = Color.Black.copy(alpha = AlphaTokens.Glow)),
            )
        }

    public val button: GlassStyle
        @Composable
        @ReadOnlyComposable
        get() {
            val colors = GlassTheme.colors
            return GlassStyle(
                surface = colors.glassSurface,
                tint = colors.glassTint,
                border = colors.glassBorder,
                shape = GlassShapes.Capsule,
                // Matches GlassButtonPillEffects (= GlassEffects.ButtonPill) - GlassButton's real
                // default `effects`. Only blur/lens/vibrancy/grain are actually consumed by
                // GlassButton (via .toEffects()); it computes surface/tint/border/rim itself per
                // variant/hover/focus state, so those fields above are placeholders, not real
                // defaults, for this specific consumer.
                blur = OpticalSizeValue.Fixed(BackdropBlur.ButtonPill),
                lens = OpticalSizeValue.Fixed(LensRefraction.ButtonPill),
                lensChromaticAberration = true,
                grain = GRAIN_STANDARD,
                rim = Rim(),
            )
        }

    public val dialog: GlassStyle
        @Composable
        @ReadOnlyComposable
        get() {
            val colors = GlassTheme.colors
            val isDark = GlassTheme.isDark
            return GlassStyle(
                surface = colors.glassSurface,
                tint = colors.glassTint,
                shape = GlassShapes.Dialog,
                blur = OpticalSizeValue.Fixed(if (isDark) BackdropBlur.DialogDark else BackdropBlur.DialogLight),
                lens = OpticalSizeValue.Fixed(LensRefraction.Dialog),
                lensDepthEffect = true,
                // dialog() uses colorControls' own saturation boost instead of vibrancy() - using
                // both would double the boost.
                vibrancy = false,
                brightness = if (isDark) BackdropBrightness.DialogDark else BackdropBrightness.DialogLight,
                saturation = BackdropSaturation.Vibrant,
                rim = Rim(),
                shadow = Shadow(radius = Elevation.Modal, color = colors.glassShadow),
            )
        }

    private const val GRAIN_STANDARD = 0.015f
}
