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

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toPixelMap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.awt.Color as AwtColor
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Deterministic screenshot comparison helper for Compose Desktop JVM tests.
 *
 * Supports baseline generation and pixel-similarity regression verification.
 * Baselines are stored in `src/desktopTest/resources/screenshots/`.
 *
 * To regenerate or update baselines, run Gradle with `-PupdateScreenshots=true` or set
 * environment variable `UPDATE_SCREENSHOTS=true` / system property `-DupdateScreenshots=true`.
 */
object ScreenshotTestHelper {

    private val isUpdateMode: Boolean =
        System.getProperty("updateScreenshots")?.toBoolean() == true ||
            System.getenv("UPDATE_SCREENSHOTS")?.toBoolean() == true

    /**
     * Resolves the baseline directory in source tree.
     */
    private fun getBaselineDir(): File {
        val userDir = File(System.getProperty("user.dir"))
        return if (userDir.name == "designsystem") {
            File(userDir, "src/desktopTest/resources/screenshots")
        } else {
            File(userDir, "core/designsystem/src/desktopTest/resources/screenshots")
        }
    }

    private fun getReportsDir(): File {
        val userDir = File(System.getProperty("user.dir"))
        val dir = if (userDir.name == "designsystem") {
            File(userDir, "build/reports/screenshots")
        } else {
            File(userDir, "core/designsystem/build/reports/screenshots")
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Compares an [ImageBitmap] against a saved baseline PNG.
     *
     * @param screenshotName Unique identifier for the screenshot (e.g. "glass_button_variants")
     * @param actualImage The rendered [ImageBitmap]
     * @param tolerancePercent Maximum allowed percentage of differing pixels (default: 0.5%)
     */
    fun assertMatchesBaseline(
        screenshotName: String,
        actualImage: ImageBitmap,
        tolerancePercent: Double = 0.5,
    ) {
        val baselineDir = getBaselineDir()
        if (!baselineDir.exists()) {
            baselineDir.mkdirs()
        }

        val baselineFile = File(baselineDir, "$screenshotName.png")
        val skiaBitmap = actualImage.asSkiaBitmap()
        val image = Image.makeFromBitmap(skiaBitmap)
        val actualBytes = image.encodeToData(EncodedImageFormat.PNG)?.bytes
            ?: error("Failed to encode actual image to PNG for '$screenshotName'")

        if (isUpdateMode || !baselineFile.exists()) {
            baselineFile.writeBytes(actualBytes)
            val action = if (isUpdateMode) "Updated" else "Generated new"
            println("[SCREENSHOT] $action baseline at: ${baselineFile.absolutePath}")
            return
        }

        val baselineBufferedImage = ImageIO.read(baselineFile)
            ?: error("Failed to read baseline image at ${baselineFile.absolutePath}")

        assertEquals(
            baselineBufferedImage.width,
            actualImage.width,
            "Screenshot width mismatch for '$screenshotName'. " +
                "Baseline: ${baselineBufferedImage.width}, Actual: ${actualImage.width}"
        )
        assertEquals(
            baselineBufferedImage.height,
            actualImage.height,
            "Screenshot height mismatch for '$screenshotName'. " +
                "Baseline: ${baselineBufferedImage.height}, Actual: ${actualImage.height}"
        )

        val actualPixelMap = actualImage.toPixelMap()
        val width = actualImage.width
        val height = actualImage.height
        val totalPixels = width * height

        var differingPixels = 0
        val diffImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val baselineArgb = baselineBufferedImage.getRGB(x, y)
                val baseA = (baselineArgb shr 24) and 0xFF
                val baseR = (baselineArgb shr 16) and 0xFF
                val baseG = (baselineArgb shr 8) and 0xFF
                val baseB = baselineArgb and 0xFF

                val actualColor = actualPixelMap[x, y]
                val actA = (actualColor.alpha * 255).toInt()
                val actR = (actualColor.red * 255).toInt()
                val actG = (actualColor.green * 255).toInt()
                val actB = (actualColor.blue * 255).toInt()

                val deltaA = abs(baseA - actA)
                val deltaR = abs(baseR - actR)
                val deltaG = abs(baseG - actG)
                val deltaB = abs(baseB - actB)

                // Per-channel color threshold of 4 (out of 255) to accommodate minor GPU subpixel AA
                val isDifferent = deltaR > 4 || deltaG > 4 || deltaB > 4 || deltaA > 4

                if (isDifferent) {
                    differingPixels++
                    // Highlight differing pixels with bright magenta in the diff artifact
                    diffImage.setRGB(x, y, AwtColor.MAGENTA.rgb)
                } else {
                    // Muted grayscale for matching pixels
                    val gray = (actR + actG + actB) / 3
                    val dimmed = AwtColor(gray, gray, gray, 60).rgb
                    diffImage.setRGB(x, y, dimmed)
                }
            }
        }

        val diffPercent = (differingPixels.toDouble() / totalPixels) * 100.0

        if (diffPercent > tolerancePercent) {
            val reportsDir = getReportsDir()
            val actualReportFile = File(reportsDir, "${screenshotName}_actual.png")
            val diffReportFile = File(reportsDir, "${screenshotName}_diff.png")

            actualReportFile.writeBytes(actualBytes)
            ImageIO.write(diffImage, "PNG", diffReportFile)

            val errorMsg = buildString {
                appendLine("=== Screenshot Regression Failure for '$screenshotName' ===")
                appendLine("Differing pixels: $differingPixels / $totalPixels (${"%.4f".format(diffPercent)}%)")
                appendLine("Tolerance allowed: $tolerancePercent%")
                appendLine("Baseline: ${baselineFile.absolutePath}")
                appendLine("Actual:   ${actualReportFile.absolutePath}")
                appendLine("Diff:     ${diffReportFile.absolutePath}")
                appendLine("To update baselines, run with -PupdateScreenshots=true")
            }
            error(errorMsg)
        } else {
            val formattedDiff = "%.4f".format(diffPercent)
            println("[SCREENSHOT] '$screenshotName' PASSED (diff: $formattedDiff%, tolerance: $tolerancePercent%)")
        }
    }
}
