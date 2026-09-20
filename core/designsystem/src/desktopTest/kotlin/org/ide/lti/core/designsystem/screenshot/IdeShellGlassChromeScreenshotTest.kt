/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.screenshot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.kashif_e.backdrop.backdrops.layerBackdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.layout.IdeNavigatorPanel
import org.ide.lti.core.designsystem.component.layout.IdePipelineRail
import org.ide.lti.core.designsystem.component.layout.IdeStatusBar
import org.ide.lti.core.designsystem.component.layout.IdeTopAppBar
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.PreviewSampleColors
import org.ide.lti.core.designsystem.theme.config.AppTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * IdeTopAppBar/IdeStatusBar/IdePipelineRail/IdeNavigatorPanel are the real production shell
 * chrome (behind GlobalAppTopBar/StudioTopAppBar/IdeAppFrame - confirmed via StudioScreen.kt),
 * migrated onto Modifier.glass(GlassStyles.{bar,rail,navigator}) gated by `enabled = isBlueGlass`
 * so Dark/Light themes keep their existing flat chrome exactly as before. No existing test
 * exercised these composables directly with real backdrop content (IdeShellComponentTest and
 * GlobalAppTopBarTest both stub slots or only check color tokens) - this proves both branches of
 * that gate render correctly: real blur under AppTheme.Blue, flat fallback under AppTheme.Dark.
 */
class IdeShellGlassChromeScreenshotTest {

    @OptIn(ExperimentalTestApi::class)
    private fun captureOverStripes(appTheme: AppTheme, content: @Composable () -> Unit): ImageBitmap {
        lateinit var captured: ImageBitmap
        runDesktopComposeUiTest(width = TEST_WIDTH, height = TEST_HEIGHT) {
            setContent {
                val backdrop = rememberLayerBackdrop()
                LtiTheme(appTheme = appTheme, sharedBackdrop = backdrop) {
                    Box(Modifier.fillMaxSize()) {
                        Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop)) { drawStripes() }
                        content()
                    }
                }
            }
            captured = onRoot().captureToImage()
        }
        return captured
    }

    private fun DrawScope.drawStripes() {
        val stripeWidth = size.width / STRIPE_COUNT
        for (i in 0 until STRIPE_COUNT) {
            val color = if (i % 2 == 0) PreviewSampleColors.Blue else PreviewSampleColors.Orange
            drawRect(color, topLeft = Offset(i * stripeWidth, 0f), size = size.copy(width = stripeWidth))
        }
    }

    /** [sampleY] defaults to vertical center; pass an explicit row for a short, top-anchored bar. */
    private fun stripeVariance(image: ImageBitmap, sampleY: Int = image.height / 2): Double {
        val pixelMap = image.toPixelMap()
        val values = (0 until image.width).map { x -> pixelMap[x, sampleY].red.toDouble() }
        val mean = values.average()
        return values.sumOf { (it - mean) * (it - mean) } / values.size
    }

    @Test
    fun topAppBarBlursUnderBlueThemeAndStaysFlatUnderDarkTheme() {
        // GlassDimens.TopBarHeight = 56dp - sample well inside the bar, not the flat canvas below it.
        val blueVariance = stripeVariance(captureOverStripes(AppTheme.Blue) { IdeTopAppBar() }, sampleY = BAR_SAMPLE_Y)
        val darkVariance = stripeVariance(captureOverStripes(AppTheme.Dark) { IdeTopAppBar() }, sampleY = BAR_SAMPLE_Y)
        val flatReference = stripeVariance(captureOverStripes(AppTheme.Blue) {}, sampleY = BAR_SAMPLE_Y)

        assertTrue(
            blueVariance < flatReference * VARIANCE_REDUCTION_CEILING,
            "IdeTopAppBar did not visibly blur under AppTheme.Blue - the isBlueGlass gate or " +
                "Modifier.glass wiring may be broken",
        )
        // IdeShellTokens.Dark's topBarBackground is fully opaque, so the correct flat-fallback
        // signal isn't "still shows the raw stripes" (opaque paint hides them completely either
        // way) - it's "uniform, stripe-free solid fill", i.e. variance near zero, and meaningfully
        // *more* uniform than the real blurred capture (which still carries some residual pattern
        // from the sampled backdrop).
        assertTrue(
            darkVariance < UNIFORM_FILL_VARIANCE_CEILING,
            "IdeTopAppBar under AppTheme.Dark was not a uniform opaque fill (variance=$darkVariance) - " +
                "may be sampling the backdrop when it shouldn't",
        )
        assertTrue(
            blueVariance > darkVariance,
            "IdeTopAppBar under AppTheme.Blue was as uniform as the opaque Dark fallback - " +
                "the isBlueGlass gate may not be reaching the real backdrop path",
        )
    }

    @Test
    fun statusBarBlursUnderBlueTheme() {
        // GlassDimens.StatusBarHeight = 32dp - sample well inside the bar.
        val flatReference = stripeVariance(captureOverStripes(AppTheme.Blue) {}, sampleY = BAR_SAMPLE_Y)
        val blue = captureOverStripes(AppTheme.Blue) { IdeStatusBar() }
        assertTrue(
            stripeVariance(blue, sampleY = BAR_SAMPLE_Y) < flatReference * VARIANCE_REDUCTION_CEILING,
            "IdeStatusBar did not visibly blur under AppTheme.Blue",
        )
    }

    @Test
    fun ideCardSurfaceBlursUnderBlueThemeAndStaysFlatUnderDarkTheme() {
        val cardBox: @Composable () -> Unit = {
            Box(
                Modifier
                    .size(ComponentSize.MinPanelWidth, ComponentSize.ActionCardHeight)
                    .ideCardSurface(),
            )
        }
        val blueVariance = stripeVariance(captureOverStripes(AppTheme.Blue, cardBox))
        val darkVariance = stripeVariance(captureOverStripes(AppTheme.Dark, cardBox))
        val flatReference = stripeVariance(captureOverStripes(AppTheme.Blue) {})

        assertTrue(
            blueVariance < flatReference * VARIANCE_REDUCTION_CEILING,
            "ideCardSurface did not visibly blur under AppTheme.Blue",
        )
        assertTrue(
            darkVariance < UNIFORM_FILL_VARIANCE_CEILING,
            "ideCardSurface under AppTheme.Dark was not a uniform opaque fill (variance=$darkVariance)",
        )
    }

    @Test
    fun pipelineRailBlursUnderBlueTheme() {
        val flatReference = stripeVariance(captureOverStripes(AppTheme.Blue) {})
        val blue = captureOverStripes(AppTheme.Blue) {
            IdePipelineRail(stages = emptyList(), selectedStageId = null, onSelectStage = {})
        }
        assertTrue(
            stripeVariance(blue) < flatReference * VARIANCE_REDUCTION_CEILING,
            "IdePipelineRail did not visibly blur under AppTheme.Blue",
        )
    }

    @Test
    fun navigatorPanelBlursUnderBlueTheme() {
        val flatReference = stripeVariance(captureOverStripes(AppTheme.Blue) {})
        val blue = captureOverStripes(AppTheme.Blue) {
            IdeNavigatorPanel(
                headerTitle = "Navigator",
                headerSubtitle = null,
                items = emptyList(),
                selectedItemId = null,
                onSelectItem = {},
            )
        }
        assertTrue(
            stripeVariance(blue) < flatReference * VARIANCE_REDUCTION_CEILING,
            "IdeNavigatorPanel did not visibly blur under AppTheme.Blue",
        )
    }

    private companion object {
        const val TEST_WIDTH = 320
        const val TEST_HEIGHT = 200
        const val STRIPE_COUNT = 10
        const val BAR_SAMPLE_Y = 15
        const val VARIANCE_REDUCTION_CEILING = 0.85
        const val UNIFORM_FILL_VARIANCE_CEILING = 1.0
    }
}
