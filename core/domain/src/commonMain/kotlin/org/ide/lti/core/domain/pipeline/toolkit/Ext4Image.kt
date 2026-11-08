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

    /** mkfs.erofs's `-z` value meaning "no compression". */
    public const val NO_COMPRESSION: String = "none"

    /** [p]'s filesystem: its own choice, else the build's default. */
    public fun of(build: BuildSettings, p: String): String =
        (build.partitionFilesystem[p] ?: build.filesystem).lowercase()

    /** The compressors of `mkfs.erofs -z`, with the levels each takes (`lz4` takes none). */
    private val COMPRESSOR_LEVELS: Map<String, Set<Int>> = mapOf(
        "lz4" to emptySet(),
        "lz4hc" to (0..12).toSet(),
        "lzma" to ((0..9) + (100..109)).toSet(),
        "deflate" to (0..9).toSet(),
        "zstd" to (0..22).toSet(),
    )

    /** The levels Build & Sign offers for [compressor] (empty: it takes none), lowest to highest. */
    public fun levels(compressor: String): List<Int> = when (compressor) {
        // 0 is lz4hc's own default; 1-12 as the tool names its levels.
        "lz4hc" -> (1..12).toList()
        "lzma" -> (0..9).toList()
        "deflate" -> (1..9).toList()
        "zstd" -> (1..22).toList()
        else -> emptyList()
    }

    /** mkfs.erofs's own level for [compressor] when none is given: picking the compressor starts there. */
    public fun defaultLevel(compressor: String): Int? = when (compressor) {
        "lz4hc" -> 9
        "lzma" -> 6
        "deflate" -> 1
        "zstd" -> 3
        else -> null
    }

    /** Why [value] cannot be a `-z` value, or null when it can. */
    public fun compressionProblem(value: String): String? {
        val name = value.substringBefore(',')
        val level = value.substringAfter(',', "").removePrefix("level=")
        return when {
            value == NO_COMPRESSION -> null

            name !in COMPRESSOR_LEVELS -> "Unknown compression '$value': use ${COMPRESSOR_LEVELS.keys.joinToString()}"

            ',' in value && level.toIntOrNull() !in COMPRESSOR_LEVELS.getValue(name) ->
                "Compression level '$level' is not one $name takes"

            else -> null
        }
    }

    /** mkfs.erofs's `-z` for [p] (`lz4hc,9`, `lz4`, [NO_COMPRESSION]): its own choice, else the build's. */
    public fun erofsCompression(build: BuildSettings, p: String): String =
        build.partitionCompression[p] ?: build.level?.let { "${build.compression.substringBefore(',')},$it" }
            ?: build.compression

    /** The first kernel that reads EROFS data deduplication and fragments (`-Ededupe,fragments`). */
    public val DEDUPE_KERNEL: Pair<Int, Int> = 6 to 1

    /** `6.6.92-android15-8` as (6, 6); null when it does not start with two numbers. */
    public fun kernelRelease(version: String?): Pair<Int, Int>? = version?.let(RELEASE::find)
        ?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }

    private val RELEASE = Regex("""^(\d+)\.(\d+)""")

    /** Whether [version] (a kernel release) reads deduplicated EROFS images: 6.1 or newer. */
    public fun kernelDedupes(version: String?): Boolean = kernelRelease(version)?.let { (major, minor) ->
        major > DEDUPE_KERNEL.first || (major == DEDUPE_KERNEL.first && minor >= DEDUPE_KERNEL.second)
    } == true

    /**
     * Whether [p]'s EROFS image is built with `-Ededupe,fragments` (identical data stored once; vendor shrinks about a
     * quarter, the build is much slower): its own choice, else on exactly when the boot kernel ([kernelVersion]) reads
     * it. Uncompressed images are never deduplicated (mkfs.erofs dedupes compressed data only).
     */
    public fun erofsDedupe(build: BuildSettings, p: String, kernelVersion: String?): Boolean =
        erofsCompression(build, p) != NO_COMPRESSION && (build.partitionDedupe[p] ?: kernelDedupes(kernelVersion))

    /** The cluster sizes Build & Sign offers besides one block (`BuildSettings.partitionClusterKib`), in KiB. */
    public val CLUSTER_KIB: List<Int> = listOf(16, 64, 128)

    /** `mkfs.erofs -C` for [p]: its cluster in bytes, or null for one block (uncompressed images have none). */
    public fun erofsCluster(build: BuildSettings, p: String): Long? =
        build.partitionClusterKib[p]?.takeIf { erofsCompression(build, p) != NO_COMPRESSION }?.let { it * KIB }

    private const val KIB = 1024L

    /**
     * The kernel decoder [p]'s EROFS image needs (`lz4`, `lzma`, `deflate`, `zstd`), as `KernelFacts.erofsCompressions`
     * names them; null when it is not compressed. lz4hc is written for the lz4 decoder.
     */
    public fun erofsDecoder(build: BuildSettings, p: String): String? =
        erofsCompression(build, p).substringBefore(',').lowercase().takeUnless { it == NO_COMPRESSION }
            ?.let { if (it == "lz4hc") "lz4" else it }
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
        getfattr -R -h -n security.capability -e hex --absolute-names "$3" 2>/dev/null || true
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
     * Why [p] cannot be F2FS: sload_f2fs (1.16) applies fs_config owners and modes but never writes
     * `security.capability`, so files that need capabilities (vendor daemons such as cnd, imsdaemon) would lose
     * them. The two debug-only AOSP runners are not counted. Null when nothing in [fsConfig] has capabilities.
     */
    public fun capabilityRefusal(p: String, fsConfig: String): String? {
        val needing = fsConfig.lines().map { it.trim() }
            .filter { it.substringAfter("capabilities=", "0x0") != "0x0" }
            .map { it.substringBefore(' ') }
            .filterNot { it.endsWith("/bin/run-as") || it.endsWith("/bin/simpleperf_app_runner") }
        if (needing.isEmpty()) return null
        return "F2FS_CAPABILITIES: $p cannot be built as F2FS: sload_f2fs does not write file capabilities, which " +
            "${needing.size} file(s) need (${needing.take(
                CAPABILITY_NAMES,
            ).joinToString()}). Build $p as EROFS or ext4."
    }

    private const val CAPABILITY_NAMES = 5

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
