/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.capsule.ContinuousCapsule
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.backdrops.layerBackdrop
import com.kashif_e.backdrop.backdrops.rememberCombinedBackdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.vibrancy
import com.kashif_e.backdrop.highlight.Highlight
import com.kashif_e.backdrop.shadow.InnerShadow
import com.kashif_e.backdrop.shadow.Shadow
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.utils.DampedDragAnimation
import org.ide.lti.core.designsystem.utils.InteractiveHighlight
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

internal val LocalGlassBottomTabScale =
    staticCompositionLocalOf { { 1f } }

@Composable
fun RowScope.GlassBottomTab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scale = LocalGlassBottomTabScale.current
    Column(
        modifier
            .clip(ContinuousCapsule)
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick
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
        content = content
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
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    onTabSelected: ((index: Int) -> Unit)? = null,
    tabs: @Composable RowScope.() -> Unit
) {
    val isLightTheme = !isSystemInDarkTheme()
    val glassColors = GlassTheme.colors
    val accentColor = glassColors.glassAccent
    val containerColor = glassColors.glassSurfaceElevated

    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - Spacing.Small.toPx()) / tabsCount
        }

        // Drag-pan offset: dragging the whole bar animates back to 0 on release.
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    Spacing.ExtraSmall.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember(selectedTabIndex) {
            mutableIntStateOf(selectedTabIndex())
        }

        val drag = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
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
                        if (isLtr) (drag.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (drag.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        // Layer 1 — empty glass bar (the visible frosted pill).
        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { ContinuousCapsule },
                    effects = {
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(24f.dp.toPx(), 24f.dp.toPx())
                    },
                    layerBlock = {
                        val p = drag.pressProgress
                        val s = lerp(1f, 1f + Spacing.Medium.toPx() / size.width, p)
                        scaleX = s
                        scaleY = s
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight.modifier)
                .height(ComponentSize.BottomTabsHeight)
                .fillMaxWidth()
                .padding(Spacing.ExtraSmall),
            verticalAlignment = Alignment.CenterVertically,
            content = tabs
        )

        // Layer 2 — invisible "tinted" row, exported as tabsBackdrop.
        CompositionLocalProvider(
            LocalGlassBottomTabScale provides { lerp(1f, 1.2f, drag.pressProgress) }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { ContinuousCapsule },
                        effects = {
                            val p = drag.pressProgress
                            vibrancy()
                            blur(8f.dp.toPx())
                            lens(24f.dp.toPx() * p, 24f.dp.toPx() * p)
                        },
                        highlight = {
                            val p = drag.pressProgress
                            Highlight.Default.copy(alpha = p)
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(interactiveHighlight.modifier)
                    .height(ComponentSize.BottomTabItemHeight)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.ExtraSmall)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
                content = tabs
            )
        }

        // Layer 3 — the spotlight pill, sampling wallpaper + tabsBackdrop.
        Box(
            Modifier
                .padding(horizontal = Spacing.ExtraSmall)
                .graphicsLayer {
                    translationX =
                        if (isLtr) drag.value * tabWidth + panelOffset
                        else size.width - (drag.value + 1f) * tabWidth + panelOffset
                }
                .then(interactiveHighlight.gestureModifier)
                .then(drag.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { ContinuousCapsule },
                    effects = {
                        val p = drag.pressProgress
                        lens(10f.dp.toPx() * p, 14f.dp.toPx() * p, chromaticAberration = true)
                    },
                    highlight = {
                        val p = drag.pressProgress
                        Highlight.Default.copy(alpha = p)
                    },
                    shadow = { Shadow(alpha = drag.pressProgress) },
                    innerShadow = {
                        val p = drag.pressProgress
                        InnerShadow(radius = Spacing.Small * p, alpha = p)
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
                            alpha = 1f - p
                        )
                        drawRect(glassColors.glassShadow.copy(alpha = AlphaTokens.UltraFaint * p))
                    }
                )
                .height(ComponentSize.BottomTabItemHeight)
                .fillMaxWidth(1f / tabsCount)
        )
    }
}
