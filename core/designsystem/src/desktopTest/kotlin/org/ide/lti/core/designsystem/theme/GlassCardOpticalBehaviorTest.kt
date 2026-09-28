/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.layout.GlassBackdrop
import org.ide.lti.core.testing.optical.GlassOpticalVerifier
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Controlled behavioral optical test fixture for production [GlassCard].
 *
 * Verifies real optical physics across all application themes (Dark, Blue, Light):
 * 1. Spatial edge diffusion of high-frequency backdrops (assertOpticalDiffusion & assertOpticalBlurSpread)
 * 2. Unblurred translucent negative control rejection (proves flat alpha cannot fake glass)
 * 3. Background modulation response under bright vs dark backdrops (assertBackdropModulation)
 * 4. Strict solid invariance and token match when effects are disabled
 * 5. WCAG AA contrast compliance across the card interior
 */
@OptIn(ExperimentalTestApi::class)
class GlassCardOpticalBehaviorTest {

    private val allThemes = listOf(AppTheme.Dark, AppTheme.Blue, AppTheme.Light)

    @Test
    fun testGlassCardSpatialDiffusionOverStripesAcrossAllThemes() {
        for (theme in allThemes) {
            var rawPattern: ImageBitmap? = null
            var glassRender: ImageBitmap? = null

            // 1. Render raw high-frequency stripe pattern
            runDesktopComposeUiTest(width = 320, height = 200) {
                setContent {
                    LtiTheme(appTheme = theme) {
                        Box(Modifier.fillMaxSize()) {
                            StripeCanvas(
                                modifier = Modifier.fillMaxSize().hazeSource(GlassTheme.hazeState),
                                stripeWidthPx = 16f,
                            )
                        }
                    }
                }
                waitForIdle()
                rawPattern = onRoot().captureToImage()
            }

            // 2. Render same stripes with production GlassCard over the center
            runDesktopComposeUiTest(width = 320, height = 200) {
                setContent {
                    LtiTheme(appTheme = theme) {
                        Box(Modifier.fillMaxSize()) {
                            StripeCanvas(
                                modifier = Modifier.fillMaxSize().hazeSource(GlassTheme.hazeState),
                                stripeWidthPx = 16f,
                            )
                            GlassCard(
                                modifier = Modifier
                                    .size(200.dp, 120.dp)
                                    .align(Alignment.Center),
                                contentPadding = 0.dp,
                            ) {
                                Box(Modifier.fillMaxSize())
                            }
                        }
                    }
                }
                waitForIdle()
                glassRender = onRoot().captureToImage()
            }

            assertNotNull(rawPattern)
            assertNotNull(glassRender)

            // Sample center region inside card (card is 200x120 centered in 320x200 -> bounds [60, 40, 260, 160])
            val cardInterior = Rect(80f, 60f, 240f, 140f)

            // Verify gradient retention reduction
            GlassOpticalVerifier.assertOpticalDiffusion(
                rawPattern = rawPattern!!,
                glassRender = glassRender!!,
                roi = cardInterior,
                maxGradientRetentionRatio = 0.40,
            )

            // Verify spatial edge spreading (proves true blur spread across >= 3px and energy outside peak >= 20%)
            GlassOpticalVerifier.assertOpticalBlurSpread(
                glassRender = glassRender!!,
                roi = cardInterior,
                stripePeriodPx = 16,
                minTransitionWidthPx = 3,
                minSpreadFraction = 0.20,
            )

            // Verify 10-90% edge transition width expansion (proves authentic spatial optical blur
            // with >= 1.8x expansion)
            GlassOpticalVerifier.assertEdgeTransitionExpansion(
                rawPattern = rawPattern!!,
                glassRender = glassRender!!,
                roi = cardInterior,
                stripePeriodPx = 16,
                minExpansionRatio = 1.8,
            )
        }
    }

    @Test
    fun testUnblurredTranslucentNegativeControlFailsDiffusionCheck() {
        var unblurredRender: ImageBitmap? = null

        // Render unblurred translucent fill over the same stripes
        runDesktopComposeUiTest(width = 320, height = 200) {
            setContent {
                LtiTheme(appTheme = AppTheme.Dark) {
                    Box(Modifier.fillMaxSize()) {
                        StripeCanvas(modifier = Modifier.fillMaxSize(), stripeWidthPx = 16f)
                        Box(
                            modifier = Modifier
                                .size(200.dp, 120.dp)
                                .align(Alignment.Center)
                                .background(Color.White.copy(alpha = 0.5f), shape = GlassShapes.HazeCard),
                        )
                    }
                }
            }
            waitForIdle()
            unblurredRender = onRoot().captureToImage()
        }

        assertNotNull(unblurredRender)
        val cardInterior = Rect(80f, 60f, 240f, 140f)

        // An unblurred translucent overlay retains a 1-pixel sharp step transition (spreadFraction ~ 0)
        // and MUST be rejected by assertOpticalBlurSpread
        val error = assertFailsWith<AssertionError> {
            GlassOpticalVerifier.assertOpticalBlurSpread(
                glassRender = unblurredRender!!,
                roi = cardInterior,
                stripePeriodPx = 16,
                minTransitionWidthPx = 3,
                minSpreadFraction = 0.20,
            )
        }
        assertTrue(
            error.message!!.contains("Optical blur failure: edge spreading is insufficient"),
            "Expected failure to identify insufficient edge spreading on flat translucent fill, " +
                "but got: ${error.message}",
        )
    }

    @Test
    fun testGlassCardBackdropModulationWhenEffectsOnAcrossAllThemes() {
        for (theme in allThemes) {
            var cardOverWhite: ImageBitmap? = null
            var cardOverBlack: ImageBitmap? = null

            // Render card over bright backdrop
            runDesktopComposeUiTest(width = 320, height = 200) {
                setContent {
                    LtiTheme(appTheme = theme, effectsEnabled = true) {
                        Box(Modifier.fillMaxSize()) {
                            Canvas(Modifier.fillMaxSize().hazeSource(GlassTheme.hazeState)) {
                                drawRect(Color.White)
                            }
                            GlassCard(
                                modifier = Modifier
                                    .size(200.dp, 120.dp)
                                    .align(Alignment.Center),
                                contentPadding = 0.dp,
                            ) {
                                Box(Modifier.fillMaxSize())
                            }
                        }
                    }
                }
                waitForIdle()
                cardOverWhite = onRoot().captureToImage()
            }

            // Render card over dark backdrop
            runDesktopComposeUiTest(width = 320, height = 200) {
                setContent {
                    LtiTheme(appTheme = theme, effectsEnabled = true) {
                        Box(Modifier.fillMaxSize()) {
                            Canvas(Modifier.fillMaxSize().hazeSource(GlassTheme.hazeState)) {
                                drawRect(Color.Black)
                            }
                            GlassCard(
                                modifier = Modifier
                                    .size(200.dp, 120.dp)
                                    .align(Alignment.Center),
                                contentPadding = 0.dp,
                            ) {
                                Box(Modifier.fillMaxSize())
                            }
                        }
                    }
                }
                waitForIdle()
                cardOverBlack = onRoot().captureToImage()
            }

            assertNotNull(cardOverWhite)
            assertNotNull(cardOverBlack)

            val cardBounds = Rect(60f, 40f, 260f, 160f)

            GlassOpticalVerifier.assertBackdropModulation(
                cardOverBackdropA = cardOverWhite!!,
                cardOverBackdropB = cardOverBlack!!,
                cardBounds = cardBounds,
                insetPx = 16,
                minDeltaModulation = 0.03,
                effectsEnabled = true,
            )
        }
    }

    @Test
    fun testGlassCardSolidInvarianceWhenEffectsOffAcrossAllThemes() {
        for (theme in allThemes) {
            var cardOverWhite: ImageBitmap? = null
            var cardOverBlack: ImageBitmap? = null
            var expectedFallback: Color = Color.Unspecified

            // Render card over bright backdrop with effects OFF
            runDesktopComposeUiTest(width = 320, height = 200) {
                setContent {
                    LtiTheme(appTheme = theme, effectsEnabled = false) {
                        expectedFallback = MaterialTheme.colorScheme.surfaceContainerHigh
                        Box(Modifier.fillMaxSize()) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawRect(Color.White)
                            }
                            GlassCard(
                                modifier = Modifier
                                    .size(200.dp, 120.dp)
                                    .align(Alignment.Center),
                                contentPadding = 0.dp,
                            ) {
                                Box(Modifier.fillMaxSize())
                            }
                        }
                    }
                }
                waitForIdle()
                cardOverWhite = onRoot().captureToImage()
            }

            // Render card over dark backdrop with effects OFF
            runDesktopComposeUiTest(width = 320, height = 200) {
                setContent {
                    LtiTheme(appTheme = theme, effectsEnabled = false) {
                        Box(Modifier.fillMaxSize()) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawRect(Color.Black)
                            }
                            GlassCard(
                                modifier = Modifier
                                    .size(200.dp, 120.dp)
                                    .align(Alignment.Center),
                                contentPadding = 0.dp,
                            ) {
                                Box(Modifier.fillMaxSize())
                            }
                        }
                    }
                }
                waitForIdle()
                cardOverBlack = onRoot().captureToImage()
            }

            assertNotNull(cardOverWhite)
            assertNotNull(cardOverBlack)

            val cardBounds = Rect(60f, 40f, 260f, 160f)

            GlassOpticalVerifier.assertBackdropModulation(
                cardOverBackdropA = cardOverWhite!!,
                cardOverBackdropB = cardOverBlack!!,
                cardBounds = cardBounds,
                insetPx = 16,
                effectsEnabled = false,
                expectedSolidFallback = expectedFallback,
            )
        }
    }

    @Test
    fun testGlassCardWCAGTextContrastOverBackdropAcrossAllThemes() {
        for (theme in allThemes) {
            var onSurfaceColor: Color = Color.Unspecified
            var image: ImageBitmap? = null
            runDesktopComposeUiTest(width = 320, height = 200) {
                setContent {
                    LtiTheme(appTheme = theme, effectsEnabled = true) {
                        onSurfaceColor = MaterialTheme.colorScheme.onSurface
                        Box(Modifier.fillMaxSize()) {
                            GlassBackdrop()
                            GlassCard(
                                modifier = Modifier
                                    .size(200.dp, 120.dp)
                                    .align(Alignment.Center),
                                contentPadding = 0.dp,
                            ) {
                                Box(Modifier.fillMaxSize())
                            }
                        }
                    }
                }
                waitForIdle()
                image = onRoot().captureToImage()
            }

            assertNotNull(image)
            val cardInterior = Rect(80f, 60f, 240f, 140f)

            GlassOpticalVerifier.assertAdjacentTextContrast(
                textToken = onSurfaceColor,
                background = image!!,
                adjacentBounds = cardInterior,
                minRatio = 4.5,
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun StripeCanvas(modifier: Modifier, stripeWidthPx: Float) {
        Canvas(modifier) {
            val count = (size.width / (stripeWidthPx * 2)).toInt() + 1
            for (i in 0..count) {
                val x = i * stripeWidthPx * 2
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(x, 0f),
                    size = Size(stripeWidthPx, size.height),
                )
                drawRect(
                    color = Color.White,
                    topLeft = Offset(x + stripeWidthPx, 0f),
                    size = Size(stripeWidthPx, size.height),
                )
            }
        }
    }
}
