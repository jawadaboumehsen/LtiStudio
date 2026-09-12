package com.kashif_e.backdrop.effects

import com.kashif_e.backdrop.BackdropEffectScope

/**
 * Canonical Apple Human Interface Guidelines (HIG) glass material presets.
 * 
 * Provides calibrated optical blur, saturation boost, luminance curves,
 * and edge refraction for macOS Sequoia, iOS 18, and visionOS Liquid Glass.
 */

/**
 * Apple Ultra-Thin Material: Lightest translucent frosted glass.
 * Used for floating controls, media HUDs, active status indicators.
 */
fun BackdropEffectScope.ultraThinMaterial(
    chromaticAberration: Boolean = false
) {
    colorControls(brightness = 0.05f, saturation = 1.8f)
    blur(10f)
    lens(
        refractionHeight = 8f,
        refractionAmount = 14f,
        depthEffect = false,
        chromaticAberration = chromaticAberration
    )
}

/**
 * Apple Thin Material: Lightweight frosted surface.
 * Used for cards, action sheets, sidebars, floating search bars.
 */
fun BackdropEffectScope.thinMaterial(
    chromaticAberration: Boolean = false
) {
    colorControls(brightness = 0.03f, saturation = 1.6f)
    blur(16f)
    lens(
        refractionHeight = 12f,
        refractionAmount = 20f,
        depthEffect = false,
        chromaticAberration = chromaticAberration
    )
}

/**
 * Apple Regular Material: Standard system glass.
 * Used for modals, dialogs, popovers, and default liquid panels.
 */
fun BackdropEffectScope.regularMaterial(
    chromaticAberration: Boolean = false
) {
    colorControls(brightness = 0.0f, saturation = 1.5f)
    blur(24f)
    lens(
        refractionHeight = 16f,
        refractionAmount = 26f,
        depthEffect = true,
        chromaticAberration = chromaticAberration
    )
}

/**
 * Apple Thick Material: Deep frosted drawer glass.
 * Used for control center widgets, bottom sheets, navigation bars.
 */
fun BackdropEffectScope.thickMaterial(
    chromaticAberration: Boolean = false
) {
    colorControls(brightness = -0.02f, saturation = 1.35f)
    blur(34f)
    lens(
        refractionHeight = 20f,
        refractionAmount = 32f,
        depthEffect = true,
        chromaticAberration = chromaticAberration
    )
}

/**
 * Apple Chrome Material: Heavy system window chrome and dock.
 * Maximum blur with deep tone compression for window headers and docked bars.
 */
fun BackdropEffectScope.chromeMaterial(
    chromaticAberration: Boolean = false
) {
    colorControls(brightness = -0.05f, saturation = 1.25f)
    blur(42f)
    lens(
        refractionHeight = 24f,
        refractionAmount = 38f,
        depthEffect = true,
        chromaticAberration = chromaticAberration
    )
}
