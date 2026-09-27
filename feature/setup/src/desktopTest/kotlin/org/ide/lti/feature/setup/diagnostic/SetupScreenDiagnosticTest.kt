/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.diagnostic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.ide.lti.core.designsystem.component.layout.GlassBackdrop
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.ColorContrast
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.colorSchemeFor
import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.feature.setup.SetupScreenContent
import org.ide.lti.feature.setup.SetupTab
import org.ide.lti.feature.setup.SetupUiActions
import org.ide.lti.feature.setup.SetupUiState
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Diagnostic test harness for SetupScreen rendered in full context over GlassBackdrop.
 *
 * Strictly located in `:feature:setup:desktopTest` to prevent architectural inversion.
 *
 * Measures:
 * 1. Worst-case interior card background luminance across multiple sampled coordinates.
 * 2. WCAG AA contrast of `onSurface` (>= 4.5:1) and `onSurfaceVariant` (>= 4.5:1) against worst-case card interior.
 * 3. Contrast of unboxed headline & subtitle text on `IdeAppFrame` central panel directly over radiant hubs.
 * 4. Compares Blue, Dark, and Light themes across effects-enabled and effects-disabled states.
 */
@OptIn(ExperimentalTestApi::class)
class SetupScreenDiagnosticTest {

    private val sampleToolchainState = ToolchainSetupState(
        steps = listOf(
            SetupStepDetail(
                stage = SetupStepStage.WSL_DETECTION,
                title = "WSL runtime",
                description = "Ubuntu 22.04 LTS",
                status = StepStatus.SUCCESS,
                provenance = StepProvenance.LIVE,
            ),
            SetupStepDetail(
                stage = SetupStepStage.SERVER_CONNECTIVITY,
                title = "Build service",
                description = "Bridge needs verification",
                status = StepStatus.PENDING,
            ),
            SetupStepDetail(
                stage = SetupStepStage.TOOLCHAIN_COMPILATION,
                title = "Tool packages",
                description = "Packages have not been checked",
                status = StepStatus.PENDING,
            ),
        ),
    )

    @Test
    fun testRealScreenContrastAcrossThreeThemesEffectsOn() =
        assertRealScreenContrastAcrossThreeThemes(effectsEnabled = true)

    @Test
    fun testRealScreenContrastAcrossThreeThemesEffectsOff() =
        assertRealScreenContrastAcrossThreeThemes(effectsEnabled = false)

    private fun assertRealScreenContrastAcrossThreeThemes(effectsEnabled: Boolean) = runDesktopComposeUiTest(
        width = SCREEN_WIDTH,
        height = SCREEN_HEIGHT,
    ) {
        val themes = listOf(AppTheme.Dark, AppTheme.Blue, AppTheme.Light)

        for (theme in themes) {
            setContent {
                LtiTheme(appTheme = theme, effectsEnabled = effectsEnabled) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop()
                        SetupScreenContent(
                            state = SetupUiState(
                                toolchainSetupState = sampleToolchainState,
                                selectedTab = SetupTab.PROJECTS,
                            ),
                            actions = SetupUiActions(),
                        )
                    }
                }
            }

            val image = onRoot().captureToImage()
            assertNotNull(image)
            val pixelMap = image.toPixelMap()
            val scheme = colorSchemeFor(theme)

            // 1. Measure interior card background region (Overview Card right area at x ~ 650-800, y ~ 175-225)
            val cardSamplePoints = listOf(
                Pair(650, 180),
                Pair(700, 190),
                Pair(750, 200),
                Pair(800, 210),
            )

            var worstCardRatioOnSurface = Double.MAX_VALUE
            var worstCardRatioOnSurfaceVariant = Double.MAX_VALUE
            var worstBgColor = scheme.surface

            for ((x, y) in cardSamplePoints) {
                if (x < image.width && y < image.height) {
                    val sampledColor = pixelMap[x, y]
                    val onSurfaceRatio = ColorContrast.contrastRatio(scheme.onSurface, sampledColor)
                    val onSurfaceVariantRatio = ColorContrast.contrastRatio(scheme.onSurfaceVariant, sampledColor)

                    if (onSurfaceRatio < worstCardRatioOnSurface) {
                        worstCardRatioOnSurface = onSurfaceRatio
                        worstBgColor = sampledColor
                    }
                    if (onSurfaceVariantRatio < worstCardRatioOnSurfaceVariant) {
                        worstCardRatioOnSurfaceVariant = onSurfaceVariantRatio
                    }
                }
            }

            // 2. Measure unboxed header text background region on IdeAppFrame central panel (x ~ 700-800, y ~ 80-110)
            val unboxedSamplePoints = listOf(
                Pair(700, 85),
                Pair(750, 95),
                Pair(800, 105),
            )

            var worstUnboxedRatioHeadline = Double.MAX_VALUE
            var worstUnboxedRatioSubtitle = Double.MAX_VALUE

            for ((x, y) in unboxedSamplePoints) {
                if (x < image.width && y < image.height) {
                    val sampledColor = pixelMap[x, y]
                    val headlineRatio = ColorContrast.contrastRatio(scheme.onSurface, sampledColor)
                    val subtitleRatio = ColorContrast.contrastRatio(scheme.onSurfaceVariant, sampledColor)

                    if (headlineRatio < worstUnboxedRatioHeadline) worstUnboxedRatioHeadline = headlineRatio
                    if (subtitleRatio < worstUnboxedRatioSubtitle) worstUnboxedRatioSubtitle = subtitleRatio
                }
            }

            println(
                """
                |---------------------------------------------------------------------------------
                |[DIAGNOSTIC] Theme: ${theme.id} (EffectsEnabled=$effectsEnabled)
                |  Card Interior Worst Sample:
                |    Sampled Bg Color: $worstBgColor
                |    onSurface Contrast: $worstCardRatioOnSurface:1 (Required >= 4.5:1)
                |    onSurfaceVariant Contrast: $worstCardRatioOnSurfaceVariant:1 (Required >= 4.5:1)
                |  Unboxed Text on IdeAppFrame Central Panel:
                |    Headline onSurface Contrast: $worstUnboxedRatioHeadline:1 (Required >= 3.0:1)
                |    Subtitle onSurfaceVariant Contrast: $worstUnboxedRatioSubtitle:1 (Required >= 4.5:1)
                |---------------------------------------------------------------------------------
                """.trimMargin(),
            )
            assertTrue(
                worstCardRatioOnSurface >= 4.5,
                "Card interior onSurface contrast ($worstCardRatioOnSurface:1) failed WCAG AA 4.5:1 in ${theme.id}",
            )
            assertTrue(
                worstCardRatioOnSurfaceVariant >= 4.5,
                "Card interior onSurfaceVariant contrast ($worstCardRatioOnSurfaceVariant:1) " +
                    "failed WCAG AA 4.5:1 in ${theme.id}",
            )
            assertTrue(
                worstUnboxedRatioHeadline >= 3.0,
                "IdeAppFrame central panel headline contrast ($worstUnboxedRatioHeadline:1) " +
                    "failed WCAG AA 3.0:1 in ${theme.id}",
            )
            assertTrue(
                worstUnboxedRatioSubtitle >= 4.5,
                "IdeAppFrame central panel subtitle contrast ($worstUnboxedRatioSubtitle:1) " +
                    "failed WCAG AA 4.5:1 in ${theme.id}",
            )
        }
        println("[DIAGNOSTIC] All 3 themes (Dark, Blue, Light) passed strict WCAG AA contrast gates!")
    }

    private companion object {
        const val SCREEN_WIDTH = 1120
        const val SCREEN_HEIGHT = 700
    }
}
