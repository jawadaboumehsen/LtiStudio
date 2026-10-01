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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize

/**
 * Cohesive panel visibility snapshot adhering to Single Responsibility.
 */
sealed interface PanelVisibilityState {
    data object AllCollapsed : PanelVisibilityState
    data class Active(val isLeftVisible: Boolean, val isRightVisible: Boolean, val isBottomVisible: Boolean) :
        PanelVisibilityState
}

/**
 * Internal state holder for an individual draggable panel axis (Left, Right, or Bottom).
 * Manages dragging bounds, hysteresis, and two-step auto-hide behavior.
 */
@Stable
internal class PanelDragAxisState(
    val panel: LayoutPanel,
    val initialWeight: Float,
    val initialShow: Boolean,
    val minWeight: Float,
    private val computeMaxAllowedWeight: () -> Float,
    private val onDragEndCallback: ((panel: LayoutPanel, weight: Float) -> Unit)? = null,
) {
    var weight by mutableStateOf(initialWeight)
        internal set

    var isVisible by mutableStateOf(initialShow)
        internal set

    var isDragging by mutableStateOf(false)
        private set

    private var wasAtMinBeforeDrag by mutableStateOf(false)
    private var hitMinThisDrag by mutableStateOf(false)
    private var triedBelowMinThisDrag by mutableStateOf(false)

    fun onDragStart() {
        isDragging = true
        wasAtMinBeforeDrag = (weight <= minWeight + 0.001f)
        hitMinThisDrag = false
        triedBelowMinThisDrag = false
    }

    fun onDrag(weightDelta: Float) {
        val newWeight = weight + weightDelta
        if (newWeight < minWeight) {
            if (wasAtMinBeforeDrag && !hitMinThisDrag) {
                weight = minWeight
                triedBelowMinThisDrag = true
            } else {
                weight = minWeight
                hitMinThisDrag = true
            }
        } else {
            val maxAllowed = computeMaxAllowedWeight()
            weight = newWeight.coerceAtMost(maxAllowed)
            triedBelowMinThisDrag = false
        }
    }

    fun onDragEnd() {
        isDragging = false
        if (triedBelowMinThisDrag && wasAtMinBeforeDrag) {
            isVisible = false
            triedBelowMinThisDrag = false
        }
        onDragEndCallback?.invoke(panel, weight)
    }

    fun toggle() {
        isVisible = !isVisible
        if (isVisible) {
            weight = initialWeight
            wasAtMinBeforeDrag = false
            hitMinThisDrag = false
            triedBelowMinThisDrag = false
        }
    }

    fun reset() {
        weight = initialWeight
        isVisible = initialShow
        isDragging = false
        wasAtMinBeforeDrag = false
        hitMinThisDrag = false
        triedBelowMinThisDrag = false
    }
}

/**
 * State holder for [GlassLayout].
 * Manages all panel sizes, visibility, focused panel, and drag interactions.
 */
@Stable
class GlassLayoutState(
    initialLeftWeight: Float = 0.2f,
    initialRightWeight: Float = 0.25f,
    initialBottomWeight: Float = 0.3f,
    val initialShowLeft: Boolean = true,
    val initialShowRight: Boolean = true,
    val initialShowBottom: Boolean = false,
    val minLeftWeight: Float = 0.15f,
    val minRightWeight: Float = 0.2f,
    val minBottomWeight: Float = 0.1f,
    val minCenterWeight: Float = 0.4f,
    val maxLeftWeight: Float = 0.5f,
    val maxRightWeight: Float = 0.5f,
    val maxBottomWeight: Float = 0.6f,
    val onDragEnd: ((panel: LayoutPanel, weight: Float) -> Unit)? = null,
) {
    private val leftAxis = PanelDragAxisState(
        panel = LayoutPanel.Left,
        initialWeight = initialLeftWeight,
        initialShow = initialShowLeft,
        minWeight = minLeftWeight,
        computeMaxAllowedWeight = { (1.0f - minCenterWeight - rightWeight).coerceAtMost(maxLeftWeight) },
        onDragEndCallback = onDragEnd,
    )

    private val rightAxis = PanelDragAxisState(
        panel = LayoutPanel.Right,
        initialWeight = initialRightWeight,
        initialShow = initialShowRight,
        minWeight = minRightWeight,
        computeMaxAllowedWeight = { (1.0f - minCenterWeight - leftWeight).coerceAtMost(maxRightWeight) },
        onDragEndCallback = onDragEnd,
    )

    private val bottomAxis = PanelDragAxisState(
        panel = LayoutPanel.Bottom,
        initialWeight = initialBottomWeight,
        initialShow = initialShowBottom,
        minWeight = minBottomWeight,
        computeMaxAllowedWeight = { maxBottomWeight },
        onDragEndCallback = onDragEnd,
    )

    var focusedPanel by mutableStateOf<LayoutPanel?>(null)

    val visibilityState: PanelVisibilityState
        get() = if (!showLeft && !showRight && !showBottom) {
            PanelVisibilityState.AllCollapsed
        } else {
            PanelVisibilityState.Active(
                isLeftVisible = showLeft,
                isRightVisible = showRight,
                isBottomVisible = showBottom,
            )
        }

    val leftWeight: Float
        get() = leftAxis.weight
    val rightWeight: Float
        get() = rightAxis.weight
    val bottomWeight: Float
        get() = bottomAxis.weight

    val showLeft: Boolean
        get() = leftAxis.isVisible
    val showRight: Boolean
        get() = rightAxis.isVisible
    val showBottom: Boolean
        get() = bottomAxis.isVisible

    var containerSize by mutableStateOf(IntSize.Zero)
        internal set

    val isDragging: Boolean
        get() = leftAxis.isDragging || rightAxis.isDragging || bottomAxis.isDragging

    fun onLeftDragStart() {
        leftAxis.onDragStart()
    }

    fun onLeftDrag(weightDelta: Float) {
        leftAxis.onDrag(weightDelta)
    }

    fun onLeftDragEnd() {
        leftAxis.onDragEnd()
    }

    fun toggleLeft() {
        leftAxis.toggle()
    }

    fun onRightDragStart() {
        rightAxis.onDragStart()
    }

    fun onRightDrag(weightDelta: Float) {
        rightAxis.onDrag(weightDelta)
    }

    fun onRightDragEnd() {
        rightAxis.onDragEnd()
    }

    fun toggleRight() {
        rightAxis.toggle()
    }

    fun onBottomDragStart() {
        bottomAxis.onDragStart()
    }

    fun onBottomDrag(weightDelta: Float) {
        bottomAxis.onDrag(weightDelta)
    }

    fun onBottomDragEnd() {
        bottomAxis.onDragEnd()
    }

    fun toggleBottom() {
        bottomAxis.toggle()
    }

    fun resetAll() {
        leftAxis.reset()
        rightAxis.reset()
        bottomAxis.reset()
        focusedPanel = null
    }
}

/**
 * Backward-compatible typealias for [GlassLayoutState].
 */
typealias LtiLayoutState = GlassLayoutState

/**
 * Creates and remembers a [GlassLayoutState] across recompositions.
 */
@Composable
fun rememberLtiLayoutState(
    initialLeftWeight: Float = 0.2f,
    initialRightWeight: Float = 0.25f,
    initialBottomWeight: Float = 0.3f,
    initialShowLeft: Boolean = true,
    initialShowRight: Boolean = true,
    initialShowBottom: Boolean = false,
    minLeftWeight: Float = 0.15f,
    minRightWeight: Float = 0.2f,
    minBottomWeight: Float = 0.1f,
    minCenterWeight: Float = 0.4f,
    maxLeftWeight: Float = 0.5f,
    maxRightWeight: Float = 0.5f,
    maxBottomWeight: Float = 0.6f,
    onDragEnd: ((panel: LayoutPanel, weight: Float) -> Unit)? = null,
): GlassLayoutState = remember {
    GlassLayoutState(
        initialLeftWeight = initialLeftWeight,
        initialRightWeight = initialRightWeight,
        initialBottomWeight = initialBottomWeight,
        initialShowLeft = initialShowLeft,
        initialShowRight = initialShowRight,
        initialShowBottom = initialShowBottom,
        minLeftWeight = minLeftWeight,
        minRightWeight = minRightWeight,
        minBottomWeight = minBottomWeight,
        minCenterWeight = minCenterWeight,
        maxLeftWeight = maxLeftWeight,
        maxRightWeight = maxRightWeight,
        maxBottomWeight = maxBottomWeight,
        onDragEnd = onDragEnd,
    )
}
