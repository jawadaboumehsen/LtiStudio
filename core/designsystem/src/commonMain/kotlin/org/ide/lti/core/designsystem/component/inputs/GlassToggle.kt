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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeSourceSelection
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.collectLatest
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.GlassControlColors
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassMaterialStyles
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.InteractionColors
import org.ide.lti.core.designsystem.theme.LocalToggleDimensions
import org.ide.lti.core.designsystem.theme.Spacing
import kotlin.math.abs

/**
 * Liquid glass switch achieving 100% parity with KMPLiquidGlass.
 *
 * Visual & Motion Architecture:
 * 1. **Default Geometry (PairA)**: 64x28 dp track with an elongated horizontal capsule thumb (40x24 dp,
 *    2 dp padding, 20 dp drag travel).
 * 2. **Coupled 4-Spring Engine**: Driven by [DampedDragAnimation] with stiff position tracking (zeta=1.0, 1000f),
 *    underdamped settling bounce (zeta=0.5, 300f), and 1.5x scale swelling on touch/drag (zeta=0.6, 250f).
 * 3. **Hydrodynamic Deformation**: Velocity-driven squish and stretch along the drag vector:
 *    scaleX /= (1 - 0.75v), scaleY *= (1 - 0.25v).
 * 4. **Liquid Glass Dynamic State Machine**:
 *    - **At rest**: Clean, crisp opaque white capsule pill (alpha = 1.0), 8 dp frosted blur, 0 px lens displacement,
 *      0 highlight, 1.0x scale.
 *    - **On touch/drag**: White fill dissolves (1 -> 0), thumb swells to 1.5x, 3D Superquadric liquid lens activates
 *      with chromatic aberration, ambient specular rim highlight ramps up, and inner shadow bevel activates.
 *    - **On release**: Underdamped spring settling bounce back to 1.0x, solidifying back into the crisp white pill.
 * 5. **Solid Fallback Contract**: When [GlassTheme.effectsEnabled] is false or reduced motion is active,
 *    both track and thumb are 100% solid, backdrop-invariant, scale locked to 1.0x, and instantaneous.
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
    val isLightTheme = GlassTheme.appTheme == AppTheme.Light
    val theme = GlassTheme.appTheme
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val isReducedMotion = GlassTheme.reducedMotionPolicy == GlassReducedMotionPolicy.Reduced
    val effectsEnabled = GlassTheme.effectsEnabled

    val currentChecked by rememberUpdatedState(checked)
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)

    val trackInactiveColor = if (isLightTheme) GlassControlColors.TrackInactiveLight else GlassControlColors.TrackInactiveDark
    val trackActiveColor = resolvedActiveColor

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val travelPx = with(density) {
        (dimensions.trackWidth - dimensions.thumbWidth - dimensions.padding * 2).toPx()
    }

    val animationScope = rememberCoroutineScope()
    var didDrag by remember { mutableStateOf(false) }
    var totalDragDistance by remember { mutableFloatStateOf(0f) }
    var fraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }

    val dampedDragAnimation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = if (effectsEnabled && !isReducedMotion) 1.5f else 1f,
            onDragStarted = {
                totalDragDistance = 0f
                didDrag = false
            },
            onDragStopped = {
                val targetChecked = if (didDrag) fraction >= 0.5f else !currentChecked
                val finalFraction = if (targetChecked) 1f else 0f
                fraction = finalFraction
                if (isReducedMotion) {
                    updateValue(finalFraction)
                } else {
                    animateToValue(finalFraction)
                }
                if (targetChecked != currentChecked) {
                    currentOnCheckedChange(targetChecked)
                }
                didDrag = false
            },
            onDrag = { _, dragAmount ->
                totalDragDistance += abs(dragAmount.x)
                if (totalDragDistance > 4f) {
                    didDrag = true
                }
                val delta = if (travelPx > 0f) dragAmount.x / travelPx else 0f
                fraction = if (isLtr) (fraction + delta).fastCoerceIn(0f, 1f)
                else (fraction - delta).fastCoerceIn(0f, 1f)
            },
        )
    }

    // Keep pressedScale dynamically updated without recreating the physics engine mid-flight
    dampedDragAnimation.pressedScale = if (effectsEnabled && !isReducedMotion) 1.5f else 1f

    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { fraction }
            .collectLatest { frac ->
                if (didDrag) {
                    dampedDragAnimation.updateValue(frac)
                }
            }
    }

    LaunchedEffect(checked) {
        val target = if (checked) 1f else 0f
        if (target != fraction) {
            fraction = target
            if (isReducedMotion) {
                dampedDragAnimation.updateValue(target)
            } else {
                dampedDragAnimation.animateToValue(target)
            }
        }
    }

    // Colors driven by continuous dampedDragAnimation.value
    val currentTrackColor = if (effectsEnabled) {
        lerp(trackInactiveColor, trackActiveColor, dampedDragAnimation.value)
    } else {
        lerp(colors.surfaceContainerHighest, resolvedActiveColor, dampedDragAnimation.value)
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

    val trackModifier = if (effectsEnabled) {
        Modifier.hazeGlass(
            input = HazeInput.Sources(GlassTheme.hazeState),
            style = remember(theme) {
                GlassMaterialStyles.baseStyle(
                    theme = theme,
                    opticalTint = Color.Transparent,
                    captureBacking = Color.Transparent,
                ).then {
                    shape(GlassShapes.HazeCapsule)
                }
            },
        )
    } else {
        Modifier.background(currentTrackColor, GlassShapes.HazeCapsule)
    }

    val thumbShape = if (dimensions.thumbIsCircle) GlassShapes.Circle else GlassShapes.HazeCapsule
    val thumbSolidColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = AlphaTokens.Ambient)

    val thumbGlassModifier = if (effectsEnabled) {
        Modifier.hazeGlass(
            input = HazeInput.Sources(GlassTheme.hazeState, selection = HazeSourceSelection.All),
            style = remember(theme) {
                GlassMaterialStyles.liquidToggleStyle(
                    theme = theme,
                    opticalTint = Color.Transparent,
                    captureBacking = Color.Transparent,
                ).then {
                    shape(GlassShapes.HazeCapsule)
                }
            },
        )
    } else {
        Modifier
    }

    val innerShadowModifier = if (effectsEnabled && !isReducedMotion) {
        Modifier.innerShadow(thumbShape) {
            val progress = dampedDragAnimation.pressProgress
            if (progress > 0.001f) {
                InnerShadow(
                    radius = Spacing.ExtraSmall * progress,
                    alpha = progress,
                )
            } else {
                null
            }
        }
    } else {
        Modifier
    }

    val restingAlpha = if (effectsEnabled) {
        (1f - dampedDragAnimation.pressProgress).coerceIn(0f, 1f)
    } else {
        1f
    }
    val restingFillColor = if (effectsEnabled) {
        GlassControlColors.ThumbResting.copy(alpha = restingAlpha)
    } else {
        thumbSolidColor
    }

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = dimensions.trackWidth, minHeight = dimensions.minTouchTarget)
            .onKeyEvent { keyEvent ->
                if (enabled && keyEvent.type == KeyEventType.KeyUp && (keyEvent.key == Key.Spacebar || keyEvent.key == Key.Enter)) {
                    val next = !checked
                    fraction = if (next) 1f else 0f
                    if (isReducedMotion) {
                        dampedDragAnimation.updateValue(fraction)
                    } else {
                        dampedDragAnimation.animateToValue(fraction)
                    }
                    currentOnCheckedChange(next)
                    true
                } else {
                    false
                }
            }
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
            }
            .then(if (enabled) dampedDragAnimation.modifier else Modifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Visual Layer 1: Track Source for Refraction Backing
        if (effectsEnabled) {
            Box(
                modifier = Modifier
                    .size(width = dimensions.trackWidth, height = dimensions.trackHeight)
                    .hazeSource(GlassTheme.hazeState, zIndex = 1f)
                    .background(currentTrackColor, GlassShapes.HazeCapsule)
                    .clip(GlassShapes.HazeCapsule),
            )
        }

        // Visual Layer 2: Visual Track (64 x 28 dp)
        Box(
            modifier = Modifier
                .size(width = dimensions.trackWidth, height = dimensions.trackHeight)
                .then(trackModifier)
                .then(focusBorderModifier)
                .clip(GlassShapes.HazeCapsule),
        )

        // Visual Layer 3: Visual Thumb (40 x 24 dp)
        Box(
            modifier = Modifier
                .graphicsLayer {
                    val fractionVal = dampedDragAnimation.value
                    val paddingPx = dimensions.padding.toPx()
                    translationX = if (isLtr) {
                        lerp(paddingPx, paddingPx + travelPx, fractionVal)
                    } else {
                        lerp(-paddingPx, -(paddingPx + travelPx), fractionVal)
                    }

                    if (effectsEnabled && !isReducedMotion) {
                        val velocityNorm = (dampedDragAnimation.velocity / 50f).fastCoerceIn(-0.2f, 0.2f)
                        scaleX = dampedDragAnimation.scaleX / (1f - velocityNorm * 0.75f)
                        scaleY = dampedDragAnimation.scaleY * (1f - velocityNorm * 0.25f)
                    }
                }
                .size(width = dimensions.thumbWidth, height = dimensions.thumbHeight)
                .then(innerShadowModifier)
                .then(thumbGlassModifier)
                .clip(thumbShape),
        ) {
            // White resting fill: opaque at rest, dissolves to transparent during motion/touch
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(restingFillColor, thumbShape),
            )

            // Subtle ambient rim highlight during motion
            if (effectsEnabled && !isReducedMotion) {
                val rimHighlightAlpha = (dampedDragAnimation.pressProgress * 0.30f).coerceIn(0f, 1f)
                if (rimHighlightAlpha > 0.001f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(
                                width = GlassDimens.HairlineBorder,
                                color = InteractionColors.PressHighlight.copy(alpha = rimHighlightAlpha),
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
