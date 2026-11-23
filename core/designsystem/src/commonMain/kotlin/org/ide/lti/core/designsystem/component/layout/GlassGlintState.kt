/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.layout

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import org.ide.lti.core.designsystem.theme.AccentPresetColors
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.MotionDuration

/**
 * State-reactive ambient glass glint states communicating IDE lifecycle and operation modes.
 */
@Immutable
sealed interface GlassGlintState {
    object Idle : GlassGlintState
    data class ActiveBuild(val progress: Float? = null) : GlassGlintState
    data class Warning(val message: String? = null) : GlassGlintState
    data class Error(val message: String? = null) : GlassGlintState
    data class DeviceConnected(val deviceName: String? = null) : GlassGlintState
}

/**
 * Applies an ambient state-reactive glass glint and outline pulse to a glass card or top bar surface.
 *
 * Communicates:
 * - Soft cyan shimmer/pulse during active compile/repack builds
 * - Subtle amber border glint when warnings are present
 * - Coral border glint when errors are encountered
 * - Crisp emerald glint when a device is connected
 */
@Composable
fun Modifier.glassAmbientGlint(
    glintState: GlassGlintState,
    shape: Shape = GlassShapes.HazeFlat,
    borderWidth: Dp = GlassDimens.HairlineBorder,
): Modifier {
    if (glintState is GlassGlintState.Idle) return this

    val infiniteTransition = rememberInfiniteTransition(label = "GlassGlintPulse")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = AlphaTokens.Faint,
        targetValue = AlphaTokens.Glow,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = MotionDuration.Slow * 2,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "GlassGlintAlpha",
    )

    return this.drawWithCache {
        val strokePx = if (borderWidth == Dp.Hairline) 1f else borderWidth.toPx()
        val halfStroke = strokePx / 2f
        val innerWidth = size.width - strokePx
        val innerHeight = size.height - strokePx

        val (targetColor, strokeAlpha) = when (glintState) {
            is GlassGlintState.Idle -> Color.Transparent to 0f
            is GlassGlintState.ActiveBuild -> AccentPresetColors.Cyan to pulseProgress
            is GlassGlintState.Warning -> AccentPresetColors.Amber to AlphaTokens.Border
            is GlassGlintState.Error -> AccentPresetColors.Crimson to AlphaTokens.Border
            is GlassGlintState.DeviceConnected -> AccentPresetColors.Emerald to AlphaTokens.Border
        }

        if (strokeAlpha <= 0f || targetColor == Color.Transparent || innerWidth <= 0f || innerHeight <= 0f) {
            onDrawWithContent { drawContent() }
        } else {
            val outline = shape.createOutline(
                Size(innerWidth, innerHeight),
                layoutDirection,
                this,
            )
            val stroke = Stroke(width = strokePx)
            val glintColor = targetColor.copy(alpha = strokeAlpha)

            onDrawWithContent {
                drawContent()
                translate(left = halfStroke, top = halfStroke) {
                    drawOutline(outline = outline, color = glintColor, style = stroke)
                }
                if (glintState is GlassGlintState.ActiveBuild) {
                    val sweepBrush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            glintColor.copy(alpha = pulseProgress * 2f),
                            Color.Transparent,
                        ),
                        startX = 0f,
                        endX = size.width,
                    )
                    drawRect(
                        brush = sweepBrush,
                        topLeft = Offset(0f, 0f),
                        size = Size(size.width, strokePx * 2f),
                    )
                }
            }
        }
    }
}
