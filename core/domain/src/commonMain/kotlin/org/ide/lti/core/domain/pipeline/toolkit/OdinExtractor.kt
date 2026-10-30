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
 * Standard Samsung Odin firmware package slots.
 */
public enum class OdinSlot {
    AP,
    BL,
    CP,
    CSC,
    HOME_CSC,
}

/**
 * Adapter that produces execution commands for Samsung Odin firmware package extraction (FR-001, FR-006).
 *
 * Handles AP/BL/CP/CSC tarballs, `.tar.md5` trailer checksum verification, and `.lz4` decompression.
 */
public object OdinExtractor {

    /**
     * Identifies the [OdinSlot] corresponding to [fileName].
     */
    public fun classifySlot(fileName: String): OdinSlot? {
        val name = fileName.substringAfterLast('/')
        return when {
            name.startsWith("HOME_CSC_") -> OdinSlot.HOME_CSC
            name.startsWith("CSC_") -> OdinSlot.CSC
            name.startsWith("AP_") -> OdinSlot.AP
            name.startsWith("BL_") -> OdinSlot.BL
            name.startsWith("CP_") -> OdinSlot.CP
            else -> null
        }
    }

    /**
     * Checks if [fileNameOrPath] carries a `.tar.md5` or `.md5` suffix.
     */
    public fun isTarMd5(fileNameOrPath: String): Boolean {
        val lower = fileNameOrPath.lowercase()
        return lower.endsWith(".tar.md5") || lower.endsWith(".md5")
    }

    /**
     * Generates a command that verifies the declared MD5 trailer of a `.tar.md5` file against
     * the archive data.
     */
    public fun verifyMd5Command(tarMd5Path: String, workingDir: String): StepCommand {
        // A .tar.md5 is the tar followed by one `md5sum` line (hash, two spaces, name, newline); the tar ends in NUL
        // padding, so the line is matched, not taken after the last newline. The path travels as $1.
        val script = "LINE=\$(tail -c 512 \"\$1\" | grep -aoE '[0-9a-f]{32}  [^[:cntrl:]]+\$' | tail -n 1) && " +
            "[ -n \"\$LINE\" ] && DECLARED=\${LINE%% *} && " +
            "SIZE=\$(( \$(stat -c %s \"\$1\") - \${#LINE} - 1 )) && " +
            "ACTUAL=\$(head -c \"\$SIZE\" \"\$1\" | md5sum | cut -d' ' -f1) && " +
            "[ \"\$DECLARED\" = \"\$ACTUAL\" ]"
        return stepCommand(
            toolId = "sh",
            args = listOf("-c", script, "sh", tarMd5Path),
            workingDir = workingDir,
        )
    }

    /**
     * Generates a [StepCommand] to extract [tarPath] into [destDir] using `tar`.
     */
    public fun extractTarCommand(tarPath: String, destDir: String, workingDir: String): StepCommand = stepCommand(
        toolId = "tar",
        args = listOf("-xf", tarPath, "-C", destDir),
        workingDir = workingDir,
    )

    /**
     * Generates a [StepCommand] to decompress an LZ4 compressed file [lz4Path] into [outImagePath].
     */
    public fun decompressLz4Command(lz4Path: String, outImagePath: String, workingDir: String): StepCommand =
        stepCommand(
            toolId = "lz4",
            args = listOf("-d", "-f", lz4Path, outImagePath),
            workingDir = workingDir,
        )

    /**
     * Discovers raw partition names present in [members] list of a Samsung Odin archive.
     */
    public fun discoverPartitions(members: List<String>): List<String> = members.mapNotNull { raw ->
        val filename = raw.substringAfterLast('/')
        val cleaned = filename
            .removeSuffix(".lz4")
            .removeSuffix(".ext4")
            .removeSuffix(".f2fs")
            .removeSuffix(".img")
        if (cleaned.isNotBlank() && (filename.contains(".img") || filename.contains(".bin"))) {
            cleaned
        } else {
            null
        }
    }.distinct()

    /**
     * Extracts the declared 32-character MD5 checksum trailer from [bytes].
     */
    public fun extractDeclaredMd5(bytes: ByteArray): String? {
        val tail = bytes.copyOfRange(maxOf(0, bytes.size - TRAILER_WINDOW), bytes.size).decodeToString()
        return TRAILER.find(tail)?.groupValues?.get(1)?.lowercase()
    }

    private const val TRAILER_WINDOW = 512

    /** `md5sum`'s line at the very end: 32 hex digits, two spaces, the file name, an optional newline. */
    private val TRAILER = Regex("""([0-9a-fA-F]{32})  [^\x00-\x1f]+\r?\n?$""")
}
