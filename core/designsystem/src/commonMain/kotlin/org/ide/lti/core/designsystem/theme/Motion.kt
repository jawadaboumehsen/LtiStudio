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

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween

/**
 * Centralized duration tokens (in milliseconds) for transitions, fades, hover
 * effects, and animations across the design system.
 */
@Suppress("PropertyName") // Design-system tokens use PascalCase per CLAUDE.md token catalog
object MotionDuration {
    /** Instant micro-transition for rapid gestures and quick-tap liquid reveals (80ms). */
    const val Instant: Int = 80

    /** Quick interaction transition for pointer reveals and micro-interactions (100ms). */
    const val Quick: Int = 100

    /** Responsive relaxation transition for gesture stretch (120ms). */
    const val Responsive: Int = 120

    /** Fast transition for hover states, scale, and micro-interactions (150ms). */
    const val Fast: Int = 150

    /** Liquid motion thumb translation and settling duration (180ms). */
    const val LiquidMotion: Int = 180

    /** Moderate transition for panel resizes and layout adjustments (200ms). */
    const val Moderate: Int = 200

    /** Standard transition for modal fades, scrims, and screen transitions (300ms). */
    const val Standard: Int = 300

    /** Slow transition for prominent page slides and large surface sweeps (450ms). */
    const val Slow: Int = 450

    /** Hover delay before a tooltip appears (500ms). */
    const val TooltipDelay: Int = 500
}

/**
 * Centralized spring physics tokens (damping ratio, stiffness) for fluid,
 * interactive liquid-glass gestures and animations.
 */
@Suppress("PropertyName") // Design-system tokens use PascalCase per CLAUDE.md token catalog
object MotionSpring {
    /** Stiff instantaneous position tracking stiffness (1000f). */
    const val StiffnessHigh: Float = 1000f

    /** Custom interactive stiffness for direct-manipulation gestures (300f). */
    const val StiffnessInteractive: Float = 300f

    /** Fluid organic scale swelling stiffness (250f). */
    const val StiffnessScale: Float = 250f

    /** Critical damping ratio for non-oscillating snap (1.0f). */
    const val DampingRatioNoBouncy: Float = 1.0f

    /** Medium bouncy damping ratio for interactive feedback (0.5f). */
    const val DampingRatioMediumBouncy: Float = 0.5f

    /** Fluid scale-X swelling damping ratio (0.6f). */
    const val DampingRatioScaleX: Float = 0.6f

    /** Fluid scale-Y swelling damping ratio (0.7f). */
    const val DampingRatioScaleY: Float = 0.7f

    /** Smooth iOS navigation transition stiffness (380f). */
    const val StiffnessNavigation: Float = 380f

    /** iOS critically-damped spring ratio for fluid navigation transitions (0.85f). */
    const val DampingRatioNavigation: Float = 0.85f
}

/**
 * Centralized easing curve tokens for non-linear animation acceleration and deceleration.
 */
object MotionEasing {
    /** Standard easing curve (0.4, 0.0, 0.2, 1.0) for smooth acceleration/deceleration. */
    val Standard: CubicBezierEasing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
}

/**
 * Centralized visibility threshold tokens for animatable property convergence.
 */
@Suppress("PropertyName") // Design-system tokens use PascalCase per CLAUDE.md token catalog
object MotionThreshold {
    /** Fine precision threshold for sub-pixel and normalized progress animations (0.001f). */
    const val Fine: Float = 0.001f
}

/**
 * Resolves an [AnimationSpec] that falls back to [snap] when [reducedMotion] is enabled.
 * Defaults to a standard fast tween ([MotionDuration.Fast]) for regular motion.
 */
fun <T> resolveAnimationSpec(reducedMotion: Boolean, durationMillis: Int = MotionDuration.Fast): AnimationSpec<T> =
    if (reducedMotion) snap() else tween(durationMillis)

/**
 * IDE shell micro-interaction and transition motion tokens.
 */
@Suppress("PropertyName")
object IdeShellMotion {
    const val StateTransitionDuration: Int = 100
    const val DrawerTransitionDuration: Int = 200
    val Easing = MotionEasing.Standard
}
