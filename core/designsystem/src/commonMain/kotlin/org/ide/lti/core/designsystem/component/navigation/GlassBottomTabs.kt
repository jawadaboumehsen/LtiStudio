/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kashif_e.backdrop.backdrops.layerBackdrop
import com.kashif_e.backdrop.backdrops.rememberCombinedBackdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.vibrancy
import com.kashif_e.backdrop.highlight.Highlight
import com.kashif_e.backdrop.shadow.InnerShadow
import com.kashif_e.backdrop.shadow.Shadow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.ide.lti.core.designsystem.component.actions.*
import org.ide.lti.core.designsystem.component.devtools.*
import org.ide.lti.core.designsystem.component.display.*
import org.ide.lti.core.designsystem.component.feedback.*
import org.ide.lti.core.designsystem.component.inputs.*
import org.ide.lti.core.designsystem.component.layout.*
import org.ide.lti.core.designsystem.component.primitives.*
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BackdropBlur
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.Elevation
import org.ide.lti.core.designsystem.theme.GlassEffects
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.LensRefraction
import org.ide.lti.core.designsystem.theme.MotionSpring
import org.ide.lti.core.designsystem.theme.MotionThreshold
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.glass.GlassSceneState
import org.ide.lti.core.designsystem.theme.glass.rememberGlassSceneState
import org.ide.lti.core.designsystem.utils.DampedDragAnimation
import org.ide.lti.core.designsystem.utils.InteractiveHighlight
import kotlin.math.abs
import kotlin.math.sign

internal val LocalGlassBottomTabScale =
    staticCompositionLocalOf { { 1f } }

@Composable
fun RowScope.GlassBottomTab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scale = LocalGlassBottomTabScale.current
    val glassColors = GlassTheme.colors
    Column(
        modifier
            .clip(GlassShapes.Capsule)
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            },
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = {
            CompositionLocalProvider(LocalContentColor provides glassColors.onGlass) {
                content()
            }
        },
    )
}

/**
 * Glass bottom tab bar with a drag-to-switch spotlight indicator — the kmp-liquid-glass skill's
 * "LiquidBottomTabs" recipe. Three layers make this look like Apple's bottom bar:
 *
 * 1. An empty glass bar (the visible frosted pill everything sits on).
 * 2. An invisible tinted row, exported as its own backdrop purely so the spotlight (layer 3)
 *    can sample a color-filtered copy of the tab content through itself.
 * 3. The spotlight pill, which refracts the wallpaper *and* that tinted row — this is the
 *    "active tab glows through the moving indicator" trick.
 *
 * The whole bar can be dragged horizontally to switch tabs; on release it springs back and
 * settles on the nearest tab.
 */
@Composable
fun GlassBottomTabs(
    selectedTabIndex: () -> Int,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    backdrop: GlassSceneState? = null,
    onTabSelected: ((index: Int) -> Unit)? = null,
    tabs: @Composable RowScope.() -> Unit,
) {
    val actualBackdrop = backdrop ?: GlassTheme.scene ?: rememberGlassSceneState()
    val glassColors = GlassTheme.colors
    val accentColor = glassColors.glassAccent
    val containerColor = glassColors.glassSurfaceElevated

    val tabsBackdrop = rememberGlassSceneState()

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - Spacing.Small.toPx()) / tabsCount
        }

        // Drag-pan offset: dragging the whole bar animates back to 0 on release.
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset: () -> Float = remember(density) {
            {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    Spacing.ExtraSmall.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        val reducedMotion = GlassTheme.reducedMotion
        var currentIndex by remember(selectedTabIndex) {
            mutableIntStateOf(selectedTabIndex())
        }

        val drag = remember(animationScope, reducedMotion) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = MotionThreshold.Fine,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                reducedMotion = reducedMotion,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(
                            0f,
                            if (reducedMotion) {
                                snap()
                            } else {
                                spring(
                                    dampingRatio = MotionSpring.DampingRatioNoBouncy,
                                    stiffness = MotionSpring.StiffnessInteractive,
                                    visibilityThreshold = MotionThreshold.Offset,
                                )
                            },
                        )
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat()),
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                },
            )
        }
        LaunchedEffect(selectedTabIndex) {
            snapshotFlow { selectedTabIndex() }.collectLatest { currentIndex = it }
        }
        LaunchedEffect(drag) {
            snapshotFlow { currentIndex }.drop(1).collectLatest { index ->
                drag.animateToValue(index.toFloat())
                onTabSelected?.invoke(index)
            }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) {
                            (drag.value + 0.5f) * tabWidth + panelOffset()
                        } else {
                            size.width - (drag.value + 0.5f) * tabWidth + panelOffset()
                        },
                        size.height / 2f,
                    )
                },
            )
        }

        // Layer 1 — empty glass bar (the visible frosted pill).
        GlassBottomTabBarBackground(
            backdrop = actualBackdrop,
            containerColor = containerColor,
            panelOffset = panelOffset,
            drag = drag,
            interactiveHighlight = interactiveHighlight,
        )

        // Layer 2 — invisible "tinted" row, exported as tabsBackdrop.
        // Purely so the spotlight (layer 3) can sample a color-filtered copy of the tab content.
        GlassBottomTabTintedRow(
            backdrop = actualBackdrop,
            tabsBackdrop = tabsBackdrop,
            containerColor = containerColor,
            accentColor = accentColor,
            panelOffset = panelOffset,
            drag = drag,
            interactiveHighlight = interactiveHighlight,
            tabs = tabs,
        )

        // Layer 3 — the spotlight pill, sampling wallpaper + tabsBackdrop.
        GlassBottomTabSpotlight(
            backdrop = actualBackdrop,
            tabsBackdrop = tabsBackdrop,
            tabsCount = tabsCount,
            tabWidth = tabWidth,
            panelOffset = panelOffset,
            isLtr = isLtr,
            drag = drag,
            interactiveHighlight = interactiveHighlight,
        )

        // Layer 4 — visible, interactive foreground tab content row.
        Row(
            modifier = Modifier
                .graphicsLayer { translationX = panelOffset() }
                .height(ComponentSize.BottomTabsHeight)
                .fillMaxWidth()
                .padding(Spacing.ExtraSmall),
            verticalAlignment = Alignment.CenterVertically,
            content = tabs,
        )
    }
}

@Composable
private fun GlassBottomTabBarBackground(
    backdrop: GlassSceneState,
    containerColor: Color,
    panelOffset: () -> Float,
    drag: DampedDragAnimation,
    interactiveHighlight: InteractiveHighlight,
) {
    val effectsEnabled = GlassTheme.effectsEnabled
    Row(
        Modifier
            .graphicsLayer { translationX = panelOffset() }
            .then(
                if (effectsEnabled) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop.engineBackdrop,
                        shape = { GlassShapes.Capsule },
                        effects = GlassEffects.BottomTabs,
                        layerBlock = {
                            val p = drag.pressProgress
                            val s = lerp(1f, 1f + Spacing.Medium.toPx() / size.width, p)
                            scaleX = s
                            scaleY = s
                        },
                        onDrawSurface = { drawRect(containerColor) },
                    )
                } else {
                    Modifier
                        .clip(GlassShapes.Capsule)
                        .background(containerColor)
                },
            )
            .glassOutlineBorder(
                color = GlassTheme.colors.glassBorder,
                width = StrokeWidth.Hairline,
                shape = GlassShapes.Capsule,
            )
            .then(interactiveHighlight.modifier)
            .height(ComponentSize.BottomTabsHeight)
            .fillMaxWidth()
            .padding(Spacing.ExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {}
}

@Composable
private fun GlassBottomTabTintedRow(
    backdrop: GlassSceneState,
    tabsBackdrop: GlassSceneState,
    containerColor: Color,
    accentColor: Color,
    panelOffset: () -> Float,
    drag: DampedDragAnimation,
    interactiveHighlight: InteractiveHighlight,
    tabs: @Composable RowScope.() -> Unit,
) {
    val effectsEnabled = GlassTheme.effectsEnabled
    if (!effectsEnabled) return

    CompositionLocalProvider(
        LocalGlassBottomTabScale provides { lerp(1f, 1.2f, drag.pressProgress) },
    ) {
        Row(
            Modifier
                .clearAndSetSemantics {}
                .alpha(0f)
                .layerBackdrop(tabsBackdrop.engineBackdrop)
                .graphicsLayer { translationX = panelOffset() }
                .drawBackdrop(
                    backdrop = backdrop.engineBackdrop,
                    shape = { GlassShapes.Capsule },
                    effects = {
                        val p = drag.pressProgress
                        vibrancy()
                        blur(BackdropBlur.BottomTabs.toPx())
                        lens(
                            LensRefraction.BottomTabs.height.toPx() * p,
                            LensRefraction.BottomTabs.amount.toPx() * p,
                        )
                    },
                    highlight = {
                        val p = drag.pressProgress
                        Highlight.Default.copy(alpha = p)
                    },
                    onDrawSurface = {
                        drawRect(accentColor)
                    },
                )
                .then(interactiveHighlight.gestureModifier)
                .then(drag.modifier)
                .height(ComponentSize.BottomTabsHeight)
                .fillMaxWidth()
                .padding(Spacing.ExtraSmall),
            verticalAlignment = Alignment.CenterVertically,
            content = tabs,
        )
    }
}

@Composable
private fun GlassBottomTabSpotlight(
    backdrop: GlassSceneState,
    tabsBackdrop: GlassSceneState,
    tabsCount: Int,
    tabWidth: Float,
    panelOffset: () -> Float,
    isLtr: Boolean,
    drag: DampedDragAnimation,
    interactiveHighlight: InteractiveHighlight,
) {
    val glassColors = GlassTheme.colors
    val effectsEnabled = GlassTheme.effectsEnabled
    Box(
        Modifier
            .padding(horizontal = Spacing.ExtraSmall)
            .graphicsLayer {
                val offset = panelOffset()
                translationX =
                    if (isLtr) {
                        drag.value * tabWidth + offset
                    } else {
                        size.width - (drag.value + 1f) * tabWidth + offset
                    }
            }
            .then(interactiveHighlight.gestureModifier)
            .then(drag.modifier)
            .then(
                if (effectsEnabled) {
                    Modifier.drawBackdrop(
                        backdrop = rememberCombinedBackdrop(backdrop.engineBackdrop, tabsBackdrop.engineBackdrop),
                        shape = { GlassShapes.Capsule },
                        effects = {
                            val p = drag.pressProgress
                            lens(
                                LensRefraction.TabSpotlight.height.toPx() * p,
                                LensRefraction.TabSpotlight.amount.toPx() * p,
                                chromaticAberration = true,
                            )
                        },
                        highlight = {
                            val p = drag.pressProgress
                            Highlight.Default.copy(alpha = p)
                        },
                        shadow = { Shadow(alpha = drag.pressProgress) },
                        innerShadow = {
                            val p = drag.pressProgress
                            InnerShadow(radius = Elevation.Panel * p, alpha = p)
                        },
                        layerBlock = {
                            scaleX = drag.scaleX
                            scaleY = drag.scaleY
                            val v = drag.velocity / 10f
                            scaleX /= 1f - (v * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (v * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                        },
                        onDrawSurface = {
                            val p = drag.pressProgress
                            drawRect(
                                glassColors.onGlass.copy(alpha = AlphaTokens.Subtle),
                                alpha = 1f - p,
                            )
                            drawRect(glassColors.glassShadow.copy(alpha = AlphaTokens.UltraFaint * p))
                        },
                    )
                } else {
                    Modifier
                        .clip(GlassShapes.Capsule)
                        .background(glassColors.onGlass.copy(alpha = AlphaTokens.Subtle))
                },
            )
            .height(ComponentSize.BottomTabItemHeight)
            .fillMaxWidth(1f / tabsCount),
    )
}
