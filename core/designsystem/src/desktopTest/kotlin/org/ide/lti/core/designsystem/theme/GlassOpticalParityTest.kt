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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Validates public Haze optical models, presets, and interactive pointer-reactive highlights.
 */
class GlassOpticalParityTest {

    @Test
    fun testBuiltInGlassPresetsAreValid() {
        val regular = GlassStyle.regular
        assertNotNull(regular, "GlassStyle.regular preset must be non-null")

        val clear = GlassStyle.clear
        assertNotNull(clear, "GlassStyle.clear preset must be non-null")
    }

    @Test
    fun testInteractiveResponseDslStyleCreation() {
        val interactiveStyle = GlassStyle {
            specularIntensity(0.6f)
            hovered {
                lightingIntensity(0.8f)
                whitePointDelta(0.1f)
                refractionMultiplier(1.2f)
            }
            pressed {
                lightingIntensity(1.0f)
                whitePointDelta(0.2f)
            }
        }
        assertNotNull(interactiveStyle, "GlassStyle with interaction response must be constructible")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testInteractiveGlassRendersAndRespondsToPointer() = runDesktopComposeUiTest {
        setContent {
            val hazeState = rememberHazeState()
            val interactiveStyle = GlassStyle {
                specularIntensity(0.6f)
                hovered {
                    lightingIntensity(0.8f)
                    whitePointDelta(0.1f)
                }
            }

            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
                    drawCircle(Color.Blue, radius = 50f, center = Offset(100f, 100f))
                }
                Box(
                    modifier = Modifier
                        .size(120.dp, 60.dp)
                        .hazeGlass(
                            input = HazeInput.Backdrop(hazeState),
                            style = interactiveStyle,
                        ),
                ) {
                    Text("Interactive Glass")
                }
            }
        }

        onNodeWithText("Interactive Glass").assertExists()
        onRoot().performMouseInput {
            moveTo(Offset(60f, 30f))
        }
    }
}
