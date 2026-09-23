/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.feedback

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassTheme

/**
 * Target edge for [GlassScrollEdgeFade].
 */
enum class GlassScrollEdge {
    Top,
    Bottom,
}

/**
 * Progressive-blur scroll-edge fade strip for glass panels - built on Haze's real
 * `Modifier.hazeBlur` + `HazeProgressive.verticalGradient`, sampling the app's one shared
 * [GlassTheme.hazeState].
 *
 * @param edge The panel boundary ([GlassScrollEdge.Top] or [GlassScrollEdge.Bottom]).
 * @param modifier Modifier to be applied to the fade strip.
 * @param height The vertical extent of the fade strip.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun GlassScrollEdgeFade(
    edge: GlassScrollEdge,
    modifier: Modifier = Modifier,
    height: Dp = ComponentSize.ScrollEdgeFadeHeight,
) {
    val tint = MaterialTheme.colorScheme.surface
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .hazeBlur(
                input = HazeInput.Backdrop(GlassTheme.hazeState),
                style = remember(tint, edge) {
                    HazeBlurStyle {
                        backgroundColor(tint)
                        progressive(
                            if (edge == GlassScrollEdge.Top) {
                                HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
                            } else {
                                HazeProgressive.verticalGradient(startIntensity = 0f, endIntensity = 1f)
                            },
                        )
                    }
                },
            ),
    )
}
