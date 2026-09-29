/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.studio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.RoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
import org.ide.lti.core.designsystem.component.layout.GlassBackdrop
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.model.run.StageId
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

private const val WIDTH_STANDARD: Int = 1120
private const val HEIGHT_STANDARD: Int = 700
private const val WIDTH_COMPACT: Int = 900
private const val HEIGHT_COMPACT: Int = 600

@OptIn(ExperimentalTestApi::class)
class WorkspaceOverviewScreenshotTest {

    private fun createTestOverviewState(): WorkspaceOverviewUiState {
        val items = listOf(
            ReadinessItemUi(
                id = ReadinessId.SOURCE,
                title = "Source firmware",
                contextLabel = "Pipeline step 01",
                severity = ReadinessSeverity.Warning,
                statusLabel = "Needs selection",
                description = "No source package or baseline zip assigned.",
                action = WorkspaceActionUi.OpenStage(StageId.FIRMWARE_ACQUISITION),
                reviewRequired = true,
            ),
            ReadinessItemUi(
                id = ReadinessId.DEBLOAT,
                title = "Debloat",
                contextLabel = "Pipeline step 04",
                severity = ReadinessSeverity.Neutral,
                statusLabel = "No removals configured",
                description = "Using stock package list with default keep rules.",
                action = WorkspaceActionUi.OpenStage(StageId.DEBLOAT),
                reviewRequired = false,
            ),
            ReadinessItemUi(
                id = ReadinessId.PATCHES,
                title = "Patches",
                contextLabel = "Pipeline step 05",
                severity = ReadinessSeverity.Neutral,
                statusLabel = "No modules enabled",
                description = "0 of 3 available patch modules selected.",
                action = WorkspaceActionUi.OpenStage(StageId.MODULE_APPLICATION),
                reviewRequired = false,
            ),
            ReadinessItemUi(
                id = ReadinessId.SIGNING,
                title = "Signing key",
                contextLabel = "Security context",
                severity = ReadinessSeverity.Warning,
                statusLabel = "Key required",
                description = "Release and AVB keypair not verified for production.",
                action = WorkspaceActionUi.OpenStage(StageId.BUILD_FLASHABLE_ZIP),
                reviewRequired = true,
            ),
            ReadinessItemUi(
                id = ReadinessId.PUBLICATION,
                title = "Publication",
                contextLabel = "Pipeline step 08",
                severity = ReadinessSeverity.Neutral,
                statusLabel = "Optional until build completes",
                description = "Release target example-org/rom-releases.",
                action = WorkspaceActionUi.OpenStage(StageId.PUBLISH_RELEASE),
                reviewRequired = false,
            ),
        )

        return WorkspaceOverviewUiState(
            workspaceTitle = "Your ROM workspace",
            subtitle = "Configure the next build for PQ84P01.",
            targetSummary = TargetSummaryUi(
                revision = DisplayValue.Available("4"),
                binding = DisplayValue.Available("Bound to this workspace"),
                deviceId = DisplayValue.Available("PQ84P01"),
                androidTarget = DisplayValue.Available("14 (API 34)"),
                partitionSlot = DisplayValue.Available("A / B (Seamless)"),
                buildFlavor = DisplayValue.Available("userdebug"),
            ),
            readinessItems = items,
            reviewCount = 2,
            nextAction = WorkspaceActionUi.OpenStage(StageId.FIRMWARE_ACQUISITION),
            buildState = BuildStateUi.NotStarted,
            recentActivity = emptyList(),
        )
    }

    @Test
    fun testWorkspaceOverviewDark() = runDesktopComposeUiTest(
        width = WIDTH_STANDARD,
        height = HEIGHT_STANDARD,
    ) {
        verifyOverviewScreenshot(
            name = "workspace_overview_dark",
            theme = AppTheme.Dark,
        )
    }

    @Test
    fun testWorkspaceOverviewLight() = runDesktopComposeUiTest(
        width = WIDTH_STANDARD,
        height = HEIGHT_STANDARD,
    ) {
        verifyOverviewScreenshot(
            name = "workspace_overview_light",
            theme = AppTheme.Light,
        )
    }

    @Test
    fun testWorkspaceOverviewBlue() = runDesktopComposeUiTest(
        width = WIDTH_STANDARD,
        height = HEIGHT_STANDARD,
    ) {
        verifyOverviewScreenshot(
            name = "workspace_overview_blue",
            theme = AppTheme.Blue,
        )
    }

    @Test
    fun testWorkspaceOverviewEffectsOff() = runDesktopComposeUiTest(
        width = WIDTH_STANDARD,
        height = HEIGHT_STANDARD,
    ) {
        verifyOverviewScreenshot(
            name = "workspace_overview_effects_off",
            theme = AppTheme.Dark,
            effectsEnabled = false,
        )
    }

    @Test
    fun testWorkspaceOverviewCompact() = runDesktopComposeUiTest(
        width = WIDTH_COMPACT,
        height = HEIGHT_COMPACT,
    ) {
        verifyOverviewScreenshot(
            name = "workspace_overview_compact",
            theme = AppTheme.Blue,
            width = WIDTH_COMPACT,
            height = HEIGHT_COMPACT,
        )
    }

    @Test
    fun testWorkspaceOverviewFontScale200() = runDesktopComposeUiTest(
        width = WIDTH_STANDARD,
        height = HEIGHT_STANDARD,
    ) {
        verifyOverviewScreenshot(
            name = "workspace_overview_font_scale_200",
            theme = AppTheme.Dark,
            fontScale = 2.0f,
        )
    }

    private fun DesktopComposeUiTest.verifyOverviewScreenshot(
        name: String,
        theme: AppTheme,
        effectsEnabled: Boolean = true,
        width: Int = WIDTH_STANDARD,
        height: Int = HEIGHT_STANDARD,
        fontScale: Float = 1.0f,
    ) {
        val state = createTestOverviewState()
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1.0f, fontScale = fontScale),
            ) {
                LtiTheme(appTheme = theme, effectsEnabled = effectsEnabled) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop()
                        WorkspaceOverview(
                            state = state,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        onRoot().assertIsDisplayed()
        onRoot().captureToImage().also { image ->
            assertEquals(width, image.width)
            assertEquals(height, image.height)
            saveScreenshot(name, image)
        }
    }

    private fun saveScreenshot(name: String, bitmap: ImageBitmap) {
        val reportsDir = File("build/reports/screenshots/rom-studio-shell")
        if (!reportsDir.exists()) reportsDir.mkdirs()
        val bytes = Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
        if (bytes != null) File(reportsDir, "$name.png").writeBytes(bytes)

        val targetPath = "src/desktopTest/resources/screenshots/$name.png"
        bitmap.captureRoboImage(
            filePath = targetPath,
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.005f),
                recordOptions = RoborazziOptions.RecordOptions(resizeScale = 1.0),
            ),
        )
    }
}
