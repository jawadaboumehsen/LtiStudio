/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.toolkit

import org.ide.lti.core.domain.pipeline.stepCommand
import org.ide.lti.core.model.run.StepCommand

/**
 * Adapter that produces execution commands for Android block-based OTA extraction (FR-001, FR-007).
 *
 * Reconstructs raw partition images from transfer lists and `.new.dat` / `.new.dat.br` data.
 */
public object BlockOtaExtractor {

    /**
     * Generates a [StepCommand] to decompress a Brotli-compressed `.new.dat.br` file.
     */
    public fun decompressBrotliCommand(brPath: String, outPath: String, workingDir: String): StepCommand = stepCommand(
        toolId = "brotli",
        args = listOf("-d", "-f", brPath, "-o", outPath),
        workingDir = workingDir,
    )

    /**
     * Generates a [StepCommand] to convert transfer list and new data into a raw image using `sdat2img`.
     */
    public fun sdat2imgCommand(
        transferListPath: String,
        newDatPath: String,
        outImagePath: String,
        workingDir: String,
    ): StepCommand = stepCommand(
        toolId = "sdat2img.py",
        args = listOf(transferListPath, newDatPath, outImagePath),
        workingDir = workingDir,
    )

    /**
     * Discovers all partition names present in the archive by inspecting member names for `*.transfer.list`.
     */
    public fun discoverPartitions(memberNames: List<String>): List<String> =
        memberNames.filter { it.endsWith(".transfer.list") }
            .map { it.substringAfterLast('/').substringBefore(".transfer.list") }
            .distinct()

    /**
     * Constructs the sequence of conversion commands needed to produce `<partition>.img`.
     */
    public fun conversionSteps(
        partition: String,
        isBrotli: Boolean,
        sourceDir: String,
        outDir: String,
        workingDir: String,
    ): List<StepCommand> {
        val steps = mutableListOf<StepCommand>()
        val transferList = "$sourceDir/$partition.transfer.list"
        val newDat = "$sourceDir/$partition.new.dat"
        val outImg = "$outDir/$partition.img"

        if (isBrotli) {
            val newDatBr = "$sourceDir/$partition.new.dat.br"
            steps += decompressBrotliCommand(newDatBr, newDat, workingDir)
        }
        steps += sdat2imgCommand(transferList, newDat, outImg, workingDir)
        return steps
    }

    /**
     * Constructs the canonical [Refusal] for an incremental block-OTA package (FR-007).
     */
    public fun incrementalRefusal(item: String, details: String? = null): Refusal =
        PackageManifest.incrementalRefusal(item = item, details = details).refusal
            ?: Refusal(
                cause = "The package is an incremental update.",
                item = item,
                nextStep = "Import the full update instead.",
                details = details,
            )
}
