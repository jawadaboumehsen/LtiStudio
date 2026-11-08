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

    /** fs_mgr flags that make a partition verified: AVB (hashtree from vbmeta, its keys) and older dm-verity. */
    private val INTEGRITY_FLAGS = setOf("avb", "avb_keys", "verify", "verifyatboot")

    /**
     * [fstab] with the AVB and verity flags of every line removed: the phone mounts the
     * partitions without dm-verity, whatever vbmeta says. Lines without such a flag stay byte for byte.
     */
    public fun withoutIntegrity(fstab: String): String = fstab.lines().joinToString("\n") { line ->
        val tokens = TOKEN.findAll(line).toList()
        val flags = tokens.getOrNull(FS_MGR_FLAGS)
        if (mountPoint(line) == null || flags == null) return@joinToString line
        val parts = flags.value.split(',')
        val kept = parts.filterNot { it.substringBefore('=') in INTEGRITY_FLAGS }
        val shown = kept.joinToString(",").ifEmpty { "defaults" }
        if (kept.size == parts.size) line else line.replaceRange(flags.range, shown)
    }

    /** One line of a mount table: where from, where to, as what, how, and its fs_mgr flags. */
    public data class Row(
        val source: String,
        val mountPoint: String,
        val type: String,
        val mountOptions: String,
        val flags: List<String>,
    )

    /** The lines of [fstab] that mount something (comments and short lines left out), in order. */
    public fun rows(fstab: String): List<Row> = fstab.lines().mapNotNull { line ->
        if (line.trimStart().startsWith("#")) return@mapNotNull null
        val t = TOKEN.findAll(line).map { it.value }.toList()
        if (t.size <= MOUNT_OPTIONS) return@mapNotNull null
        Row(t[0], t[MOUNT_POINT], t[TYPE], t[MOUNT_OPTIONS], t.getOrNull(FS_MGR_FLAGS)?.split(',').orEmpty())
    }

    /** Whether [flag] (`avb=vbmeta_system`, `verify`) makes a partition verified. */
    public fun isIntegrity(flag: String): Boolean = flag.substringBefore('=') in INTEGRITY_FLAGS

    /** Whether [flag] (`fileencryption=…`, `forceencrypt`) makes a partition encrypted. */
    public fun isEncryption(flag: String): Boolean = flag.substringBefore('=') in ENCRYPTION_FLAGS

    /** Whether [flag] can be removed on its own: a verity or an encryption flag (nothing a line needs to mount). */
    public fun isRemovable(flag: String): Boolean = isIntegrity(flag) || isEncryption(flag)

    /**
     * [fstab] without each `mount point -> flag name` of [removals] on every line of that mount point (a flag is
     * matched by its name, before any `=`). Other lines stay byte for byte.
     */
    public fun withoutFlags(fstab: String, removals: List<Pair<String, String>>): String =
        fstab.lines().joinToString("\n") { line ->
            val point = mountPoint(line)
            val names = removals.filter { it.first == point }.map { it.second.substringBefore('=') }.toSet()
            val flags = TOKEN.findAll(line).elementAtOrNull(FS_MGR_FLAGS)
            if (names.isEmpty() || flags == null) return@joinToString line
            val parts = flags.value.split(',')
            val kept = parts.filterNot { it.substringBefore('=') in names }
            val shown = kept.joinToString(",").ifEmpty { "defaults" }
            if (kept.size == parts.size) line else line.replaceRange(flags.range, shown)
        }

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
