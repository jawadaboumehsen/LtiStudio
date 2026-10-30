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
 * Adapter that produces execution commands for joining sparse-chunk images and handling NTPI packages (FR-001, FR-005).
 *
 * Joins split vendor sparse chunks (e.g. `system.img_sparsechunk.0`, `system.img_sparsechunk.1`)
 * into complete raw partition images via `simg2img`. Provides the canonical refusal for unsupported NTPI packages.
 */
public object ChunkExtractor {

    private val CHUNK_REGEX = Regex(
        """^(.*?)(?:\.img)?(?:_sparsechunk|\.chunk|\.sparsechunk)\.?(\d+)$""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Checks if [fileNameOrPath] matches a sparse chunk naming pattern.
     */
    public fun isSparseChunk(fileNameOrPath: String): Boolean {
        val fileName = fileNameOrPath.substringAfterLast('/')
        return CHUNK_REGEX.matches(fileName)
    }

    /**
     * Groups chunk file paths by their base partition name and sorts each partition's chunks
     * numerically in ascending order.
     */
    public fun groupSparseChunks(filePaths: List<String>): Map<String, List<String>> {
        val result = mutableMapOf<String, MutableList<Pair<Int, String>>>()
        for (path in filePaths) {
            val fileName = path.substringAfterLast('/')
            val match = CHUNK_REGEX.matchEntire(fileName) ?: continue
            val baseName = match.groupValues[1].removeSuffix(".img")
            val index = match.groupValues[2].toIntOrNull() ?: 0
            result.getOrPut(baseName) { mutableListOf() }.add(index to path)
        }
        return result.mapValues { (_, pairs) ->
            pairs.sortedBy { it.first }.map { it.second }
        }
    }

    /**
     * Generates a [StepCommand] using `simg2img` to join [chunkPaths] into [outImagePath].
     */
    public fun joinChunksCommand(chunkPaths: List<String>, outImagePath: String, workingDir: String): StepCommand =
        stepCommand(
            toolId = "simg2img",
            args = chunkPaths + listOf(outImagePath),
            workingDir = workingDir,
        )

    /**
     * Canonical refusal when encountering an NTPI (Nothing Phone packed image) archive (FR-005).
     */
    public fun ntpiRefusal(item: String, details: String? = null): Refusal = Refusal(
        cause = "Nothing Phone packed images (NTPI) are not currently supported.",
        item = item,
        nextStep = "Extract partition images with the vendor utility before importing.",
        details = details,
    )
}
