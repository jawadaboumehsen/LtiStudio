/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.workbench

import org.ide.lti.core.domain.pipeline.stages.FirmwareExtractionStage
import org.ide.lti.core.domain.pipeline.toolkit.ArchiveDiff
import org.ide.lti.core.domain.pipeline.toolkit.CodePackageDiff
import org.ide.lti.core.domain.pipeline.toolkit.FsType
import org.ide.lti.core.domain.pipeline.toolkit.Refusal
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException
import org.ide.lti.core.domain.pipeline.toolkit.Superblock
import org.ide.lti.core.domain.pipeline.toolkit.SuperblockRow
import org.ide.lti.core.domain.pipeline.toolkit.TreeCompare
import org.ide.lti.core.domain.pipeline.toolkit.TreeDiff

/**
 * One side of a comparison, in any workspace: a partition's tree (`firmware/extracted/<p>`, the stock firmware, or
 * `work/<p>`, the build) or an image read straight from its file (EROFS, ext4, F2FS; sparse or inside super too).
 */
public sealed interface CompareSide {
    public val workspaceId: String

    public data class Tree(override val workspaceId: String, val folder: String) : CompareSide

    public data class Image(override val workspaceId: String, val image: String, val member: String? = null) :
        CompareSide
}

/** The partition a side holds: the tree's folder name, or the image's (or super member's) name. */
public val CompareSide.partition: String
    get() = when (this) {
        is CompareSide.Tree -> folder.substringAfterLast('/')
        is CompareSide.Image -> partitionOf(image, member)
    }

/** What a workspace offers to compare: its partition trees and its images. */
public data class CompareSources(val trees: List<String>, val images: List<WorkspaceImage>)

/** Two sides compared: every changed path, the superblocks side by side, and how many files were hashed. */
public data class SideComparison(
    val a: CompareSide,
    val b: CompareSide,
    val diff: TreeDiff,
    val superblocks: List<SuperblockRow>,
    val hashedFiles: Int,
)

/** One path of a [SideComparison] looked at closely. */
public sealed interface EntryComparison {
    public val path: String

    /** Not a regular file on one side (added, removed, a folder or a link): the row says all there is. */
    public data class NoContent(override val path: String, val reason: String) : EntryComparison

    /** A text file: its line diff, or null when either side is too long to diff. */
    public data class Text(override val path: String, val lines: List<String>?) : EntryComparison

    /** A zip archive: its entries on each side. */
    public data class Archive(override val path: String, val entries: ArchiveDiff) : EntryComparison

    /** A JAR or APK: its entries, then its dex files and methods. */
    public data class Code(override val path: String, val entries: ArchiveDiff, val code: CodePackageDiff) :
        EntryComparison

    /** Any other file: sizes and hashes. */
    public data class Binary(
        override val path: String,
        val sizeA: Long,
        val sizeB: Long,
        val sha256A: String,
        val sha256B: String,
    ) : EntryComparison
}

/** A side read once: its entries, its own sidecars and the superblock of its image. */
internal class SideSnapshot(
    val entries: Map<String, TreeCompare.Entry>,
    val sidecars: TreeCompare.Sidecars,
    val superblock: Superblock?,
    /** For an image side: the raw image read, and its filesystem. */
    val image: Pair<String, FsType>? = null,
)

private val PARTITION_TREE = Regex("""(work|${FirmwareExtractionStage.EXTRACTED})/[A-Za-z0-9_]{1,64}""")

/** `find -printf` format of [TreeCompare.parseListing]: `type \t size \t mtime \t path \t link target`. */
private const val COMPARE_LISTING = "%y\\t%s\\t%T@\\t%P\\t%l\\n"

/**
 * `sh -c SOURCES_SCRIPT sh <workspace>`: the partition trees of a workspace, the stock ones (with their sidecars in
 * firmware/extracted) and the build's (with theirs in work/configs).
 */
private const val SOURCES_SCRIPT = """
    cd "$1" || exit 0
    for d in firmware/extracted/*/ work/*/; do
      d="${'$'}{d%/}"; p="${'$'}{d##*/}"
      case "${'$'}d" in work/*) c=work/configs ;; *) c=firmware/extracted ;; esac
      [ -d "${'$'}d" ] && [ ! -L "${'$'}d" ] && [ -f "${'$'}c/fs_config-${'$'}p" ] && echo "${'$'}d"
    done
    exit 0
"""

/** A side's folder or image path, checked: the trees and images a workspace keeps, nothing else. */
internal fun requireSide(side: CompareSide) {
    when (side) {
        is CompareSide.Tree -> require(side.folder.matches(PARTITION_TREE)) {
            "Pick a partition tree (firmware/extracted/<p> or work/<p>)."
        }

        is CompareSide.Image -> require(".." !in side.image.split('/') && !side.image.startsWith("/")) {
            "Pick an image inside the workspace."
        }
    }
}

internal suspend fun WorkbenchRunner.Job.compareSources(): CompareSources = CompareSources(
    trees = sh(SOURCES_SCRIPT, root).lines().filter(String::isNotBlank).sorted(),
    images = workspaceImages(),
)

/** [side] read: a tree with its sidecars from where Extract or Assemble keeps them; an image from its file. */
internal suspend fun WorkbenchRunner.Job.snapshot(side: CompareSide): SideSnapshot {
    requireSide(side)
    val p = side.partition
    return when (side) {
        is CompareSide.Tree -> {
            if (!exists(side.folder)) throw RefusalException(Refusal("There is no such tree.", side.folder, RUN_FIRST))
            val stock = side.folder.startsWith(FirmwareExtractionStage.EXTRACTED)
            val configs = if (stock) FirmwareExtractionStage.EXTRACTED else "work/configs"
            SideSnapshot(
                entries = TreeCompare.parseListing(sh(TreeCompare.LISTING_SCRIPT, abs(side.folder))),
                sidecars = TreeCompare.Sidecars(
                    p,
                    readText("$configs/fs_config-$p").orEmpty(),
                    readText("$configs/file_context-$p").orEmpty(),
                ),
                // The tree's own image: the stock one, or the one the last build made from work/.
                superblock = superblockOf(
                    if (stock) "${FirmwareExtractionStage.EXTRACTED}/$p.img" else "out/images/$p.img",
                ),
            )
        }

        is CompareSide.Image -> {
            val img = readableImage(side.image, side.member)
            if (isSuperImage(img)) {
                throw RefusalException(
                    Refusal("${side.image} is a super image.", side.image, "Pick one of the partitions inside it."),
                )
            }
            val fs = fsOfImage(img)
            val (listing, sidecars) = listImage(img, p, fs, COMPARE_LISTING)
            SideSnapshot(
                entries = TreeCompare.parseListing(listing),
                sidecars = TreeCompare.Sidecars(p, sidecars.fsConfigContent, sidecars.fileContextContent),
                superblock = superblockOf(img),
                image = img to fs,
            )
        }
    }
}

/** sha256 of [paths] on [side] as [snapshot] read it, path -> hash. */
internal suspend fun WorkbenchRunner.Job.hashSide(
    side: CompareSide,
    snapshot: SideSnapshot,
    paths: List<String>,
): Map<String, String> {
    val image = snapshot.image
    return when {
        image != null -> hashInImage(image.first, image.second, paths)
        paths.isEmpty() -> emptyMap()
        else -> hashTree((side as CompareSide.Tree).folder, paths)
    }
}

private suspend fun WorkbenchRunner.Job.hashTree(folder: String, paths: List<String>): Map<String, String> {
    requireFolderInside("$BROWSE_CACHE/x", "workbench")
    run("mkdir", "-p", abs(BROWSE_CACHE))
    val list = "$BROWSE_CACHE/hash-list.txt"
    writeText(list, paths.joinToString("\n", postfix = "\n"))
    return sh(TREE_HASH_SCRIPT, abs(folder), abs(list)).lineSequence()
        .mapNotNull(CompareWorkbench::checksumLine).toMap()
}

/**
 * [path] of [side] as a regular file this job (any workspace) can read: the tree's own file, the F2FS dump's, or a
 * copy taken out of the image into `workbench/compare/<tag>`; null when it is not a regular file there.
 */
internal suspend fun WorkbenchRunner.Job.fileOf(side: CompareSide, path: String, tag: String): String? {
    requireSide(side)
    require(".." !in path.split('/') && !path.startsWith("/")) { "'$path' is not a path inside the partition." }
    val file = when (side) {
        is CompareSide.Tree -> abs("${side.folder}/$path")

        is CompareSide.Image -> {
            val img = readableImage(side.image, side.member)
            when (val fs = fsOfImage(img)) {
                FsType.F2fs -> {
                    if (!exists(f2fsTree(img))) listImage(img, side.partition, fs, COMPARE_LISTING)
                    "${f2fsTree(img)}/$path"
                }

                else -> {
                    val dest = "workbench/compare/$tag"
                    requireFolderInside("$dest/x", "workbench")
                    run("rm", "-rf", abs(dest))
                    copyFromImage(img, fs, dest, "workbench", listOf(path))
                    abs("$dest/$path")
                }
            }
        }
    }
    return file.takeIf { tryRun("sh", "-c", "[ -f \"$1\" ] && [ ! -L \"$1\" ]", "sh", it).exitCode == 0 }
}

private const val RUN_FIRST = "Run the build through Extract (stock trees) or Assemble (the build's trees) first."
