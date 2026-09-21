/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import org.ide.lti.core.designsystem.component.actions.*
import org.ide.lti.core.designsystem.component.devtools.*
import org.ide.lti.core.designsystem.component.display.*
import org.ide.lti.core.designsystem.component.feedback.*
import org.ide.lti.core.designsystem.component.inputs.*
import org.ide.lti.core.designsystem.component.navigation.*
import org.ide.lti.core.designsystem.component.primitives.*
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.glass.GlassSceneState

/**
 * A docked content panel rendered as a seamless [GlassSurface].
 *
 * Uses the active theme's [PanelConfiguration] polymorphically for shape, surface color,
 * borders, tints, and specular highlights without hardcoded theme branching.
 */
@Composable
internal fun GlassContentPanel(
    isDragging: Boolean,
    backdrop: GlassSceneState?,
    modifier: Modifier = Modifier,
    isFocused: Boolean = false,
    content: @Composable () -> Unit,
) {
    val glassColors = GlassTheme.colors
    val panelConfig = GlassTheme.panel

    val currentBorderColor = if (isFocused) {
        panelConfig.focusedBorderColor ?: glassColors.focusRing
    } else {
        panelConfig.borderColor
    }

    GlassSurface(
        modifier = modifier,
        shape = panelConfig.shape,
        backdrop = backdrop,
        surfaceColor = panelConfig.surfaceColor,
        tint = panelConfig.tint,
        borderColor = currentBorderColor,
        borderWidth = panelConfig.borderWidth,
        highlight = { panelConfig.highlight },
        shadow = { panelConfig.shadow },
        effects = panelConfig.effects ?: if (isDragging) {
            LtiLayoutDefaults.DragOptimizedEffects
        } else {
            LtiLayoutDefaults.EnhancedPanelEffects
        },
    ) {
        CompositionLocalProvider(LocalContentColor provides glassColors.onGlass) {
            content()
        }
    }
}

@Composable
internal fun GlassLeftPanel(
    state: LtiLayoutState,
    width: Dp,
    appearance: GlassLayoutAppearance,
    backdrop: GlassSceneState?,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = state.showLeft,
        enter = expandHorizontally(
            expandFrom = Alignment.Start,
            animationSpec = LtiLayoutAnimations.PanelExpandSpring,
        ) + fadeIn(animationSpec = LtiLayoutAnimations.PanelFade),
        exit = shrinkHorizontally(
            shrinkTowards = Alignment.Start,
            animationSpec = LtiLayoutAnimations.PanelExpandSpring,
        ) + fadeOut(animationSpec = LtiLayoutAnimations.PanelFade),
    ) {
        val hasFloatingGaps = GlassTheme.panel.hasFloatingGaps
        val panelModifier = if (hasFloatingGaps) {
            Modifier
                .width(width)
                .fillMaxHeight()
                .padding(start = Spacing.Small, top = Spacing.Small, bottom = Spacing.Small)
        } else {
            Modifier
                .width(width)
                .fillMaxHeight()
        }

        Row(modifier = Modifier.fillMaxHeight()) {
            GlassContentPanel(
                isDragging = state.isDragging,
                backdrop = backdrop,
                isFocused = state.focusedPanel == LayoutPanel.Left,
                modifier = panelModifier
                    .pointerInput(state) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            state.focusedPanel = LayoutPanel.Left
                        }
                    }
                    .semantics {
                        val stateDesc = if (state.showLeft) "visible" else "hidden"
                        contentDescription = "Left panel: $stateDesc"
                    },
                content = content,
            )

            GlassDragDivider(
                orientation = DividerOrientation.Vertical,
                appearance = appearance,
                onDragStart = { state.onLeftDragStart() },
                onDrag = { dragDelta ->
                    val containerWidth = state.containerSize.width.toFloat()
                    val weightDelta = dragDelta / containerWidth
                    state.onLeftDrag(weightDelta)
                },
                onDragEnd = { state.onLeftDragEnd() },
            )
        }
    }
}

@Composable
internal fun RowScope.GlassCenterColumn(
    state: LtiLayoutState,
    density: Density,
    animatedBottomWeight: Float,
    appearance: GlassLayoutAppearance,
    backdrop: GlassSceneState?,
    mainContent: @Composable () -> Unit,
    bottomPanel: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
    ) {
        val totalFixedHeight = LtiLayoutDefaults.TopBarHeight + LtiLayoutDefaults.StatusBarHeight
        val availableHeight = with(density) {
            (state.containerSize.height - totalFixedHeight.toPx()).toDp()
        }
        val bottomPanelHeight = (availableHeight * animatedBottomWeight)
            .coerceIn(LtiLayoutDefaults.MinPanelHeight, LtiLayoutDefaults.MaxPanelHeight)

        val hasFloatingGaps = GlassTheme.panel.hasFloatingGaps
        val editorModifier = if (hasFloatingGaps) {
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = Spacing.ExtraSmall, vertical = Spacing.Small)
        } else {
            Modifier
                .weight(1f)
                .fillMaxWidth()
        }

        GlassContentPanel(
            isDragging = state.isDragging,
            backdrop = backdrop,
            isFocused = state.focusedPanel == LayoutPanel.Center,
            modifier = editorModifier
                .pointerInput(state) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        state.focusedPanel = LayoutPanel.Center
                    }
                }
                .semantics {
                    contentDescription = "Main content panel"
                },
            content = mainContent,
        )

        AnimatedVisibility(
            visible = state.showBottom,
            enter = expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = LtiLayoutAnimations.PanelExpandSpring,
            ) + fadeIn(animationSpec = LtiLayoutAnimations.PanelFade),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = LtiLayoutAnimations.PanelExpandSpring,
            ) + fadeOut(animationSpec = LtiLayoutAnimations.PanelFade),
        ) {
            Column {
                GlassDragDivider(
                    orientation = DividerOrientation.Horizontal,
                    appearance = appearance,
                    onDragStart = { state.onBottomDragStart() },
                    onDrag = { dragDelta ->
                        val totalFixedHeightPx = with(density) { totalFixedHeight.toPx() }
                        val containerHeight = state.containerSize.height - totalFixedHeightPx
                        val weightDelta = -dragDelta / containerHeight
                        state.onBottomDrag(weightDelta)
                    },
                    onDragEnd = { state.onBottomDragEnd() },
                )

                val bottomModifier = if (hasFloatingGaps) {
                    Modifier
                        .height(bottomPanelHeight)
                        .fillMaxWidth()
                        .padding(start = Spacing.ExtraSmall, end = Spacing.ExtraSmall, bottom = Spacing.Small)
                } else {
                    Modifier
                        .height(bottomPanelHeight)
                        .fillMaxWidth()
                }

                GlassContentPanel(
                    isDragging = state.isDragging,
                    backdrop = backdrop,
                    isFocused = state.focusedPanel == LayoutPanel.Bottom,
                    modifier = bottomModifier
                        .pointerInput(state) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                state.focusedPanel = LayoutPanel.Bottom
                            }
                        }
                        .semantics {
                            val stateDesc = if (state.showBottom) "visible" else "hidden"
                            contentDescription = "Bottom panel: $stateDesc"
                        },
                    content = bottomPanel,
                )
            }
        }
    }
}

@Composable
internal fun GlassRightPanel(
    state: LtiLayoutState,
    width: Dp,
    appearance: GlassLayoutAppearance,
    backdrop: GlassSceneState?,
    content: @Composable () -> Unit,
) {
    val hasFloatingGaps = GlassTheme.panel.hasFloatingGaps
    val rightPanelModifier = if (hasFloatingGaps) {
        Modifier
            .width(width)
            .fillMaxHeight()
            .padding(end = Spacing.Small, top = Spacing.Small, bottom = Spacing.Small)
    } else {
        Modifier
            .width(width)
            .fillMaxHeight()
    }

    AnimatedVisibility(
        visible = state.showRight,
        enter = expandHorizontally(
            expandFrom = Alignment.End,
            animationSpec = LtiLayoutAnimations.PanelExpandSpring,
        ) + fadeIn(animationSpec = LtiLayoutAnimations.PanelFade),
        exit = shrinkHorizontally(
            shrinkTowards = Alignment.End,
            animationSpec = LtiLayoutAnimations.PanelExpandSpring,
        ) + fadeOut(animationSpec = LtiLayoutAnimations.PanelFade),
    ) {
        Row(modifier = Modifier.fillMaxHeight()) {
            GlassDragDivider(
                orientation = DividerOrientation.Vertical,
                appearance = appearance,
                onDragStart = { state.onRightDragStart() },
                onDrag = { dragDelta ->
                    val containerWidth = state.containerSize.width.toFloat()
                    val weightDelta = -dragDelta / containerWidth
                    state.onRightDrag(weightDelta)
                },
                onDragEnd = { state.onRightDragEnd() },
            )

            GlassContentPanel(
                isDragging = state.isDragging,
                backdrop = backdrop,
                isFocused = state.focusedPanel == LayoutPanel.Right,
                modifier = rightPanelModifier
                    .pointerInput(state) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            state.focusedPanel = LayoutPanel.Right
                        }
                    }
                    .semantics {
                        val stateDesc = if (state.showRight) "visible" else "hidden"
                        contentDescription = "Right panel: $stateDesc"
                    },
                content = content,
            )
        }
    }
}
