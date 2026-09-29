/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.configuration

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
import org.ide.lti.core.model.target.PackagePolicy
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetRegion
import org.ide.lti.core.model.target.TargetStatus
import org.ide.lti.core.model.target.TargetValidationError
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
class ConfigurationScreenshotTest {

    private val sampleTarget = TargetDevice(
        id = "PQ84P01",
        name = "REDMAGIC Astra",
        codename = "PQ84P01",
        revision = 1,
        availableRegions = listOf(TargetRegion.GLOBAL),
        availableFirmwares = emptyMap(),
        packagePolicy = PackagePolicy(listOf("system"), listOf("boot")),
        socPlatform = "SM8650",
        filesystemType = "erofs",
        superPartitionBytes = 17179869184L,
        dynamicPartitions = listOf("system"),
        bootPartitions = listOf("boot"),
        status = TargetStatus.QUALIFIED,
        description = "REDMAGIC Astra qualification device",
    )

    // =============================================================================================
    // Three-Theme Standard Viewport (1120x700)
    // =============================================================================================

    @Test
    fun testConfiguration_Dark_EffectsOn() = runDesktopComposeUiTest(
        width = WIDTH_STANDARD,
        height = HEIGHT_STANDARD,
    ) {
        renderAndCapture(
            theme = AppTheme.Dark,
            state = createStandardUiState(),
            name = "configuration_dark_effects_on",
        )
    }

    @Test
    fun testConfiguration_Dark_EffectsOff() = runDesktopComposeUiTest(
        width = WIDTH_STANDARD,
        height = HEIGHT_STANDARD,
    ) {
        renderAndCapture(
            theme = AppTheme.Dark,
            state = createStandardUiState(),
            name = "configuration_dark_effects_off",
            effectsEnabled = false,
        )
    }

    @Test
    fun testConfiguration_Blue_EffectsOn() = runDesktopComposeUiTest(
        width = WIDTH_STANDARD,
        height = HEIGHT_STANDARD,
    ) {
        renderAndCapture(
            theme = AppTheme.Blue,
            state = createStandardUiState(),
            name = "configuration_blue_effects_on",
        )
    }

    @Test
    fun testConfiguration_Blue_EffectsOff() = runDesktopComposeUiTest(
        width = WIDTH_STANDARD,
        height = HEIGHT_STANDARD,
    ) {
        renderAndCapture(
            theme = AppTheme.Blue,
            state = createStandardUiState(),
            name = "configuration_blue_effects_off",
            effectsEnabled = false,
        )
    }

    @Test
    fun testConfiguration_Light_EffectsOn() = runDesktopComposeUiTest(
        width = WIDTH_STANDARD,
        height = HEIGHT_STANDARD,
    ) {
        renderAndCapture(
            theme = AppTheme.Light,
            state = createStandardUiState(),
            name = "configuration_light_effects_on",
        )
    }

    @Test
    fun testConfiguration_Light_EffectsOff() = runDesktopComposeUiTest(
        width = WIDTH_STANDARD,
        height = HEIGHT_STANDARD,
    ) {
        renderAndCapture(
            theme = AppTheme.Light,
            state = createStandardUiState(),
            name = "configuration_light_effects_off",
            effectsEnabled = false,
        )
    }

    // =============================================================================================
    // Compact Viewport (900x600) & Tab Switching
    // =============================================================================================

    @Test
    fun testConfiguration_Compact_900x600() = runDesktopComposeUiTest(
        width = WIDTH_COMPACT,
        height = HEIGHT_COMPACT,
    ) {
        renderAndCapture(
            theme = AppTheme.Dark,
            state = createStandardUiState(),
            name = "configuration_compact_dark",
            width = WIDTH_COMPACT,
            height = HEIGHT_COMPACT,
        )
    }

    @Test
    fun testConfiguration_ValidationBanner() = runDesktopComposeUiTest(
        width = WIDTH_STANDARD,
        height = HEIGHT_STANDARD,
    ) {
        val state = createStandardUiState().copy(
            validationErrors = listOf(
                TargetValidationError.InvalidSuperPartitionSize,
            ),
        )
        renderAndCapture(
            theme = AppTheme.Dark,
            state = state,
            name = "configuration_validation_dark",
        )
    }

    // =============================================================================================
    // Helper Methods
    // =============================================================================================

    private fun createStandardUiState(): ConfigurationUiState = ConfigurationUiState(
        availableTargets = listOf(sampleTarget),
        selectedTargetId = sampleTarget.id,
        editingTarget = sampleTarget,
        activeTab = ConfigurationTab.GENERAL,
        isDirty = false,
        isSyncing = false,
    )

    private fun DesktopComposeUiTest.renderAndCapture(
        theme: AppTheme,
        state: ConfigurationUiState,
        name: String,
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
                        ConfigurationScreenContent(
                            state = state,
                            onBackClick = {},
                            onSearchChange = {},
                            onSelectTarget = {},
                            onAddTarget = {},
                            onDuplicateTarget = {},
                            onDeleteTarget = {},
                            onSelectTab = {},
                            onUpdateEditingTarget = {},
                            onRevert = {},
                            onApply = {},
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
        val reportsDir = File("build/reports/screenshots/configuration")
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
