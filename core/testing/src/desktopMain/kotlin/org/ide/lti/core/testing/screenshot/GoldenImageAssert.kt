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
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toPixelMap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import java.awt.Color as AwtColor

/**
 * Deterministic golden image and layout assertion engine for Compose Desktop tests.
 *
 * Supports:
 * - Pixel diff verification with configurable tolerance (default 0.5%)
 * - Approved region masking (e.g. native window controls, subpixel text AA)
 * - Immutable reference protection against accidental baseline updates
 * - Visual diff image and structured report generation on regression
 * - Exact semantic inventory and anchor bounding box validation
 */
object GoldenImageAssert {

    val isSystemUpdateMode: Boolean
        get() = System.getProperty("updateScreenshots")?.toBoolean() == true ||
            System.getenv("UPDATE_SCREENSHOTS")?.toBoolean() == true

    /**
     * Resolves the default baseline directory based on the execution context.
     */
    fun defaultBaselineDir(): File {
        val userDir = File(System.getProperty("user.dir"))
        return when {
            userDir.name == "designsystem" -> File(userDir, "src/desktopTest/resources/screenshots")
            userDir.name == "workspace" -> File(userDir, "src/desktopTest/resources/screenshots")
            userDir.name == "testing" -> File(userDir, "src/desktopTest/resources/screenshots")
            userDir.name == "setup" -> File(userDir, "src/desktopTest/resources/screenshots")
            else -> File(userDir, "core/designsystem/src/desktopTest/resources/screenshots")
        }
    }

    /**
     * Resolves the default reports directory for failure artifacts.
     */
    fun defaultReportsDir(): File {
        val userDir = File(System.getProperty("user.dir"))
        val dir = when {
            userDir.name == "designsystem" -> File(userDir, "build/reports/screenshots")
            userDir.name == "workspace" -> File(userDir, "build/reports/screenshots")
            userDir.name == "testing" -> File(userDir, "build/reports/screenshots")
            userDir.name == "setup" -> File(userDir, "build/reports/screenshots")
            else -> File(userDir, "build/reports/screenshots")
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Asserts that actual semantic tags strictly match required inventory and contain no forbidden tags.
     */
    fun assertInventory(actualTags: Set<String>, expectedTags: Set<String>, forbiddenTags: Set<String> = emptySet()) {
        val missing = expectedTags - actualTags
        val forbidden = actualTags.intersect(forbiddenTags)
        if (missing.isNotEmpty() || forbidden.isNotEmpty()) {
            val msg = buildString {
                appendLine("=== Component Inventory Assertion Failure ===")
                if (missing.isNotEmpty()) appendLine("Missing required: $missing")
                if (forbidden.isNotEmpty()) appendLine("Forbidden present: $forbidden")
            }
            throw AssertionError(msg)
        }
    }

    /**
     * Asserts that anchor rectangles are within [tolerancePx] of the expected geometry.
     */
    fun assertAnchors(actualBounds: Map<String, Rect>, expectedAnchors: Map<String, Rect>, tolerancePx: Int = 2) {
        val errors = mutableListOf<String>()
        for ((name, expected) in expectedAnchors) {
            val actual = actualBounds[name]
            if (actual == null) {
                errors.add("Missing anchor for '$name'")
                continue
            }
            val dLeft = abs(actual.left - expected.left)
            val dTop = abs(actual.top - expected.top)
            val dRight = abs(actual.right - expected.right)
            val dBottom = abs(actual.bottom - expected.bottom)

            if (dLeft > tolerancePx || dTop > tolerancePx || dRight > tolerancePx || dBottom > tolerancePx) {
                errors.add(
                    "Anchor '$name' out of tolerance (max ${tolerancePx}px). " +
                        "Expected: [l=${expected.left}, t=${expected.top}, " +
                        "r=${expected.right}, b=${expected.bottom}], " +
                        "Actual: [l=${actual.left}, t=${actual.top}, r=${actual.right}, b=${actual.bottom}], " +
                        "Deltas: [dl=$dLeft, dt=$dTop, dr=$dRight, db=$dBottom]",
                )
            }
        }
        if (errors.isNotEmpty()) {
            throw AssertionError("=== Layout Anchor Failures ===\n" + errors.joinToString("\n"))
        }
    }

    /**
     * Encodes an [ImageBitmap] to PNG and writes it to [file].
     */
    fun writeImageToFile(image: ImageBitmap, file: File) {
        file.parentFile?.mkdirs()
        val skiaBitmap = image.asSkiaBitmap()
        val skiaImage = Image.makeFromBitmap(skiaBitmap)
        val bytes = skiaImage.encodeToData(EncodedImageFormat.PNG)?.bytes
            ?: error("Failed to encode image to PNG for ${file.absolutePath}")
        file.writeBytes(bytes)
    }

    /**
     * Compares an [ImageBitmap] against a golden baseline PNG with masking and tolerance.
     */
    fun assertMatchesBaseline(
        screenshotName: String,
        actualImage: ImageBitmap,
        tolerancePercent: Double = 0.5,
        masks: List<Rect> = emptyList(),
        isImmutableReference: Boolean = false,
        baselineDir: File? = null,
        reportsDir: File? = null,
        forceUpdate: Boolean = false,
    ) {
        val targetBaselineDir = baselineDir ?: defaultBaselineDir()
        targetBaselineDir.mkdirs()

        val baselineFile = File(targetBaselineDir, "$screenshotName.png")
        val effectiveUpdate = forceUpdate || isSystemUpdateMode
        validateImmutableReference(screenshotName, baselineFile, isImmutableReference, effectiveUpdate)

        if (effectiveUpdate || !baselineFile.exists()) {
            writeImageToFile(actualImage, baselineFile)
            val action = if (effectiveUpdate) "Updated" else "Generated new"
            println("[SCREENSHOT] $action baseline at: ${baselineFile.absolutePath}")
            return
        }

        val baselineBufferedImage = ImageIO.read(baselineFile)
            ?: error("Failed to read baseline image at ${baselineFile.absolutePath}")
        assertMatchingDimensions(screenshotName, baselineBufferedImage, actualImage)

        val comparison = comparePixels(baselineBufferedImage, actualImage, masks)
        if (comparison.diffPercent <= tolerancePercent) {
            val formattedDiff = "%.4f".format(comparison.diffPercent)
            println("[SCREENSHOT] '$screenshotName' PASSED (diff: $formattedDiff%, tolerance: $tolerancePercent%)")
            return
        }

        throwScreenshotRegression(
            screenshotName = screenshotName,
            actualImage = actualImage,
            baselineFile = baselineFile,
            reportsDir = reportsDir ?: defaultReportsDir(),
            tolerancePercent = tolerancePercent,
            comparison = comparison,
        )
    }

    private fun validateImmutableReference(
        screenshotName: String,
        baselineFile: File,
        isImmutableReference: Boolean,
        effectiveUpdate: Boolean,
    ) {
        if (isImmutableReference && effectiveUpdate && baselineFile.exists()) {
            throw IllegalStateException(
                "Approved reference '$screenshotName' is immutable and cannot be updated by test passes.",
            )
        }
        if (isImmutableReference && !baselineFile.exists()) {
            throw IllegalStateException(
                "Immutable reference '$screenshotName' not found at ${baselineFile.absolutePath}",
            )
        }
    }

    private fun assertMatchingDimensions(screenshotName: String, baseline: BufferedImage, actual: ImageBitmap) {
        if (baseline.width != actual.width || baseline.height != actual.height) {
            throw AssertionError(
                "Screenshot dimension mismatch for '$screenshotName'. " +
                    "Baseline: ${baseline.width}x${baseline.height}, " +
                    "Actual: ${actual.width}x${actual.height}",
            )
        }
    }

    private fun comparePixels(baseline: BufferedImage, actual: ImageBitmap, masks: List<Rect>): PixelComparison {
        val actualPixelMap = actual.toPixelMap()
        val width = actual.width
        val height = actual.height
        val totalPixels = width * height
        var differingPixels = 0
        var maskedPixels = 0
        val diffImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

        for (y in 0 until height) {
            for (x in 0 until width) {
                if (isMasked(x, y, masks)) {
                    maskedPixels++
                    diffImage.setRGB(x, y, AwtColor(0, 200, 255, 40).rgb)
                    continue
                }

                val baselineArgb = baseline.getRGB(x, y)
                val actualColor = actualPixelMap[x, y]
                val actualArgb = actualColor.toArgbChannels()
                if (isDifferent(baselineArgb, actualArgb)) {
                    differingPixels++
                    diffImage.setRGB(x, y, AwtColor.MAGENTA.rgb)
                } else {
                    val gray = (actualArgb.red + actualArgb.green + actualArgb.blue) / 3
                    diffImage.setRGB(x, y, AwtColor(gray, gray, gray, 60).rgb)
                }
            }
        }

        val unmaskedTotalPixels = totalPixels - maskedPixels
        val diffPercent = if (unmaskedTotalPixels == 0) {
            0.0
        } else {
            (differingPixels.toDouble() / unmaskedTotalPixels) * 100.0
        }
        return PixelComparison(differingPixels, maskedPixels, unmaskedTotalPixels, diffPercent, diffImage)
    }

    private fun isMasked(x: Int, y: Int, masks: List<Rect>): Boolean = masks.any { mask ->
        x >= mask.left && x < mask.right && y >= mask.top && y < mask.bottom
    }

    private fun androidx.compose.ui.graphics.Color.toArgbChannels(): ArgbChannels = ArgbChannels(
        alpha = (alpha * 255).toInt(),
        red = (red * 255).toInt(),
        green = (green * 255).toInt(),
        blue = (blue * 255).toInt(),
    )

    private fun isDifferent(baselineArgb: Int, actual: ArgbChannels): Boolean {
        val baseline = ArgbChannels(
            alpha = (baselineArgb shr 24) and 0xFF,
            red = (baselineArgb shr 16) and 0xFF,
            green = (baselineArgb shr 8) and 0xFF,
            blue = baselineArgb and 0xFF,
        )
        return abs(baseline.alpha - actual.alpha) > COLOR_DELTA_THRESHOLD ||
            abs(baseline.red - actual.red) > COLOR_DELTA_THRESHOLD ||
            abs(baseline.green - actual.green) > COLOR_DELTA_THRESHOLD ||
            abs(baseline.blue - actual.blue) > COLOR_DELTA_THRESHOLD
    }

    private fun throwScreenshotRegression(
        screenshotName: String,
        actualImage: ImageBitmap,
        baselineFile: File,
        reportsDir: File,
        tolerancePercent: Double,
        comparison: PixelComparison,
    ): Nothing {
        val actualFile = File(reportsDir, "${screenshotName}_actual.png")
        val diffFile = File(reportsDir, "${screenshotName}_diff.png")
        val reportFile = File(reportsDir, "${screenshotName}_report.txt")
        writeImageToFile(actualImage, actualFile)
        ImageIO.write(comparison.diffImage, "PNG", diffFile)
        reportFile.writeText(
            buildReport(screenshotName, baselineFile, actualFile, diffFile, tolerancePercent, comparison),
        )
        throw AssertionError(
            buildFailureMessage(
                screenshotName,
                baselineFile,
                actualFile,
                diffFile,
                reportFile,
                tolerancePercent,
                comparison,
            ),
        )
    }

    private fun buildReport(
        screenshotName: String,
        baselineFile: File,
        actualFile: File,
        diffFile: File,
        tolerancePercent: Double,
        comparison: PixelComparison,
    ): String = buildString {
        appendLine("=== Screenshot Regression Report for '$screenshotName' ===")
        appendLine("Timestamp: ${System.currentTimeMillis()}")
        appendLine(comparison.summary("Differing Pixels"))
        appendLine("Masked Pixels: ${comparison.maskedPixels}")
        appendLine("Tolerance Allowed: $tolerancePercent%")
        appendLine("Baseline File: ${baselineFile.absolutePath}")
        appendLine("Actual File:   ${actualFile.absolutePath}")
        appendLine("Diff File:     ${diffFile.absolutePath}")
    }

    private fun buildFailureMessage(
        screenshotName: String,
        baselineFile: File,
        actualFile: File,
        diffFile: File,
        reportFile: File,
        tolerancePercent: Double,
        comparison: PixelComparison,
    ): String = buildString {
        appendLine("=== Screenshot Regression Failure for '$screenshotName' ===")
        appendLine(comparison.summary("Differing pixels"))
        appendLine("Tolerance allowed: $tolerancePercent%")
        appendLine("Baseline: ${baselineFile.absolutePath}")
        appendLine("Actual:   ${actualFile.absolutePath}")
        appendLine("Diff:     ${diffFile.absolutePath}")
        appendLine("Report:   ${reportFile.absolutePath}")
        appendLine("To update baselines, run with -PupdateScreenshots=true")
    }

    private fun PixelComparison.summary(label: String): String =
        "$label: $differingPixels / $unmaskedPixels (${"%.4f".format(diffPercent)}%)"

    private data class ArgbChannels(val alpha: Int, val red: Int, val green: Int, val blue: Int)

    private data class PixelComparison(
        val differingPixels: Int,
        val maskedPixels: Int,
        val unmaskedPixels: Int,
        val diffPercent: Double,
        val diffImage: BufferedImage,
    )

    private const val COLOR_DELTA_THRESHOLD = 4
}
