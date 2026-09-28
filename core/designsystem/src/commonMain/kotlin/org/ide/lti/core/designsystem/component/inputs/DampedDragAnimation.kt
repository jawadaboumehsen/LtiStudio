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
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastFirstOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.time.TimeSource

/**
 * High-fidelity coupled spring physics engine for fluid glass controls.
 * Replicates the canonical multi-spring dynamics and hydrodynamic deformation from KMPLiquidGlass:
 *
 * 1. Stiff instantaneous position tracking: `spring(1f, 1000f)`
 * 2. Underdamped velocity bounce: `spring(0.5f, 300f)`
 * 3. Coupled organic scale swelling (1.0x to 1.5x): `spring(0.6f, 250f)` and `spring(0.7f, 250f)`
 * 4. Microsecond velocity tracking and pointer gesture arbitration via [MutatorMutex].
 */
class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float> = 0f..1f,
    val visibilityThreshold: Float = 0.001f,
    val initialScale: Float = 1f,
    var pressedScale: Float = 1.5f,
    var isReducedMotion: Boolean = false,
    var gestureStartFractionProvider: () -> Float = { initialValue },
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit = {},
    val onDragStopped: DampedDragAnimation.() -> Unit = {},
    val onDragCanceled: DampedDragAnimation.() -> Unit = {},
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit = { _, _ -> },
) {

    private val valueAnimationSpec = spring(
        dampingRatio = org.ide.lti.core.designsystem.theme.MotionSpring.DampingRatioNoBouncy,
        stiffness = org.ide.lti.core.designsystem.theme.MotionSpring.StiffnessHigh,
        visibilityThreshold = visibilityThreshold,
    )
    private val velocityAnimationSpec = spring(
        dampingRatio = org.ide.lti.core.designsystem.theme.MotionSpring.DampingRatioMediumBouncy,
        stiffness = org.ide.lti.core.designsystem.theme.MotionSpring.StiffnessInteractive,
        visibilityThreshold = visibilityThreshold * 10f,
    )
    private val pressProgressAnimationSpec = spring(
        dampingRatio = org.ide.lti.core.designsystem.theme.MotionSpring.DampingRatioNoBouncy,
        stiffness = org.ide.lti.core.designsystem.theme.MotionSpring.StiffnessHigh,
        visibilityThreshold = org.ide.lti.core.designsystem.theme.MotionThreshold.Fine,
    )
    private val scaleXAnimationSpec = spring(
        dampingRatio = org.ide.lti.core.designsystem.theme.MotionSpring.DampingRatioScaleX,
        stiffness = org.ide.lti.core.designsystem.theme.MotionSpring.StiffnessScale,
        visibilityThreshold = org.ide.lti.core.designsystem.theme.MotionThreshold.Fine,
    )
    private val scaleYAnimationSpec = spring(
        dampingRatio = org.ide.lti.core.designsystem.theme.MotionSpring.DampingRatioScaleY,
        stiffness = org.ide.lti.core.designsystem.theme.MotionSpring.StiffnessScale,
        visibilityThreshold = org.ide.lti.core.designsystem.theme.MotionThreshold.Fine,
    )

    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)

    private val mutatorMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()
    private val timeMark = TimeSource.Monotonic.markNow()
    private var activeTransitionJob: Job? = null
    private var dragPositionJob: Job? = null
    private var gestureScaleJob: Job? = null
    private var gestureStartFraction: Float = initialValue

    val value: Float get() = valueAnimation.value
    val progress: Float get() = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectDragGestures(
            onDragStart = { down ->
                gestureStartFraction = gestureStartFractionProvider()
                onDragStarted(down.position)
                press()
            },
            onDragEnd = {
                onDragStopped()
                release()
            },
            onDragCancel = {
                cancelAndRestore()
                onDragCanceled()
            },
        ) { change, dragAmount ->
            onDrag(size, dragAmount)
        }
    }

    private suspend fun cancelAndJoinPriorJobs(
        cancelTransition: Boolean = true,
        cancelDrag: Boolean = true,
        cancelScale: Boolean = true,
    ) {
        val currentJob = kotlin.coroutines.coroutineContext[Job]
        val t = if (cancelTransition && activeTransitionJob !== currentJob) activeTransitionJob else null
        val d = if (cancelDrag && dragPositionJob !== currentJob) dragPositionJob else null
        val s = if (cancelScale && gestureScaleJob !== currentJob) gestureScaleJob else null

        if (t != null) activeTransitionJob = null
        if (d != null) dragPositionJob = null
        if (s != null) gestureScaleJob = null

        t?.cancelAndJoin()
        d?.cancelAndJoin()
        s?.cancelAndJoin()
    }

    fun press() {
        if (isReducedMotion) return
        velocityTracker.resetTracking()
        gestureScaleJob = animationScope.launch {
            cancelAndJoinPriorJobs(cancelTransition = false, cancelDrag = false, cancelScale = true)
            coroutineScope {
                launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
                launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
            }
        }
    }

    fun release() {
        if (isReducedMotion) return
        gestureScaleJob = animationScope.launch {
            cancelAndJoinPriorJobs(cancelTransition = false, cancelDrag = false, cancelScale = true)
            delay(16L)
            val threshold = (valueRange.endInclusive - valueRange.start) * 0.05f
            if (valueAnimation.isRunning || abs(value - valueAnimation.targetValue) > threshold) {
                withTimeoutOrNull(500L) {
                    snapshotFlow { valueAnimation.value }
                        .filter { abs(it - valueAnimation.targetValue) < threshold }
                        .first()
                }
            }
            coroutineScope {
                launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
                launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
            }
        }
    }

    fun updateValue(value: Float) {
        val target = value.coerceIn(valueRange)
        dragPositionJob = animationScope.launch {
            cancelAndJoinPriorJobs(cancelTransition = false, cancelDrag = true, cancelScale = false)
            valueAnimation.animateTo(target, valueAnimationSpec) {
                updateVelocity(this@launch)
            }
        }
    }

    fun cancelAndRestore() {
        activeTransitionJob = animationScope.launch {
            cancelAndJoinPriorJobs()
            mutatorMutex.mutate {
                coroutineScope {
                    val target = gestureStartFraction.coerceIn(valueRange)
                    val animJob = launch { valueAnimation.animateTo(target, valueAnimationSpec) }
                    if (velocity != 0f) {
                        launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                    }
                    // Direct collapse to resting state without secondary 1.5x swell
                    launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                    launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
                    launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
                    animJob.join()
                }
            }
        }
    }

    fun snapImmediate(target: Float) {
        activeTransitionJob = animationScope.launch {
            cancelAndJoinPriorJobs()
            mutatorMutex.mutate {
                val clamped = target.coerceIn(valueRange)
                valueAnimation.snapTo(clamped)
                velocityAnimation.snapTo(0f)
                pressProgressAnimation.snapTo(0f)
                scaleXAnimation.snapTo(initialScale)
                scaleYAnimation.snapTo(initialScale)
            }
        }
    }

    fun animateToValue(value: Float) {
        activeTransitionJob = animationScope.launch {
            cancelAndJoinPriorJobs()
            mutatorMutex.mutate {
                coroutineScope {
                    // 1. Immediately swell and dissolve white pill into 3D liquid glass lens
                    launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                    launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
                    launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }

                    val target = value.coerceIn(valueRange)
                    val animJob = launch { valueAnimation.animateTo(target, valueAnimationSpec) }
                    if (velocity != 0f) {
                        launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                    }

                    // 2. Hold liquid glass state across the entire transit until reaching the destination
                    val threshold = (valueRange.endInclusive - valueRange.start) * 0.05f
                    snapshotFlow { valueAnimation.value }
                        .filter { abs(it - target) < threshold }
                        .first()

                    // 3. Upon arrival: smoothly settle and solidify back into resting pill
                    launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                    launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
                    launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
                    animJob.join()
                }
            }
        }
    }

    private fun updateVelocity(scope: CoroutineScope) {
        velocityTracker.addPosition(
            timeMark.elapsedNow().inWholeMilliseconds,
            Offset(value, 0f),
        )
        val targetVelocity = velocityTracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start)
        scope.launch { velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec) }
    }
}

/**
 * Continuous pointer gesture detector that observes drag start, delta movements, and termination
 * without consuming events unexpectedly.
 */
suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(false, PointerEventPass.Initial)
        val down = awaitFirstDown(false)
        val drag = initialDown

        onDragStart(down)
        onDrag(drag, Offset.Zero)
        val upEvent = drag(
            pointerId = drag.id,
            onDrag = { onDrag(it, it.positionChange()) },
        )
        if (upEvent == null) {
            onDragCancel()
        } else {
            onDragEnd(upEvent)
        }
    }
}

private suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit,
): PointerInputChange? {
    val initialChange = currentEvent.changes.fastFirstOrNull { it.id == pointerId }
    if (initialChange?.pressed != true) {
        return initialChange
    }
    var pointer = pointerId
    var result: PointerInputChange? = null
    var active = true
    while (active) {
        val change = awaitDragOrUp(pointer)
        when {
            change == null || change.isConsumed -> active = false
            change.changedToUpIgnoreConsumed() -> {
                result = change
                active = false
            }
            else -> {
                onDrag(change)
                pointer = change.id
            }
        }
    }
    return result
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(pointerId: PointerId): PointerInputChange? {
    var pointer = pointerId
    var result: PointerInputChange? = null
    while (result == null) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: break
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) {
                result = dragEvent
            } else {
                pointer = otherDown.id
            }
        } else if (dragEvent.previousPosition != dragEvent.position) {
            result = dragEvent
        }
    }
    return result
}
