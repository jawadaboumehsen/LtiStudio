/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import kotlinx.coroutines.delay
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.MotionDuration
import org.ide.lti.core.designsystem.theme.StrokeWidth

private const val STEP_DEGREES = 45f
private const val SWEEP_DEGREES = 270f

/**
 * A busy spinner for waits that last minutes. It turns in steps, not every frame: each frame redraws the whole glass
 * window, and a spinner that animated at the display's rate cost over a core of CPU for a whole build.
 */
@Composable
fun GlassSpinner(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = StrokeWidth.Active,
) {
    var step by remember { mutableIntStateOf(0) }
    val reduced = GlassTheme.reducedMotionPolicy == GlassReducedMotionPolicy.Reduced
    LaunchedEffect(reduced) {
        while (!reduced) {
            delay(MotionDuration.SpinnerStep.toLong())
            step += 1
        }
    }
    Canvas(modifier.progressSemantics().size(ComponentSize.IconButtonSize)) {
        val stroke = strokeWidth.toPx()
        drawArc(
            color = color,
            startAngle = step * STEP_DEGREES,
            sweepAngle = SWEEP_DEGREES,
            useCenter = false,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}
