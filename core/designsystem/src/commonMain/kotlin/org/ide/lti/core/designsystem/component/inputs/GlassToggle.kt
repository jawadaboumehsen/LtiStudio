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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.glass.GlassTransformTarget
import dev.chrisbanes.haze.glass.hazeGlass
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassMaterialStyles
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.LocalToggleDimensions
import org.ide.lti.core.designsystem.theme.MotionDuration
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.resolveAnimationSpec

/**
 * Glass switch - built on Haze's real `Modifier.hazeGlass`, same shape as Haze's own sample
 * `GlassToggle` (haze/sample/shared/.../sample/components/GlassToggle.kt): drag/tap toggles the
 * value directly, track tint driven by `checked`, thumb position by an animated offset.
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
    val dimensions = LocalToggleDimensions.current
    val colors = MaterialTheme.colorScheme
    val resolvedActiveColor = if (activeColor.isSpecified) activeColor else colors.primary
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f

    val theme = GlassTheme.appTheme
    val targetBacking = if (checked) resolvedActiveColor else colors.surfaceContainerHighest
    val targetTint = if (checked) {
        resolvedActiveColor.copy(alpha = AlphaTokens.Elevated)
    } else {
        colors.onSurface.copy(alpha = AlphaTokens.Faint)
    }
    val isReducedMotion = GlassTheme.reducedMotionPolicy == GlassReducedMotionPolicy.Reduced
    val colorSpec = resolveAnimationSpec<Color>(reducedMotion = isReducedMotion, durationMillis = MotionDuration.Fast)
    val dpSpec = resolveAnimationSpec<Dp>(reducedMotion = isReducedMotion, durationMillis = MotionDuration.Fast)

    val animatedTint by animateColorAsState(targetTint, animationSpec = colorSpec, label = "ToggleTint")
    val animatedBacking by animateColorAsState(targetBacking, animationSpec = colorSpec, label = "ToggleBacking")

    val trackModifier = if (GlassTheme.effectsEnabled) {
        Modifier.hazeGlass(
            input = HazeInput.Sources(GlassTheme.hazeState),
            style = remember(theme, animatedTint, animatedBacking) {
                GlassMaterialStyles.baseStyle(
                    theme = theme,
                    opticalTint = animatedTint,
                    captureBacking = animatedBacking,
                ).then {
                    shape(GlassShapes.HazeCapsule)
                    focused {
                        lightingIntensity(0.55f)
                        whitePointDelta(0.04f)
                    }
                }
            },
            interactionSource = interactionSource,
            interactionTransformTarget = GlassTransformTarget.MaterialAndContent,
            interactionReducedMotionPolicy = GlassTheme.reducedMotionPolicy,
        )
    } else {
        Modifier.background(
            animatedBacking,
            GlassShapes.HazeCapsule,
        )
    }

    val focusBorderModifier = if (isFocused) {
        Modifier.border(
            width = GlassDimens.HairlineBorder * 2,
            color = colors.primary,
            shape = GlassShapes.HazeCapsule,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = dimensions.trackWidth, minHeight = dimensions.minTouchTarget)
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
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = dimensions.trackWidth, height = dimensions.trackHeight)
                .then(trackModifier)
                .then(focusBorderModifier)
                .clip(GlassShapes.HazeCapsule)
                .padding(dimensions.padding),
        ) {
            val travel = dimensions.trackWidth - dimensions.thumbWidth - dimensions.padding * 2
            val thumbOffset by animateDpAsState(
                targetValue = if (checked) travel else Spacing.None,
                animationSpec = dpSpec,
                label = "Glass toggle thumb",
            )
            val thumbColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = AlphaTokens.Ambient)
            val thumbShape = if (dimensions.thumbIsCircle) CircleShape else GlassShapes.Capsule
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = thumbOffset)
                    .size(width = dimensions.thumbWidth, height = dimensions.thumbHeight)
                    .clip(thumbShape)
                    .background(thumbColor),
            )
        }
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
