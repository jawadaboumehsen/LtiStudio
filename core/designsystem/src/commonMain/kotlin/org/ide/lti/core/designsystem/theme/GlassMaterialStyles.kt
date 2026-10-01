/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.GlassStyle

/**
 * Pure, testable base factory providing calibrated [GlassStyle] foundations across all themes.
 *
 * Key Design Principles:
 * 1. Produces strictly Haze [GlassStyle] instances without coupling to component solid fallback rendering.
 * 2. Inputs are strictly [theme], [opticalTint], and [captureBacking].
 * 3. Components retain `surfaceColor` strictly for their own `effectsEnabled = false` solid background rendering.
 * 4. Treats [Color.Transparent] as an explicit **no-tint** / **no-backing** opt-out.
 * 5. Treats [Color.Unspecified] as **apply theme-calibrated default**.
 * 6. Calibrates [whitePoint] per theme: Dark and Blue use low whitePoint (~0.02f-0.12f) so captured
 *    content does not wash out; Light preserves luminous high-key calibration (0.55f).
 * 7. Provides [clearStyle] for [GlassSlider]'s thumb, preserving distinct Clear optics.
 * 8. Appends writes after [GlassStyle.regular] or [GlassStyle.clear], allowing component callers
 *    to chain `.then { ... }` for shapes, interaction states, and local lighting.
 */
@OptIn(ExperimentalHazeApi::class)
object GlassMaterialStyles {

    /**
     * Constructs the calibrated base [GlassStyle] for standard surfaces, cards, and controls.
     */
    fun baseStyle(
        theme: AppTheme,
        opticalTint: Color = Color.Unspecified,
        captureBacking: Color = Color.Unspecified,
    ): GlassStyle {
        val resolvedTint = when {
            opticalTint == Color.Transparent -> Color.Transparent
            opticalTint.isSpecified -> opticalTint
            else -> defaultAtmosphericTintFor(theme)
        }
        val resolvedBacking = when {
            captureBacking == Color.Transparent -> Color.Transparent
            captureBacking.isSpecified -> captureBacking
            else -> defaultCaptureBackingFor(theme)
        }
        val resolvedWhitePoint = defaultWhitePointFor(theme)

        return GlassStyle.regular.then {
            backgroundColor(resolvedBacking)
            tint(resolvedTint)
            whitePoint(resolvedWhitePoint)
            ambientResponse(0.50f)
            specularExponent(28f)
        }
    }

    /**
     * Constructs the calibrated Clear [GlassStyle] for translucent thumbs and clear optics.
     */
    fun clearStyle(
        theme: AppTheme,
        opticalTint: Color = Color.Unspecified,
        captureBacking: Color = Color.Unspecified,
    ): GlassStyle {
        val resolvedTint = when {
            opticalTint == Color.Transparent -> Color.Transparent
            opticalTint.isSpecified -> opticalTint
            else -> defaultAtmosphericTintFor(theme)
        }
        val resolvedBacking = when {
            captureBacking == Color.Transparent -> Color.Transparent
            captureBacking.isSpecified -> captureBacking
            else -> defaultCaptureBackingFor(theme)
        }
        val resolvedWhitePoint = when (theme) {
            AppTheme.Dark -> 0.15f
            AppTheme.Blue -> 0.15f
            AppTheme.Light -> 0.18f
        }

        return GlassStyle.clear.then {
            backgroundColor(resolvedBacking)
            tint(resolvedTint)
            whitePoint(resolvedWhitePoint)
            ambientResponse(0.55f)
            specularExponent(32f)
        }
    }

    /**
     * Constructs the calibrated 3D Superquadric Liquid [GlassStyle] strictly for [GlassToggle]'s thumb.
     *
     * Activates [SurfaceProfile.SuperquadricLiquid] for authentic 3D Snell's law refraction,
     * analytic squircle gradient normal, and 12th-power grazing Fresnel highlights.
     */
    fun liquidToggleStyle(
        theme: AppTheme,
        opticalTint: Color = Color.Unspecified,
        captureBacking: Color = Color.Unspecified,
    ): GlassStyle {
        val baseClear = clearStyle(theme, opticalTint, captureBacking)
        return baseClear.then {
            surfaceProfile(dev.chrisbanes.haze.glass.SurfaceProfile.SuperquadricLiquid)
            specularIntensity(0.45f)
            ambientResponse(0.85f)
        }
    }

    private fun defaultAtmosphericTintFor(theme: AppTheme): Color = when (theme) {
        AppTheme.Dark -> DarkColorScheme.surface.copy(alpha = AlphaTokens.Hover)
        AppTheme.Blue -> BlueColorScheme.surface.copy(alpha = AlphaTokens.Hover)
        AppTheme.Light -> LightColorScheme.surface.copy(alpha = AlphaTokens.Hover)
    }

    private fun defaultCaptureBackingFor(theme: AppTheme): Color = when (theme) {
        AppTheme.Dark -> DarkColorScheme.surface
        AppTheme.Blue -> BlueColorScheme.surface
        AppTheme.Light -> LightColorScheme.surface
    }

    private fun defaultWhitePointFor(theme: AppTheme): Float = when (theme) {
        AppTheme.Dark -> 0.02f
        AppTheme.Blue -> 0.02f
        AppTheme.Light -> 0.08f
    }
}
