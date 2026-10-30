/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.text

/**
 * Edits an Android mount table (`fstab`): `<src> <mount point> <type> <mount flags> <fs_mgr flags>` per line. Only
 * the fs_mgr flags of the lines asked for change; every other byte stays as it was.
 */
public object FstabEditor {
    private const val DATA = "/data"
    private const val MOUNT_POINT = 1
    private const val TYPE = 2
    private const val MOUNT_OPTIONS = 3
    private const val FS_MGR_FLAGS = 4

    /** Read-only mount options per filesystem, as AOSP devices write them (system's stock ext4 line). */
    private val MOUNT_OPTIONS_FOR = mapOf("ext4" to "ro,barrier=1,discard", "erofs" to "ro")
    private val TOKEN = Regex("""\S+""")

    /** fs_mgr flags that make `/data` encrypted (file-based, metadata, or the older forced full-disk kind). */
    private val ENCRYPTION_FLAGS = setOf(
        "fileencryption",
        "forceencrypt",
        "forcefdeorfbe",
        "encryptable",
        "keydirectory",
        "metadata_encryption",
    )

    /** Whether [fstab] has a line mounting `/data`. */
    public fun mountsData(fstab: String): Boolean = fstab.lines().any { mountPoint(it) == DATA }

    /** [fstab] with the encryption flags of its `/data` line removed: `/data` is then mounted unencrypted. */
    public fun withoutDataEncryption(fstab: String): String = fstab.lines().joinToString("\n") { line ->
        val tokens = TOKEN.findAll(line).toList()
        if (mountPoint(line) != DATA || tokens.size <= FS_MGR_FLAGS) {
            line
        } else {
            val flags = tokens[FS_MGR_FLAGS]
            val kept = flags.value.split(',').filterNot { it.substringBefore('=') in ENCRYPTION_FLAGS }
            line.replaceRange(flags.range, kept.joinToString(",").ifEmpty { "defaults" })
        }
    }

    /**
     * [fstab] with a line added for each `mount point -> filesystem` of [wanted] that a dynamic (`logical`) line
     * declares only with other filesystems: a copy of that line with the type and its mount options, right after
     * the last line of the mount point. The phone tries the lines in order, so the stock filesystem still mounts.
     */
    public fun withFilesystems(fstab: String, wanted: Map<String, String>): String {
        val lines = fstab.lines().toMutableList()
        wanted.forEach { (mountPoint, fs) ->
            val own = lines.indices.filter { mountPoint(lines[it]) == mountPoint && isLogical(lines[it]) }
            if (own.isEmpty() || own.any { type(lines[it]) == fs }) return@forEach
            val template = lines[own.first()]
            val tokens = TOKEN.findAll(template).toList()
            val copy = template.replaceRange(tokens[MOUNT_OPTIONS].range, MOUNT_OPTIONS_FOR[fs] ?: "ro")
                .let { it.replaceRange(TOKEN.findAll(it).elementAt(TYPE).range, fs) }
            lines.add(own.last() + 1, copy)
        }
        return lines.joinToString("\n")
    }

    /** The mount points of [fstab]'s dynamic (`logical`) partitions, in order. */
    public fun logicalMountPoints(fstab: String): List<String> =
        fstab.lines().filter(::isLogical).mapNotNull(::mountPoint).distinct()

    private fun isLogical(line: String): Boolean =
        TOKEN.findAll(line).elementAtOrNull(FS_MGR_FLAGS)?.value?.split(',')?.contains("logical") == true

    private fun type(line: String): String? = TOKEN.findAll(line).elementAtOrNull(TYPE)?.value

    private fun mountPoint(line: String): String? =
        line.takeUnless { it.trimStart().startsWith("#") }?.let { TOKEN.findAll(it).elementAtOrNull(MOUNT_POINT) }
            ?.value
}
