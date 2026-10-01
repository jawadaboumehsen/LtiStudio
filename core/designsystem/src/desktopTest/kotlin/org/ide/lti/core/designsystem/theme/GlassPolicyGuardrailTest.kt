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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.GlassAccessibilitySettings
import dev.chrisbanes.haze.glass.LocalGlassAccessibilitySettings
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.feedback.GlassDialog
import org.ide.lti.core.designsystem.component.layout.IdeTopAppBar
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlassPolicyGuardrailTest {

    @Test
    fun productionCodebaseAdheresToHazeInputSourcesPolicy() {
        val rootDir = File(".").canonicalFile
        val designSystemSrc = File(rootDir, "src/commonMain/kotlin")
        assertTrue(designSystemSrc.exists(), "Source directory must exist: ${designSystemSrc.absolutePath}")

        val ktFiles = designSystemSrc.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(ktFiles.isNotEmpty(), "Kotlin source files must be found")

        var hazeGlassCallCount = 0
        val violations = mutableListOf<String>()

        for (file in ktFiles) {
            val lines = file.readLines()
            for ((index, line) in lines.withIndex()) {
                if (line.contains("hazeGlass(") && !line.trim().startsWith("//") && !line.trim().startsWith("*")) {
                    hazeGlassCallCount++
                    val snippet = lines.subList(
                        maxOf(0, index - 2),
                        minOf(lines.size, index + 10),
                    ).joinToString("\n")

                    if (snippet.contains("HazeInput.Backdrop")) {
                        violations.add("${file.name}:${index + 1} uses HazeInput.Backdrop instead of HazeInput.Sources")
                    }
                    if (snippet.contains("HazeInput.Sources") && !snippet.contains("selection =")) {
                        violations.add(
                            "${file.name}:${index + 1} uses unfiltered HazeInput.Sources without scoped selection; " +
                                "must use sceneHazeInput",
                        )
                    }
                    if (!snippet.contains("HazeInput.Sources") && !snippet.contains("sceneHazeInput")) {
                        violations.add("${file.name}:${index + 1} does not use HazeInput.Sources or sceneHazeInput")
                    }
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Found HazeInput policy violations:\n${violations.joinToString("\n")}",
        )
        assertTrue(
            hazeGlassCallCount > 0,
            "Expected to find production hazeGlass calls, but found none",
        )
    }

    @OptIn(ExperimentalTestApi::class, ExperimentalHazeApi::class)
    @Test
    fun monotonicAccessibilityPreservesParentReduceTransparencyWhenEffectsAreEnabled() = runDesktopComposeUiTest {
        var observedReduceTransparency = false
        var observedEffectsEnabled = false

        setContent {
            // Parent has reduceTransparency requested via accessibility settings
            CompositionLocalProvider(
                LocalGlassAccessibilitySettings provides GlassAccessibilitySettings(
                    reduceTransparency = true,
                    increaseContrast = false,
                    showBorders = false,
                ),
            ) {
                // Child requests effectsEnabled = true
                LtiTheme(
                    appTheme = AppTheme.Blue,
                    effectsEnabled = true,
                ) {
                    observedReduceTransparency = LocalGlassAccessibilitySettings.current.reduceTransparency
                    observedEffectsEnabled = GlassTheme.effectsEnabled
                }
            }
        }

        assertTrue(observedEffectsEnabled, "Child theme requested effectsEnabled = true")
        assertTrue(
            observedReduceTransparency,
            "Child theme with effectsEnabled = true MUST NOT clear parent reduceTransparency = true request " +
                "(monotonic)",
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun ideTopAppBarInteriorMatchesSurfaceContainerWhenEffectsDisabled() =
        runDesktopComposeUiTest(width = 300, height = 80) {
            var expectedColor: Color? = null
            setContent {
                LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = false) {
                    expectedColor = MaterialTheme.colorScheme.surfaceContainer
                    IdeTopAppBar()
                }
            }

            val pixelMap = onRoot().captureToImage().toPixelMap()
            val sampledColor = pixelMap[150, 20]
            assertColorClose(expectedColor!!, sampledColor, "IdeTopAppBar")
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun ideCardSurfaceMatchesSurfaceContainerHighWhenEffectsDisabled() =
        runDesktopComposeUiTest(width = 120, height = 120) {
            var expectedColor: Color? = null
            setContent {
                LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = false) {
                    expectedColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    Box(Modifier.fillMaxSize()) {
                        Box(
                            Modifier
                                .size(100.dp)
                                .ideCardSurface(),
                        )
                    }
                }
            }

            val pixelMap = onRoot().captureToImage().toPixelMap()
            val sampledColor = pixelMap[50, 50]
            assertColorClose(expectedColor!!, sampledColor, "ideCardSurface")
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun mutedIdeCardSurfaceMatchesSurfaceContainerWhenEffectsDisabled() =
        runDesktopComposeUiTest(width = 120, height = 120) {
            var expectedColor: Color? = null
            setContent {
                LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = false) {
                    expectedColor = MaterialTheme.colorScheme.surfaceContainer
                    Box(Modifier.fillMaxSize()) {
                        Box(
                            Modifier
                                .size(100.dp)
                                .ideCardSurface(isMuted = true),
                        )
                    }
                }
            }

            val pixelMap = onRoot().captureToImage().toPixelMap()
            val sampledColor = pixelMap[50, 50]
            assertColorClose(expectedColor!!, sampledColor, "muted ideCardSurface")
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun glassButtonPrimaryMatchesSurfaceContainerHighWhenEffectsDisabled() =
        runDesktopComposeUiTest(width = 120, height = 60) {
            var expectedColor: Color? = null
            setContent {
                LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = false) {
                    expectedColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    Box(Modifier.fillMaxSize()) {
                        GlassButton(
                            onClick = {},
                            variant = GlassButtonVariant.Primary,
                        ) {
                            Text("Action")
                        }
                    }
                }
            }

            val pixelMap = onRoot().captureToImage().toPixelMap()
            val sampledColor = pixelMap[60, 20]
            assertColorClose(expectedColor!!, sampledColor, "GlassButton.Primary")
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun glassButtonTextVariantRemainsTransparentWhenEffectsDisabled() =
        runDesktopComposeUiTest(width = 120, height = 60) {
            setContent {
                LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = false) {
                    Box(Modifier.fillMaxSize()) {
                        GlassButton(
                            onClick = {},
                            variant = GlassButtonVariant.Text,
                        ) {
                            // Empty content so we sample background directly
                        }
                    }
                }
            }

            val pixelMap = onRoot().captureToImage().toPixelMap()
            val sampledColor = pixelMap[60, 20]
            assertEquals(
                0f,
                sampledColor.alpha,
                "GlassButton.Text background must remain transparent even when effects are disabled",
            )
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun glassDialogMatchesSurfaceContainerHighestWhenEffectsDisabled() =
        runDesktopComposeUiTest(width = 200, height = 150) {
            var expectedColor: Color? = null
            setContent {
                LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = false) {
                    expectedColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    GlassDialog(
                        onDismissRequest = {},
                        confirmButton = { Text("OK") },
                        title = "Dialog Title",
                        modifier = Modifier.testTag("test_glass_dialog"),
                    ) {
                        Text("Dialog Content")
                    }
                }
            }

            val pixelMap = onNodeWithTag("test_glass_dialog").captureToImage().toPixelMap()
            val sampledColor = pixelMap[100, 30]
            assertColorClose(expectedColor!!, sampledColor, "GlassDialog")
        }

    private fun assertColorClose(expected: Color, actual: Color, component: String) {
        val deltaRed = Math.abs(expected.red - actual.red)
        val deltaGreen = Math.abs(expected.green - actual.green)
        val deltaBlue = Math.abs(expected.blue - actual.blue)
        val deltaAlpha = Math.abs(expected.alpha - actual.alpha)

        assertTrue(
            deltaRed < 0.05f && deltaGreen < 0.05f && deltaBlue < 0.05f && deltaAlpha < 0.05f,
            "$component: expected color $expected but sampled $actual " +
                "(delta R=$deltaRed, G=$deltaGreen, B=$deltaBlue, A=$deltaAlpha)",
        )
    }
}
