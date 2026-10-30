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
 * Adapter that produces execution commands for archive extraction and decompression (FR-001).
 *
 * Supports multi-file archives (ZIP, 7z, RAR, TAR) and single-file stream compressors
 * (Gz, Xz, Lz4, Bz2, Zstd, Brotli).
 */
public object ArchiveExtractor {

    public fun extractionCommand(
        format: PackageFormat,
        archivePath: String,
        destDir: String,
        workingDir: String,
    ): StepCommand = when (format) {
        PackageFormat.Zip -> stepCommand(
            toolId = "unzip",
            args = listOf("-o", archivePath, "-d", destDir),
            workingDir = workingDir,
        )
        // 7zz is the full 7-Zip: it reads RAR 1.5 to 5 too.
        PackageFormat.SevenZ, PackageFormat.Rar -> stepCommand(
            toolId = "7zz",
            args = listOf("x", "-y", "-o$destDir", archivePath),
            workingDir = workingDir,
        )
        PackageFormat.Tar -> stepCommand(
            toolId = "tar",
            args = listOf("-xf", archivePath, "-C", destDir),
            workingDir = workingDir,
        )
        PackageFormat.Gz -> singleFileDecompress("gzip", archivePath, destDir, workingDir)
        PackageFormat.Xz -> singleFileDecompress("xz", archivePath, destDir, workingDir)
        PackageFormat.Lz4 -> singleFileDecompress("lz4", archivePath, destDir, workingDir)
        PackageFormat.Bz2 -> singleFileDecompress("bzip2", archivePath, destDir, workingDir)
        PackageFormat.Zstd -> singleFileDecompress("zstd", archivePath, destDir, workingDir)
        PackageFormat.Brotli -> singleFileDecompress("brotli", archivePath, destDir, workingDir)
        else -> throw IllegalArgumentException("Format $format is not a generic archive or stream compressor")
    }

    // The paths travel as positional arguments ($1, $2), never inside the script text.
    private fun singleFileDecompress(
        tool: String,
        archivePath: String,
        destDir: String,
        workingDir: String,
    ): StepCommand {
        val outFileName = archivePath.substringAfterLast('/').substringBeforeLast('.')
        return stepCommand(
            toolId = "sh",
            args = listOf("-c", "$tool -dc \"\$1\" > \"\$2\"", "sh", archivePath, "$destDir/$outFileName"),
            workingDir = workingDir,
        )
    }
}
