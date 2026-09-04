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
import com.kashif_e.backdrop.effects.GlassMaterial
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.colorControls
import com.kashif_e.backdrop.effects.frostedGrain
import com.kashif_e.backdrop.effects.glassChrome
import com.kashif_e.backdrop.effects.glassMaterial
import com.kashif_e.backdrop.effects.glassRegular
import com.kashif_e.backdrop.effects.glassThick
import com.kashif_e.backdrop.effects.glassThin
import com.kashif_e.backdrop.effects.glassUltraThin
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.vibrancy
import com.kashif_e.backdrop.highlight.Highlight
import com.kashif_e.backdrop.highlight.HighlightStyle
import com.kashif_e.backdrop.shadow.Shadow

/**
 * Centralized registry of all backdrop visual effect stacks across the Liquid Glass design system.
 *
 * Categorized by semantic material tiers matching Apple Liquid Glass & macOS design guidelines:
 * 1. **Chrome Tier**: Precision shell toolbars (TopBar, StatusBar, DockedNav)
 * 2. **Surface & Container Tier**: Standard liquid surfaces (Standard, Card, Panel)
 * 3. **Interactive Controls Tier**: Buttons, chips, inputs, toggles, sliders
 * 4. **Modal & Scrim Tier**: Frosted dialogs, bottom sheets, diffuse scrims
 */
object GlassEffects {

    // ==================== 1. CHROME TIER ====================

    /**
     * Top app bar chrome effect stack — high precision, low displacement so window controls
     * and breadcrumbs stay razor-sharp over active content.
     */
    val TopBar: BackdropEffectScope.() -> Unit = {
        vibrancy()
        blur(BackdropBlur.TopBar.toPx())
        lens(LensRefraction.TopBar.height.toPx(), LensRefraction.TopBar.amount.toPx())
        frostedGrain(0.015f)
    }

    /**
     * Status bar footer chrome effect stack — identical calibration to [TopBar] for visual unity.
     */
    val StatusBar: BackdropEffectScope.() -> Unit = TopBar

    /**
     * Docked navigation bar effect stack for bottom rails and side panel rails.
     * Omits lens refraction to safely support [RectangleShape] without runtime corner exceptions.
     */
    val DockedNav: BackdropEffectScope.() -> Unit = {
        vibrancy()
        blur(BackdropBlur.Panel.toPx())
        frostedGrain(0.015f)
    }

    // ==================== 2. SURFACE & CONTAINER TIER ====================

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
     * Card surface effect stack — tuned for high readability over dense IDE code and project feeds.
     */
    val Card: BackdropEffectScope.() -> Unit = {
        vibrancy()
        blur(BackdropBlur.Card.toPx())
        lens(
            refractionHeight = LensRefraction.Card.height.toPx(),
            refractionAmount = LensRefraction.Card.amount.toPx(),
            depthEffect = true,
            chromaticAberration = true,
        )
        frostedGrain(0.015f)
    }

    /**
     * Sidebar and tool window panel effect stack — deep frosted glass blur for prominent z-depth.
     */
    val Panel: BackdropEffectScope.() -> Unit = {
        vibrancy()
        blur(BackdropBlur.Panel.toPx())
        lens(
            refractionHeight = LensRefraction.Panel.height.toPx(),
            refractionAmount = LensRefraction.Panel.amount.toPx(),
            depthEffect = true,
            chromaticAberration = true,
        )
        frostedGrain(0.015f)
    }

    // ==================== 3. INTERACTIVE CONTROLS TIER ====================

    /**
     * Compact button effect stack — crisp/glossy with light blur and tight lens for icon-sized buttons.
     */
    val Button: BackdropEffectScope.() -> Unit = {
        vibrancy()
        blur(BackdropBlur.Button.toPx())
        lens(
            refractionHeight = LensRefraction.Button.height.toPx(),
            refractionAmount = LensRefraction.Button.amount.toPx(),
            depthEffect = false,
            chromaticAberration = true,
        )
        frostedGrain(0.015f)
    }

    /**
     * Pill action button effect stack — scaled up so lens refraction stays visible across wide surfaces.
     */
    val ButtonPill: BackdropEffectScope.() -> Unit = {
        vibrancy()
        blur(BackdropBlur.ButtonPill.toPx())
        lens(
            refractionHeight = LensRefraction.ButtonPill.height.toPx(),
            refractionAmount = LensRefraction.ButtonPill.amount.toPx(),
            depthEffect = false,
            chromaticAberration = true,
        )
        frostedGrain(0.015f)
    }

    /**
     * Chip and metadata tag effect stack — ultra-subtle blur and fast lens for compact badges.
     */
    val Chip: BackdropEffectScope.() -> Unit = {
        vibrancy()
        blur(BackdropBlur.Subtle.toPx())
        lens(LensRefraction.Button.height.toPx(), LensRefraction.Button.amount.toPx())
    }

    /**
     * Text and search input field effect stack — minimum distortion for uncompromised text legibility.
     */
    val TextField: BackdropEffectScope.() -> Unit = {
        vibrancy()
        blur(BackdropBlur.TextField.toPx())
        lens(LensRefraction.TextField.height.toPx(), LensRefraction.TextField.amount.toPx())
    }

    /**
     * Switch toggle thumb effect stack — tactile displacement for interactive controls.
     */
    val Toggle: BackdropEffectScope.() -> Unit = {
        vibrancy()
        blur(BackdropBlur.Toggle.toPx())
        lens(LensRefraction.Toggle.height.toPx(), LensRefraction.Toggle.amount.toPx())
    }

    /**
     * Slider thumb effect stack — spotlight displacement.
     */
    val Slider: BackdropEffectScope.() -> Unit = {
        vibrancy()
        blur(BackdropBlur.Slider.toPx())
        lens(LensRefraction.Slider.height.toPx(), LensRefraction.Slider.amount.toPx())
    }

    /**
     * Ultra-lean performance monitor and high-frequency debug overlay effect stack (blur only).
     */
    val PerformanceMonitor: BackdropEffectScope.() -> Unit = {
        blur(BackdropBlur.Subtle.toPx())
    }

    /**
     * Bottom tab bar container effect stack.
     */
    val BottomTabs: BackdropEffectScope.() -> Unit = {
        vibrancy()
        blur(BackdropBlur.BottomTabs.toPx())
        lens(LensRefraction.BottomTabs.height.toPx(), LensRefraction.BottomTabs.amount.toPx())
    }

    /**
     * Tab bar spotlight pill indicator effect stack.
     */
    val TabSpotlight: BackdropEffectScope.() -> Unit = {
        vibrancy()
        blur(BackdropBlur.Standard.toPx())
        lens(LensRefraction.TabSpotlight.height.toPx(), LensRefraction.TabSpotlight.amount.toPx())
    }

    /**
     * Micro inset effect stack for layout minimaps and splitter grip handles.
     */
    val Minimap: BackdropEffectScope.() -> Unit = {
        blur(BackdropBlur.Minimap.toPx())
        lens(LensRefraction.Minimap.height.toPx(), LensRefraction.Minimap.amount.toPx())
    }

    /**
     * Splitter grip handle effect stack — semantic alias for [Minimap].
     */
    val SplitterGrip: BackdropEffectScope.() -> Unit = Minimap

    // ==================== 4. MODAL & SCRIM TIER ====================

    /**
     * Modal dialog effect stack — heavy frosted glass with theme-aware brightness and saturation boost.
     */
    fun dialog(isLightTheme: Boolean): BackdropEffectScope.() -> Unit = {
        colorControls(
            brightness = if (isLightTheme) BackdropBrightness.DialogLight else BackdropBrightness.DialogDark,
            saturation = BackdropSaturation.Vibrant,
        )
        blur((if (isLightTheme) BackdropBlur.DialogLight else BackdropBlur.DialogDark).toPx())
        lens(
            refractionHeight = LensRefraction.Dialog.height.toPx(),
            refractionAmount = LensRefraction.Dialog.amount.toPx(),
            depthEffect = true,
        )
    }

    /**
     * Bottom sheet effect stack — heavy frosted drawer with vibrancy boost.
     */
    val BottomSheet: BackdropEffectScope.() -> Unit = {
        colorControls(saturation = BackdropSaturation.Vibrant)
        blur(BackdropBlur.BottomSheet.toPx())
        lens(
            refractionHeight = LensRefraction.BottomSheet.height.toPx(),
            refractionAmount = LensRefraction.BottomSheet.amount.toPx(),
            depthEffect = true,
        )
    }

    /**
     * Scrim overlay effect stack — diffuse blur for modal scrims and backdrop dimming.
     */
    val Scrim: BackdropEffectScope.() -> Unit = {
        vibrancy()
        blur(BackdropBlur.Scrim.toPx())
    }

    // ==================== 5. STANDARDIZED GLASS MATERIALS ====================

    /** Ultra-Thin glass material preset for floating HUDs and lightweight controls. */
    val UltraThin: BackdropEffectScope.() -> Unit = { glassUltraThin(chromaticAberration = true) }

    /** Thin glass material preset for cards, action sheets, and toolbars. */
    val Thin: BackdropEffectScope.() -> Unit = { glassThin(chromaticAberration = true) }

    /** Regular glass material preset for standard liquid glass modals and panels. */
    val Regular: BackdropEffectScope.() -> Unit = { glassRegular(chromaticAberration = true) }

    /** Thick glass material preset for control center widgets and bottom sheets. */
    val Thick: BackdropEffectScope.() -> Unit = { glassThick(chromaticAberration = true) }

    /** Chrome glass material preset for heavy window chrome and docked bars. */
    val Chrome: BackdropEffectScope.() -> Unit = { glassChrome(chromaticAberration = true) }
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
