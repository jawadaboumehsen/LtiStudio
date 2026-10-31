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
 * Adapter that produces execution commands for Android OTA payload extraction (FR-001, FR-007).
 *
 * Streams partitions directly from `payload.bin` or full-OTA zip archives without
 * unpacking a full temporary copy of `payload.bin` to disk (SC-003).
 */
public object PayloadExtractor {

    /**
     * Generates a [StepCommand] to extract partitions directly from [archiveOrPayloadPath] into [destDir].
     */
    public fun extractionCommand(
        archiveOrPayloadPath: String,
        destDir: String,
        workingDir: String,
        partitions: List<String> = emptyList(),
        concurrency: Int? = null,
    ): StepCommand {
        val args = mutableListOf<String>()
        args += "-o"
        args += destDir
        if (partitions.isNotEmpty()) {
            args += "-partitions"
            args += partitions.joinToString(",")
        }
        if (concurrency != null && concurrency > 0) {
            args += "-concurrency"
            args += concurrency.toString()
        }
        args += archiveOrPayloadPath
        return stepCommand(
            toolId = "payload-dumper-go",
            args = args,
            workingDir = workingDir,
        )
    }

    /**
     * Inspects OTA zip `metadata` string to determine if the package is an incremental update: only an incremental
     * names the build it starts from. `pre-device` is in full OTAs too.
     */
    public fun isIncrementalMetadata(metadataContent: String): Boolean = metadataContent.lineSequence().any { line ->
        val trimmed = line.trim()
        trimmed.startsWith("pre-build=") || trimmed.startsWith("pre-build-incremental=")
    }

    /**
     * Constructs the canonical [Refusal] for an incremental payload package (FR-007).
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
