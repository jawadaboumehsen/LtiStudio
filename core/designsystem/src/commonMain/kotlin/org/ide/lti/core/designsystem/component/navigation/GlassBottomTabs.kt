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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.hazeGlass
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassMaterialStyles
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.MotionThreshold
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.sceneHazeInput
import org.ide.lti.core.designsystem.utils.DampedDragAnimation
import org.ide.lti.core.designsystem.utils.InteractiveHighlight

@Composable
fun RowScope.GlassBottomTab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
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
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                content()
            }
        },
    )
}

/**
 * Glass bottom tab bar with a drag-to-switch sliding accent indicator, built on Haze's real
 * `Modifier.hazeGlass` for the frosted bar background. The bar can be dragged horizontally to
 * switch tabs; on release it springs to the nearest tab.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalHazeApi::class)
@Composable
fun GlassBottomTabs(
    selectedTabIndex: () -> Int,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    onTabSelected: ((index: Int) -> Unit)? = null,
    tabs: @Composable RowScope.() -> Unit,
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - Spacing.Small.toPx()) / tabsCount
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
                visibilityThreshold = MotionThreshold.Fine,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat()),
                    )
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
                            (drag.value + 0.5f) * tabWidth
                        } else {
                            size.width - (drag.value + 0.5f) * tabWidth
                        },
                        size.height / 2f,
                    )
                },
            )
        }

        // Layer 1 — the visible frosted glass bar.
        GlassBottomTabBarBackground(
            containerColor = containerColor,
            interactiveHighlight = interactiveHighlight,
        )

        // Layer 2 — sliding accent spotlight indicator behind the active tab.
        GlassBottomTabSpotlight(
            accentColor = accentColor,
            tabsCount = tabsCount,
            tabWidth = tabWidth,
            isLtr = isLtr,
            drag = drag,
            interactiveHighlight = interactiveHighlight,
        )

        // Layer 3 — visible, interactive foreground tab content row.
        Row(
            modifier = Modifier
                .height(ComponentSize.BottomTabsHeight)
                .fillMaxWidth()
                .padding(Spacing.ExtraSmall),
            verticalAlignment = Alignment.CenterVertically,
            content = tabs,
        )
    }
}

@OptIn(ExperimentalHazeApi::class)
@Composable
private fun GlassBottomTabBarBackground(containerColor: Color, interactiveHighlight: InteractiveHighlight) {
    val theme = GlassTheme.appTheme
    val surfaceModifier = if (GlassTheme.effectsEnabled) {
        Modifier.hazeGlass(
            input = sceneHazeInput(GlassTheme.hazeState),
            style = remember(theme) {
                GlassMaterialStyles.baseStyle(theme = theme).then {
                    shape(GlassShapes.HazeCapsule)
                }
            },
        )
    } else {
        Modifier.background(containerColor, GlassShapes.HazeCapsule)
    }

    val borderColor = if (GlassTheme.effectsEnabled) {
        MaterialTheme.colorScheme.outline
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Row(
        Modifier
            .then(surfaceModifier)
            .clip(GlassShapes.HazeCapsule)
            .glassOutlineBorder(
                color = borderColor,
                width = StrokeWidth.Hairline,
                shape = GlassShapes.HazeCapsule,
            )
            .then(interactiveHighlight.modifier)
            .then(interactiveHighlight.gestureModifier)
            .height(ComponentSize.BottomTabsHeight)
            .fillMaxWidth()
            .padding(Spacing.ExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {}
}

@Composable
private fun GlassBottomTabSpotlight(
    accentColor: Color,
    tabsCount: Int,
    tabWidth: Float,
    isLtr: Boolean,
    drag: DampedDragAnimation,
    interactiveHighlight: InteractiveHighlight,
) {
    val effectsEnabled = GlassTheme.effectsEnabled
    val spotlightBackground = if (effectsEnabled) {
        accentColor.copy(alpha = AlphaTokens.Faded)
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    Box(
        Modifier
            .padding(horizontal = Spacing.ExtraSmall)
            .graphicsLayer {
                translationX = if (isLtr) {
                    drag.value * tabWidth
                } else {
                    size.width - (drag.value + 1f) * tabWidth
                }
                scaleX = drag.scaleX
                scaleY = drag.scaleY
            }
            .then(interactiveHighlight.gestureModifier)
            .then(drag.modifier)
            .clip(GlassShapes.Capsule)
            .background(spotlightBackground)
            .height(ComponentSize.BottomTabItemHeight)
            .fillMaxWidth(1f / tabsCount),
    )
}
