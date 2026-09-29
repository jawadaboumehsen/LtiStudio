/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.panels.target

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
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.RoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
import org.ide.lti.core.designsystem.component.layout.GlassBackdrop
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.model.target.PackagePolicy
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetFirmware
import org.ide.lti.core.model.target.TargetRegion
import org.ide.lti.core.model.target.TargetStatus
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
@Suppress("TooManyFunctions")
class TargetDetailScreenshotTest {

    private val sampleTarget = TargetDevice(
        id = "PQ84P01",
        name = "REDMAGIC Astra",
        codename = "PQ84P01",
        revision = 1,
        availableRegions = listOf(TargetRegion.GLOBAL, TargetRegion.CHINA),
        availableFirmwares = mapOf(
            TargetRegion.GLOBAL to listOf(
                TargetFirmware(
                    version = "10.5.15_GL",
                    buildId = "PQ84P01:15",
                    androidVersion = "15",
                    securityPatch = "2026-02-01",
                ),
            ),
        ),
        packagePolicy = PackagePolicy(listOf("system"), listOf("boot")),
        socPlatform = "Snapdragon 8 Elite",
        filesystemType = "erofs",
        superPartitionBytes = 17179869184L,
        dynamicPartitions = listOf("system"),
        bootPartitions = listOf("boot"),
        status = TargetStatus.QUALIFIED,
        description = "Official baseline",
    )

    @Test
    fun testTargetDetailDark() = runDesktopComposeUiTest(
        width = WIDTH_STANDARD,
        height = HEIGHT_STANDARD,
    ) {
        verifyTargetDetailScreenshot(
            name = "target_detail_dark",
            theme = AppTheme.Dark,
        )
    }

    @Test
    fun testTargetDetailLight() = runDesktopComposeUiTest(
        width = WIDTH_STANDARD,
        height = HEIGHT_STANDARD,
    ) {
        verifyTargetDetailScreenshot(
            name = "target_detail_light",
            theme = AppTheme.Light,
        )
    }

    @Test
    fun testTargetDetailBlue() = runDesktopComposeUiTest(
        width = WIDTH_STANDARD,
        height = HEIGHT_STANDARD,
    ) {
        verifyTargetDetailScreenshot(
            name = "target_detail_blue",
            theme = AppTheme.Blue,
        )
    }

    @Test
    fun testTargetDetailEffectsOff() = runDesktopComposeUiTest(
        width = WIDTH_STANDARD,
        height = HEIGHT_STANDARD,
    ) {
        verifyTargetDetailScreenshot(
            name = "target_detail_effects_off",
            theme = AppTheme.Dark,
            effectsEnabled = false,
        )
    }

    @Test
    fun testTargetDetailCompact() = runDesktopComposeUiTest(
        width = WIDTH_COMPACT,
        height = HEIGHT_COMPACT,
    ) {
        verifyTargetDetailScreenshot(
            name = "target_detail_compact",
            theme = AppTheme.Blue,
            width = WIDTH_COMPACT,
            height = HEIGHT_COMPACT,
        )
    }

    @Test
    fun testTargetDetailFontScale200() = runDesktopComposeUiTest(
        width = WIDTH_STANDARD,
        height = HEIGHT_STANDARD,
    ) {
        verifyTargetDetailScreenshot(
            name = "target_detail_font_scale_200",
            theme = AppTheme.Dark,
            fontScale = 2.0f,
        )
    }

    @Test
    fun testWifiPairingDialogDark() = runDesktopComposeUiTest(
        width = 600,
        height = 500,
    ) {
        verifyDialogScreenshot(
            name = "target_wifi_pairing_dialog_dark",
            theme = AppTheme.Dark,
            width = 600,
            height = 500,
        )
    }

    @Test
    fun testWifiPairingDialogLight() = runDesktopComposeUiTest(
        width = 600,
        height = 500,
    ) {
        verifyDialogScreenshot(
            name = "target_wifi_pairing_dialog_light",
            theme = AppTheme.Light,
            width = 600,
            height = 500,
        )
    }

    @Test
    fun testWifiPairingDialogError() = runDesktopComposeUiTest(
        width = 600,
        height = 500,
    ) {
        verifyDialogScreenshot(
            name = "target_wifi_pairing_dialog_error",
            theme = AppTheme.Blue,
            width = 600,
            height = 500,
            errorMessage = "Failed to authenticate pairing key with 192.168.1.42:37251",
        )
    }

    private fun DesktopComposeUiTest.verifyTargetDetailScreenshot(
        name: String,
        theme: AppTheme,
        effectsEnabled: Boolean = true,
        width: Int = WIDTH_STANDARD,
        height: Int = HEIGHT_STANDARD,
        fontScale: Float = 1.0f,
    ) {
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1.0f, fontScale = fontScale),
            ) {
                LtiTheme(appTheme = theme, effectsEnabled = effectsEnabled) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop()
                        TargetDetailPanel(
                            selectedTarget = sampleTarget,
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

    private fun DesktopComposeUiTest.verifyDialogScreenshot(
        name: String,
        theme: AppTheme,
        width: Int,
        height: Int,
        errorMessage: String? = null,
    ) {
        setContent {
            LtiTheme(appTheme = theme) {
                Box(Modifier.fillMaxSize()) {
                    GlassBackdrop()
                    GlassWifiPairingDialog(
                        isOpen = true,
                        onDismissRequest = {},
                        onPairAndConnect = { _, _, _ -> },
                        errorMessage = errorMessage,
                    )
                }
            }
        }

        val dialogNode = onAllNodes(isRoot())[1]
        dialogNode.assertIsDisplayed()
        dialogNode.captureToImage().also { image ->
            assertEquals(width, image.width)
            assertEquals(height, image.height)
            saveScreenshot(name, image)
        }
    }

    private fun saveScreenshot(name: String, bitmap: ImageBitmap) {
        val reportsDir = File("build/reports/screenshots/workspace-target")
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
