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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.kashif_e.backdrop.BackdropEffectScope
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.frostedGrain
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.vibrancy
import com.kashif_e.backdrop.highlight.Highlight
import com.kashif_e.backdrop.highlight.HighlightStyle
import com.kashif_e.backdrop.shadow.Shadow

/**
 * Registry of backdrop effect stacks not (yet) worth a named [org.ide.lti.core.designsystem.theme.glass.GlassStyle]
 * role - the generic "no specific role" fallback ([Standard], used by the design system's own
 * generic primitives `Modifier.glassEffect()`/`GlassSurface()` when a caller specifies nothing
 * more specific) and [Scrim] (used only by `Modifier.glassScrim()`, itself core:designsystem-
 * internal). Every named component tier this object used to hold (TopBar, StatusBar, DockedNav,
 * Card, Button, ButtonPill, Chip, TextField, Toggle, Slider, PerformanceMonitor, BottomTabs,
 * TabSpotlight, Minimap, SplitterGrip, BottomSheet, dialog(), and the generic UltraThin/Thin/
 * Regular/Thick/Chrome material presets - the last five had no real consumer at all, only a smoke
 * test asserting they were non-null) has migrated to a [org.ide.lti.core.designsystem.theme.glass.GlassStyles]
 * role or been deleted as confirmed-dead code.
 */
object GlassEffects {

    /**
     * Canonical baseline liquid glass surface effect stack: vibrancy + moderate blur + lens refraction.
     */
    val Standard: BackdropEffectScope.() -> Unit = {
        vibrancy()
        blur(BackdropBlur.Standard.toPx())
        lens(
            refractionHeight = LensRefraction.Surface.height.toPx(),
            refractionAmount = LensRefraction.Surface.amount.toPx(),
            depthEffect = true,
            chromaticAberration = true,
        )
        frostedGrain(0.015f)
    }

    /**
     * Scrim overlay effect stack — diffuse blur for modal scrims and backdrop dimming.
     */
    val Scrim: BackdropEffectScope.() -> Unit = {
        vibrancy()
        blur(BackdropBlur.Scrim.toPx())
    }
}

/**
 * Centralized registry of all glass specular rim highlight strategies.
 */
object GlassHighlights {
    /** No specular highlight. */
    val None: () -> Highlight? = { null }

    /** Plain specular hairline highlight (default for surfaces and buttons). */
    val Plain: () -> Highlight? = { Highlight.Plain }

    /** Dual-tone physical glass bevel (specular top + counter shadow bottom). Canonical default. */
    val Default: () -> Highlight? = { Highlight.GlassBevel }

    /** Soft omnidirectional ambient highlight (ideal for hover/selected states). */
    val Ambient: () -> Highlight? = { Highlight.Ambient }

    /** Dual-tone physical glass bevel (specular top + counter shadow bottom). */
    val Bevel: () -> Highlight? = { Highlight.GlassBevel }

    /** Dynamic pointer-reactive specular highlight tracking mouse/cursor coordinates. */
    fun interactive(pointerPosition: Offset): Highlight =
        Highlight(style = HighlightStyle.PointerLight(pointerPosition = pointerPosition))
}

/**
 * Centralized registry of all glass ambient shadow presets.
 */
object GlassShadows {
    /** No shadow. */
    val None: (() -> Shadow?)? = null

    /** Subtle floating card elevation shadow (Raised: 4dp). */
    val Card: () -> Shadow? = {
        Shadow(
            radius = Elevation.Raised,
            color = Color.Black.copy(alpha = 0.15f),
        )
    }

    /** Elevated floating surface shadow (Panel: 8dp). */
    val Elevated: () -> Shadow? = {
        Shadow(
            radius = Elevation.Panel,
            color = Color.Black.copy(alpha = 0.2f),
        )
    }

    /** Deep modal sheet and dialog shadow (Modal: 16dp). */
    val Modal: () -> Shadow? = {
        Shadow(
            radius = Elevation.Modal,
            color = Color.Black.copy(alpha = 0.3f),
        )
    }

    /** Toggle switch thumb shadow (Raised: 4dp). */
    val ToggleThumb: () -> Shadow? = {
        Shadow(
            radius = Elevation.Raised,
            color = Color.Black.copy(alpha = 0.2f),
        )
    }
}
