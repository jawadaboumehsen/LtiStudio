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
import com.kashif_e.backdrop.OpticalSizeValue
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BackdropBlur
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
 * Known gap: [GlassStyle] has no `colorAdjustment`/saturation field yet (see the plan's §7 review
 * amendment / external review's Phase 2), so [dialog]'s brightness+saturation boost from
 * `GlassEffects.dialog()` is not reproduced here - only its blur/lens/rim. Tracked, not silently
 * dropped.
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

    public val navigator: GlassStyle
        @Composable
        @ReadOnlyComposable
        get() {
            val colors = GlassTheme.colors
            return GlassStyle(
                surface = colors.glassSurface,
                shape = GlassShapes.Panel,
                blur = OpticalSizeValue.Fixed(BackdropBlur.Panel),
                lens = OpticalSizeValue.Fixed(LensRefraction.Panel),
                grain = GRAIN_STANDARD,
            )
        }

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
                shape = GlassShapes.Card,
                blur = OpticalSizeValue.Fixed(BackdropBlur.Card),
                lens = OpticalSizeValue.Fixed(LensRefraction.Card),
                grain = GRAIN_STANDARD,
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
                shape = GlassShapes.Medium,
                blur = OpticalSizeValue.Fixed(BackdropBlur.Button),
                lens = OpticalSizeValue.Fixed(LensRefraction.Button),
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
                surface = colors.glassSurfaceElevated,
                shape = GlassShapes.Dialog,
                blur = OpticalSizeValue.Fixed(if (isDark) BackdropBlur.DialogDark else BackdropBlur.DialogLight),
                lens = OpticalSizeValue.Fixed(LensRefraction.Dialog),
                rim = Rim(),
            )
        }

    private const val GRAIN_STANDARD = 0.015f
}
