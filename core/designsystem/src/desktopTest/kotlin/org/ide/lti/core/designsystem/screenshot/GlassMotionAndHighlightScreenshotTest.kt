/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import org.ide.lti.core.designsystem.component.GlassBottomSheet
import org.ide.lti.core.designsystem.component.GlassBottomSheetValue
import org.ide.lti.core.designsystem.component.GlassIcon
import org.ide.lti.core.designsystem.component.GlassNavigationBar
import org.ide.lti.core.designsystem.component.GlassNavigationBarItem
import org.ide.lti.core.designsystem.component.GlassNavigationRail
import org.ide.lti.core.designsystem.component.GlassNavigationRailItem
import org.ide.lti.core.designsystem.component.GlassSlider
import org.ide.lti.core.designsystem.component.GlassToggle
import org.ide.lti.core.designsystem.component.rememberGlassBottomSheetState
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.junit.Test

/**
 * Visual screenshot regression tests for reduced motion support, modal highlight parity,
 * docked navigation chrome highlight, and interactive icon hover highlights.
 */
class GlassMotionAndHighlightScreenshotTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testGlassBottomSheetModalParity() = runDesktopComposeUiTest(width = 460, height = 260) {
        setContent {
            val backdrop = rememberLayerBackdrop()
            val state = rememberGlassBottomSheetState(initialValue = GlassBottomSheetValue.Expanded)
            LtiTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(GlassTheme.colors.glassShadow.copy(alpha = AlphaTokens.Scrim)),
                ) {
                    GlassBottomSheet(
                        state = state,
                        onDismissRequest = {},
                        backdrop = backdrop,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.Large, vertical = Spacing.Medium),
                            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                        ) {
                            Text(
                                text = "Glass Bottom Sheet Modal",
                                style = MaterialTheme.typography.titleMedium,
                                color = GlassTheme.colors.onGlass,
                            )
                            Text(
                                text = "Visual parity with GlassDialog: Highlight.Default edge lighting " +
                                    "& spring offset physics.",
                                style = MaterialTheme.typography.bodySmall,
                                color = GlassTheme.colors.onGlassSecondary,
                            )
                        }
                    }
                }
            }
        }

        val image = onRoot().captureToImage()
        ScreenshotTestHelper.assertMatchesBaseline("glass_bottom_sheet_modal", image)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testGlassDockedNavigationChromeHighlight() = runDesktopComposeUiTest(width = 540, height = 260) {
        setContent {
            val backdrop = rememberLayerBackdrop()
            LtiTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(GlassTheme.colors.glassSurface)
                        .padding(Spacing.Small),
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        GlassNavigationRail {
                            GlassNavigationRailItem(
                                selected = true,
                                onClick = {},
                                icon = {
                                    Icon(
                                        painter = AppIcons.FolderPainterResource(),
                                        contentDescription = "Explorer",
                                        modifier = Modifier.size(IconSize.Large),
                                    )
                                },
                                label = { Text("Files") },
                            )
                            GlassNavigationRailItem(
                                selected = false,
                                onClick = {},
                                icon = {
                                    Icon(
                                        painter = AppIcons.SearchPainterResource(),
                                        contentDescription = "Search",
                                        modifier = Modifier.size(IconSize.Large),
                                    )
                                },
                                label = { Text("Search") },
                            )
                        }

                        Spacer(Modifier.width(Spacing.Small))

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            GlassNavigationBar {
                                GlassNavigationBarItem(
                                    selected = true,
                                    onClick = {},
                                    icon = {
                                        Icon(
                                            painter = AppIcons.FolderPainterResource(),
                                            contentDescription = "Home",
                                            modifier = Modifier.size(IconSize.Large),
                                        )
                                    },
                                    label = { Text("Home") },
                                )
                                GlassNavigationBarItem(
                                    selected = false,
                                    onClick = {},
                                    icon = {
                                        Icon(
                                            painter = AppIcons.SettingsPainterResource(),
                                            contentDescription = "Settings",
                                            modifier = Modifier.size(IconSize.Large),
                                        )
                                    },
                                    label = { Text("Settings") },
                                )
                            }
                        }
                    }
                }
            }
        }

        val image = onRoot().captureToImage()
        ScreenshotTestHelper.assertMatchesBaseline("glass_docked_navigation_highlight", image)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testGlassIconHoverHighlight() = runDesktopComposeUiTest(width = 380, height = 160) {
        setContent {
            val backdrop = rememberLayerBackdrop()
            LtiTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(GlassTheme.colors.glassSurface)
                        .padding(Spacing.Medium),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraLarge),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            GlassIcon(
                                onClick = {},
                                backdrop = backdrop,
                                modifier = Modifier
                                    .testTag("idle_glass_icon")
                                    .size(ComponentSize.IconButtonSize),
                            ) {
                                Icon(
                                    painter = AppIcons.SparklesPainterResource(),
                                    contentDescription = "Idle Icon",
                                    modifier = Modifier.size(IconSize.Medium),
                                )
                            }
                            Spacer(Modifier.height(Spacing.ExtraSmall))
                            Text(
                                text = "Idle (Plain)",
                                style = MaterialTheme.typography.labelSmall,
                                color = GlassTheme.colors.onGlassMuted,
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            GlassIcon(
                                onClick = {},
                                backdrop = backdrop,
                                modifier = Modifier
                                    .testTag("hovered_glass_icon")
                                    .size(ComponentSize.IconButtonSize),
                            ) {
                                Icon(
                                    painter = AppIcons.SettingsPainterResource(),
                                    contentDescription = "Hovered Icon",
                                    modifier = Modifier.size(IconSize.Medium),
                                )
                            }
                            Spacer(Modifier.height(Spacing.ExtraSmall))
                            Text(
                                text = "Hovered (Ambient)",
                                style = MaterialTheme.typography.labelSmall,
                                color = GlassTheme.colors.onGlassMuted,
                            )
                        }
                    }
                }
            }
        }

        onNodeWithTag("hovered_glass_icon").performMouseInput {
            moveTo(center)
        }
        waitForIdle()

        val image = onRoot().captureToImage()
        ScreenshotTestHelper.assertMatchesBaseline("glass_icon_hover_highlight", image)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testDampedDragReducedMotionStateComparison() = runDesktopComposeUiTest(width = 520, height = 260) {
        setContent {
            val backdrop = rememberLayerBackdrop()
            var normalToggle by remember { mutableStateOf(true) }
            var reducedToggle by remember { mutableStateOf(true) }
            var normalSlider by remember { mutableFloatStateOf(0.7f) }
            var reducedSlider by remember { mutableFloatStateOf(0.7f) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(GlassTheme.colors.glassSurface)
                    .padding(Spacing.Medium),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Large),
                ) {
                    // Left Column: Normal Motion (spring physics enabled)
                    Box(modifier = Modifier.weight(1f)) {
                        LtiTheme(darkTheme = true, reducedMotion = false) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
                            ) {
                                Text(
                                    text = "Normal Motion (Spring)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = GlassTheme.colors.onGlass,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Toggle",
                                        color = GlassTheme.colors.onGlassSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    GlassToggle(
                                        selected = { normalToggle },
                                        onSelect = { normalToggle = it },
                                        backdrop = backdrop,
                                    )
                                }
                                GlassSlider(
                                    value = { normalSlider },
                                    onValueChange = { normalSlider = it },
                                    valueRange = 0f..1f,
                                    visibilityThreshold = 0.01f,
                                    backdrop = backdrop,
                                )
                            }
                        }
                    }

                    // Right Column: Reduced Motion (decorative squish/springs snapped)
                    Box(modifier = Modifier.weight(1f)) {
                        LtiTheme(darkTheme = true, reducedMotion = true) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
                            ) {
                                Text(
                                    text = "Reduced Motion (Snap)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = GlassTheme.colors.onGlass,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Toggle",
                                        color = GlassTheme.colors.onGlassSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    GlassToggle(
                                        selected = { reducedToggle },
                                        onSelect = { reducedToggle = it },
                                        backdrop = backdrop,
                                    )
                                }
                                GlassSlider(
                                    value = { reducedSlider },
                                    onValueChange = { reducedSlider = it },
                                    valueRange = 0f..1f,
                                    visibilityThreshold = 0.01f,
                                    backdrop = backdrop,
                                )
                            }
                        }
                    }
                }
            }
        }

        val image = onRoot().captureToImage()
        ScreenshotTestHelper.assertMatchesBaseline("glass_damped_drag_reduced_motion_comparison", image)
    }
}
