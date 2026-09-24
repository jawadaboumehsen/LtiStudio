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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.actions.GlassTextButton
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.testing.screenshot.GoldenImageAssert
import org.junit.Test

/**
 * Visual screenshot regression tests for visible keyboard focus ring indicator.
 */
class GlassFocusScreenshotTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testGlassButtonFocused() = runDesktopComposeUiTest(width = 480, height = 220) {
        setContent {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            LtiTheme(appTheme = AppTheme.Dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(Spacing.Medium),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GlassButton(
                                onClick = {},
                                modifier = Modifier.focusRequester(focusRequester),
                                variant = GlassButtonVariant.Standard,
                            ) {
                                Text("Focused Standard")
                            }
                            GlassButton(
                                onClick = {},
                                variant = GlassButtonVariant.Standard,
                            ) {
                                Text("Unfocused")
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GlassPrimaryButton(onClick = {}) {
                                Text("Primary Action")
                            }
                            GlassIconButton(onClick = {}) {
                                Icon(
                                    painter = AppIcons.RefreshPainterResource(),
                                    contentDescription = "Refresh",
                                    modifier = Modifier.size(IconSize.Medium),
                                )
                            }
                            GlassTextButton(onClick = {}) {
                                Text("Text Action")
                            }
                        }
                    }
                }
            }
        }

        val image = onRoot().captureToImage()
        GoldenImageAssert.assertMatchesBaseline("glass_button_focused", image)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testGlassButtonFocusRingSuppressedOnMouseClick() = runDesktopComposeUiTest(width = 320, height = 160) {
        setContent {
            LtiTheme(appTheme = AppTheme.Dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(Spacing.Medium),
                    contentAlignment = Alignment.Center,
                ) {
                    GlassButton(
                        onClick = {},
                        variant = GlassButtonVariant.Standard,
                    ) {
                        Text("Clicked")
                    }
                }
            }
        }

        onNodeWithText("Clicked").performClick()
        waitForIdle()

        val image = onRoot().captureToImage()
        GoldenImageAssert.assertMatchesBaseline("glass_button_focus_ring_suppressed_on_click", image)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testGlassButtonFocusRingReappearsOnKeyPressWhileFocused() = runDesktopComposeUiTest(width = 320, height = 160) {
        setContent {
            LtiTheme(appTheme = AppTheme.Dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(Spacing.Medium),
                    contentAlignment = Alignment.Center,
                ) {
                    GlassButton(
                        onClick = {},
                        variant = GlassButtonVariant.Standard,
                    ) {
                        Text("Clicked")
                    }
                }
            }
        }

        onNodeWithText("Clicked").performClick()
        waitForIdle()

        onRoot().performKeyInput {
            pressKey(Key.Tab)
        }
        waitForIdle()

        val image = onRoot().captureToImage()
        GoldenImageAssert.assertMatchesBaseline("glass_button_focus_ring_reappears_on_key_press", image)
    }
}
