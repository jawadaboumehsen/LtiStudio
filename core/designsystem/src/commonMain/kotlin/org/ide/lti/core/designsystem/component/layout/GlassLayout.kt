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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.kashif_e.backdrop.OpticalSizeValue
import org.ide.lti.core.designsystem.component.actions.*
import org.ide.lti.core.designsystem.component.devtools.*
import org.ide.lti.core.designsystem.component.display.*
import org.ide.lti.core.designsystem.component.feedback.*
import org.ide.lti.core.designsystem.component.inputs.*
import org.ide.lti.core.designsystem.component.navigation.*
import org.ide.lti.core.designsystem.component.primitives.*
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BackdropBlur
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.LensRefraction
import org.ide.lti.core.designsystem.theme.PreviewSampleColors
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.glass.GlassSceneState
import org.ide.lti.core.designsystem.theme.glass.GlassStyles
import org.ide.lti.core.designsystem.theme.glass.Rim
import org.jetbrains.compose.ui.tooling.preview.Preview

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
    backdrop: GlassSceneState? = null,
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
    backdrop: GlassSceneState? = null,
) {
    val density = LocalDensity.current
    val actualBackdrop = backdrop ?: GlassTheme.scene
    val glassColors = GlassTheme.colors

    // The outermost application chrome frame - its own real, documented tier (BackdropBlur.Immersive),
    // distinct from GlassStyles.bar's tuning for a bar drawing its own standalone chrome (e.g. a
    // catalog preview). No rim: this frame has never drawn one (highlight = null previously).
    val frameStyle = GlassStyles.bar.then {
        copy(
            blur = OpticalSizeValue.Fixed(BackdropBlur.Immersive),
            lens = OpticalSizeValue.Fixed(LensRefraction.Surface),
            rim = Rim.None,
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { state.containerSize = it }
            .semantics {
                contentDescription = "LtiRomGui IDE Layout with resizable panels"
            }
            .glass(frameStyle),
    ) {
        CompositionLocalProvider(LocalContentColor provides glassColors.onGlass) {
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
private fun GlassLayoutPreview() {
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
