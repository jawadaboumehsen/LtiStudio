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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.ide.lti.core.designsystem.component.GlassButton
import org.ide.lti.core.designsystem.component.GlassButtonVariant
import org.ide.lti.core.designsystem.component.GlassCard
import org.ide.lti.core.designsystem.component.GlassNavigationBar
import org.ide.lti.core.designsystem.component.GlassNavigationBarItem
import org.ide.lti.core.designsystem.component.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.GlassTextButton
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.junit.Test

/**
 * Visual screenshot regression tests verifying that components render solid,
 * opaque fallbacks when Fluid Glass Effects are disabled (effectsEnabled = false).
 */
class GlassEffectsDisabledScreenshotTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testEffectsDisabledButtons() = runDesktopComposeUiTest(width = 480, height = 180) {
        setContent {
            LtiTheme(darkTheme = true, effectsEnabled = false) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(GlassTheme.colors.canvasSurface)
                        .padding(Spacing.Medium),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium)) {
                            GlassPrimaryButton(onClick = {}) {
                                Text("Primary Action")
                            }
                            GlassButton(onClick = {}, variant = GlassButtonVariant.Secondary) {
                                Text("Secondary")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium)) {
                            GlassButton(onClick = {}, variant = GlassButtonVariant.Standard) {
                                Text("Standard Glass")
                            }
                            GlassTextButton(onClick = {}) {
                                Text("Text Button")
                            }
                        }
                    }
                }
            }
        }

        val image = onRoot().captureToImage()
        ScreenshotTestHelper.assertMatchesBaseline("glass_buttons_effects_disabled", image)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testEffectsDisabledCard() = runDesktopComposeUiTest(width = 400, height = 240) {
        setContent {
            LtiTheme(darkTheme = true, effectsEnabled = false) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(GlassTheme.colors.canvasSurface)
                        .padding(Spacing.Large),
                    contentAlignment = Alignment.Center
                ) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = Spacing.Medium,
                    ) {
                        Column {
                            Text(
                                text = "Design System Card",
                                style = MaterialTheme.typography.titleMedium,
                                color = GlassTheme.colors.onGlass,
                            )
                            Spacer(Modifier.height(Spacing.ExtraSmall))
                            Text(
                                text = "Solid fallback rendering with GPU glass effects disabled.",
                                style = MaterialTheme.typography.bodySmall,
                                color = GlassTheme.colors.onGlassSecondary,
                            )
                            Spacer(Modifier.height(Spacing.SmallMedium))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                GlassPrimaryButton(onClick = {}) {
                                    Text("Explore")
                                }
                            }
                        }
                    }
                }
            }
        }

        val image = onRoot().captureToImage()
        ScreenshotTestHelper.assertMatchesBaseline("glass_card_effects_disabled", image)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testEffectsDisabledNavigationBar() = runDesktopComposeUiTest(width = 480, height = 120) {
        setContent {
            LtiTheme(darkTheme = true, effectsEnabled = false) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(GlassTheme.colors.canvasSurface)
                        .padding(Spacing.Small),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val items = listOf("Explorer", "Editor", "Search", "Settings")
                    val icons = listOf(
                        AppIcons.Folder,
                        AppIcons.File,
                        AppIcons.Search,
                        AppIcons.Settings,
                    )
                    GlassNavigationBar {
                        items.forEachIndexed { index, item ->
                            GlassNavigationBarItem(
                                selected = index == 0,
                                onClick = {},
                                icon = {
                                    Icon(
                                        imageVector = icons[index],
                                        contentDescription = item,
                                        modifier = Modifier.size(IconSize.Large),
                                    )
                                },
                                label = { Text(item) },
                            )
                        }
                    }
                }
            }
        }

        val image = onRoot().captureToImage()
        ScreenshotTestHelper.assertMatchesBaseline("glass_navigation_bar_effects_disabled", image)
    }
}
