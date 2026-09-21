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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.kashif_e.backdrop.ResolvedGlassEffect
import com.kashif_e.backdrop.ResolvedProgressiveBlur
import com.kashif_e.backdrop.drawPlainBackdrop
import org.ide.lti.core.designsystem.component.actions.*
import org.ide.lti.core.designsystem.component.devtools.*
import org.ide.lti.core.designsystem.component.display.*
import org.ide.lti.core.designsystem.component.inputs.*
import org.ide.lti.core.designsystem.component.layout.*
import org.ide.lti.core.designsystem.component.navigation.*
import org.ide.lti.core.designsystem.component.primitives.*
import org.ide.lti.core.designsystem.theme.BackdropBlur
import org.ide.lti.core.designsystem.theme.BackdropTint
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.glass.GlassSceneState

/**
 * Target edge for [GlassScrollEdgeFade].
 */
enum class GlassScrollEdge {
    Top,
    Bottom,
}

/**
 * Progressive-blur scroll-edge fade strip for glass panels.
 *
 * Visually softens rows as they scroll under a panel boundary by sampling the
 * scrollable container's [GlassSceneState].
 *
 * @param backdrop The panel content's [GlassSceneState] to sample and blur.
 * @param edge The panel boundary ([GlassScrollEdge.Top] or [GlassScrollEdge.Bottom]).
 * @param modifier Modifier to be applied to the fade strip.
 * @param height The vertical extent of the fade strip.
 */
@Composable
fun GlassScrollEdgeFade(
    backdrop: GlassSceneState,
    edge: GlassScrollEdge,
    modifier: Modifier = Modifier,
    height: Dp = ComponentSize.ScrollEdgeFadeHeight,
) {
    if (!GlassTheme.effectsEnabled) return
    val tint = GlassTheme.colors.canvasSurface
    val blurRadiusPx = with(LocalDensity.current) { BackdropBlur.ScrollEdge.toPx() }
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .drawPlainBackdrop(
                backdrop = backdrop.engineBackdrop,
                shape = { RectangleShape },
                resolvedEffect = ResolvedGlassEffect(
                    progressiveBlur = ResolvedProgressiveBlur(
                        blurRadiusPx = blurRadiusPx,
                        tintColor = tint,
                        tintIntensity = BackdropTint.ScrollEdgeIntensity,
                        fadeStart = if (edge == GlassScrollEdge.Top) 1f else 0f,
                        fadeEnd = if (edge == GlassScrollEdge.Top) 0f else 1f,
                    ),
                ),
            ),
    )
}
