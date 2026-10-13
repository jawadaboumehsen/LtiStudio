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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.MotionSpring
import org.ide.lti.core.designsystem.theme.Spacing

/**
 * An iOS-style progress bar: a slim rounded capsule on a faint track, filled with a springy ease (no stop dot).
 *
 * [progress] is 0..1, or null when how far along it is cannot be measured (then it runs indeterminate, or, with
 * reduced motion, rests as a static band: nothing moves). Changes in [progress] ease in unless motion is reduced.
 */
@Composable
fun GlassProgressBar(progress: Float?, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val reduced = GlassTheme.reducedMotionPolicy == GlassReducedMotionPolicy.Reduced
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = AlphaTokens.Glow)
    val shape = GlassShapes.HazeCapsule
    val barModifier =
        modifier
            .fillMaxWidth()
            .height(ComponentSize.ProgressBarHeight)
            .clip(shape)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            )

    when {
        progress != null -> {
            val shown by animateFloatAsState(
                targetValue = progress.coerceIn(0f, 1f),
                animationSpec =
                if (reduced) {
                    snap()
                } else {
                    spring(MotionSpring.DampingRatioNoBouncy, MotionSpring.StiffnessInteractive)
                },
                label = "glassProgress",
            )
            LinearProgressIndicator(
                progress = { shown },
                modifier = barModifier,
                color = color,
                trackColor = track,
                gapSize = Spacing.None,
                drawStopIndicator = {},
            )
        }
        reduced -> Box(barModifier.background(color.copy(alpha = AlphaTokens.Faded)))
        else -> LinearProgressIndicator(modifier = barModifier, color = color, trackColor = track)
    }
}
