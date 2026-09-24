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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG 2.x relative luminance and contrast ratio utilities for theme colors.
 */
object ColorContrast {
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

    /**
     * Formats a [Color] into a standard 8-character or 6-character hex string (e.g. "#38BDF8").
     */
    fun toHexString(color: Color): String {
        val alpha = (color.alpha * 255).toInt()
        val red = (color.red * 255).toInt()
        val green = (color.green * 255).toInt()
        val blue = (color.blue * 255).toInt()
        val rHex = red.toString(16).padStart(2, '0').uppercase()
        val gHex = green.toString(16).padStart(2, '0').uppercase()
        val bHex = blue.toString(16).padStart(2, '0').uppercase()
        return if (alpha == 255) {
            "#$rHex$gHex$bHex"
        } else {
            val aHex = alpha.toString(16).padStart(2, '0').uppercase()
            "#$aHex$rHex$gHex$bHex"
        }
    }
}
