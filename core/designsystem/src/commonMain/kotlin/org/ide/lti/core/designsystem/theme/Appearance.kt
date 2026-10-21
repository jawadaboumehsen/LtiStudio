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

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

/** The highlight colour of buttons, selections and focus; [Default] keeps the app style's own. */
enum class AccentColor(val id: String, val label: String, val color: Color?) {
    Default("default", "Theme default", null),
    Cobalt("cobalt", "Cobalt", AccentPresetColors.Cobalt),
    Cyan("cyan", "Cyan", AccentPresetColors.Cyan),
    Emerald("emerald", "Emerald", AccentPresetColors.Emerald),
    Purple("purple", "Purple", AccentPresetColors.Purple),
    Amber("amber", "Amber", AccentPresetColors.Amber),
    Crimson("crimson", "Crimson", AccentPresetColors.Crimson),
    ;

    companion object {
        fun fromId(id: String?): AccentColor = entries.firstOrNull { it.id == id } ?: Default
    }
}

/**
 * How the whole app looks beyond its style: [accent] colour, [compact] density (spacing and controls 10% tighter, text
 * unchanged) and [textScale] (text only). Applied once, by the outermost [LtiTheme] that is given one.
 */
data class Appearance(
    val accent: AccentColor = AccentColor.Default,
    val compact: Boolean = false,
    val textScale: Float = 1f,
) {
    companion object {
        /** The text sizes offered, as scale factors. */
        val TextScales: List<Float> = listOf(1f, 1.1f, 1.25f)
        const val COMPACT_SCALE: Float = 0.9f
    }
}

val LocalAppearance = staticCompositionLocalOf { Appearance() }

/** This scheme with [accent] as its primary roles (readable text on it, containers tinted from it). */
fun ColorScheme.withAccent(accent: AccentColor): ColorScheme {
    val color = accent.color ?: return this
    // Whichever of black or white text contrasts more with the accent (WCAG ratio).
    val l = color.luminance()
    val onColor = if ((l + GLARE) / GLARE > (1f + GLARE) / (l + GLARE)) Color.Black else Color.White
    val container = color.copy(alpha = AlphaTokens.Faded).compositeOver(surface)
    return copy(
        primary = color,
        onPrimary = onColor,
        primaryContainer = container,
        onPrimaryContainer = onSurface,
        inversePrimary = color,
        surfaceTint = color,
    )
}

private const val GLARE = 0.05f
