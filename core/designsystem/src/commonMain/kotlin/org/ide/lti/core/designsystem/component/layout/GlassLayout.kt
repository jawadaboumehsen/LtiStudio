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

import org.ide.lti.core.designsystem.component.primitives.*
import org.ide.lti.core.designsystem.component.actions.*
import org.ide.lti.core.designsystem.component.inputs.*
import org.ide.lti.core.designsystem.component.display.*
import org.ide.lti.core.designsystem.component.navigation.*
import org.ide.lti.core.designsystem.component.layout.*
import org.ide.lti.core.designsystem.component.feedback.*
import org.ide.lti.core.designsystem.component.devtools.*

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.kashif_e.backdrop.Backdrop
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.PreviewSampleColors
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
 * │ topBar() - GlassTopBar (40dp fixed)                   │
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
 * @param state Layout state controlling panel visibility and sizing. Use [rememberLtiLayoutState].
 * @param modifier Modifier to be applied to the root layout container.
 * @param leftRail Optional leading chrome rail composable.
 * @param leftPanel Content for the left panel (typically file explorer).
 * @param mainContent Content for the main central area (typically code editor).
 * @param bottomPanel Content for the bottom panel (typically terminal/console).
 * @param rightPanel Content for the right panel (typically properties/AI assistant).
 * @param rightRail Optional trailing chrome rail composable.
 * @param topBar Top application bar composable (default: [GlassTopBar]).
 * @param statusBar Bottom status bar composable (default: [GlassStatusBar]).
 * @param showToggleButtons Whether to show floating panel toggle buttons (default: false).
 * @param appearance Visual appearance configuration (default: [GlassLayoutAppearance.Default]).
 * @param backdrop Shared backdrop for glass effects (default: from [GlassTheme]).
 */
import androidx.compose.runtime.Immutable

/**
 * Cohesive configuration of composable slots for [GlassLayout].
 * Adheres to the Interface Segregation Principle (ISP).
 */
@Immutable
data class GlassLayoutSlots(
    val mainContent: @Composable () -> Unit,
    val leftPanel: (@Composable () -> Unit)? = null,
    val rightPanel: (@Composable () -> Unit)? = null,
    val bottomPanel: (@Composable () -> Unit)? = null,
    val leftRail: (@Composable () -> Unit)? = null,
    val rightRail: (@Composable () -> Unit)? = null,
    val topBar: (@Composable () -> Unit)? = null,
    val statusBar: (@Composable () -> Unit)? = null,
)

/**
 * Professional resizable panel layout for LtiRomGui IDE with Liquid Glass theme.
 * Accepts cohesive [GlassLayoutSlots].
 */
@Composable
fun GlassLayout(
    state: LtiLayoutState,
    slots: GlassLayoutSlots,
    modifier: Modifier = Modifier,
    showToggleButtons: Boolean = false,
    appearance: GlassLayoutAppearance = GlassLayoutAppearance.Default,
    backdrop: Backdrop? = null,
) = GlassLayout(
    state = state,
    modifier = modifier,
    leftRail = { slots.leftRail?.invoke() },
    leftPanel = { slots.leftPanel?.invoke() },
    mainContent = slots.mainContent,
    bottomPanel = { slots.bottomPanel?.invoke() },
    rightPanel = { slots.rightPanel?.invoke() },
    rightRail = { slots.rightRail?.invoke() },
    topBar = slots.topBar ?: {
        GlassTopBar(
            panelToggles = TopBarPanelToggles(
                isLeftVisible = state.showLeft,
                isRightVisible = state.showRight,
                isBottomVisible = state.showBottom,
                onToggleLeft = { state.toggleLeft() },
                onToggleRight = { state.toggleRight() },
                onToggleBottom = { state.toggleBottom() },
            ),
            drawOwnChrome = false,
        )
    },
    statusBar = slots.statusBar ?: { GlassStatusBar(drawOwnChrome = false) },
    showToggleButtons = showToggleButtons,
    appearance = appearance,
    backdrop = backdrop,
)

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
            panelToggles = TopBarPanelToggles(
                isLeftVisible = state.showLeft,
                isRightVisible = state.showRight,
                isBottomVisible = state.showBottom,
                onToggleLeft = { state.toggleLeft() },
                onToggleRight = { state.toggleRight() },
                onToggleBottom = { state.toggleBottom() },
            ),
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
            val horizontalDividerColor = if (GlassTheme.panel.hasFloatingGaps) {
                glassColors.glassBorder
            } else {
                glassColors.glassBorder.copy(alpha = AlphaTokens.Faint)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(StrokeWidth.Hairline)
                    .background(horizontalDividerColor),
            )

            // Middle Row: Left Rail (Chrome) + Content GlassSurface + Right Rail (Chrome)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                leftRail()

                // Content area: panels dock with theme-specific geometry and gutters
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
                    .background(horizontalDividerColor),
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

// ==================== PREVIEW ====================

/**
 * Preview of [GlassLayout] with sample panels.
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
