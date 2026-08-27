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

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Automated WCAG 2.x contrast ratio verification for dynamic glass color schemes.
 */
class GlassContrastTest {

    @Test
    fun testDarkGlassColorSchemeContrastRatios() {
        val scheme = DarkGlassColorScheme
        val surface = scheme.glassSurface

        val onGlassContrast = contrastRatio(scheme.onGlass, surface)
        val onGlassSecondaryContrast = contrastRatio(scheme.onGlassSecondary, surface)
        val onGlassMutedContrast = contrastRatio(scheme.onGlassMuted, surface)
        val glassAccentContrast = contrastRatio(scheme.glassAccent, surface)
        val focusRingContrast = contrastRatio(scheme.focusRing, surface)

        println("=== DarkGlassColorScheme Contrast Ratios ===")
        println("onGlass vs glassSurface: $onGlassContrast (expected >= 4.5)")
        println("onGlassSecondary vs glassSurface: $onGlassSecondaryContrast (expected >= 4.5)")
        println("onGlassMuted vs glassSurface: $onGlassMutedContrast (expected >= 3.0)")
        println("glassAccent vs glassSurface: $glassAccentContrast (expected >= 3.0)")
        println("focusRing vs glassSurface: $focusRingContrast (expected >= 3.0)")

        // Body / normal text pairs per WCAG AA (>= 4.5:1)
        assertTrue(
            onGlassContrast >= 4.5,
            "Dark scheme onGlass contrast $onGlassContrast must be >= 4.5:1",
        )
        assertTrue(
            onGlassSecondaryContrast >= 4.5,
            "Dark scheme onGlassSecondary contrast $onGlassSecondaryContrast must be >= 4.5:1",
        )

        // UI components / graphical objects / muted text per WCAG AA (>= 3.0:1)
        assertTrue(
            onGlassMutedContrast >= 3.0,
            "Dark scheme onGlassMuted contrast $onGlassMutedContrast must be >= 3.0:1",
        )
        assertTrue(
            glassAccentContrast >= 3.0,
            "Dark scheme glassAccent contrast $glassAccentContrast must be >= 3.0:1",
        )
        assertTrue(
            focusRingContrast >= 3.0,
            "Dark scheme focusRing contrast $focusRingContrast must be >= 3.0:1",
        )
    }

    @Test
    fun testLightGlassColorSchemeContrastRatios() {
        val scheme = LightGlassColorScheme
        val surface = scheme.glassSurface

        val onGlassContrast = contrastRatio(scheme.onGlass, surface)
        val onGlassSecondaryContrast = contrastRatio(scheme.onGlassSecondary, surface)
        val onGlassMutedContrast = contrastRatio(scheme.onGlassMuted, surface)
        val glassAccentContrast = contrastRatio(scheme.glassAccent, surface)
        val focusRingContrast = contrastRatio(scheme.focusRing, surface)

        println("=== LightGlassColorScheme Contrast Ratios ===")
        println("onGlass vs glassSurface: $onGlassContrast (expected >= 4.5)")
        println("onGlassSecondary vs glassSurface: $onGlassSecondaryContrast (expected >= 4.5)")
        println("onGlassMuted vs glassSurface: $onGlassMutedContrast (expected >= 3.0)")
        println("glassAccent vs glassSurface: $glassAccentContrast (expected >= 3.0)")
        println("focusRing vs glassSurface: $focusRingContrast (expected >= 3.0)")

        // Body / normal text pairs per WCAG AA (>= 4.5:1)
        assertTrue(
            onGlassContrast >= 4.5,
            "Light scheme onGlass contrast $onGlassContrast must be >= 4.5:1",
        )
        assertTrue(
            onGlassSecondaryContrast >= 4.5,
            "Light scheme onGlassSecondary contrast $onGlassSecondaryContrast must be >= 4.5:1",
        )

        // UI components / graphical objects / muted text per WCAG AA (>= 3.0:1)
        assertTrue(
            onGlassMutedContrast >= 3.0,
            "Light scheme onGlassMuted contrast $onGlassMutedContrast must be >= 3.0:1",
        )
        assertTrue(
            glassAccentContrast >= 3.0,
            "Light scheme glassAccent contrast $glassAccentContrast must be >= 3.0:1",
        )
        assertTrue(
            focusRingContrast >= 3.0,
            "Light scheme focusRing contrast $focusRingContrast must be >= 3.0:1",
        )
    }

    companion object {
        /**
         * Linearizes an sRGB channel value in the range [0.0, 1.0] per WCAG 2.x specifications.
         */
        fun linearize(channel: Float): Double {
            val c = channel.toDouble()
            return if (c <= 0.03928) {
                c / 12.92
            } else {
                ((c + 0.055) / 1.055).pow(2.4)
            }
        }

        /**
         * Computes the WCAG 2.x relative luminance of a [Color].
         * L = 0.2126 * R_lin + 0.7152 * G_lin + 0.0722 * B_lin
         */
        fun relativeLuminance(color: Color): Double {
            val rLin = linearize(color.red)
            val gLin = linearize(color.green)
            val bLin = linearize(color.blue)
            return 0.2126 * rLin + 0.7152 * gLin + 0.0722 * bLin
        }

        /**
         * Computes the WCAG 2.x contrast ratio between two colors.
         * Contrast Ratio = (L_lighter + 0.05) / (L_darker + 0.05)
         */
        fun contrastRatio(color1: Color, color2: Color): Double {
            val lum1 = relativeLuminance(color1)
            val lum2 = relativeLuminance(color2)
            val lighter = max(lum1, lum2)
            val darker = min(lum1, lum2)
            return (lighter + 0.05) / (darker + 0.05)
        }
    }
}
