/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.inputs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.ide.lti.core.designsystem.theme.MotionDuration
import kotlin.math.abs

/**
 * Internal motion controller for [GlassToggle], coordinating thumb translation ([positionProgress]),
 * optical glass exposure ([revealProgress]), and bounded visual stretch ([stretchFactor]).
 *
 * Enforces single-owner state architecture:
 * - Direct drag representation while dragging ([dragFraction]).
 * - Single settling [positionAnimatable] during transitions and idle.
 * - Single [revealAnimatable] governing refractive material reveal and resting fill opacity.
 * - Concurrent reveal-up transition on quick taps ensuring mid-travel refraction before settling.
 * - Reconciles external checked updates safely even while animation is in flight.
 */
@Stable
internal class GlassToggleMotionController(initialChecked: Boolean, private val coroutineScope: CoroutineScope) {
    var externalChecked: Boolean by mutableStateOf(initialChecked)
    var isReducedMotion: Boolean by mutableStateOf(false)
    var onCheckedChange: (Boolean) -> Unit = {}

    /** Direct drag fraction while dragging (0f..1f), null while idle or settling. */
    var dragFraction: Float? by mutableStateOf(null)
        private set

    /** Direct visual stretch scale during drag (1.0f..1.05f). */
    var dragStretch: Float by mutableStateOf(1f)
        private set

    /** Single owner of settling position animation (0f = Off, 1f = On). */
    val positionAnimatable = Animatable(if (initialChecked) 1f else 0f)

    /** Single owner of glass reveal animation (0f = Resting Solid, 1f = Liquid Glass). */
    val revealAnimatable = Animatable(0f)

    /** Single owner of stretch relaxation animation. */
    val stretchAnimatable = Animatable(1f)

    /** Resolved position progress (0f..1f). */
    val positionProgress: Float
        get() = dragFraction ?: positionAnimatable.value

    /** Resolved optical reveal progress (0f..1f). */
    val revealProgress: Float
        get() = revealAnimatable.value

    /** Resolved visual stretch factor (1.0f..1.05f). */
    val stretchFactor: Float
        get() = if (dragFraction != null) dragStretch else stretchAnimatable.value

    private var activeTransitionJob: Job? = null

    /** The controlled parent has been notified, but may not have recomposed with this value yet. */
    private var requestedChecked: Boolean? = null

    fun onPointerDown() {
        if (isReducedMotion) return
        activeTransitionJob?.cancel()
        activeTransitionJob = coroutineScope.launch {
            revealAnimatable.animateTo(1f, tween(durationMillis = MotionDuration.Quick, easing = FastOutSlowInEasing))
        }
    }

    fun onPointerReleaseWithoutCommit() {
        if (isReducedMotion || dragFraction != null) return
        activeTransitionJob?.cancel()
        activeTransitionJob = coroutineScope.launch {
            revealAnimatable.animateTo(0f, tween(durationMillis = MotionDuration.Fast))
        }
    }

    /** Drag consumes the press gesture, so it explicitly retains the optical reveal. */
    fun onDragStart() {
        dragFraction = positionProgress
        onPointerDown()
    }

    fun onDrag(dragDeltaPx: Float, travelPx: Float, direction: Float) {
        if (dragFraction == null) onDragStart()
        val current = positionProgress
        val deltaFraction = if (travelPx > 0f) (dragDeltaPx * direction) / travelPx else 0f
        val newFraction = (current + deltaFraction).coerceIn(0f, 1f)
        dragFraction = newFraction

        // Bounded visual stretch calculation during drag
        val velocityFactor = (abs(deltaFraction) * 4f).coerceIn(0f, 0.05f)
        dragStretch = 1f + velocityFactor
    }

    fun onDragEnd() {
        val finalFraction = dragFraction ?: positionProgress
        val targetChecked = finalFraction >= 0.5f
        commitTransition(targetChecked, startProgress = finalFraction, notifyCallback = true)
    }

    fun onDragCancel() {
        val finalFraction = dragFraction ?: positionProgress
        // Revert to external checked state without firing callback
        commitTransition(externalChecked, startProgress = finalFraction, notifyCallback = false)
    }

    fun onToggleableClick(targetChecked: Boolean) {
        commitTransition(targetChecked, startProgress = positionProgress, notifyCallback = true)
    }

    private fun commitTransition(targetChecked: Boolean, startProgress: Float, notifyCallback: Boolean = true) {
        val targetProgress = if (targetChecked) 1f else 0f
        val shouldCallback = notifyCallback && (targetChecked != externalChecked) && (requestedChecked != targetChecked)
        if (shouldCallback) requestedChecked = targetChecked

        if (isReducedMotion) {
            activeTransitionJob?.cancel()
            dragFraction = null
            dragStretch = 1f
            coroutineScope.launch {
                positionAnimatable.snapTo(targetProgress)
                revealAnimatable.snapTo(0f)
                stretchAnimatable.snapTo(1f)
            }
            if (shouldCallback) onCheckedChange(targetChecked)
            return
        }

        activeTransitionJob?.cancel()
        activeTransitionJob = coroutineScope.launch {
            // Initialize animatables to current release values before clearing drag state
            positionAnimatable.snapTo(startProgress)
            stretchAnimatable.snapTo(dragStretch)
            dragFraction = null
            dragStretch = 1f

            // 1. Position travel
            val moveJob = launch {
                positionAnimatable.animateTo(
                    targetValue = targetProgress,
                    animationSpec = tween(durationMillis = MotionDuration.LiquidMotion, easing = FastOutSlowInEasing),
                )
            }

            // 2. QUICK TAP REVEAL GUARANTEE:
            // Ensure reveal reaches 1.0 (in 80ms) even if pointer was released quickly,
            // so thumb is fully refractive at mid-travel!
            val revealUpJob = launch {
                revealAnimatable.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = MotionDuration.Instant, easing = FastOutSlowInEasing),
                )
            }

            // Relax stretch back to 1.0f
            val stretchJob = launch {
                stretchAnimatable.animateTo(1f, tween(durationMillis = MotionDuration.Responsive))
            }

            // Await full arrival at destination
            moveJob.join()
            revealUpJob.join()
            stretchJob.join()

            // 3. Thumb has arrived at destination -> return reveal to resting silhouette
            revealAnimatable.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = MotionDuration.Moderate, easing = LinearOutSlowInEasing),
            )
        }

        if (shouldCallback) {
            onCheckedChange(targetChecked)
        }
    }

    /** Reconciles animatable with external checked state when recomposition delivers new props. */
    fun reconcileExternalChecked(newChecked: Boolean) {
        externalChecked = newChecked
        if (requestedChecked == newChecked) {
            // This is the acknowledgement of our own transition, not a new external command.
            // Cancelling here used to leave the moving thumb fully opaque for the entire stroke.
            requestedChecked = null
            return
        }
        requestedChecked = null
        val targetProgress = if (newChecked) 1f else 0f
        // If not actively dragging, ensure position aligns with external checked state
        if (dragFraction == null && positionAnimatable.targetValue != targetProgress) {
            activeTransitionJob?.cancel()
            activeTransitionJob = coroutineScope.launch {
                positionAnimatable.animateTo(targetProgress, tween(MotionDuration.Fast))
            }
        }
    }
}
