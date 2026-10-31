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

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Entry representing a file or directory in a ramdisk.
 */
@Serializable
public data class RamdiskEntry(
    public val path: String,
    public val sizeBytes: Long,
    public val isDirectory: Boolean = false,
)

/**
 * Result of ramdisk integrity verification (archive vs folder, FR-021).
 *
 * Key: `boot.repack.check`
 */
@Serializable
public data class RamdiskCheckResult(
    public val ok: Boolean,
    public val differences: List<String> = emptyList(),
    public val missingInArchive: List<String> = emptyList(),
    public val extraInArchive: List<String> = emptyList(),
    public val sizeDifferences: List<String> = emptyList(),
) {
    public companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

        public fun fromJson(text: String): RamdiskCheckResult = json.decodeFromString(serializer(), text)
        public fun toJson(result: RamdiskCheckResult): String = json.encodeToString(serializer(), result)
    }
}

/**
 * Pure integrity comparator between a repacked ramdisk archive and its source folder (FR-021).
 */
public object RamdiskCheck {

    /** Ramdisks the kernel starts `/init` from; a vendor_boot ramdisk is merged into one of them and has none. */
    private val INIT_RAMDISKS = setOf("boot", "init_boot", "recovery")

    /**
     * The refusal when [partition]'s ramdisk ([paths], as listed from it) has no `/init`: the kernel would have
     * nothing to run and panic ("Attempted to kill init"), so the device would not boot.
     */
    public fun missingInit(partition: String, paths: List<String>): Refusal? =
        if (partition in INIT_RAMDISKS && paths.none { normalizePath(it) == "init" }) {
            Refusal(
                cause = "This ramdisk has no /init, so the kernel would have nothing to start and the device would " +
                    "not boot.",
                item = "$partition.img",
                nextStep = "Put init back in the ramdisk folder (or unpack the image again), then repack.",
            )
        } else {
            null
        }

    /**
     * The refusal when [partition]'s `/init` is there but is not something the kernel can start: [head] is the
     * hex of its first four bytes, or `link` when it is a symbolic link (the kernel follows those). An ELF starts
     * `7f454c46`; anything else panics the kernel ("Attempted to kill init").
     */
    public fun badInit(partition: String, head: String): Refusal? =
        if (partition in INIT_RAMDISKS && head != "link" && head.isNotEmpty() && !head.startsWith(ELF_MAGIC)) {
            Refusal(
                cause = "This ramdisk's /init is not a program (it starts $head), so the kernel cannot start it and " +
                    "the device would not boot.",
                item = "$partition.img",
                nextStep = "Put the original init back in the ramdisk folder (or unpack the image again), then repack.",
            )
        } else {
            null
        }

    private const val ELF_MAGIC = "7f454c46"

    /**
     * The refusal when [partition]'s ramdisk is compressed with [format] (the kernel's name for it, null when it is
     * not compressed) and the boot kernel, which unpacks it, reads only [kernelFormats]: it would not be unpacked
     * and the device would not boot. Says nothing when the kernel does not tell (no embedded config).
     */
    public fun unreadableRamdisk(partition: String, format: String?, kernelFormats: List<String>?): Refusal? =
        if (format != null && kernelFormats != null && format !in kernelFormats) {
            Refusal(
                cause = "This ramdisk is compressed with $format, and the boot kernel unpacks only " +
                    "${kernelFormats.joinToString().ifEmpty { "uncompressed ramdisks" }}: the device would not boot.",
                item = "$partition.img",
                nextStep = "Patch it again with a ramdisk format the kernel reads (the stock image's format).",
            )
        } else {
            null
        }

    public fun normalizePath(rawPath: String): String = rawPath.replace('\\', '/')
        .trimStart('/')
        .removePrefix("./")
        .trimEnd('/')

    public fun compare(
        archiveEntries: List<RamdiskEntry>,
        folderEntries: List<RamdiskEntry>,
        ignoreDirectories: Boolean = true,
    ): RamdiskCheckResult {
        val archiveMap = archiveEntries
            .filter { !ignoreDirectories || !it.isDirectory }
            .associate { normalizePath(it.path) to it.sizeBytes }

        val folderMap = folderEntries
            .filter { !ignoreDirectories || !it.isDirectory }
            .associate { normalizePath(it.path) to it.sizeBytes }

        val missingInArchive = folderMap.keys.filter { !archiveMap.containsKey(it) }.sorted()
        val extraInArchive = archiveMap.keys.filter { !folderMap.containsKey(it) }.sorted()

        val sizeDifferences = mutableListOf<String>()
        for ((path, folderSize) in folderMap) {
            val archiveSize = archiveMap[path]
            if (archiveSize != null && archiveSize != folderSize) {
                sizeDifferences.add(
                    "Size mismatch for $path: folder has $folderSize bytes, archive has $archiveSize bytes",
                )
            }
        }
        sizeDifferences.sort()

        val differences = buildList {
            missingInArchive.forEach { add("Missing in archive: $it") }
            extraInArchive.forEach { add("Extra in archive: $it") }
            addAll(sizeDifferences)
        }

        return RamdiskCheckResult(
            ok = differences.isEmpty(),
            differences = differences,
            missingInArchive = missingInArchive,
            extraInArchive = extraInArchive,
            sizeDifferences = sizeDifferences,
        )
    }
}
