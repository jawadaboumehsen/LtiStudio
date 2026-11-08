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
     * `sh -c SCRIPT sh <workspace> <unpack_bootimg> [mkdtboimg]`: every line of the tree's fstabs and of the fstabs
     * inside the ramdisks of `work/kernel/<image>.img`, each as `<source>\t<line>`; a source is
     * `work/vendor/etc/fstab.qcom` or `vendor_boot.img:first_stage_ramdisk/fstab.qcom`. The device tree's fstab
     * (`firmware/android/fstab/<name>` in a boot image's dtb or a dtbo overlay, what first-stage init reads when the
     * ramdisk has no fstab) comes as fstab lines too, from `vendor_boot.img:dtb` or `dtbo.img:dt.<n>`; a node with
     * `status = "disabled"` as a `#` line.
     */
    public const val SCRIPT: String = """
        w="$1"; u="$2"; m="${'$'}{3:-}"
        for f in "${'$'}w"/work/vendor/etc/fstab.* "${'$'}w"/work/odm/etc/fstab.*; do
          [ -f "${'$'}f" ] && sed "s#^#${'$'}{f#"${'$'}w"/}\t#" "${'$'}f"
        done
        t=${'$'}(mktemp -d); trap 'rm -rf "${'$'}t"' EXIT
        # The fstab nodes of a device tree as fstab lines: <dev> /<name> <type> <mnt_flags> <fsmgr_flags>.
        dtfstab() {
          dtc -q -I dtb -O dts "$1" 2>/dev/null | awk -v src="$2" '
            /\{[ \t]*$/ { line = $0; sub(/[ \t]*\{[ \t]*$/, "", line); n = split(line, w, /[ \t]+/)
              d++; path[d] = w[n]; if (path[d - 1] == "fstab") { cur = w[n]; split("", p) } next }
            /^[ \t]*\};/ { if (cur != "" && path[d - 1] == "fstab" && path[d] == cur) {
                printf "%s\t%s%s /%s %s %s %s\n", src, (p["status"] == "disabled" ? "#" : ""),
                  (p["dev"] != "" ? p["dev"] : "none"), cur, p["type"],
                  (p["mnt_flags"] != "" ? p["mnt_flags"] : "defaults"),
                  (p["fsmgr_flags"] != "" ? p["fsmgr_flags"] : "defaults"); cur = "" }
              d--; next }
            cur != "" && /=/ { k = $1; v = $0; sub(/^[^=]*=[ \t]*/, "", v); sub(/;[ \t]*$/, "", v); gsub(/"/, "", v)
              p[k] = v }'
        }
        for img in "${'$'}w"/work/kernel/*.img; do
          [ -f "${'$'}img" ] || continue
          n=${'$'}(basename "${'$'}img"); mkdir -p "${'$'}t/${'$'}n/r"
          if [ "${'$'}n" = dtbo.img ]; then
            [ -n "${'$'}m" ] || continue
            mkdir -p "${'$'}t/dtbo" && ( cd "${'$'}t/dtbo" && "${'$'}m" dump "${'$'}img" -b dt >/dev/null 2>&1 ) || continue
            for o in "${'$'}t"/dtbo/dt.*; do [ -f "${'$'}o" ] && dtfstab "${'$'}o" "dtbo.img:${'$'}(basename "${'$'}o")"; done
            continue
          fi
          "${'$'}u" --boot_img "${'$'}img" --out "${'$'}t/${'$'}n/u" >/dev/null 2>&1 || continue
          [ -s "${'$'}t/${'$'}n/u/dtb" ] && dtfstab "${'$'}t/${'$'}n/u/dtb" "${'$'}n:dtb"
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
    private const val MOUNT_FLAGS = 3
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

    /**
     * The `logical` lines of [SCRIPT]'s output that the phone uses; comments and other partitions are left out. The
     * device tree's fstab counts only when no boot image ramdisk has one: first-stage init reads the ramdisk's first.
     */
    public fun parse(output: String): List<Entry> {
        val rows = rows(output)
        val ramdisk = ramdiskHasMounts(rows)
        return rows.filter { !(ramdisk && isDeviceTree(it.source)) }.mapNotNull { row ->
            val tokens = row.tokens
            // A short or commented line has no fs_mgr flags token, or a first token starting with '#'.
            val logical = tokens.getOrNull(FS_MGR_FLAGS)?.split(',')?.contains("logical") == true &&
                !tokens.first().startsWith("#")
            if (logical) Entry(row.source, tokens[MOUNT_POINT], tokens[TYPE]) else null
        }
    }

    /** Whether a boot image's ramdisk has a mount line: an fstab of comments alone leaves the device tree in use. */
    private fun ramdiskHasMounts(rows: List<Row>): Boolean = rows.any {
        !it.source.startsWith("work/") && !isDeviceTree(it.source) && it.tokens.size > FS_MGR_FLAGS &&
            !it.tokens.first().startsWith("#")
    }

    /** One mount of the device tree's fstab. */
    public data class DeviceTreeMount(
        val source: String,
        val mountPoint: String,
        val device: String,
        val type: String,
        val mountFlags: String,
        val fsMgrFlags: String,
        val disabled: Boolean,
    )

    /**
     * The device tree's fstab in [SCRIPT]'s output, and whether the phone uses it: only when no ramdisk of the boot
     * images carries an fstab (a first-stage fstab there wins).
     */
    public data class DeviceTree(val mounts: List<DeviceTreeMount>, val inUse: Boolean)

    public fun deviceTree(output: String): DeviceTree {
        val rows = rows(output)
        val mounts = rows.filter { isDeviceTree(it.source) && it.tokens.size > FS_MGR_FLAGS }.map { row ->
            val t = row.tokens
            DeviceTreeMount(
                source = row.source,
                mountPoint = t[MOUNT_POINT],
                device = t[0].removePrefix("#"),
                type = t[TYPE],
                mountFlags = t[MOUNT_FLAGS],
                fsMgrFlags = t[FS_MGR_FLAGS],
                disabled = t[0].startsWith("#"),
            )
        }
        return DeviceTree(mounts, inUse = mounts.any { !it.disabled } && !ramdiskHasMounts(rows))
    }

    private data class Row(val source: String, val tokens: List<String>)

    private fun rows(output: String): List<Row> = output.lines().mapNotNull { row ->
        val source = row.substringBefore('\t', "").ifEmpty { return@mapNotNull null }
        TOKEN.findAll(row.substringAfter('\t')).map { it.value }.toList().takeIf { it.isNotEmpty() }
            ?.let { Row(source, it) }
    }

    /** `vendor_boot.img:dtb` or `dtbo.img:dt.3`, not a ramdisk file. */
    private fun isDeviceTree(source: String) = source.endsWith(":dtb") || source.contains(":dt.")

    /** For each partition built as [wanted]`[p]`, the mount tables that declare `/<p>` without that filesystem. */
    public fun missing(entries: List<Entry>, wanted: Map<String, String>): List<Missing> =
        entries.groupBy { it.source }.flatMap { (source, lines) ->
            wanted.mapNotNull { (partition, fs) ->
                val declared = lines.filter { it.mountPoint == "/$partition" }.map { it.type }.toSet()
                Missing(source, partition, fs, declared).takeIf { declared.isNotEmpty() && fs !in declared }
            }
        }
}
