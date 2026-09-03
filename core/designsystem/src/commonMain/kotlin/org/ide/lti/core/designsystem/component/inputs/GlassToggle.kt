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

import org.ide.lti.core.designsystem.component.primitives.*
import org.ide.lti.core.designsystem.component.actions.*
import org.ide.lti.core.designsystem.component.inputs.*
import org.ide.lti.core.designsystem.component.display.*
import org.ide.lti.core.designsystem.component.navigation.*
import org.ide.lti.core.designsystem.component.layout.*
import org.ide.lti.core.designsystem.component.feedback.*
import org.ide.lti.core.designsystem.component.devtools.*

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.backdrops.layerBackdrop
import com.kashif_e.backdrop.backdrops.rememberBackdrop
import com.kashif_e.backdrop.backdrops.rememberCombinedBackdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.highlight.Highlight
import com.kashif_e.backdrop.shadow.InnerShadow
import com.kashif_e.backdrop.shadow.Shadow
import kotlinx.coroutines.flow.collectLatest
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BackdropBlur
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.Elevation
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.LensRefraction
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.ToggleColors
import org.ide.lti.core.designsystem.utils.DampedDragAnimation

@Composable
fun GlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = GlassTheme.colors.glassAccent,
    backdrop: Backdrop? = null,
) = GlassToggle(
    selected = { checked },
    onSelect = if (enabled) onCheckedChange else { _ -> },
    modifier = modifier,
    activeColor = activeColor,
    backdrop = backdrop,
)

@Suppress("LongMethod")
@Composable
fun GlassToggle(
    selected: () -> Boolean,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = GlassTheme.colors.glassAccent,
    backdrop: Backdrop? = null,
) {
    val actualBackdrop = backdrop ?: GlassTheme.backdrop ?: rememberLayerBackdrop()
    val glassColors = GlassTheme.colors
    val effectsEnabled = GlassTheme.effectsEnabled
    val resolvedActiveColor = if (activeColor.isSpecified) {
        activeColor
    } else if (GlassTheme.isDark) {
        ToggleColors.ActiveDark
    } else {
        ToggleColors.ActiveLight
    }
    val trackColor = glassColors.glassBorder

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { ComponentSize.ToggleDragWidth.toPx() }
    val animationScope = rememberCoroutineScope()
    val reducedMotion = GlassTheme.reducedMotion
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (selected()) 1f else 0f) }
    val dampedDragAnimation = remember(animationScope, reducedMotion) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            reducedMotion = reducedMotion,
            onDragStarted = {},
            onDragStopped = {
                if (didDrag) {
                    fraction = if (targetValue >= 0.5f) 1f else 0f
                    onSelect(fraction == 1f)
                    didDrag = false
                } else {
                    fraction = if (selected()) 0f else 1f
                    onSelect(fraction == 1f)
                }
            },
            onDrag = { _, dragAmount ->
                if (!didDrag) {
                    didDrag = dragAmount.x != 0f
                }
                val delta = dragAmount.x / dragWidth
                fraction =
                    if (isLtr) {
                        (fraction + delta).fastCoerceIn(0f, 1f)
                    } else {
                        (fraction - delta).fastCoerceIn(0f, 1f)
                    }
            },
        )
    }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { fraction }
            .collectLatest { fraction ->
                dampedDragAnimation.updateValue(fraction)
            }
    }
    LaunchedEffect(selected) {
        snapshotFlow { selected() }
            .collectLatest { isSelected ->
                val target = if (isSelected) 1f else 0f
                if (target != fraction) {
                    fraction = target
                    dampedDragAnimation.animateToValue(target)
                }
            }
    }

    val trackBackdrop = rememberLayerBackdrop()

    Box(
        modifier = modifier
            .toggleable(
                value = selected(),
                onValueChange = onSelect,
                role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .clip(GlassShapes.Capsule)
                .drawBehind {
                    val fraction = dampedDragAnimation.value
                    drawRect(lerp(trackColor, resolvedActiveColor, fraction))
                }
                .size(ComponentSize.ToggleTrackWidth, ComponentSize.ToggleTrackHeight),
        )

        Box(
            Modifier
                .graphicsLayer {
                    val fraction = dampedDragAnimation.value
                    val padding = Spacing.ExtraExtraSmall.toPx()
                    translationX =
                        if (isLtr) {
                            lerp(padding, padding + dragWidth, fraction)
                        } else {
                            lerp(-padding, -(padding + dragWidth), fraction)
                        }
                }
                .then(dampedDragAnimation.modifier)
                .then(
                    toggleThumbModifier(
                        effectsEnabled = effectsEnabled,
                        actualBackdrop = actualBackdrop,
                        trackBackdrop = trackBackdrop,
                        dampedDragAnimation = dampedDragAnimation,
                        glassColors = glassColors,
                    ),
                )
                .size(ComponentSize.ToggleThumbWidth, ComponentSize.ToggleThumbHeight),
        )
    }
}

@Composable
private fun toggleThumbModifier(
    effectsEnabled: Boolean,
    actualBackdrop: Backdrop,
    trackBackdrop: com.kashif_e.backdrop.backdrops.LayerBackdrop,
    dampedDragAnimation: DampedDragAnimation,
    glassColors: org.ide.lti.core.designsystem.theme.GlassColorScheme,
): Modifier {
    return if (effectsEnabled) {
        Modifier.drawBackdrop(
            backdrop = rememberCombinedBackdrop(
                actualBackdrop,
                rememberBackdrop(trackBackdrop) { drawBackdrop ->
                    val progress = dampedDragAnimation.pressProgress
                    val scaleX = lerp(2f / 3f, 0.75f, progress)
                    val scaleY = lerp(0f, 0.75f, progress)
                    scale(scaleX, scaleY) {
                        drawBackdrop()
                    }
                },
            ),
            shape = { GlassShapes.Capsule },
            effects = {
                val progress = dampedDragAnimation.pressProgress
                blur(BackdropBlur.Toggle.toPx() * (1f - progress))
                lens(
                    LensRefraction.Toggle.height.toPx() * progress,
                    LensRefraction.Toggle.amount.toPx() * progress,
                    chromaticAberration = true,
                )
            },
            highlight = {
                val progress = dampedDragAnimation.pressProgress
                Highlight.Ambient.copy(
                    width = Highlight.Ambient.width / 1.5f,
                    blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                    alpha = progress,
                )
            },
            shadow = {
                Shadow(
                    radius = Elevation.Raised,
                    color = glassColors.glassShadow.copy(alpha = AlphaTokens.Faint),
                )
            },
            innerShadow = {
                val progress = dampedDragAnimation.pressProgress
                InnerShadow(
                    radius = Elevation.Raised * progress,
                    alpha = progress,
                )
            },
            layerBlock = {
                scaleX = dampedDragAnimation.scaleX
                scaleY = dampedDragAnimation.scaleY
                val velocity = dampedDragAnimation.velocity / 50f
                scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
            },
            onDrawSurface = {
                val progress = dampedDragAnimation.pressProgress
                drawRect(glassColors.onGlass.copy(alpha = 1f - progress))
            },
        )
    } else {
        Modifier
            .clip(GlassShapes.Capsule)
            .background(glassColors.onGlass)
    }
}
