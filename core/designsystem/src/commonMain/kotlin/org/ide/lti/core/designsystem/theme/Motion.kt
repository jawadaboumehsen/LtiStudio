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
object MotionDuration {
    /** Fast transition for hover states, scale, and micro-interactions (150ms). */
    const val Fast: Int = 150

    /** Moderate transition for panel resizes and layout adjustments (200ms). */
    const val Moderate: Int = 200

    /** Standard transition for modal fades, scrims, and screen transitions (300ms). */
    const val Standard: Int = 300

    /** Slow transition for prominent page slides and large surface sweeps (450ms). */
    const val Slow: Int = 450

    /** Hover delay before a tooltip appears (500ms). */
    const val TooltipDelay: Int = 500

    /** Damped step interpolation duration for splash progress bar (350ms). */
    const val SplashProgress: Int = 350

    /** Continuous ambient breathing cycle duration for splash volumetric aura (2200ms). */
    const val AmbientBreathing: Int = 2200

    /** Specular sweep duration across brand emblem (2400ms). */
    const val SpecularSweep: Int = 2400
}

/**
 * Centralized spring physics tokens (damping ratio, stiffness) for fluid,
 * interactive liquid-glass gestures and animations.
 */
object MotionSpring {
    /** Custom interactive stiffness for direct-manipulation gestures (300f). */
    const val StiffnessInteractive: Float = 300f

    /** Medium bouncy damping ratio for interactive feedback (0.5f). */
    const val DampingRatioMediumBouncy: Float = 0.5f
}

/**
 * Centralized easing curve tokens for non-linear animation acceleration and deceleration.
 */
object MotionEasing {
    /** Standard easing curve (0.4, 0.0, 0.2, 1.0) for smooth acceleration/deceleration. */
    val Standard: CubicBezierEasing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

    /** Emphasized decelerate curve (0.4, 0.4, 0.17, 0.9) for reveal and swipe transitions. */
    val EmphasizedDecelerate: CubicBezierEasing = CubicBezierEasing(0.4f, 0.4f, 0.17f, 0.9f)

    /** Emphasized accelerate curve (0.3, 0.0, 0.8, 0.15) for dismiss and exit transitions. */
    val EmphasizedAccelerate: CubicBezierEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
}

/**
 * Centralized visibility threshold tokens for animatable property convergence.
 */
object MotionThreshold {
    /** Fine precision threshold for sub-pixel and normalized progress animations (0.001f). */
    const val Fine: Float = 0.001f
}

/**
 * Resolves an [AnimationSpec] that falls back to [snap] when [reducedMotion] is enabled.
 * Defaults to a standard fast tween ([MotionDuration.Fast]) for regular motion.
 */
fun <T> resolveAnimationSpec(
    reducedMotion: Boolean,
    durationMillis: Int = MotionDuration.Fast,
): AnimationSpec<T> = if (reducedMotion) snap() else tween(durationMillis)

/**
 * IDE shell micro-interaction and transition motion tokens.
 */
@Suppress("PropertyName")
object IdeShellMotion {
    const val StateTransitionDuration: Int = 100
    const val DrawerTransitionDuration: Int = 200
    val Easing = MotionEasing.Standard
}
