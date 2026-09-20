/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.tooltip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.delay
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.MotionDuration
import org.ide.lti.core.designsystem.theme.Spacing

/**
 * Wraps [content] with a hover-triggered tooltip showing [tooltipText] after [delayMillis].
 *
 * Pass `delayMillis = Int.MAX_VALUE` to effectively disable the tooltip (used by callers that
 * only want it to appear conditionally).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
public fun GlassTooltipArea(
    tooltipText: String,
    modifier: Modifier = Modifier,
    delayMillis: Int = MotionDuration.TooltipDelay,
    content: @Composable () -> Unit,
) {
    var isHovered by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    LaunchedEffect(isHovered) {
        if (isHovered) {
            delay(delayMillis.toLong())
            isVisible = true
        } else {
            isVisible = false
        }
    }

    Box(
        modifier = modifier
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false },
    ) {
        content()
        if (isVisible && tooltipText.isNotBlank()) {
            Popup(popupPositionProvider = remember(density) { TooltipAbovePositionProvider(density) }) {
                Text(
                    text = tooltipText,
                    color = GlassTheme.colors.onGlass,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .background(
                            color = GlassTheme.colors.glassSurfaceElevated.copy(alpha = AlphaTokens.Elevated),
                            shape = GlassShapes.Compact,
                        )
                        .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall),
                )
            }
        }
    }
}

private class TooltipAbovePositionProvider(private val density: Density) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val gapPx = with(density) { Spacing.ExtraExtraSmall.toPx() }.toInt()
        val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        val y = anchorBounds.top - popupContentSize.height - gapPx
        return IntOffset(x.coerceAtLeast(0), y.coerceAtLeast(0))
    }
}
