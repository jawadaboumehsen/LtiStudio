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
import org.ide.lti.core.designsystem.component.display.GlassIcon
import org.ide.lti.core.designsystem.component.feedback.GlassBottomSheet
import org.ide.lti.core.designsystem.component.feedback.GlassBottomSheetValue
import org.ide.lti.core.designsystem.component.feedback.rememberGlassBottomSheetState
import org.ide.lti.core.designsystem.component.inputs.GlassSlider
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
import org.ide.lti.core.designsystem.component.navigation.GlassNavigationBar
import org.ide.lti.core.designsystem.component.navigation.GlassNavigationBarItem
import org.ide.lti.core.designsystem.component.navigation.GlassNavigationRail
import org.ide.lti.core.designsystem.component.navigation.GlassNavigationRailItem
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.testing.screenshot.GoldenImageAssert
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
            val state = rememberGlassBottomSheetState(initialValue = GlassBottomSheetValue.Expanded)
            LtiTheme(appTheme = AppTheme.Dark) {
                val colors = MaterialTheme.colorScheme
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.scrim.copy(alpha = AlphaTokens.Scrim)),
                ) {
                    GlassBottomSheet(
                        state = state,
                        onDismissRequest = {},
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
                                color = colors.onSurface,
                            )
                            Text(
                                text = "Visual parity with GlassDialog: Highlight edge lighting & offset physics.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        val image = onRoot().captureToImage()
        GoldenImageAssert.assertMatchesBaseline("glass_bottom_sheet_modal", image)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testGlassDockedNavigationChromeHighlight() = runDesktopComposeUiTest(width = 540, height = 260) {
        setContent {
            LtiTheme(appTheme = AppTheme.Dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
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
        GoldenImageAssert.assertMatchesBaseline("glass_docked_navigation_highlight", image)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testGlassIconHoverHighlight() = runDesktopComposeUiTest(width = 380, height = 160) {
        setContent {
            LtiTheme(appTheme = AppTheme.Dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            GlassIcon(
                                onClick = {},
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        GoldenImageAssert.assertMatchesBaseline("glass_icon_hover_highlight", image)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testDampedDragReducedMotionStateComparison() = runDesktopComposeUiTest(width = 520, height = 260) {
        setContent {
            var normalToggle by remember { mutableStateOf(true) }
            var reducedToggle by remember { mutableStateOf(true) }
            var normalSlider by remember { mutableFloatStateOf(0.7f) }
            var reducedSlider by remember { mutableFloatStateOf(0.7f) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(Spacing.Medium),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Large),
                ) {
                    // Left Column: Normal Motion
                    Box(modifier = Modifier.weight(1f)) {
                        LtiTheme(appTheme = AppTheme.Dark) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
                            ) {
                                Text(
                                    text = "Normal Motion",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Toggle",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    GlassToggle(
                                        checked = normalToggle,
                                        onCheckedChange = { normalToggle = it },
                                    )
                                }
                                GlassSlider(
                                    value = { normalSlider },
                                    onValueChange = { normalSlider = it },
                                    valueRange = 0f..1f,
                                )
                            }
                        }
                    }

                    // Right Column: Reduced Motion
                    Box(modifier = Modifier.weight(1f)) {
                        LtiTheme(appTheme = AppTheme.Dark) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
                            ) {
                                Text(
                                    text = "Alternative State",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Toggle",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    GlassToggle(
                                        checked = reducedToggle,
                                        onCheckedChange = { reducedToggle = it },
                                    )
                                }
                                GlassSlider(
                                    value = { reducedSlider },
                                    onValueChange = { reducedSlider = it },
                                    valueRange = 0f..1f,
                                )
                            }
                        }
                    }
                }
            }
        }

        val image = onRoot().captureToImage()
        GoldenImageAssert.assertMatchesBaseline("glass_damped_drag_reduced_motion_comparison", image)
    }
}
