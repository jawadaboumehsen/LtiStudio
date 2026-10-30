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

import org.ide.lti.core.domain.pipeline.text.SidecarNormalizer
import org.ide.lti.core.model.workspace.BuildSettings

/** The filesystems a dynamic partition can be built as, and which one a partition gets. */
public object BuildFilesystems {
    public const val EROFS: String = "erofs"
    public const val EXT4: String = "ext4"
    public const val F2FS: String = "f2fs"

    /** EROFS (mkfs.erofs), ext4 (mke2fs + e2fsdroid) and F2FS (make_f2fs + sload_f2fs, read back by dump.f2fs). */
    public val SUPPORTED: List<String> = listOf(EROFS, EXT4, F2FS)

    /** [p]'s filesystem: its own choice, else the build's default. */
    public fun of(build: BuildSettings, p: String): String =
        (build.partitionFilesystem[p] ?: build.filesystem).lowercase()
}

/**
 * A read-only ext4 image built the way AOSP builds one: `mke2fs` makes an empty filesystem, `e2fsdroid` fills it
 * from the tree with the owners, modes and labels of the sidecars. There is no size to start from, so it is built
 * twice: a generous trial, then the blocks the trial used plus 1%.
 */
public object Ext4Image {
    /** e2fsdroid's file timestamp, the same as mkfs.erofs's (`-T`): the image does not change with the clock. */
    public const val TIMESTAMP: String = "1640995200"

    private const val SPARE_INODES = 64
    private const val SPARE_BLOCKS = 64
    private const val TRIAL_SLACK = 32L * 1024 * 1024
    private const val PERCENT = 100

    /**
     * `sh -c SIDECARS_SCRIPT sh <fs_config> <file_contexts> <out dir> <partition>`: copies of the sidecars as
     * `<out>/fs_config` and `<out>/file_contexts` with a line for `lost+found`, which mke2fs creates and
     * e2fsdroid refuses to label otherwise (root's owner and label, mode 700).
     */
    public const val SIDECARS_SCRIPT: String = """
        set -e
        p="$4"
        cp "$1" "$3/fs_config"; cp "$2" "$3/file_contexts"
        if [ "${'$'}p" = system ]; then r=/; lf='/lost\+found'; k=lost+found; else r=/${'$'}p; lf="/${'$'}p/lost\\+found"; k=${'$'}p/lost+found; fi
        l=${'$'}(awk -v r="${'$'}r" '${'$'}1==r{print ${'$'}2; exit}' "$3/file_contexts")
        printf '%s %s\n' "${'$'}lf" "${'$'}{l:-u:object_r:system_file:s0}" >> "$3/file_contexts"
        printf '%s 0 0 700 capabilities=0x0\n' "${'$'}k" >> "$3/fs_config"
    """

    /** `sh -c MEASURE_SCRIPT sh <tree>`: `<bytes> <entries>` of the tree. */
    public const val MEASURE_SCRIPT: String =
        "printf '%s %s\\n' \"\$(du -sb --apparent-size \"\$1\" | cut -f1)\" \"\$(find \"\$1\" | wc -l)\""

    public data class Measure(val bytes: Long, val entries: Long) {
        public val inodes: Long get() = entries + SPARE_INODES
    }

    public fun parseMeasure(output: String): Measure? = output.trim().split(' ').takeIf { it.size == 2 }?.let {
        val bytes = it[0].toLongOrNull() ?: return null
        val entries = it[1].toLongOrNull() ?: return null
        Measure(bytes, entries)
    }

    /** The trial's blocks: the content, 10% more, a block per entry, and 32 MiB of filesystem overhead. */
    public fun trialBlocks(measure: Measure, blockSize: Int): Long =
        (measure.bytes + measure.bytes / 10 + measure.entries * blockSize + TRIAL_SLACK) / blockSize

    /** The final blocks: what the trial used, 1% more, and a few spare. */
    public fun finalBlocks(trialBlocks: Long, freeBlocks: Long): Long {
        val used = trialBlocks - freeBlocks
        return used + used / PERCENT + SPARE_BLOCKS
    }

    /** `Free blocks:` of `dumpe2fs -h`. */
    public fun parseFreeBlocks(dumpe2fs: String): Long? =
        dumpe2fs.lines().firstOrNull { it.startsWith("Free blocks:") }?.substringAfter(':')?.trim()?.toLongOrNull()

    /** `mke2fs` for a read-only image of [p] mounted at [mountPoint] (`/` for system). */
    public fun mke2fsArgs(p: String, mountPoint: String, blockSize: Int, inodes: Long, img: String, blocks: Long) =
        listOf(
            "-q", "-t", "ext4", "-O", "^has_journal", "-b", blockSize.toString(), "-m", "0",
            "-L", p, "-M", mountPoint, "-N", inodes.toString(), "-I", "256", img, blocks.toString(),
        )

    /** `e2fsdroid` filling [img] from [tree] with the sidecar copies in [sidecars]. */
    public fun e2fsdroidArgs(mountPoint: String, sidecars: String, tree: String, img: String) = listOf(
        "-e", "-T", TIMESTAMP, "-C", "$sidecars/fs_config", "-S", "$sidecars/file_contexts",
        "-a", mountPoint, "-f", tree, img,
    )
}

/**
 * A read-only F2FS image built the way AOSP's `mkf2fsuserimg --readonly` does: `make_f2fs -O ro` on a sized file,
 * `sload_f2fs` fills it from the tree, then it is rebuilt at the "Max image size" sload's check reports. Not with
 * `-g android`, which forces the root to 0:0 (vendor's root is 0:2000).
 */
public object F2fsImage {
    private const val MIB = 1024L * 1024
    private const val MIN_MIB = 32L
    private const val SPARE_MIB = 16L
    private const val PERCENT = 100
    private const val BLOCK = 4096
    private val MAX_IMAGE = Regex("""Max image size: (\d+) MB""")

    /**
     * `sh -c PREPARE_SCRIPT sh <fs_config> <file_contexts> <out dir> <partition> <tree>`: `<out>/file_contexts`, a
     * copy with a line for the path sload labels the root by (the mount point twice: `/vendor/vendor`, `//`), and
     * prints `<bytes> <entries> <root uid>:<root gid>`.
     */
    public const val PREPARE_SCRIPT: String = """
        set -e
        p="$4"
        if [ "${'$'}p" = system ]; then m=/; k='^ '; else m=/${'$'}p; k='^/ '; fi
        l=${'$'}(awk -v r="${'$'}m" '${'$'}1==r{print ${'$'}2; exit}' "$2")
        cp "$2" "$3/file_contexts"
        printf '%s %s\n' "${'$'}m${'$'}m" "${'$'}{l:-u:object_r:system_file:s0}" >> "$3/file_contexts"
        o=${'$'}(grep -m1 "${'$'}k" "$1" | awk '{print ${'$'}(NF-3)":"${'$'}(NF-2)}')
        printf '%s %s %s\n' "${'$'}(du -sb --apparent-size "$5" | cut -f1)" "${'$'}(find "$5" | wc -l)" "${'$'}{o:-0:0}"
    """

    /**
     * `fakeroot sh -c READBACK_SCRIPT sh <dump.f2fs> <image> <dir> [keep]`: dumps the image into `<dir>` (owners,
     * modes, labels kept by fakeroot, so everything runs in this one session), prints `uid gid mode path` per entry
     * (`L` for a symlink's mode: a Linux link has none), a `#--` line, the getfattr labels; then removes `<dir>`
     * unless `keep` (Extract keeps it as the partition's tree).
     */
    public const val READBACK_SCRIPT: String = """
        set -e
        rm -rf "$3"; mkdir -p "$3"
        "$1" -r -P -L -y -o "$3" "$2" >/dev/null
        find "$3" ! -type l -exec stat -c '%u %g %a %n' {} +
        find "$3" -type l -exec stat -c '%u %g L %n' {} +
        echo '#--'
        getfattr -R -h -n security.selinux --absolute-names "$3" || true
        [ "$4" = keep ] || rm -rf "$3"
    """

    public data class Prepared(val measure: Ext4Image.Measure, val rootOwner: String)

    public fun parsePrepare(output: String): Prepared? = output.trim().split(' ').takeIf { it.size == 3 }?.let {
        val measure = Ext4Image.parseMeasure("${it[0]} ${it[1]}") ?: return null
        Prepared(measure, it[2])
    }

    /** The trial's bytes: as generous as ext4's trial. */
    public fun trialBytes(measure: Ext4Image.Measure): Long = Ext4Image.trialBlocks(measure, BLOCK) * BLOCK

    public fun parseMaxImageMib(sload: String): Long? = MAX_IMAGE.find(sload)?.groupValues?.get(1)?.toLongOrNull()

    /**
     * The final bytes: sload's max image size, 1% and 16 MiB more (AOSP's +6 MiB fails on vendor: the smaller
     * volume has less main area), at least 32 MiB (make_f2fs refuses a smaller volume).
     */
    public fun finalBytes(maxMib: Long): Long = maxOf(maxMib + maxMib / PERCENT + SPARE_MIB, MIN_MIB) * MIB

    public fun makeArgs(rootOwner: String, img: String): List<String> =
        listOf("-f", "-w", BLOCK.toString(), "-O", "ro", "-R", rootOwner, "-T", Ext4Image.TIMESTAMP, "-q", img)

    public fun sloadArgs(fsConfig: String, contexts: String, mountPoint: String, tree: String, img: String) = listOf(
        "-C", fsConfig, "-s", contexts, "-t", mountPoint, "-T", Ext4Image.TIMESTAMP, "-f", tree, img,
    )

    /**
     * [READBACK_SCRIPT]'s output as the stat and getfattr dumps; a symlink gets the mode [expectedFsConfig] asks,
     * else 777 (a stock image read by Extract has no fs_config to ask).
     */
    public fun splitReadback(output: String, p: String, dir: String, expectedFsConfig: String): Pair<String, String> {
        val stat = output.substringBefore("\n#--")
        val labels = output.substringAfter("#--\n", "")
        val modes = expectedFsConfig.lines().mapNotNull { line ->
            val parts = line.split(' ')
            if (parts.size < 5) null else parts.dropLast(4).joinToString(" ") to parts[parts.size - 2]
        }.toMap()
        val fixed = stat.lines().joinToString("\n") { line ->
            val parts = line.split(' ', limit = 4)
            if (parts.size < 4 || parts[2] != "L") return@joinToString line
            val rel = parts[3].removePrefix(dir).trim('/')
            val mode = modes[SidecarNormalizer.fsConfigPath(p, rel)] ?: "777"
            "${parts[0]} ${parts[1]} $mode ${parts[3]}"
        }
        return fixed to labels
    }
}
