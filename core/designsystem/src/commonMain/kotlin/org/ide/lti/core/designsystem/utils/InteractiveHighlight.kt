/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.utils

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.ide.lti.core.designsystem.theme.InteractionColors
import org.ide.lti.core.designsystem.theme.MotionSpring
import org.ide.lti.core.designsystem.theme.MotionThreshold
import org.ide.lti.core.designsystem.theme.Spacing
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
) {

    private val pressProgressAnimationSpec =
        spring(MotionSpring.DampingRatioMediumBouncy, MotionSpring.StiffnessInteractive, MotionThreshold.Fine)
    private val positionAnimationSpec =
        spring(MotionSpring.DampingRatioMediumBouncy, MotionSpring.StiffnessInteractive, Offset.VisibilityThreshold)

    private val pressProgressAnimation =
        Animatable(0f, MotionThreshold.Fine)
    private val positionAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero
    val pressProgress: Float get() = pressProgressAnimation.value
    val offset: Offset get() = positionAnimation.value - startPosition

    val modifier: Modifier =
        Modifier.drawWithContent {
            val progress = pressProgressAnimation.value
            if (progress > 0f) {
                drawRect(
                    InteractionColors.PressHighlight.copy(0.25f * progress),
                    blendMode = BlendMode.Plus,
                )
            }

            drawContent()
        }

    val gestureModifier: Modifier =
        Modifier.pointerInput(animationScope) {
            inspectDragGestures(
                onDragStart = { down ->
                    startPosition = down.position
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                        launch { positionAnimation.snapTo(startPosition) }
                    }
                },
                onDragEnd = {
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                        launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                    }
                },
                onDragCancel = {
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                        launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                    }
                },
            ) { change, _ ->
                animationScope.launch { positionAnimation.snapTo(change.position) }
            }
        }
}

/**
 * Applies physical squash-and-stretch and drag damping to a graphics layer during press/drag interactions.
 */
fun GraphicsLayerScope.applyInteractiveHighlightTransform(interactiveHighlight: InteractiveHighlight) {
    val width = size.width
    val height = size.height

    val progress = interactiveHighlight.pressProgress
    val scale = lerp(1f, 1f + Spacing.ExtraSmall.toPx() / size.height, progress)

    val maxOffset = size.minDimension
    val initialDerivative = 0.05f
    val offset = interactiveHighlight.offset
    val tx = maxOffset * tanh((initialDerivative * offset.x / maxOffset).toDouble())
    val ty = maxOffset * tanh((initialDerivative * offset.y / maxOffset).toDouble())
    translationX = tx.toFloat()
    translationY = ty.toFloat()

    val maxDragScale = Spacing.ExtraSmall.toPx() / size.height
    val offsetAngle = atan2(offset.y.toDouble(), offset.x.toDouble()).toFloat()
    scaleX = scale +
        maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
        (width / height).fastCoerceAtMost(1f)
    scaleY = scale +
        maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
        (height / width).fastCoerceAtMost(1f)
}
