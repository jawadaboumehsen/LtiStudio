/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.testing.screenshot

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GoldenImageAssertTest {

    private val tempDir = File(System.getProperty("java.io.tmpdir"), "golden_test_${System.currentTimeMillis()}")

    @BeforeTest
    fun setUp() {
        tempDir.mkdirs()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun createSolidBitmap(width: Int, height: Int, argbColor: Int): ImageBitmap {
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo.makeN32(width, height, ColorAlphaType.PREMUL))
        bitmap.erase(argbColor)
        return org.jetbrains.skia.Image.makeFromBitmap(bitmap).toComposeImageBitmap()
    }

    @Test
    fun testAssertInventoryPassesWhenMatching() {
        val actualTags = setOf("TopAppBar", "PipelineRail", "WorkspaceNavigator", "StatusBar")
        val expectedTags = setOf("TopAppBar", "PipelineRail", "WorkspaceNavigator", "StatusBar")
        GoldenImageAssert.assertInventory(actualTags, expectedTags)
    }

    @Test
    fun testAssertInventoryFailsWhenMissingRequiredOrContainsForbidden() {
        val actualTags = setOf("TopAppBar", "PipelineRail", "StudioDock")
        val expectedTags = setOf("TopAppBar", "PipelineRail", "WorkspaceNavigator", "StatusBar")
        val forbiddenTags = setOf("StudioDock")

        val error = assertFailsWith<AssertionError> {
            GoldenImageAssert.assertInventory(actualTags, expectedTags, forbiddenTags)
        }
        assertTrue(
            error.message!!.contains("Missing required: [WorkspaceNavigator, StatusBar]") ||
                error.message!!.contains("Forbidden present: [StudioDock]"),
        )
    }

    @Test
    fun testAssertAnchorsWithinTolerance() {
        val actual = mapOf(
            "TopAppBar" to Rect(0f, 0f, 1584f, 50f),
            "PipelineRail" to Rect(0f, 50f, 70f, 1250f),
        )
        val expected = mapOf(
            "TopAppBar" to Rect(0f, 0f, 1584f, 51f),
            "PipelineRail" to Rect(0f, 50f, 71f, 1250f),
        )
        GoldenImageAssert.assertAnchors(actual, expected, tolerancePx = 2)
    }

    @Test
    fun testAssertAnchorsFailsWhenExceedingTolerance() {
        val actual = mapOf("TopAppBar" to Rect(0f, 0f, 1584f, 50f))
        val expected = mapOf("TopAppBar" to Rect(0f, 0f, 1584f, 60f)) // 10px difference

        assertFailsWith<AssertionError> {
            GoldenImageAssert.assertAnchors(actual, expected, tolerancePx = 2)
        }
    }

    @Test
    fun testImmutableReferenceCannotBeOverwritten() {
        val baselineFile = File(tempDir, "immutable_reference.png")
        baselineFile.writeBytes(ByteArray(10))

        val error = assertFailsWith<IllegalStateException> {
            GoldenImageAssert.assertMatchesBaseline(
                screenshotName = "immutable_reference",
                actualImage = createSolidBitmap(10, 10, -0x1000000),
                baselineDir = tempDir,
                isImmutableReference = true,
                forceUpdate = true,
            )
        }
        assertTrue(error.message!!.contains("immutable", ignoreCase = true))
    }

    @Test
    fun testApprovedMasksExcludePixelDifferences() {
        // Create a 100x100 white image
        val baseImg = createSolidBitmap(100, 100, -0x1) // 0xFFFFFFFF
        val baselineFile = File(tempDir, "masked_test.png")
        GoldenImageAssert.writeImageToFile(baseImg, baselineFile)

        val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(100, 100)
        val canvas = surface.canvas
        canvas.clear(-0x1) // white
        val paint = org.jetbrains.skia.Paint().apply { color = -0x10000 } // red
        canvas.drawRect(org.jetbrains.skia.Rect.makeXYWH(0f, 0f, 10f, 10f), paint)
        val actualImg = surface.makeImageSnapshot().toComposeImageBitmap()

        // Mask the differing 10x10 region
        val masks = listOf(Rect(0f, 0f, 10f, 10f))
        GoldenImageAssert.assertMatchesBaseline(
            screenshotName = "masked_test",
            actualImage = actualImg,
            baselineDir = tempDir,
            masks = masks,
            tolerancePercent = 0.5,
        )
    }

    @Test
    fun testDiffReportEmittedWhenDiffExceedsThreshold() {
        val baseImg = createSolidBitmap(100, 100, -0x1000000) // black
        val baselineFile = File(tempDir, "diff_report_test.png")
        GoldenImageAssert.writeImageToFile(baseImg, baselineFile)

        // Actual is fully white (100% diff)
        val actualImg = createSolidBitmap(100, 100, -0x1)
        val reportsDir = File(tempDir, "reports")

        val error = assertFailsWith<AssertionError> {
            GoldenImageAssert.assertMatchesBaseline(
                screenshotName = "diff_report_test",
                actualImage = actualImg,
                baselineDir = tempDir,
                reportsDir = reportsDir,
                tolerancePercent = 0.5,
            )
        }

        assertTrue(error.message!!.contains("Screenshot Regression Failure"))
        assertTrue(File(reportsDir, "diff_report_test_actual.png").exists())
        assertTrue(File(reportsDir, "diff_report_test_diff.png").exists())
        assertTrue(File(reportsDir, "diff_report_test_report.txt").exists())
    }
}
