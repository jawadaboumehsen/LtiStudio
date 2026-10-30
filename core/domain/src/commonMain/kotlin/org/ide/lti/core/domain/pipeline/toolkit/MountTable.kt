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

/**
 * The filesystems the device's mount tables let each dynamic partition mount as. The
 * phone mounts `/<partition>` with the lines of its fstab, trying each line until one mounts, so a partition built
 * as ext4 needs an ext4 line in every fstab that declares it: `vendor/etc/fstab.*` (and `odm/etc`) of the tree, and
 * the first-stage fstab inside the boot images' ramdisks (vendor_boot on most devices).
 */
public object MountTable {
    /**
     * `sh -c SCRIPT sh <workspace> <unpack_bootimg>`: every line of the tree's fstabs and of the fstabs inside the
     * ramdisks of `work/kernel/<image>.img`, each as `<source>\t<line>`; a source is `work/vendor/etc/fstab.qcom`
     * or `vendor_boot.img:first_stage_ramdisk/fstab.qcom`.
     */
    public const val SCRIPT: String = """
        w="$1"; u="$2"
        for f in "${'$'}w"/work/vendor/etc/fstab.* "${'$'}w"/work/odm/etc/fstab.*; do
          [ -f "${'$'}f" ] && sed "s#^#${'$'}{f#"${'$'}w"/}\t#" "${'$'}f"
        done
        t=${'$'}(mktemp -d); trap 'rm -rf "${'$'}t"' EXIT
        for img in "${'$'}w"/work/kernel/*.img; do
          [ -f "${'$'}img" ] || continue
          n=${'$'}(basename "${'$'}img"); mkdir -p "${'$'}t/${'$'}n/r"
          "${'$'}u" --boot_img "${'$'}img" --out "${'$'}t/${'$'}n/u" >/dev/null 2>&1 || continue
          for rd in "${'$'}t/${'$'}n/u"/ramdisk "${'$'}t/${'$'}n/u"/vendor_ramdisk*; do
            [ -s "${'$'}rd" ] || continue
            ( cd "${'$'}t/${'$'}n/r" && { lz4 -dc "${'$'}rd" 2>/dev/null || zstd -dc "${'$'}rd" 2>/dev/null ||
              gzip -dc "${'$'}rd" 2>/dev/null || cat "${'$'}rd"; } | cpio -idmu --quiet 2>/dev/null ) || true
          done
          find "${'$'}t/${'$'}n/r" -type f -name 'fstab.*' | while read -r f; do
            sed "s#^#${'$'}n:${'$'}{f#"${'$'}t/${'$'}n/r/"}\t#" "${'$'}f"
          done
        done
    """

    private const val MOUNT_POINT = 1
    private const val TYPE = 2
    private const val FS_MGR_FLAGS = 4
    private val TOKEN = Regex("""\S+""")

    /** One line of a mount table for a dynamic (`logical`) partition. */
    public data class Entry(val source: String, val mountPoint: String, val type: String)

    /** A mount table that declares a partition, but not as the filesystem it is built as. */
    public data class Missing(
        val source: String,
        val partition: String,
        val filesystem: String,
        val declared: Set<String>,
    )

    /** The `logical` lines of [SCRIPT]'s output; comments and other partitions are left out. */
    public fun parse(output: String): List<Entry> = output.lines().mapNotNull { row ->
        val source = row.substringBefore('\t', "").ifEmpty { return@mapNotNull null }
        val tokens = TOKEN.findAll(row.substringAfter('\t')).map { it.value }.toList()
        // A short or commented line has no fs_mgr flags token, or a first token starting with '#'.
        val logical = tokens.getOrNull(FS_MGR_FLAGS)?.split(',')?.contains("logical") == true &&
            !tokens.first().startsWith("#")
        if (logical) Entry(source, tokens[MOUNT_POINT], tokens[TYPE]) else null
    }

    /** For each partition built as [wanted]`[p]`, the mount tables that declare `/<p>` without that filesystem. */
    public fun missing(entries: List<Entry>, wanted: Map<String, String>): List<Missing> =
        entries.groupBy { it.source }.flatMap { (source, lines) ->
            wanted.mapNotNull { (partition, fs) ->
                val declared = lines.filter { it.mountPoint == "/$partition" }.map { it.type }.toSet()
                Missing(source, partition, fs, declared).takeIf { declared.isNotEmpty() && fs !in declared }
            }
        }
}
