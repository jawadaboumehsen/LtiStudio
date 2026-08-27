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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import org.ide.lti.core.designsystem.component.GlassButton
import org.ide.lti.core.designsystem.component.GlassButtonVariant
import org.ide.lti.core.designsystem.component.GlassCard
import org.ide.lti.core.designsystem.component.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.GlassSlider
import org.ide.lti.core.designsystem.component.GlassSurface
import org.ide.lti.core.designsystem.component.GlassTextButton
import org.ide.lti.core.designsystem.component.GlassTextField
import org.ide.lti.core.designsystem.component.GlassToggle
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ColorContrast
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.DarkGlassColorScheme
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.junit.Test

/**
 * Visual screenshot regression test suite for core glass design system components.
 *
 * Uses off-screen rendering via [runDesktopComposeUiTest] to verify visual fidelity
 * across builds against verified baselines.
 */
class GlassComponentScreenshotTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testGlassButtonVariants() = runDesktopComposeUiTest(width = 480, height = 180) {
        setContent {
            LtiTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(GlassTheme.colors.glassSurface)
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
        ScreenshotTestHelper.assertMatchesBaseline("glass_button_variants", image)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testGlassCard() = runDesktopComposeUiTest(width = 400, height = 240) {
        setContent {
            LtiTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(GlassTheme.colors.glassSurface)
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
                                text = "GPU-accelerated backdrop blur and lens refraction container.",
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
        ScreenshotTestHelper.assertMatchesBaseline("glass_card", image)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testGlassTextField() = runDesktopComposeUiTest(width = 400, height = 220) {
        setContent {
            LtiTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(GlassTheme.colors.glassSurface)
                        .padding(Spacing.Large),
                    contentAlignment = Alignment.Center
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                        GlassTextField(
                            value = "workspace_branch",
                            onValueChange = {},
                            label = "Branch Name",
                            placeholder = "Enter git branch",
                            leadingIcon = {
                                Icon(
                                    imageVector = AppIcons.Search,
                                    contentDescription = null,
                                    tint = GlassTheme.colors.onGlassSecondary,
                                )
                            }
                        )
                    }
                }
            }
        }

        val image = onRoot().captureToImage()
        ScreenshotTestHelper.assertMatchesBaseline("glass_text_field", image)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testGlassDialogSurface() = runDesktopComposeUiTest(width = 440, height = 260) {
        setContent {
            LtiTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(GlassTheme.colors.glassShadow.copy(alpha = AlphaTokens.Scrim))
                        .padding(Spacing.Large),
                    contentAlignment = Alignment.Center
                ) {
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        surfaceColor = GlassTheme.colors.glassSurfaceElevated,
                        borderColor = GlassTheme.colors.glassBorderHighlight,
                    ) {
                        Column(modifier = Modifier.padding(Spacing.DialogPadding)) {
                            Text(
                                text = "Confirm Changes",
                                style = MaterialTheme.typography.titleMedium,
                                color = GlassTheme.colors.onGlass,
                            )
                            Spacer(Modifier.height(Spacing.Small))
                            Text(
                                text = "Are you sure you want to commit these workspace token updates?",
                                style = MaterialTheme.typography.bodySmall,
                                color = GlassTheme.colors.onGlassSecondary,
                            )
                            Spacer(Modifier.height(Spacing.Medium))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GlassTextButton(onClick = {}) {
                                    Text("Cancel")
                                }
                                Spacer(Modifier.width(Spacing.Small))
                                GlassPrimaryButton(onClick = {}) {
                                    Text("Confirm")
                                }
                            }
                        }
                    }
                }
            }
        }

        val image = onRoot().captureToImage()
        ScreenshotTestHelper.assertMatchesBaseline("glass_dialog_surface", image)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testGlassToggleAndSlider() = runDesktopComposeUiTest(width = 420, height = 200) {
        setContent {
            val backdrop = rememberLayerBackdrop()
            var sliderValue by remember { mutableFloatStateOf(0.6f) }
            var toggleChecked by remember { mutableStateOf(true) }

            LtiTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(GlassTheme.colors.glassSurface)
                        .padding(Spacing.Large),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Enable Fluid Lighting", color = GlassTheme.colors.onGlass)
                            GlassToggle(
                                selected = { toggleChecked },
                                onSelect = { toggleChecked = it },
                                backdrop = backdrop,
                            )
                        }

                        GlassSlider(
                            value = { sliderValue },
                            onValueChange = { sliderValue = it },
                            valueRange = 0f..1f,
                            visibilityThreshold = 0.01f,
                            backdrop = backdrop,
                        )
                    }
                }
            }
        }

        val image = onRoot().captureToImage()
        ScreenshotTestHelper.assertMatchesBaseline("glass_toggle_and_slider", image)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testGlassColorSchemeSwatches() = runDesktopComposeUiTest(width = 480, height = 240) {
        setContent {
            LtiTheme(darkTheme = true) {
                val scheme = DarkGlassColorScheme
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(scheme.glassSurface)
                        .padding(Spacing.Medium),
                    contentAlignment = Alignment.Center
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                        Text(
                            text = "Color Tokens & Contrast Ratios",
                            style = MaterialTheme.typography.titleSmall,
                            color = scheme.onGlass
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                            SwatchCard("onGlass", scheme.onGlass, scheme.glassSurface)
                            SwatchCard("onSecondary", scheme.onGlassSecondary, scheme.glassSurface)
                            SwatchCard("onMuted", scheme.onGlassMuted, scheme.glassSurface)
                            SwatchCard("accent", scheme.glassAccent, scheme.glassSurface)
                            SwatchCard("focusRing", scheme.focusRing, scheme.glassSurface)
                        }
                    }
                }
            }
        }

        val image = onRoot().captureToImage()
        ScreenshotTestHelper.assertMatchesBaseline("glass_color_scheme_swatches", image)
    }

    @Composable
    private fun SwatchCard(
        name: String,
        color: androidx.compose.ui.graphics.Color,
        surfaceColor: androidx.compose.ui.graphics.Color,
    ) {
        val contrast = ColorContrast.contrastRatio(color, surfaceColor)

        Column(
            modifier = Modifier
                .width(ComponentSize.ButtonMinWidth)
                .background(color.copy(alpha = AlphaTokens.Hover))
                .padding(Spacing.ExtraSmall),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(Spacing.Medium)
                    .background(color)
            )
            Spacer(Modifier.height(Spacing.ExtraExtraSmall))
            Text(name, style = MaterialTheme.typography.labelSmall, color = GlassTheme.colors.onGlass)
            Text(
                text = "${"%.1f".format(contrast)}:1",
                style = MaterialTheme.typography.labelSmall,
                color = GlassTheme.colors.onGlassMuted,
            )
        }
    }
}
