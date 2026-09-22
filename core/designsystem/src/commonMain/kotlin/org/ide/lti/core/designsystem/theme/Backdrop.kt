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

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Centralized blur radius tokens for glass backdrops.
 */
object BackdropBlur {
    /** Ultra-subtle blur for buttons, text fields, search capsules, and performance monitors (2dp). */
    val Subtle: Dp = 2.dp

    /** Standard blur for basic glass surfaces, top bars, and layout minimaps (4dp). */
    val Standard: Dp = 4.dp

    /** Moderate blur for side panels and docked navigation bars (6dp). */
    val Panel: Dp = 6.dp

    /** Deep blur for recessed content panels, tool windows, and heavy frosted containers (12dp). */
    val Deep: Dp = 12.dp

    /** Immersive blur for the outermost application chrome frame (18dp). */
    val Immersive: Dp = 18.dp

    // Semantic aliases
    val Button: Dp = 2.dp
    val TextField: Dp = 2.dp

    /** Frosted-glass top bar blur - deliberately closer to [Card]'s intensity than [Standard]'s,
     * since a full-width, low-height bar reads as barely-softened at a subtle radius. */
    val TopBar: Dp = 8.dp
    val Minimap: Dp = 4.dp

    /** Scaled-up blur for wide, text-labeled pill buttons (GlassButton), where the icon-tier
     * [Button] radius reads as flat/imperceptible once stretched across the larger surface. */
    val ButtonPill: Dp = 4.dp
    val Card: Dp = 8.dp
    val BottomTabs: Dp = 8.dp
    val Slider: Dp = 8.dp
    val Toggle: Dp = 8.dp
    val DialogDark: Dp = 8.dp
    val DialogLight: Dp = 16.dp
    val BottomSheet: Dp = 16.dp
    val Scrim: Dp = 16.dp

    /** Blur for progressive-blur scroll-edge fades on panel containers (4dp). */
    val ScrollEdge: Dp = 4.dp
}

/**
 * Paired refraction height and displacement amount tokens for backdrop lens distortion.
 *
 * @property height Refraction height in [Dp] (bezel thickness / curvature profile).
 * @property amount Refraction displacement amount in [Dp] (pixel shift magnitude).
 */
data class LensRefraction(
    val height: Dp,
    val amount: Dp,
) {
    companion object {
        /** No refraction - for glass roles (e.g. `rail`) that omit lens to safely support a
         * rectangular shape. */
        val None: LensRefraction = LensRefraction(0.dp, 0.dp)

        /** Micro refraction for tiny inset views like layout minimaps (2dp, 4dp). */
        val Minimap: LensRefraction = LensRefraction(2.dp, 4.dp)

        /** Compact refraction for top app bars and search capsules (4dp, 8dp). */
        val TopBar: LensRefraction = LensRefraction(4.dp, 8.dp)

        /** Interactive displacement for switch/toggle thumbs (5dp, 10dp). */
        val Toggle: LensRefraction = LensRefraction(5.dp, 10.dp)

        /** Mild refraction for text inputs and subtle tool windows (8dp, 16dp). */
        val TextField: LensRefraction = LensRefraction(8.dp, 16.dp)

        /** Focused refraction for slider thumbs and tab spotlight indicators (10dp, 14dp). */
        val Slider: LensRefraction = LensRefraction(10.dp, 14.dp)
        val TabSpotlight: LensRefraction = LensRefraction(10.dp, 14.dp)

        /** Standard interactive refraction for buttons, action icons, and side panels (12dp, 24dp). */
        val Button: LensRefraction = LensRefraction(12.dp, 24.dp)
        val Panel: LensRefraction = LensRefraction(12.dp, 24.dp)

        /** Medium refraction for content cards and containers (16dp, 24dp). */
        val Card: LensRefraction = LensRefraction(16.dp, 24.dp)

        /** Canonical refraction for standard glass surfaces (16dp, 32dp). */
        val Surface: LensRefraction = LensRefraction(16.dp, 32.dp)

        /** Scaled-up refraction for wide, text-labeled pill buttons (GlassButton), matching
         * [Surface] so the lens effect stays visible across the wider button surface instead of
         * the icon-tier [Button] radius that reads as flat once stretched across a pill. */
        val ButtonPill: LensRefraction = Surface

        /** Symmetrical refraction for bottom tab bar containers (24dp, 24dp). */
        val BottomTabs: LensRefraction = LensRefraction(24.dp, 24.dp)

        /** Deep refraction for modal bottom sheets (24dp, 40dp). */
        val BottomSheet: LensRefraction = LensRefraction(24.dp, 40.dp)

        /** Prominent refraction for modal dialogs (24dp, 48dp). */
        val Dialog: LensRefraction = LensRefraction(24.dp, 48.dp)
    }
}

/**
 * Centralized saturation boost tokens for backdrop color adjustments.
 */
object BackdropSaturation {
    /** Vibrant saturation boost for frosted modal sheets and dialogs (1.5f). */
    const val Vibrant: Float = 1.5f
}

/**
 * Centralized brightness adjustment tokens for backdrop color adjustments.
 */
object BackdropBrightness {
    /** Lifted brightness for light-theme modal dialog frosted backdrops (0.2f). */
    const val DialogLight: Float = 0.2f

    /** Unadjusted brightness for dark-theme modal dialog frosted backdrops (0.0f). */
    const val DialogDark: Float = 0.0f
}

/**
 * Centralized tint intensity tokens for backdrop effects.
 */
object BackdropTint {
    /** Tint intensity for progressive-blur scroll-edge fades (0.8f). */
    const val ScrollEdgeIntensity: Float = 0.8f
}
