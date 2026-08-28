/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.theme

/**
 * Centralized alpha and opacity tokens for glass surfaces, interactive states,
 * borders, scrims, and overlays across the design system.
 */
object AlphaTokens {
    /** Ultra-faint opacity for ambient shadow layers (3%). */
    const val UltraFaint: Float = 0.03f

    /** Faint opacity for ambient shadows and subtle glass tinting (5%). */
    const val Faint: Float = 0.05f

    /** Subtle opacity for spotlight surface and light overlays (10%). */
    const val Subtle: Float = 0.10f

    /** Glow opacity for divider hover states and subtle tint fills (15%). */
    const val Glow: Float = 0.15f

    /** Active/hover state opacity for selections and buttons (20%). */
    const val Hover: Float = 0.20f

    /** Muted opacity for dividers, scrims, and gradient edges (30%). */
    const val Muted: Float = 0.30f
    const val Scrim: Float = 0.30f

    /** Medium opacity for disabled surfaces and status bar backgrounds (40%). */
    const val Medium: Float = 0.40f
    const val Disabled: Float = 0.40f

    /** Half opacity for borders, elevated badges, and secondary surfaces (50%). */
    const val Half: Float = 0.50f
    const val Border: Float = 0.50f
    const val Elevated: Float = 0.50f

    /** Prominent opacity for active highlight gradients (60%). */
    const val Prominent: Float = 0.60f

    /** Intense opacity for saturated icon tint layers (75%). */
    const val IconTint: Float = 0.75f

    /** Faded opacity for the outer edge of a radial gradient glow (25%). */
    const val Faded: Float = 0.25f

    /** Ambient opacity for a soft gradient glow layer (35%). */
    const val Ambient: Float = 0.35f

    /** Soft opacity for a gradient glow's inner layer (45%). */
    const val Soft: Float = 0.45f

    /** Strong opacity for a gradient glow's brightest core (55%). */
    const val Strong: Float = 0.55f

    /**
     * Fill opacity multiplier for GlassButton's surface layer, applied on top of the shared
     * glassSurface/glassSurfaceElevated tokens' own alpha. Buttons are wide, mostly-flat pills
     * where the lens refraction shader only distorts a thin band near the edges (see
     * backdrop/effects/Lens.kt) — the working full-surface blur underneath needs a translucent
     * fill, not a near-opaque one, or it gets painted over and the glass reads as flat (35%).
     */
    const val ButtonFill: Float = 0.35f
}
