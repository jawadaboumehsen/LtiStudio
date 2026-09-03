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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.BackdropEffectScope
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.vibrancy
import com.kashif_e.backdrop.highlight.Highlight
import com.kashif_e.backdrop.shadow.Shadow
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.icon.ideGeneralChevronUp
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BackdropBlur
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.Elevation
import org.ide.lti.core.designsystem.theme.GlassEffects
import org.ide.lti.core.designsystem.theme.GlassLayoutColors
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.LensRefraction
import org.ide.lti.core.designsystem.theme.MotionDuration
import org.ide.lti.core.designsystem.theme.MotionEasing
import org.ide.lti.core.designsystem.theme.PreviewSampleColors
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.resolveAnimationSpec
import org.jetbrains.compose.ui.tooling.preview.Preview
import java.awt.Cursor
import org.ide.lti.core.designsystem.theme.CornerRadius as ThemeCornerRadius

/**
 * Professional resizable panel layout for LtiRomGui IDE with Liquid Glass theme.
 *
 * This component provides a complete IDE-style layout with glass morphism effects:
 *
 * ```
 * ┌───────────────────────────────────────────────────────┐
 * │ topBar() - GlassTopBar (40dp fixed)               │
 * ├────────────┬───────────────────────────┬──────────────┤
 * │            │                           │              │
 * │  LEFT      │   MAIN (Editor)           │    RIGHT     │
 * │  Panel     ├───────────────────────────┤    Panel     │
 * │(weight)    │   BOTTOM Panel (weight)   │  (weight)    │
 * │            │   [Optional/Collapsible]  │              │
 * ├────────────┴───────────────────────────┴──────────────┤
 * │ statusBar() - GlassStatusBar (22dp fixed)            │
 * └───────────────────────────────────────────────────────┘
 * ```
 *
 * ## Features
 *
 * **Glass Morphism:**
 * - All panels use glass morphism effects (blur, vibrancy, lens refraction)
 * - Shared backdrop for consistent glass rendering across all panels
 * - Glass-styled drag dividers with interactive hover effects
 * - Glass icon buttons for panel toggles
 *
 * **Responsive Layout:**
 * - Weight-based responsive sizing (0.0-1.0 = percentage of container)
 * - Fully adaptive to any screen size
 * - Minimum/maximum constraints on panel sizes
 * - Center column (MAIN + BOTTOM) has minimum width constraint
 *
 * **Interactive Behavior:**
 * - Smooth expand/shrink animations with spring physics
 * - Resizable panels via drag dividers
 * - Resize cursors on drag dividers (↔ for horizontal, ↕ for vertical)
 * - Two-step auto-hide: panel hides when dragged below minimum twice
 * - Hover feedback on interactive elements
 *
 * **Accessibility:**
 * - Proper content descriptions for screen readers
 * - Semantic properties for panel states
 * - Keyboard navigation support
 *
 * ## Usage Example
 *
 * ```kotlin
 * val layoutState = rememberLtiLayoutState(
 *     initialLeftWeight = 0.2f,
 *     initialRightWeight = 0.25f,
 *     initialBottomWeight = 0.3f
 * )
 *
 * GlassLayout(
 *     state = layoutState,
 *     leftPanel = { FileExplorer() },
 *     mainContent = { CodeEditor() },
 *     bottomPanel = { Terminal() },
 *     rightPanel = { AIAssistant() },
 *     appearance = GlassLayoutAppearance(
 *         panelCornerRadius = 16.dp,
 *         dividerActiveColor = Color.Blue
 *     )
 * )
 * ```
 *
 * @param state Layout state controlling panel visibility and sizing. Use [rememberLtiLayoutState].
 * @param leftPanel Content for the left panel (typically file explorer).
 * @param mainContent Content for the main central area (typically code editor).
 * @param bottomPanel Content for the bottom panel (typically terminal/console).
 * @param rightPanel Content for the right panel (typically properties/AI assistant).
 * @param modifier Modifier to be applied to the root layout container.
 * @param topBar Top application bar composable (default: [GlassTopBar]).
 * @param statusBar Bottom status bar composable (default: [GlassStatusBar]).
 * @param showToggleButtons Whether to show floating panel toggle buttons (default: true).
 * @param appearance Visual appearance configuration (default: [GlassLayoutAppearance.Default]).
 *
 * @see LtiLayoutState
 * @see GlassLayoutAppearance
 * @see rememberLtiLayoutState
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GlassLayout(
    state: LtiLayoutState,
    modifier: Modifier = Modifier,
    leftRail: @Composable () -> Unit = {},
    leftPanel: @Composable () -> Unit = {},
    mainContent: @Composable () -> Unit = {},
    bottomPanel: @Composable () -> Unit = {},
    rightPanel: @Composable () -> Unit = {},
    rightRail: @Composable () -> Unit = {},
    topBar: @Composable () -> Unit = {
        GlassTopBar(
            isLeftPanelVisible = state.showLeft,
            isRightPanelVisible = state.showRight,
            isMiddleDownPanelVisible = state.showBottom,
            onClickLeftPanelIcon = { state.toggleLeft() },
            onClickRightPanelIcon = { state.toggleRight() },
            onClickMiddleDownPanelIcon = { state.toggleBottom() },
            drawOwnChrome = false,
        )
    },
    statusBar: @Composable () -> Unit = { GlassStatusBar(drawOwnChrome = false) },
    showToggleButtons: Boolean = false,
    appearance: GlassLayoutAppearance = GlassLayoutAppearance.Default,
    backdrop: Backdrop? = null,
) {
    val density = LocalDensity.current
    val actualBackdrop = backdrop ?: GlassTheme.backdrop
    val glassColors = GlassTheme.colors

    GlassSurface(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { state.containerSize = it }
            .semantics {
                contentDescription = "LtiRomGui IDE Layout with resizable panels"
            },
        shape = RectangleShape,
        backdrop = actualBackdrop,
        surfaceColor = glassColors.chromeSurface,
        tint = Color.Transparent,
        borderColor = Color.Transparent,
        borderWidth = Dp.Hairline,
        effects = LtiLayoutDefaults.ChromeEffects,
        highlight = { null },
        shadow = { null },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar (flush to the top and side edges of the frame)
            Box(modifier = Modifier.fillMaxWidth()) {
                topBar()
            }

            // Hairline horizontal divider below top bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(StrokeWidth.Hairline)
                    .background(glassColors.glassBorder.copy(alpha = AlphaTokens.Faint)),
            )

            // Middle Row: Left Rail (Chrome) + Content GlassSurface + Right Rail (Chrome)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                leftRail()

                // Content area: panels dock flush edge-to-edge against rails, top bar, and status bar.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        val animatedLeftWeight by animateFloatAsState(
                            targetValue = state.leftWeight,
                            animationSpec = if (state.isDragging) {
                                snap()
                            } else {
                                spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessLow,
                                )
                            },
                            label = "leftWeight",
                        )

                        val animatedRightWeight by animateFloatAsState(
                            targetValue = state.rightWeight,
                            animationSpec = if (state.isDragging) {
                                snap()
                            } else {
                                spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessLow,
                                )
                            },
                            label = "rightWeight",
                        )

                        val animatedBottomWeight by animateFloatAsState(
                            targetValue = state.bottomWeight,
                            animationSpec = if (state.isDragging) {
                                snap()
                            } else {
                                spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessLow,
                                )
                            },
                            label = "bottomWeight",
                        )

                        val availableWidth = with(density) { state.containerSize.width.toDp() }
                        val leftPanelWidth = (availableWidth * animatedLeftWeight)
                            .coerceIn(LtiLayoutDefaults.MinPanelWidth, LtiLayoutDefaults.MaxPanelWidth)
                        val rightPanelWidth = (availableWidth * animatedRightWeight)
                            .coerceIn(LtiLayoutDefaults.MinPanelWidth, LtiLayoutDefaults.MaxPanelWidth)

                        GlassLeftPanel(
                            state = state,
                            width = leftPanelWidth,
                            appearance = appearance,
                            backdrop = actualBackdrop,
                            content = leftPanel,
                        )

                        GlassCenterColumn(
                            state = state,
                            density = density,
                            animatedBottomWeight = animatedBottomWeight,
                            appearance = appearance,
                            backdrop = actualBackdrop,
                            mainContent = mainContent,
                            bottomPanel = bottomPanel,
                        )

                        GlassRightPanel(
                            state = state,
                            width = rightPanelWidth,
                            appearance = appearance,
                            backdrop = actualBackdrop,
                            content = rightPanel,
                        )
                    }
                }

                rightRail()
            }

            // Hairline horizontal divider above status bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(StrokeWidth.Hairline)
                    .background(glassColors.glassBorder.copy(alpha = AlphaTokens.Faint)),
            )

            // Status Bar (flush to the bottom and side edges of the frame)
            Box(modifier = Modifier.fillMaxWidth()) {
                statusBar()
            }
        }
    }

    if (showToggleButtons) {
        GlassToggleButtonsOverlay(state = state)
    }
}

/**
 * A docked content panel rendered as a seamless [GlassSurface].
 *
 * Workspace panels dock flush edge-to-edge against each other and against the outer chrome.
 * Single shared hairline dividers provide clear, modern tool-window separation without
 * doubled borders or clashing curved corners.
 */
@Composable
private fun GlassContentPanel(
    isDragging: Boolean,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    isFocused: Boolean = false,
    content: @Composable () -> Unit,
) {
    val glassColors = GlassTheme.colors
    GlassSurface(
        modifier = modifier,
        shape = RectangleShape,
        backdrop = backdrop,
        surfaceColor = glassColors.canvasSurface.copy(alpha = AlphaTokens.Medium),
        tint = glassColors.glassTint.copy(alpha = AlphaTokens.UltraFaint),
        borderColor = Color.Transparent,
        borderWidth = Dp.Hairline,
        highlight = { null },
        effects = if (isDragging) {
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
private fun GlassLeftPanel(
    state: LtiLayoutState,
    width: Dp,
    appearance: GlassLayoutAppearance,
    backdrop: Backdrop?,
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
        Row(modifier = Modifier.fillMaxHeight()) {
            GlassContentPanel(
                isDragging = state.isDragging,
                backdrop = backdrop,
                isFocused = state.focusedPanel == WorkspacePanel.Explorer,
                modifier = Modifier
                    .width(width)
                    .fillMaxHeight()
                    .pointerInput(state) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            state.focusedPanel = WorkspacePanel.Explorer
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
private fun RowScope.GlassCenterColumn(
    state: LtiLayoutState,
    density: androidx.compose.ui.unit.Density,
    animatedBottomWeight: Float,
    appearance: GlassLayoutAppearance,
    backdrop: Backdrop?,
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

        GlassContentPanel(
            isDragging = state.isDragging,
            backdrop = backdrop,
            isFocused = state.focusedPanel == WorkspacePanel.Editor,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(state) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        state.focusedPanel = WorkspacePanel.Editor
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
                        val containerHeight = (state.containerSize.height - totalFixedHeightPx).toFloat()
                        val weightDelta = -dragDelta / containerHeight
                        state.onBottomDrag(weightDelta)
                    },
                    onDragEnd = { state.onBottomDragEnd() },
                )

                GlassContentPanel(
                    isDragging = state.isDragging,
                    backdrop = backdrop,
                    isFocused = state.focusedPanel == WorkspacePanel.Terminal,
                    modifier = Modifier
                        .height(bottomPanelHeight)
                        .fillMaxWidth()
                        .pointerInput(state) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                state.focusedPanel = WorkspacePanel.Terminal
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
private fun GlassRightPanel(
    state: LtiLayoutState,
    width: Dp,
    appearance: GlassLayoutAppearance,
    backdrop: Backdrop?,
    content: @Composable () -> Unit,
) {
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
                isFocused = state.focusedPanel == WorkspacePanel.AiAssistant,
                modifier = Modifier
                    .width(width)
                    .fillMaxHeight()
                    .pointerInput(state) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            state.focusedPanel = WorkspacePanel.AiAssistant
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

@Composable
private fun GlassToggleButtonsOverlay(
    state: LtiLayoutState,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopEnd,
    ) {
        Row(
            modifier = Modifier.padding(
                top = LtiLayoutDefaults.ToggleButtonTopPadding,
                end = LtiLayoutDefaults.ToggleButtonEndPadding,
            ),
            horizontalArrangement = Arrangement.spacedBy(LtiLayoutDefaults.ToggleButtonSpacing),
        ) {
            AnimatedToggleButton(
                onClick = { state.toggleLeft() },
                selected = state.showLeft,
            ) {
                Icon(
                    painter = if (state.showLeft) {
                        AppIcons.ClosePainterResource()
                    } else {
                        AppIcons.MenuPainterResource()
                    },
                    contentDescription = if (state.showLeft) {
                        "Hide left panel"
                    } else {
                        "Show left panel"
                    },
                    modifier = Modifier.size(IconSize.Medium),
                )
            }

            AnimatedToggleButton(
                onClick = { state.toggleBottom() },
                selected = state.showBottom,
            ) {
                Icon(
                    painter = if (state.showBottom) {
                        AppIcons.ChevronDownPainterResource()
                    } else {
                        AppIcons.ideGeneralChevronUp()
                    },
                    contentDescription = if (state.showBottom) {
                        "Hide bottom panel"
                    } else {
                        "Show bottom panel"
                    },
                    modifier = Modifier.size(IconSize.Medium),
                )
            }

            AnimatedToggleButton(
                onClick = { state.toggleRight() },
                selected = state.showRight,
            ) {
                Icon(
                    painter = if (state.showRight) {
                        AppIcons.ClosePainterResource()
                    } else {
                        AppIcons.SettingsPainterResource()
                    },
                    contentDescription = if (state.showRight) {
                        "Hide right panel"
                    } else {
                        "Show right panel"
                    },
                    modifier = Modifier.size(IconSize.Medium),
                )
            }
        }
    }
}

/**
 * Visual appearance configuration for [GlassLayout].
 *
 * Use this to customize the visual styling of the layout without affecting behavior.
 *
 * @param panelPadding Padding around each panel (default: 4.dp).
 * @param panelCornerRadius Corner radius for panel glass cards (default: 12.dp).
 * @param dividerWidth Width of drag dividers (default: 8.dp).
 * @param dividerColor Color of dividers in normal state (default: from GlassTheme).
 * @param dividerActiveColor Color of dividers when hovered/dragging (default: from GlassTheme).
 */
data class GlassLayoutAppearance(
    val panelPadding: Dp = LtiLayoutDefaults.PanelPadding,
    val panelCornerRadius: Dp = LtiLayoutDefaults.PanelCornerRadius,
    val dividerWidth: Dp = LtiLayoutDefaults.DividerWidth,
    val dividerLinePadding: Dp = LtiLayoutDefaults.DividerLinePadding,
    val dividerColor: Color = Color.Unspecified,
    val dividerActiveColor: Color = Color.Unspecified,
) {
    companion object {
        /**
         * Default appearance configuration using theme-aware values.
         */
        val Default = GlassLayoutAppearance()
    }
}

/**
 * Default values for [GlassLayout].
 *
 * These constants define the standard dimensions and spacings used throughout the layout.
 */
object LtiLayoutDefaults {
    /** Height of the top application bar */
    val TopBarHeight = ComponentSize.TopBarHeight

    /** Height of the bottom status bar */
    val StatusBarHeight = ComponentSize.StatusBarHeight

    /** Padding around each glass panel */
    val PanelPadding = Spacing.PanelPadding

    /** Corner radius for glass panel cards */
    val PanelCornerRadius = ThemeCornerRadius.Panel

    /** Width of drag dividers (hit target) */
    val DividerWidth = ComponentSize.DividerWidth

    /** Padding applied to the start and end of the active drag divider line */
    val DividerLinePadding = Spacing.SmallMedium

    /** Width of divider visual line in normal state */
    val DividerLineWidthNormal = StrokeWidth.Standard

    /** Width of divider visual line when active (hovered/dragging) */
    val DividerLineWidthActive = StrokeWidth.Active

    /** Top padding for toggle button overlay */
    val ToggleButtonTopPadding = ComponentSize.ToggleButtonTopPadding

    /** End padding for toggle button overlay */
    val ToggleButtonEndPadding = ComponentSize.ToggleButtonEndPadding

    /** Spacing between toggle buttons */
    val ToggleButtonSpacing = ComponentSize.ToggleButtonSpacing

    // ==================== PANEL SIZE CONSTRAINTS ====================

    /** Minimum width for side panels to prevent over-shrinking */
    val MinPanelWidth = ComponentSize.MinPanelWidth

    /** Maximum width for side panels to prevent over-expanding */
    val MaxPanelWidth = ComponentSize.MaxPanelWidth

    /** Minimum height for bottom panel */
    val MinPanelHeight = ComponentSize.MinPanelHeight

    /** Maximum height for bottom panel */
    val MaxPanelHeight = ComponentSize.MaxPanelHeight

    // ==================== VISUAL POLISH ====================

    /** Immersive glass effect stack for the outer chrome frame. */
    val ChromeEffects: BackdropEffectScope.() -> Unit = {
        vibrancy()
        blur(BackdropBlur.Immersive.toPx())
        lens(LensRefraction.Surface.height.toPx(), LensRefraction.Surface.amount.toPx())
    }

    /** Enhanced glass effect stack for professional panel appearance with deep frosted glass blur. */
    val EnhancedPanelEffects: BackdropEffectScope.() -> Unit = {
        vibrancy()
        blur(BackdropBlur.Deep.toPx())
        lens(LensRefraction.Panel.height.toPx(), LensRefraction.Panel.amount.toPx())
    }

    /**
     * Lightweight effect stack optimized for drag operations.
     * Reduces GPU load during continuous resizing for smooth 60 FPS.
     */
    val DragOptimizedEffects: BackdropEffectScope.() -> Unit = {
        blur(BackdropBlur.Subtle.toPx())
    }

    /** Elevation for panel shadows */
    val PanelElevation = Elevation.Panel

    /** Shadow color for depth */
    val PanelShadowColor = GlassLayoutColors.PanelShadow
}

/**
 * Animation specifications for panel transitions and interactive effects.
 *
 * Professional animation configuration with smooth, precise motion curves
 * optimized for IDE-quality UI experience.
 */
private object LtiLayoutAnimations {
    /**
     * Smooth, no-bounce spring for professional panel transitions.
     * Low stiffness creates elegant, fluid motion similar to VS Code/JetBrains IDEs.
     */
    val PanelExpandSpring = spring<androidx.compose.ui.unit.IntSize>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        // Changed from Medium to Low for smoother motion
        stiffness = Spring.StiffnessLow,
    )

    /**
     * Coordinated fade animation with custom easing curve.
     * Uses Material Design's standard easing (0.4, 0.0, 0.2, 1.0) for smooth acceleration.
     */
    val PanelFade = tween<Float>(
        // Reduced from 400ms for snappier feel
        durationMillis = MotionDuration.Standard,
        // Custom professional easing
        easing = MotionEasing.Standard,
    )

    /**
     * Fast animation for divider size changes.
     * No bounce for precise, professional feel.
     */
    val DividerContentSize = spring<androidx.compose.ui.unit.IntSize>(
        // Changed from MediumBouncy to NoBouncy
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /**
     * Smooth animation for panel resize operations.
     * Quick but smooth for responsive feel during drag interactions.
     */
    val PanelResize = tween<androidx.compose.ui.unit.IntSize>(
        durationMillis = MotionDuration.Moderate,
        easing = FastOutSlowInEasing,
    )

    /**
     * Subtle scale animation for micro-interactions.
     * Medium bounce for playful but professional feedback.
     */
    val ButtonHoverScale = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}

/**
 * Animated toggle button with professional hover scale effect.
 *
 * Wraps GlassIconButton with smooth scale animation on hover for micro-interaction polish.
 *
 * @param onClick Callback when button is clicked
 * @param selected Whether the button is in selected state
 * @param content Icon content to display
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AnimatedToggleButton(
    onClick: () -> Unit,
    selected: Boolean,
    content: @Composable () -> Unit,
) {
    var isHovered by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.08f else 1f,
        animationSpec = LtiLayoutAnimations.ButtonHoverScale,
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false },
    ) {
        GlassIconButton(
            onClick = onClick,
            selected = selected,
            content = content,
        )
    }
}

/**
 * Orientation of a drag divider.
 */
private enum class DividerOrientation {
    /** Vertical divider for horizontal resizing */
    Vertical,

    /** Horizontal divider for vertical resizing */
    Horizontal,
}

/**
 * Unified glass-styled drag divider component.
 *
 * Provides interactive resize functionality with glass morphism effects,
 * hover feedback, and proper cursor indicators.
 *
 * @param orientation Whether this is a vertical or horizontal divider.
 * @param appearance Visual appearance configuration from parent layout.
 * @param backdrop Shared backdrop for glass effects.
 * @param onDragStart Callback when drag starts.
 * @param onDrag Callback during drag with delta in pixels.
 * @param onDragEnd Callback when drag ends or is cancelled.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun GlassDragDivider(
    orientation: DividerOrientation,
    appearance: GlassLayoutAppearance,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    backdrop: com.kashif_e.backdrop.Backdrop? = null,
    onDragEnd: () -> Unit,
) {
    var isHovered by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }

    // Instant alpha during drag for 1:1 mouse tracking, smooth fade on hover
    val alpha by animateFloatAsState(
        targetValue = if (isHovered || isDragging) 1f else 0f,
        animationSpec = if (isDragging) {
            // Instant update during drag
            snap()
        } else {
            // Smooth fade on hover, instant on reduced motion
            resolveAnimationSpec(GlassTheme.reducedMotion)
        },
    )

    // Get backdrop from theme if not explicitly provided
    val actualBackdrop = backdrop ?: GlassTheme.backdrop ?: rememberLayerBackdrop()

    // Capture colors from theme (must be done in composable context)
    val colors = GlassTheme.colors
    val effectsEnabled = GlassTheme.effectsEnabled
    val normalColor = if (appearance.dividerColor.isSpecified) {
        appearance.dividerColor
    } else {
        colors.glassBorder.copy(alpha = AlphaTokens.Hover)
    }
    val activeColor = if (appearance.dividerActiveColor.isSpecified) {
        appearance.dividerActiveColor
    } else {
        colors.glassAccent
    }

    val (cursor, sizeModifier, lineModifier) = getDividerModifiers(orientation, appearance)

    Box(
        modifier = sizeModifier
            .pointerHoverIcon(cursor)
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                        onDragStart()
                    },
                    onDragEnd = {
                        isDragging = false
                        onDragEnd()
                    },
                    onDragCancel = {
                        isDragging = false
                        onDragEnd()
                    },
                ) { change, dragAmount ->
                    change.consume()
                    val delta = when (orientation) {
                        DividerOrientation.Vertical -> dragAmount.x
                        DividerOrientation.Horizontal -> dragAmount.y
                    }
                    onDrag(delta)
                }
            }
            .semantics {
                val stateDesc = if (isDragging) "dragging" else if (isHovered) "hovered" else "idle"
                contentDescription = "Resize divider: $stateDesc"
            },
        contentAlignment = Alignment.Center,
    ) {
        // Single shared idle hairline divider between adjacent docked panels
        val idleLineModifier = when (orientation) {
            DividerOrientation.Vertical -> Modifier
                .width(StrokeWidth.Hairline)
                .fillMaxHeight()
                .background(normalColor)
            DividerOrientation.Horizontal -> Modifier
                .height(StrokeWidth.Hairline)
                .fillMaxWidth()
                .background(normalColor)
        }
        Box(modifier = idleLineModifier)

        // Divider line active state - animated glow during hover/drag
        Box(
            modifier = lineModifier
                .alpha(alpha)
                .clip(GlassShapes.Capsule)
                .then(
                    getDividerBackgroundModifier(
                        isDragging = isDragging,
                        isHovered = isHovered,
                        activeColor = activeColor,
                        normalColor = normalColor,
                        actualBackdrop = actualBackdrop,
                        orientation = orientation,
                        effectsEnabled = effectsEnabled,
                    ),
                ),
        )
    }
}

private fun getDividerModifiers(
    orientation: DividerOrientation,
    appearance: GlassLayoutAppearance,
): Triple<PointerIcon, Modifier, Modifier> = when (orientation) {
    DividerOrientation.Vertical -> Triple(
        PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)),
        Modifier.width(appearance.dividerWidth).fillMaxHeight(),
        Modifier
            .width(LtiLayoutDefaults.DividerLineWidthActive)
            .fillMaxHeight()
            .padding(vertical = appearance.dividerLinePadding),
    )
    DividerOrientation.Horizontal -> Triple(
        PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR)),
        Modifier.height(appearance.dividerWidth).fillMaxWidth(),
        Modifier
            .height(LtiLayoutDefaults.DividerLineWidthActive)
            .fillMaxWidth()
            .padding(horizontal = appearance.dividerLinePadding),
    )
}

private fun getDividerBackgroundModifier(
    isDragging: Boolean,
    isHovered: Boolean,
    activeColor: Color,
    normalColor: Color,
    actualBackdrop: com.kashif_e.backdrop.Backdrop,
    orientation: DividerOrientation,
    effectsEnabled: Boolean = true,
): Modifier = when {
    isDragging -> Modifier.background(activeColor)
    isHovered -> if (effectsEnabled) {
        Modifier
            .drawBehind {
                drawRoundRect(
                    color = activeColor.copy(alpha = AlphaTokens.Glow),
                    cornerRadius = CornerRadius(ThemeCornerRadius.Pill.toPx()),
                    size = size.copy(
                        width = size.width + Spacing.Small.toPx(),
                        height = size.height + Spacing.Small.toPx(),
                    ),
                    topLeft = Offset(
                        x = -Spacing.ExtraSmall.toPx(),
                        y = -Spacing.ExtraSmall.toPx(),
                    ),
                )
            }
            .drawBackdrop(
                backdrop = actualBackdrop,
                shape = { GlassShapes.Capsule },
                effects = GlassEffects.SplitterGrip,
                onDrawSurface = {
                    val gradient = when (orientation) {
                        DividerOrientation.Vertical -> Brush.verticalGradient(
                            colors = listOf(
                                activeColor.copy(alpha = AlphaTokens.Muted),
                                activeColor.copy(alpha = AlphaTokens.Prominent),
                                activeColor.copy(alpha = AlphaTokens.Muted),
                            ),
                        )
                        DividerOrientation.Horizontal -> Brush.horizontalGradient(
                            colors = listOf(
                                activeColor.copy(alpha = AlphaTokens.Muted),
                                activeColor.copy(alpha = AlphaTokens.Prominent),
                                activeColor.copy(alpha = AlphaTokens.Muted),
                            ),
                        )
                    }
                    drawRect(gradient)
                },
            )
    } else {
        Modifier.background(activeColor)
    }
    else -> Modifier.background(normalColor)
}

// ==================== PREVIEW ====================

/**
 * Preview of [GlassLayout] with sample panels.
 *
 * Demonstrates the complete layout with glass theme integration.
 */
@Preview
@Composable
fun PreviewGlassLayout() {
    val state = rememberLtiLayoutState()

    MaterialTheme {
        GlassLayout(
            state = state,
            leftPanel = { SamplePanel("FILE EXPLORER", PreviewSampleColors.Blue) },
            mainContent = { SamplePanel("CODE EDITOR", PreviewSampleColors.Purple) },
            bottomPanel = { SamplePanel("TERMINAL", PreviewSampleColors.Orange) },
            rightPanel = { SamplePanel("PROPERTIES", PreviewSampleColors.Green) },
        )
    }
}

/**
 * Workspace panels that can receive focus.
 */
enum class WorkspacePanel {
    Explorer,
    Editor,
    Terminal,
    AiAssistant,
}

/**
 * State holder for GlassLayout
 * Manages all panel sizes, visibility, and drag behavior
 */
@androidx.compose.runtime.Stable
internal class PanelDragAxisState(
    val panel: WorkspacePanel,
    val initialWeight: Float,
    val initialShow: Boolean,
    val minWeight: Float,
    private val computeMaxAllowedWeight: () -> Float,
    private val onDragEndCallback: ((panel: WorkspacePanel, weight: Float) -> Unit)? = null,
) {
    var weight by mutableStateOf(initialWeight)
        internal set

    var isVisible by mutableStateOf(initialShow)
        internal set

    var isDragging by mutableStateOf(false)
        private set

    private var wasAtMinBeforeDrag by mutableStateOf(false)
    private var hitMinThisDrag by mutableStateOf(false)
    private var triedBelowMinThisDrag by mutableStateOf(false)

    fun onDragStart() {
        isDragging = true
        wasAtMinBeforeDrag = (weight <= minWeight + 0.001f)
        hitMinThisDrag = false
        triedBelowMinThisDrag = false
    }

    fun onDrag(weightDelta: Float) {
        val newWeight = weight + weightDelta
        if (newWeight < minWeight) {
            if (wasAtMinBeforeDrag && !hitMinThisDrag) {
                weight = minWeight
                triedBelowMinThisDrag = true
            } else {
                weight = minWeight
                hitMinThisDrag = true
            }
        } else {
            val maxAllowed = computeMaxAllowedWeight()
            weight = newWeight.coerceAtMost(maxAllowed)
            triedBelowMinThisDrag = false
        }
    }

    fun onDragEnd() {
        isDragging = false
        if (triedBelowMinThisDrag && wasAtMinBeforeDrag) {
            isVisible = false
            triedBelowMinThisDrag = false
        }
        onDragEndCallback?.invoke(panel, weight)
    }

    fun toggle() {
        isVisible = !isVisible
        if (isVisible) {
            weight = initialWeight
            wasAtMinBeforeDrag = false
            hitMinThisDrag = false
            triedBelowMinThisDrag = false
        }
    }

    fun reset() {
        weight = initialWeight
        isVisible = initialShow
        isDragging = false
        wasAtMinBeforeDrag = false
        hitMinThisDrag = false
        triedBelowMinThisDrag = false
    }
}

/**
 * State holder for GlassLayout
 * Manages all panel sizes, visibility, and drag behavior
 */
@androidx.compose.runtime.Stable
class GlassLayoutState(
    initialLeftWeight: Float = 0.2f,
    initialRightWeight: Float = 0.25f,
    initialBottomWeight: Float = 0.3f,
    val initialShowLeft: Boolean = true,
    val initialShowRight: Boolean = true,
    val initialShowBottom: Boolean = false,
    val minLeftWeight: Float = 0.15f,
    val minRightWeight: Float = 0.2f,
    val minBottomWeight: Float = 0.1f,
    val minCenterWeight: Float = 0.4f,
    val maxLeftWeight: Float = 0.5f,
    val maxRightWeight: Float = 0.5f,
    val maxBottomWeight: Float = 0.6f,
    val onDragEnd: ((panel: WorkspacePanel, weight: Float) -> Unit)? = null,
) {
    private val leftAxis = PanelDragAxisState(
        panel = WorkspacePanel.Explorer,
        initialWeight = initialLeftWeight,
        initialShow = initialShowLeft,
        minWeight = minLeftWeight,
        computeMaxAllowedWeight = { (1.0f - minCenterWeight - rightWeight).coerceAtMost(maxLeftWeight) },
        onDragEndCallback = onDragEnd,
    )

    private val rightAxis = PanelDragAxisState(
        panel = WorkspacePanel.AiAssistant,
        initialWeight = initialRightWeight,
        initialShow = initialShowRight,
        minWeight = minRightWeight,
        computeMaxAllowedWeight = { (1.0f - minCenterWeight - leftWeight).coerceAtMost(maxRightWeight) },
        onDragEndCallback = onDragEnd,
    )

    private val bottomAxis = PanelDragAxisState(
        panel = WorkspacePanel.Terminal,
        initialWeight = initialBottomWeight,
        initialShow = initialShowBottom,
        minWeight = minBottomWeight,
        computeMaxAllowedWeight = { maxBottomWeight },
        onDragEndCallback = onDragEnd,
    )

    var focusedPanel by mutableStateOf<WorkspacePanel?>(null)

    val leftWeight: Float
        get() = leftAxis.weight
    val rightWeight: Float
        get() = rightAxis.weight
    val bottomWeight: Float
        get() = bottomAxis.weight

    val showLeft: Boolean
        get() = leftAxis.isVisible
    val showRight: Boolean
        get() = rightAxis.isVisible
    val showBottom: Boolean
        get() = bottomAxis.isVisible

    var containerSize by mutableStateOf(androidx.compose.ui.unit.IntSize.Zero)
        internal set

    val isDragging: Boolean
        get() = leftAxis.isDragging || rightAxis.isDragging || bottomAxis.isDragging

    fun onLeftDragStart() {
        leftAxis.onDragStart()
    }

    fun onLeftDrag(weightDelta: Float) {
        leftAxis.onDrag(weightDelta)
    }

    fun onLeftDragEnd() {
        leftAxis.onDragEnd()
    }

    fun toggleLeft() {
        leftAxis.toggle()
    }

    fun onRightDragStart() {
        rightAxis.onDragStart()
    }

    fun onRightDrag(weightDelta: Float) {
        rightAxis.onDrag(weightDelta)
    }

    fun onRightDragEnd() {
        rightAxis.onDragEnd()
    }

    fun toggleRight() {
        rightAxis.toggle()
    }

    fun onBottomDragStart() {
        bottomAxis.onDragStart()
    }

    fun onBottomDrag(weightDelta: Float) {
        bottomAxis.onDrag(weightDelta)
    }

    fun onBottomDragEnd() {
        bottomAxis.onDragEnd()
    }

    fun toggleBottom() {
        bottomAxis.toggle()
    }

    fun resetAll() {
        leftAxis.reset()
        rightAxis.reset()
        bottomAxis.reset()
        focusedPanel = null
    }
}

typealias LtiLayoutState = GlassLayoutState

@Composable
fun rememberLtiLayoutState(
    initialLeftWeight: Float = 0.2f,
    initialRightWeight: Float = 0.25f,
    initialBottomWeight: Float = 0.3f,
    initialShowLeft: Boolean = true,
    initialShowRight: Boolean = true,
    initialShowBottom: Boolean = false,
    minLeftWeight: Float = 0.15f,
    minRightWeight: Float = 0.2f,
    minBottomWeight: Float = 0.1f,
    minCenterWeight: Float = 0.4f,
    maxLeftWeight: Float = 0.5f,
    maxRightWeight: Float = 0.5f,
    maxBottomWeight: Float = 0.6f,
    onDragEnd: ((panel: WorkspacePanel, weight: Float) -> Unit)? = null,
): GlassLayoutState = remember {
    GlassLayoutState(
        initialLeftWeight = initialLeftWeight,
        initialRightWeight = initialRightWeight,
        initialBottomWeight = initialBottomWeight,
        initialShowLeft = initialShowLeft,
        initialShowRight = initialShowRight,
        initialShowBottom = initialShowBottom,
        minLeftWeight = minLeftWeight,
        minRightWeight = minRightWeight,
        minBottomWeight = minBottomWeight,
        minCenterWeight = minCenterWeight,
        maxLeftWeight = maxLeftWeight,
        maxRightWeight = maxRightWeight,
        maxBottomWeight = maxBottomWeight,
        onDragEnd = onDragEnd,
    )
}

@Composable
fun SamplePanel(
    title: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(color.copy(alpha = AlphaTokens.Glow))
            .padding(Spacing.Medium),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = color,
        )
    }
}
