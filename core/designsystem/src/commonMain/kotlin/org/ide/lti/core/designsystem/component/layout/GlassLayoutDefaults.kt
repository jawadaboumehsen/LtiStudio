/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.layout

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.Elevation
import org.ide.lti.core.designsystem.theme.GlassLayoutColors
import org.ide.lti.core.designsystem.theme.MotionDuration
import org.ide.lti.core.designsystem.theme.MotionEasing
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.CornerRadius as ThemeCornerRadius

/**
 * Structural panels of a multi-panel workspace layout.
 *
 * In Clean Architecture, the layout engine is a generic UI shell agnostic
 * to specific domain/feature implementations (such as code editors or terminals).
 */
sealed interface LayoutPanel {
    data object Left : LayoutPanel
    data object Center : LayoutPanel
    data object Bottom : LayoutPanel
    data object Right : LayoutPanel
    data class Custom(val id: String) : LayoutPanel
}

/**
 * Orientation of a drag divider.
 */
internal enum class DividerOrientation {
    /** Vertical divider for horizontal resizing */
    Vertical,

    /** Horizontal divider for vertical resizing */
    Horizontal,
}

/**
 * Visual appearance configuration for [GlassLayout].
 *
 * Use this to customize the visual styling of the layout without affecting behavior.
 *
 * @param panelPadding Padding around each panel (default: 4.dp).
 * @param panelCornerRadius Corner radius for panel glass cards (default: 12.dp).
 * @param dividerWidth Width of drag dividers (default: 8.dp).
 * @param dividerColor Color of dividers in normal state (default: from GlassTheme).
 * @param dividerActiveColor Color of dividers when hovered/dragging (default: from GlassTheme).
 */
@Immutable
data class GlassLayoutAppearance(
    val panelPadding: Dp = LtiLayoutDefaults.PanelPadding,
    val panelCornerRadius: Dp = LtiLayoutDefaults.PanelCornerRadius,
    val dividerWidth: Dp = LtiLayoutDefaults.DividerWidth,
    val dividerLinePadding: Dp = LtiLayoutDefaults.DividerLinePadding,
    val dividerColor: Color = Color.Unspecified,
    val dividerActiveColor: Color = Color.Unspecified,
) {
    companion object {
        /**
         * Default appearance configuration using theme-aware values.
         */
        val Default = GlassLayoutAppearance()
    }
}

/**
 * Default values for [GlassLayout].
 *
 * These constants define the standard dimensions and spacings used throughout the layout.
 */
object LtiLayoutDefaults {
    /** Height of the top application bar */
    val TopBarHeight = ComponentSize.TopBarHeight

    /** Height of the bottom status bar */
    val StatusBarHeight = ComponentSize.StatusBarHeight

    /** Padding around each glass panel */
    val PanelPadding = Spacing.PanelPadding

    /** Corner radius for glass panel cards */
    val PanelCornerRadius = ThemeCornerRadius.Panel

    /** Width of drag dividers (hit target) */
    val DividerWidth = ComponentSize.DividerWidth

    /** Padding applied to the start and end of the active drag divider line */
    val DividerLinePadding = Spacing.SmallMedium

    /** Width of divider visual line in normal state */
    val DividerLineWidthNormal = StrokeWidth.Standard

    /** Width of divider visual line when active (hovered/dragging) */
    val DividerLineWidthActive = StrokeWidth.Active

    /** Top padding for toggle button overlay */
    val ToggleButtonTopPadding = ComponentSize.ToggleButtonTopPadding

    /** End padding for toggle button overlay */
    val ToggleButtonEndPadding = ComponentSize.ToggleButtonEndPadding

    /** Spacing between toggle buttons */
    val ToggleButtonSpacing = ComponentSize.ToggleButtonSpacing

    // ==================== PANEL SIZE CONSTRAINTS ====================

    /** Minimum width for side panels to prevent over-shrinking */
    val MinPanelWidth = ComponentSize.MinPanelWidth

    /** Maximum width for side panels to prevent over-expanding */
    val MaxPanelWidth = ComponentSize.MaxPanelWidth

    /** Minimum height for bottom panel */
    val MinPanelHeight = ComponentSize.MinPanelHeight

    /** Maximum height for bottom panel */
    val MaxPanelHeight = ComponentSize.MaxPanelHeight

    // ==================== VISUAL POLISH ====================

    /** Elevation for panel shadows */
    val PanelElevation = Elevation.Panel

    /** Shadow color for depth */
    val PanelShadowColor = GlassLayoutColors.PanelShadow
}

/**
 * Animation specifications for panel transitions and interactive effects.
 *
 * Professional animation configuration with smooth, precise motion curves
 * optimized for IDE-quality UI experience.
 */
internal object LtiLayoutAnimations {
    /**
     * Smooth, no-bounce spring for professional panel transitions.
     * Low stiffness creates elegant, fluid motion similar to VS Code/JetBrains IDEs.
     */
    val PanelExpandSpring = spring<androidx.compose.ui.unit.IntSize>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow,
    )

    /**
     * Coordinated fade animation with custom easing curve.
     * Uses Material Design's standard easing (0.4, 0.0, 0.2, 1.0) for smooth acceleration.
     */
    val PanelFade = tween<Float>(
        durationMillis = MotionDuration.Standard,
        easing = MotionEasing.Standard,
    )

    /**
     * Fast animation for divider size changes.
     * No bounce for precise, professional feel.
     */
    val DividerContentSize = spring<androidx.compose.ui.unit.IntSize>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /**
     * Smooth animation for panel resize operations.
     * Quick but smooth for responsive feel during drag interactions.
     */
    val PanelResize = tween<androidx.compose.ui.unit.IntSize>(
        durationMillis = MotionDuration.Moderate,
        easing = FastOutSlowInEasing,
    )

    /**
     * Subtle scale animation for micro-interactions.
     * Medium bounce for playful but professional feedback.
     */
    val ButtonHoverScale = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}
