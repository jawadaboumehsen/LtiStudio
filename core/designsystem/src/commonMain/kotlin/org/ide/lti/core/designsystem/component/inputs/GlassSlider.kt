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

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceIn
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.GlassTransformTarget
import dev.chrisbanes.haze.glass.hazeGlass
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.sceneHazeInput
import kotlin.math.roundToInt

/**
 * Stepping behavior for [GlassSlider].
 */
sealed interface SliderStepMode {
    data object Continuous : SliderStepMode
    data class Discrete(val steps: Int) : SliderStepMode
}

/**
 * Visual styling options for [GlassSlider].
 */
@Immutable
data class SliderVisuals(
    val activeTrackColor: Color = Color.Unspecified,
    val inactiveTrackColor: Color = Color.Unspecified,
    val thumbColor: Color = Color.Unspecified,
    val trackHeight: Dp = Spacing.Small,
) {
    companion object {
        val Default = SliderVisuals()
    }
}

/**
 * Glass slider - built on Haze's real `Modifier.hazeGlass`, same shape as Haze's own sample
 * `GlassSlider` (haze/sample/shared/.../sample/components/GlassSlider.kt): a plain track, a
 * circular glass thumb whose hover/press response comes from Haze's own interaction system
 * (`interactionSource` + `interactionTransformTarget`), not hand-rolled drag-damping.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun GlassSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var width by remember { mutableFloatStateOf(1f) }
    val layoutDirection = LocalLayoutDirection.current
    val thumbDiameter = with(LocalDensity.current) { ComponentSize.SliderThumbWidth.toPx() }
    val currentValue = value()
    val rangeLength = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((currentValue - valueRange.start) / rangeLength).fastCoerceIn(0f, 1f)
    val interactionSource = remember { MutableInteractionSource() }

    fun updateAt(x: Float) {
        val directionFraction = (x / width).fastCoerceIn(0f, 1f)
        val logicalFraction = if (layoutDirection == LayoutDirection.Rtl) 1f - directionFraction else directionFraction
        onValueChange(valueRange.start + logicalFraction * (valueRange.endInclusive - valueRange.start))
    }

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .fillMaxWidth()
            .height(ComponentSize.SliderThumbHeight)
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(currentValue, valueRange)
                setProgress { requested ->
                    onValueChange(requested.coerceIn(valueRange.start, valueRange.endInclusive))
                    true
                }
            }
            .pointerInput(width) {
                detectDragGestures(
                    onDragStart = { updateAt(it.x) },
                ) { change, _ ->
                    updateAt(change.position.x)
                    change.consume()
                }
            }
            .pointerInput(width) {
                detectTapGestures { updateAt(it.x) }
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(ComponentSize.SliderTrackHeight)
                .clip(GlassShapes.Capsule)
                .background(colors.outline),
        )
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(ComponentSize.SliderTrackHeight)
                .clip(GlassShapes.Capsule)
                .background(colors.primary),
        )
        val theme = GlassTheme.appTheme
        val thumbModifier = if (GlassTheme.effectsEnabled) {
            Modifier.hazeGlass(
                input = sceneHazeInput(GlassTheme.hazeState),
                style = remember(theme) {
                    org.ide.lti.core.designsystem.theme.GlassMaterialStyles.clearStyle(
                        theme = theme,
                    ).then {
                        shape(GlassShapes.HazeCapsule)
                    }
                },
                interactionSource = interactionSource,
                interactionTransformTarget = GlassTransformTarget.MaterialAndContent,
                interactionReducedMotionPolicy = GlassTheme.reducedMotionPolicy,
            )
        } else {
            Modifier.background(colors.surfaceContainerHighest, GlassShapes.HazeCapsule)
        }

        val thumbBorderModifier = if (GlassTheme.effectsEnabled) {
            Modifier
        } else {
            Modifier.glassOutlineBorder(
                color = colors.outlineVariant,
                width = StrokeWidth.Hairline,
                shape = GlassShapes.HazeCapsule,
            )
        }

        Box(
            modifier = Modifier
                .offset { IntOffset((fraction * (width - thumbDiameter)).roundToInt(), 0) }
                .size(width = ComponentSize.SliderThumbWidth, height = ComponentSize.SliderThumbHeight)
                .then(thumbModifier)
                .clip(GlassShapes.HazeCapsule)
                .then(thumbBorderModifier),
        )
    }
}
