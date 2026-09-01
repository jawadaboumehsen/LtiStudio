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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
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
import org.ide.lti.core.designsystem.theme.GlassLayoutColors
import org.ide.lti.core.designsystem.theme.PreviewSampleColors
import org.ide.lti.core.designsystem.theme.resolveAnimationSpec
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import java.awt.Cursor
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.BackdropEffectScope
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.highlight.Highlight
import com.kashif_e.backdrop.effects.vibrancy
import com.kashif_e.backdrop.shadow.Shadow
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BackdropBlur
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.CornerRadius as ThemeCornerRadius
import org.ide.lti.core.designsystem.theme.Elevation
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.LensRefraction
import org.ide.lti.core.designsystem.theme.MotionDuration
import org.ide.lti.core.designsystem.theme.MotionEasing
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.jetbrains.compose.ui.tooling.preview.Preview

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
    val panelShape = ContinuousRoundedRectangle(appearance.panelCornerRadius)

    GlassSurface(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.ExtraSmall)
            .onSizeChanged { state.containerSize = it }
            .semantics {
                contentDescription = "LtiRomGui IDE Layout with resizable panels"
            },
        shape = panelShape,
        backdrop = actualBackdrop,
        surfaceColor = glassColors.chromeSurface,
        tint = Color.Transparent,
        borderColor = glassColors.glassBorder,
        borderWidth = StrokeWidth.Hairline,
        effects = LtiLayoutDefaults.ChromeEffects,
        highlight = { Highlight.Default },
        shadow = {
            Shadow(
                radius = LtiLayoutDefaults.PanelElevation,
                color = LtiLayoutDefaults.PanelShadowColor
            )
        }
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
                    .background(glassColors.glassBorder.copy(alpha = AlphaTokens.Faint))
            )

            // Middle Row: Left Rail (Chrome) + Content GlassSurface + Right Rail (Chrome)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                leftRail()

                // Content area: each panel is its OWN GlassSurface so the chrome background
                // shows through the gaps between them as a real, visible separation.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(Spacing.Small),
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        val animatedLeftWeight by animateFloatAsState(
                            targetValue = state.leftWeight,
                            animationSpec = if (state.isDragging) {
                                snap()
                            } else {
                                spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            },
                            label = "leftWeight"
                        )

                        val animatedRightWeight by animateFloatAsState(
                            targetValue = state.rightWeight,
                            animationSpec = if (state.isDragging) {
                                snap()
                            } else {
                                spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            },
                            label = "rightWeight"
                        )

                        val animatedBottomWeight by animateFloatAsState(
                            targetValue = state.bottomWeight,
                            animationSpec = if (state.isDragging) {
                                snap()
                            } else {
                                spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            },
                            label = "bottomWeight"
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
                            content = leftPanel
                        )

                        GlassCenterColumn(
                            state = state,
                            density = density,
                            animatedBottomWeight = animatedBottomWeight,
                            appearance = appearance,
                            backdrop = actualBackdrop,
                            mainContent = mainContent,
                            bottomPanel = bottomPanel
                        )

                        GlassRightPanel(
                            state = state,
                            width = rightPanelWidth,
                            appearance = appearance,
                            backdrop = actualBackdrop,
                            content = rightPanel
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
                    .background(glassColors.glassBorder.copy(alpha = AlphaTokens.Faint))
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
 * A single content panel rendered as its own [GlassSurface].
 *
 * Each of the four workspace panels (Explorer, editor, terminal/console, AI Assistant) paints its
 * own surface rather than sharing one continuous sheet. This is what makes the space between them
 * read as a real, visible gap: the darker chrome frame behind shows through the gaps. Padding alone
 * inside a single shared surface is invisible, since both sides of the gap are the same color.
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
        shape = ContinuousRoundedRectangle(ThemeCornerRadius.Panel),
        backdrop = backdrop,
        surfaceColor = glassColors.canvasSurface,
        tint = Color.Transparent,
        borderColor = if (isFocused) glassColors.glassBorderHighlight else Color.Unspecified,
        borderWidth = StrokeWidth.Hairline,
        // The focus border above and the passive Highlight.Plain edge shimmer both draw on the
        // same panel edge - stacking them reads as two parallel lines. Drop the highlight while
        // focused so the border stroke is the sole edge treatment, matching the button focus ring.
        highlight = { if (isFocused) null else Highlight.Plain },
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
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = state.showLeft,
        enter = expandHorizontally(
            expandFrom = Alignment.Start,
            animationSpec = LtiLayoutAnimations.PanelExpandSpring
        ) + fadeIn(animationSpec = LtiLayoutAnimations.PanelFade),
        exit = shrinkHorizontally(
            shrinkTowards = Alignment.Start,
            animationSpec = LtiLayoutAnimations.PanelExpandSpring
        ) + fadeOut(animationSpec = LtiLayoutAnimations.PanelFade)
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
                onDragEnd = { state.onLeftDragEnd() }
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
    bottomPanel: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
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
                animationSpec = LtiLayoutAnimations.PanelExpandSpring
            ) + fadeIn(animationSpec = LtiLayoutAnimations.PanelFade),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = LtiLayoutAnimations.PanelExpandSpring
            ) + fadeOut(animationSpec = LtiLayoutAnimations.PanelFade)
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
                    onDragEnd = { state.onBottomDragEnd() }
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
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = state.showRight,
        enter = expandHorizontally(
            expandFrom = Alignment.End,
            animationSpec = LtiLayoutAnimations.PanelExpandSpring
        ) + fadeIn(animationSpec = LtiLayoutAnimations.PanelFade),
        exit = shrinkHorizontally(
            shrinkTowards = Alignment.End,
            animationSpec = LtiLayoutAnimations.PanelExpandSpring
        ) + fadeOut(animationSpec = LtiLayoutAnimations.PanelFade)
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
                onDragEnd = { state.onRightDragEnd() }
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
    state: LtiLayoutState
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopEnd
    ) {
        Row(
            modifier = Modifier.padding(
                top = LtiLayoutDefaults.ToggleButtonTopPadding,
                end = LtiLayoutDefaults.ToggleButtonEndPadding
            ),
            horizontalArrangement = Arrangement.spacedBy(LtiLayoutDefaults.ToggleButtonSpacing)
        ) {
            AnimatedToggleButton(
                onClick = { state.toggleLeft() },
                selected = state.showLeft
            ) {
                Icon(
                    imageVector = if (state.showLeft) Icons.Default.Close else Icons.Default.Menu,
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
                selected = state.showBottom
            ) {
                Icon(
                    imageVector = if (state.showBottom) {
                        Icons.Default.KeyboardArrowDown
                    } else {
                        Icons.Default.KeyboardArrowUp
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
                selected = state.showRight
            ) {
                Icon(
                    imageVector = if (state.showRight) Icons.Default.Close else Icons.Default.Settings,
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
    val dividerColor: Color = Color.Unspecified,
    val dividerActiveColor: Color = Color.Unspecified
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
        lens(LensRefraction.TextField.height.toPx(), LensRefraction.TextField.amount.toPx())
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
        stiffness = Spring.StiffnessLow  // Changed from Medium to Low for smoother motion
    )

    /**
     * Coordinated fade animation with custom easing curve.
     * Uses Material Design's standard easing (0.4, 0.0, 0.2, 1.0) for smooth acceleration.
     */
    val PanelFade = tween<Float>(
        durationMillis = MotionDuration.Standard,  // Reduced from 400ms for snappier feel
        easing = MotionEasing.Standard  // Custom professional easing
    )

    /**
     * Fast animation for divider size changes.
     * No bounce for precise, professional feel.
     */
    val DividerContentSize = spring<androidx.compose.ui.unit.IntSize>(
        dampingRatio = Spring.DampingRatioNoBouncy,  // Changed from MediumBouncy to NoBouncy
        stiffness = Spring.StiffnessMedium
    )

    /**
     * Smooth animation for panel resize operations.
     * Quick but smooth for responsive feel during drag interactions.
     */
    val PanelResize = tween<androidx.compose.ui.unit.IntSize>(
        durationMillis = MotionDuration.Moderate,
        easing = FastOutSlowInEasing
    )

    /**
     * Subtle scale animation for micro-interactions.
     * Medium bounce for playful but professional feedback.
     */
    val ButtonHoverScale = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
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
    content: @Composable () -> Unit
) {
    var isHovered by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.08f else 1f,
        animationSpec = LtiLayoutAnimations.ButtonHoverScale
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
    ) {
        GlassIconButton(
            onClick = onClick,
            selected = selected,
            content = content
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
    Horizontal
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
    onDragEnd: () -> Unit
) {
    var isHovered by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }

    // Instant alpha during drag for 1:1 mouse tracking, smooth fade on hover
    val alpha by animateFloatAsState(
        targetValue = if (isHovered || isDragging) 1f else 0f,
        animationSpec = if (isDragging) {
            snap()  // Instant update during drag
        } else {
            resolveAnimationSpec(GlassTheme.reducedMotion)  // Smooth fade on hover, instant on reduced motion
        }
    )

    // Get backdrop from theme if not explicitly provided
    val actualBackdrop = backdrop ?: GlassTheme.backdrop ?: rememberLayerBackdrop()

    // Capture colors from theme (must be done in composable context)
    val colors = GlassTheme.colors
    val effectsEnabled = GlassTheme.effectsEnabled
    val normalColor = if (appearance.dividerColor.isSpecified) {
        appearance.dividerColor
    } else {
        colors.glassBorder.copy(alpha = AlphaTokens.Muted)
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
                    }
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
        contentAlignment = Alignment.Center
    ) {
        // No painted idle line: each content panel now draws its own GlassSurface, so the gap
        // between panels already reads as a real separation (the darker chrome frame shows
        // through it). Painting a line here would sit on top of that gap and muddy it. The
        // hover/drag affordance below still appears on interaction.

        // Divider line active state - simple during drag, glass effects only on hover for performance
        Box(
            modifier = lineModifier
                .alpha(alpha)
                .clip(ContinuousCapsule)
                .then(
                    getDividerBackgroundModifier(
                        isDragging = isDragging,
                        isHovered = isHovered,
                        activeColor = activeColor,
                        normalColor = normalColor,
                        actualBackdrop = actualBackdrop,
                        orientation = orientation,
                        effectsEnabled = effectsEnabled,
                    )
                )
        )
    }
}

private fun getDividerModifiers(
    orientation: DividerOrientation,
    appearance: GlassLayoutAppearance
): Triple<PointerIcon, Modifier, Modifier> = when (orientation) {
    DividerOrientation.Vertical -> Triple(
        PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)),
        Modifier.width(appearance.dividerWidth).fillMaxHeight(),
        Modifier.width(LtiLayoutDefaults.DividerLineWidthActive).fillMaxHeight()
    )
    DividerOrientation.Horizontal -> Triple(
        PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR)),
        Modifier.height(appearance.dividerWidth).fillMaxWidth(),
        Modifier.height(LtiLayoutDefaults.DividerLineWidthActive).fillMaxWidth()
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
                        height = size.height + Spacing.Small.toPx()
                    ),
                    topLeft = Offset(
                        x = -Spacing.ExtraSmall.toPx(),
                        y = -Spacing.ExtraSmall.toPx()
                    )
                )
            }
            .drawBackdrop(
                backdrop = actualBackdrop,
                shape = { ContinuousCapsule },
                effects = {
                    blur(BackdropBlur.Minimap.toPx())
                    lens(LensRefraction.Minimap.height.toPx(), LensRefraction.Minimap.amount.toPx())
                },
                onDrawSurface = {
                    val gradient = when (orientation) {
                        DividerOrientation.Vertical -> Brush.verticalGradient(
                            colors = listOf(
                                activeColor.copy(alpha = AlphaTokens.Muted),
                                activeColor.copy(alpha = AlphaTokens.Prominent),
                                activeColor.copy(alpha = AlphaTokens.Muted)
                            )
                        )
                        DividerOrientation.Horizontal -> Brush.horizontalGradient(
                            colors = listOf(
                                activeColor.copy(alpha = AlphaTokens.Muted),
                                activeColor.copy(alpha = AlphaTokens.Prominent),
                                activeColor.copy(alpha = AlphaTokens.Muted)
                            )
                        )
                    }
                    drawRect(gradient)
                }
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
            rightPanel = { SamplePanel("PROPERTIES", PreviewSampleColors.Green) }
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
    val maxBottomWeight: Float = 0.6f
) {
    private val _initialLeftWeight = initialLeftWeight
    private val _initialRightWeight = initialRightWeight
    private val _initialBottomWeight = initialBottomWeight

    var focusedPanel by mutableStateOf<WorkspacePanel?>(null)

    var leftWeight by mutableStateOf(initialLeftWeight)
        private set
    var rightWeight by mutableStateOf(initialRightWeight)
        private set
    var bottomWeight by mutableStateOf(initialBottomWeight)
        private set

    var showLeft by mutableStateOf(initialShowLeft)
        private set
    var showRight by mutableStateOf(initialShowRight)
        private set
    var showBottom by mutableStateOf(initialShowBottom)
        private set

    private var leftWasAtMinBeforeDrag by mutableStateOf(false)
    private var rightWasAtMinBeforeDrag by mutableStateOf(false)
    private var bottomWasAtMinBeforeDrag by mutableStateOf(false)

    private var leftHitMinThisDrag by mutableStateOf(false)
    private var rightHitMinThisDrag by mutableStateOf(false)
    private var bottomHitMinThisDrag by mutableStateOf(false)

    private var leftTriedBelowMinThisDrag by mutableStateOf(false)
    private var rightTriedBelowMinThisDrag by mutableStateOf(false)
    private var bottomTriedBelowMinThisDrag by mutableStateOf(false)

    var containerSize by mutableStateOf(androidx.compose.ui.unit.IntSize.Zero)
        internal set

    private var isLeftDragging by mutableStateOf(false)
    private var isRightDragging by mutableStateOf(false)
    private var isBottomDragging by mutableStateOf(false)

    val isDragging: Boolean
        get() = isLeftDragging || isRightDragging || isBottomDragging

    fun onLeftDragStart() {
        isLeftDragging = true
        leftWasAtMinBeforeDrag = (leftWeight <= minLeftWeight + 0.001f)
        leftHitMinThisDrag = false
        leftTriedBelowMinThisDrag = false
    }

    fun onLeftDrag(weightDelta: Float) {
        val newWeight = leftWeight + weightDelta
        if (newWeight < minLeftWeight) {
            if (leftWasAtMinBeforeDrag && !leftHitMinThisDrag) {
                leftWeight = minLeftWeight
                leftTriedBelowMinThisDrag = true
            } else {
                leftWeight = minLeftWeight
                leftHitMinThisDrag = true
            }
        } else {
            val maxAllowedLeft = (1.0f - minCenterWeight - rightWeight).coerceAtMost(maxLeftWeight)
            leftWeight = newWeight.coerceAtMost(maxAllowedLeft)
            leftTriedBelowMinThisDrag = false
        }
    }

    fun onLeftDragEnd() {
        isLeftDragging = false
        if (leftTriedBelowMinThisDrag && leftWasAtMinBeforeDrag) {
            showLeft = false
            leftTriedBelowMinThisDrag = false
        }
    }

    fun toggleLeft() {
        showLeft = !showLeft
        if (showLeft) {
            leftWeight = _initialLeftWeight
            leftWasAtMinBeforeDrag = false
            leftHitMinThisDrag = false
            leftTriedBelowMinThisDrag = false
        }
    }

    fun onRightDragStart() {
        isRightDragging = true
        rightWasAtMinBeforeDrag = (rightWeight <= minRightWeight + 0.001f)
        rightHitMinThisDrag = false
        rightTriedBelowMinThisDrag = false
    }

    fun onRightDrag(weightDelta: Float) {
        val newWeight = rightWeight + weightDelta
        if (newWeight < minRightWeight) {
            if (rightWasAtMinBeforeDrag && !rightHitMinThisDrag) {
                rightWeight = minRightWeight
                rightTriedBelowMinThisDrag = true
            } else {
                rightWeight = minRightWeight
                rightHitMinThisDrag = true
            }
        } else {
            val maxAllowedRight = (1.0f - minCenterWeight - leftWeight).coerceAtMost(maxRightWeight)
            rightWeight = newWeight.coerceAtMost(maxAllowedRight)
            rightTriedBelowMinThisDrag = false
        }
    }

    fun onRightDragEnd() {
        isRightDragging = false
        if (rightTriedBelowMinThisDrag && rightWasAtMinBeforeDrag) {
            showRight = false
            rightTriedBelowMinThisDrag = false
        }
    }

    fun toggleRight() {
        showRight = !showRight
        if (showRight) {
            rightWeight = _initialRightWeight
            rightWasAtMinBeforeDrag = false
            rightHitMinThisDrag = false
            rightTriedBelowMinThisDrag = false
        }
    }

    fun onBottomDragStart() {
        isBottomDragging = true
        bottomWasAtMinBeforeDrag = (bottomWeight <= minBottomWeight + 0.001f)
        bottomHitMinThisDrag = false
        bottomTriedBelowMinThisDrag = false
    }

    fun onBottomDrag(weightDelta: Float) {
        val newWeight = bottomWeight + weightDelta
        if (newWeight < minBottomWeight) {
            if (bottomWasAtMinBeforeDrag && !bottomHitMinThisDrag) {
                bottomWeight = minBottomWeight
                bottomTriedBelowMinThisDrag = true
            } else {
                bottomWeight = minBottomWeight
                bottomHitMinThisDrag = true
            }
        } else {
            bottomWeight = newWeight.coerceAtMost(maxBottomWeight)
            bottomTriedBelowMinThisDrag = false
        }
    }

    fun onBottomDragEnd() {
        isBottomDragging = false
        if (bottomTriedBelowMinThisDrag && bottomWasAtMinBeforeDrag) {
            showBottom = false
            bottomTriedBelowMinThisDrag = false
        }
    }

    fun toggleBottom() {
        showBottom = !showBottom
        if (showBottom) {
            bottomWeight = _initialBottomWeight
            bottomWasAtMinBeforeDrag = false
            bottomHitMinThisDrag = false
            bottomTriedBelowMinThisDrag = false
        }
    }

    fun resetAll() {
        leftWeight = _initialLeftWeight
        rightWeight = _initialRightWeight
        bottomWeight = _initialBottomWeight
        showLeft = initialShowLeft
        showRight = initialShowRight
        showBottom = initialShowBottom
        focusedPanel = null
        leftWasAtMinBeforeDrag = false
        rightWasAtMinBeforeDrag = false
        bottomWasAtMinBeforeDrag = false
        leftHitMinThisDrag = false
        rightHitMinThisDrag = false
        bottomHitMinThisDrag = false
        leftTriedBelowMinThisDrag = false
        rightTriedBelowMinThisDrag = false
        bottomTriedBelowMinThisDrag = false
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
    maxBottomWeight: Float = 0.6f
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
        maxBottomWeight = maxBottomWeight
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
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = color
        )
    }
}
