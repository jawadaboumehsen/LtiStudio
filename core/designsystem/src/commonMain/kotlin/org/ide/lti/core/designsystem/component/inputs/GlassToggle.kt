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

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.GlassTransformTarget
import dev.chrisbanes.haze.glass.hazeGlass
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing

/**
 * Glass switch - built on Haze's real `Modifier.hazeGlass`, same shape as Haze's own sample
 * `GlassToggle` (haze/sample/shared/.../sample/components/GlassToggle.kt): drag/tap toggles the
 * value directly, track tint driven by `checked`, thumb position by a plain `animateDpAsState`.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun GlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = Color.Unspecified,
) {
    val colors = MaterialTheme.colorScheme
    val resolvedActiveColor = if (activeColor.isSpecified) activeColor else colors.primary
    val interactionSource = remember { MutableInteractionSource() }
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = ComponentSize.ToggleTrackWidth, minHeight = ComponentSize.ToggleTrackHeight)
            .hazeGlass(
                input = HazeInput.Backdrop(GlassTheme.hazeState),
                style = remember(checked, resolvedActiveColor, colors) {
                    GlassStyle.regular then GlassStyle {
                        shape(GlassShapes.HazeCapsule)
                        tint(
                            if (checked) {
                                resolvedActiveColor.copy(alpha = AlphaTokens.Elevated)
                            } else {
                                colors.onSurface.copy(alpha = AlphaTokens.Faint)
                            },
                        )
                    }
                },
                interactionSource = interactionSource,
                interactionTransformTarget = GlassTransformTarget.MaterialAndContent,
            )
            .pointerInput(enabled, checked, direction) {
                if (enabled) {
                    var totalDrag = 0f
                    detectDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onDragEnd = {
                            val logicalDrag = totalDrag * direction
                            if (logicalDrag > 8f && !checked) onCheckedChange(true)
                            if (logicalDrag < -8f && checked) onCheckedChange(false)
                        },
                    ) { change, amount ->
                        totalDrag += amount.x
                        change.consume()
                    }
                }
            }
            .clip(GlassShapes.HazeCapsule)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = onCheckedChange,
            )
            .padding(Spacing.ExtraExtraSmall),
    ) {
        val travel = ComponentSize.ToggleTrackWidth - ComponentSize.ToggleThumbWidth - Spacing.ExtraExtraSmall * 2
        val thumbOffset by animateDpAsState(if (checked) travel else Spacing.None, label = "Glass toggle thumb")
        val thumbColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = AlphaTokens.Ambient)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = thumbOffset)
                .size(width = ComponentSize.ToggleThumbWidth, height = ComponentSize.ToggleThumbHeight)
                .clip(GlassShapes.Capsule)
                .background(thumbColor),
        )
    }
}

/**
 * Glass switch driven by a lazily-read [selected] lambda instead of a plain `checked` value.
 */
@Composable
fun GlassToggle(
    selected: () -> Boolean,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = Color.Unspecified,
) = GlassToggle(
    checked = selected(),
    onCheckedChange = onSelect,
    modifier = modifier,
    enabled = enabled,
    activeColor = activeColor,
)
