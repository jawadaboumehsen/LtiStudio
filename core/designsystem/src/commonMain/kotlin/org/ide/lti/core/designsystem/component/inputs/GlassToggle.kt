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
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.glass.GlassTransformTarget
import dev.chrisbanes.haze.glass.hazeGlass
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassMaterialStyles
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.InteractionColors
import org.ide.lti.core.designsystem.theme.LocalToggleDimensions
import kotlin.math.roundToInt

/**
 * Liquid glass switch built on Haze's native `Modifier.hazeGlass` primitives.
 *
 * Visual & Motion Architecture:
 * 1. **Sibling Visual Layers**: Track (56x32 dp) and thumb (28x28 dp) render as sibling layers within the
 *    >= 48 dp interactive container so the track's capsule clipping does not truncate the thumb's rim
 *    highlights or velocity stretch.
 * 2. **Phase-Deferred Layout Placement**: Thumb position is deferred to the Layout placement phase via
 *    [Modifier.offset], naturally respecting [LayoutDirection.Rtl] through Compose's logical start-to-end
 *    travel mapping without manual mirroring branches.
 * 3. **Liquid Motion Optics**: While pressed or moving (including rapid clicks), the thumb actively reveals
 *    a refractive Haze material with base Clear optics (refraction, shallow blur, chromatic aberration),
 *    resampling the underlying backdrop at each frame, before settling back to a clean resting silhouette.
 * 4. **Solid Fallback Contract**: When [GlassTheme.effectsEnabled] is false or reduced motion is active,
 *    both track and thumb are 100% solid, backdrop-invariant, and instantaneous.
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
    val coroutineScope = rememberCoroutineScope()
    val isReducedMotion = GlassTheme.reducedMotionPolicy == GlassReducedMotionPolicy.Reduced
    val theme = GlassTheme.appTheme
    val density = LocalDensity.current

    val controller = remember {
        GlassToggleMotionController(
            initialChecked = checked,
            coroutineScope = coroutineScope,
        )
    }

    // Safely update recomposition dependencies
    controller.isReducedMotion = isReducedMotion
    controller.onCheckedChange = onCheckedChange
    LaunchedEffect(checked) {
        controller.reconcileExternalChecked(checked)
    }

    // Connect Haze's press interactions via interactionSource
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> controller.onPointerDown()
                is PressInteraction.Release -> { /* handled by toggleable click or drag end */ }
                is PressInteraction.Cancel -> controller.onPointerReleaseWithoutCommit()
            }
        }
    }

    // Continuous track color interpolation driven by positionProgress
    val inactiveBacking = colors.surfaceContainerHighest
    val activeBacking = resolvedActiveColor
    val currentBacking = lerp(inactiveBacking, activeBacking, controller.positionProgress)

    val inactiveTint = colors.onSurface.copy(alpha = AlphaTokens.Faint)
    val activeTint = resolvedActiveColor.copy(alpha = AlphaTokens.Elevated)
    val currentTint = lerp(inactiveTint, activeTint, controller.positionProgress)

    val trackModifier = if (GlassTheme.effectsEnabled) {
        Modifier.hazeGlass(
            input = HazeInput.Sources(GlassTheme.hazeState),
            style = remember(theme, currentTint, currentBacking) {
                GlassMaterialStyles.baseStyle(
                    theme = theme,
                    opticalTint = currentTint,
                    captureBacking = currentBacking,
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
            currentBacking,
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

    // Thumb optics & material
    val thumbStyle = remember(theme) {
        GlassMaterialStyles.clearStyle(
            theme = theme,
            opticalTint = Color.Transparent,
            captureBacking = Color.Transparent,
        ).then {
            shape(GlassShapes.HazeCapsule)
            pressed {
                lightingIntensity(0.85f)
                refractionMultiplier(1.4f)
                whitePointDelta(0.06f)
            }
        }
    }

    val thumbGlassModifier = if (GlassTheme.effectsEnabled) {
        Modifier.hazeGlass(
            input = HazeInput.Sources(GlassTheme.hazeState),
            style = thumbStyle,
            interactionSource = interactionSource,
            interactionTransformTarget = GlassTransformTarget.MaterialAndContent,
            interactionReducedMotionPolicy = GlassTheme.reducedMotionPolicy,
        )
    } else {
        Modifier
    }

    val thumbShape = if (dimensions.thumbIsCircle) CircleShape else GlassShapes.Capsule
    val thumbSolidColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = AlphaTokens.Ambient)
    val restingFillColor = if (GlassTheme.effectsEnabled) {
        thumbSolidColor.copy(alpha = lerp(1.0f, 0.12f, controller.revealProgress))
    } else {
        thumbSolidColor
    }

    val trackColorOverlay = currentBacking.copy(alpha = lerp(0f, 0.12f, controller.revealProgress))
    val rimHighlightColor = if (theme == AppTheme.Light) {
        colors.onSurface.copy(alpha = lerp(0.08f, 0.20f, controller.revealProgress))
    } else {
        InteractionColors.PressHighlight.copy(alpha = lerp(0.18f, 0.40f, controller.revealProgress))
    }

    val travelPx = with(density) {
        (dimensions.trackWidth - dimensions.thumbWidth - dimensions.padding * 2).toPx()
    }
    var dragConsumedThisGesture by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = dimensions.trackWidth, minHeight = dimensions.minTouchTarget)
            .pointerInput(enabled, direction, travelPx) {
                if (enabled) {
                    detectDragGestures(
                        onDragStart = { dragConsumedThisGesture = false },
                        onDrag = { change, dragAmount ->
                            dragConsumedThisGesture = true
                            controller.onDrag(dragAmount.x, travelPx, direction)
                            change.consume()
                        },
                        onDragEnd = {
                            if (dragConsumedThisGesture) {
                                controller.onDragEnd(travelPx)
                            }
                        },
                        onDragCancel = {
                            controller.onDragCancel()
                        },
                    )
                }
            }
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = { target ->
                    if (!dragConsumedThisGesture) {
                        controller.onToggleableClick(target)
                    }
                    dragConsumedThisGesture = false
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Visual Layer 1: Visual Track (56 x 32 dp)
        Box(
            modifier = Modifier
                .size(width = dimensions.trackWidth, height = dimensions.trackHeight)
                .then(trackModifier)
                .then(focusBorderModifier)
                .clip(GlassShapes.HazeCapsule),
        )

        // Visual Layer 2: Visual Thumb (28 x 28 dp) as sibling layer
        Box(
            modifier = Modifier
                .size(width = dimensions.trackWidth, height = dimensions.trackHeight)
                .padding(dimensions.padding),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    // Phase-deferred placement: reads progress inside offset lambda; automatic RTL mirroring
                    .offset {
                        val x = (controller.positionProgress * travelPx).roundToInt()
                        IntOffset(x, 0)
                    }
                    .size(width = dimensions.thumbWidth, height = dimensions.thumbHeight)
                    // Visual-only bounded stretch deformation
                    .graphicsLayer {
                        scaleX = controller.stretchFactor
                    }
                    .then(thumbGlassModifier)
                    .clip(thumbShape),
            ) {
                // Shaped resting fill layer: opaque at resting, translucent during motion
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(restingFillColor, thumbShape),
                )
                // Restrained track-color overlay
                if (GlassTheme.effectsEnabled) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(trackColorOverlay, thumbShape),
                    )
                    // Subtle rim highlight
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(
                                width = GlassDimens.HairlineBorder,
                                color = rimHighlightColor,
                                shape = thumbShape,
                            ),
                    )
                }
            }
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
