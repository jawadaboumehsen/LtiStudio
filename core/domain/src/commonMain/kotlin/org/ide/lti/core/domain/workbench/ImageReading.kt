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

import org.ide.lti.core.domain.pipeline.text.SidecarNormalizer
import org.ide.lti.core.domain.pipeline.toolkit.F2fsImage
import org.ide.lti.core.domain.pipeline.toolkit.FormatDetector
import org.ide.lti.core.domain.pipeline.toolkit.FsType
import org.ide.lti.core.domain.pipeline.toolkit.FsTypes
import org.ide.lti.core.domain.pipeline.toolkit.ImageTreeEntry
import org.ide.lti.core.domain.pipeline.toolkit.Refusal
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException
import org.ide.lti.core.domain.pipeline.toolkit.Superblock

/** What an image in the workspace holds, as its first bytes say. */
public enum class ImageKind { Erofs, Ext4, F2fs, Super, Sparse }

/** An image the browser can open read-only: a partition image or a super image. */
public data class WorkspaceImage(val path: String, val sizeBytes: Long, val kind: ImageKind)

/** Folder for the copies a read needs (a sparse image expanded, a partition taken out of super): never the project. */
internal const val BROWSE_CACHE = "workbench/browse"

private const val SPARSE_MAGIC_LE = "3aff26ed"
private const val LP_GEOMETRY_MAGIC_LE = "67446c61"
private const val SECTION = "\n#--\n"
private val WHITESPACE = Regex("""\s+""")
private const val OWNER_FIELDS = 4
private const val UID = 1
private const val GID = 2
private const val MODE = 3
private const val CAPABILITIES = 4
private const val HEX = 16
private const val OCTAL = 8
internal const val NO_LABEL = "(no label)"

/**
 * `sh -c LIST_SCRIPT sh <workspace>`: per image, `path \t size \t first 4 bytes \t 64 bytes at 1024 \t 4 at 4096`
 * (hex), for the regular files (not links) where the workspace keeps images.
 */
private const val LIST_SCRIPT = """
    cd "$1" || exit 0
    for f in firmware/extracted/*.img firmware/raw/*.img firmware/replacements/*.img out/images/*.img out/extra/*.img; do
      [ -f "${'$'}f" ] && [ ! -L "${'$'}f" ] || continue
      printf '%s\t%s\t%s\t%s\t%s\n' "${'$'}f" "$(stat -c %s "${'$'}f")" "$(xxd -p -l 4 "${'$'}f")" \
        "$(xxd -p -s 1024 -l 64 "${'$'}f" | tr -d '\n')" "$(xxd -p -s 4096 -l 4 "${'$'}f")"
    done
"""

/**
 * `sh -c READ_SCRIPT sh <erofsfuse|fuse2fs> <image> <mount> <erofs|ext4> <find -printf format>`: mounts the image
 * read-only, prints the listing in that format, `#--`, Extract's stat dump, `#--` and the getfattr labels and
 * capabilities, then unmounts. Nothing is copied.
 */
private const val READ_SCRIPT = """
    set -e
    m="$1"; img="$2"; mnt="$3"
    mkdir -p "${'$'}mnt"
    if [ "$4" = ext4 ]; then "${'$'}m" "${'$'}img" "${'$'}mnt" -o ro,fakeroot >&2; else "${'$'}m" "${'$'}img" "${'$'}mnt" >&2; fi
    trap 'fusermount3 -u "${'$'}mnt" 2>/dev/null; rmdir "${'$'}mnt" 2>/dev/null' EXIT
    find "${'$'}mnt" -mindepth 1 -printf "$5"
    echo '#--'
    find "${'$'}mnt" -exec stat -c '%u %g %a %n' {} +
    echo '#--'
    getfattr -R -h -n security.selinux --absolute-names "${'$'}mnt"
    getfattr -R -h -n security.capability -e hex --absolute-names "${'$'}mnt" 2>/dev/null || true
"""

/**
 * `sh -c COPY_SCRIPT sh <erofsfuse|fuse2fs> <image> <mount> <erofs|ext4> <dest dir> <path>...`: mounts the image
 * read-only and copies each path (contents, links, times) to `<dest dir>/<path>`, replacing an earlier copy.
 */
private const val COPY_SCRIPT = """
    set -e
    m="$1"; img="$2"; mnt="$3"; fs="$4"; dest="$5"; INSIDE="$6"; shift 6
    mkdir -p "${'$'}mnt"
    if [ "${'$'}fs" = ext4 ]; then "${'$'}m" "${'$'}img" "${'$'}mnt" -o ro,fakeroot >&2; else "${'$'}m" "${'$'}img" "${'$'}mnt" >&2; fi
    trap 'fusermount3 -u "${'$'}mnt" 2>/dev/null; rmdir "${'$'}mnt" 2>/dev/null' EXIT
    for p; do sh -c "${'$'}INSIDE" sh "${'$'}mnt" "${'$'}dest" "${'$'}p"; done
"""

/**
 * `sh -c COPY_ONE sh <tree> <dest dir> <path>`: copies `<tree>/<path>` to `<dest dir>/<path>`, replacing an earlier
 * copy, when no link on the way (in the tree or in the destination) leads out of either; exit 3 otherwise.
 */
internal const val COPY_ONE: String = """
    set -e
    root=${'$'}(realpath -m "$1"); out=${'$'}(realpath -m "$2"); p="$3"
    from=${'$'}(realpath -m "${'$'}(dirname "$1/${'$'}p")"); to=${'$'}(realpath -m "${'$'}(dirname "$2/${'$'}p")")
    case "${'$'}from/" in "${'$'}root"/*) ;; *) echo "${'$'}p: a link on its way leaves the image" >&2; exit 3 ;; esac
    case "${'$'}to/" in "${'$'}out"/*) ;; *) echo "${'$'}p: a link on its way leaves the export folder" >&2; exit 3 ;; esac
    mkdir -p "${'$'}(dirname "$2/${'$'}p")"; rm -rf "$2/${'$'}p"
    cp -a -T "$1/${'$'}p" "$2/${'$'}p"
"""

/**
 * `sh -c HASH_SCRIPT sh <erofsfuse|fuse2fs> <image> <mount> <erofs|ext4> <list file>`: mounts the image read-only and
 * prints sha256sum's lines for the paths listed (one per line) in the file, relative to the image root. The shell
 * leaves the mount before unmounting it.
 */
private const val HASH_SCRIPT = """
    set -e
    m="$1"; img="$2"; mnt="$3"
    mkdir -p "${'$'}mnt"
    if [ "$4" = ext4 ]; then "${'$'}m" "${'$'}img" "${'$'}mnt" -o ro,fakeroot >&2; else "${'$'}m" "${'$'}img" "${'$'}mnt" >&2; fi
    trap 'cd /; fusermount3 -u "${'$'}mnt" 2>/dev/null; rmdir "${'$'}mnt" 2>/dev/null' EXIT
    cd "${'$'}mnt"
    tr '\n' '\000' < "$5" | xargs -0 -r sha256sum --
"""

/** `sh -c TREE_HASH_SCRIPT sh <folder> <list file>`: sha256sum's lines for the listed paths of the folder. */
internal const val TREE_HASH_SCRIPT: String = "cd \"\$1\" && tr '\\n' '\\000' < \"\$2\" | xargs -0 -r sha256sum --"

/** The listing the browser reads: `type \t size \t link \t path`. */
private const val BROWSER_LISTING = "%y\\t%s\\t%l\\t%P\\n"

/** Where an image sits, in words: the stock firmware, the user's own, or a build's. */
public fun imageOrigin(path: String): String = when (path.substringBeforeLast('/')) {
    "firmware/extracted" -> "Stock firmware"
    "firmware/raw" -> "Firmware package"
    "firmware/replacements" -> "Your images"
    "out/images", "out/extra" -> "Last build"
    else -> path.substringBeforeLast('/')
}

/** The images of the workspace the browser can open, by folder then name. */
internal suspend fun WorkbenchRunner.Job.workspaceImages(): List<WorkspaceImage> =
    sh(LIST_SCRIPT, root).lineSequence().mapNotNull { line ->
        val f = line.split('\t').takeIf { it.size == 5 } ?: return@mapNotNull null
        val kind = when {
            f[2] == SPARSE_MAGIC_LE -> ImageKind.Sparse

            f[4] == LP_GEOMETRY_MAGIC_LE -> ImageKind.Super

            else -> when (FsTypes.fromSuperblock(FormatDetector.fromHex(f[3]))) {
                FsType.Erofs -> ImageKind.Erofs
                FsType.Ext4 -> ImageKind.Ext4
                FsType.F2fs -> ImageKind.F2fs
                else -> return@mapNotNull null
            }
        }
        WorkspaceImage(f[0], f[1].toLongOrNull() ?: 0L, kind)
    }.toList()

/** A name for [image] inside [BROWSE_CACHE]: its workspace path with `/` as `_`. */
private fun cacheName(image: String) = image.replace('/', '_').removeSuffix(".img")

/**
 * [image] as a raw image to read: itself, or a raw copy of a sparse one in [BROWSE_CACHE] (made once, again only
 * when the image is newer); with [member], that partition taken out of the (raw) super image with lpunpack.
 */
internal suspend fun WorkbenchRunner.Job.readableImage(image: String, member: String?): String {
    require(".." !in image.split('/') && !image.startsWith("/")) { "Pick an image inside the workspace." }
    requireFolderInside("$BROWSE_CACHE/x", "workbench")
    run("mkdir", "-p", abs(BROWSE_CACHE))
    var raw = image
    if (run("xxd", "-p", "-l", "4", abs(image)).trim() == SPARSE_MAGIC_LE) {
        raw = "$BROWSE_CACHE/${cacheName(image)}.raw.img"
        requireRoomToExpand(image, raw)
        sh(
            "s=$(stat -c '%s %Y %Z %i' \"$1\"); [ -f \"$2\" ] && " +
                "[ \"$(cat \"$2.src\" 2>/dev/null)\" = \"${'$'}s\" ] && exit 0; rm -f \"$2.partial\" \"$2.src\"; " +
                "\"$3\" \"$1\" \"$2.partial\" >&2 && " +
                "mv -f -T \"$2.partial\" \"$2\" && printf '%s' \"${'$'}s\" > \"$2.src\"",
            abs(image),
            abs(raw),
            toolPath("simg2img"),
        )
    }
    if (member == null) return raw
    require(member.matches(PARTITION_NAME)) { "'$member' is not a partition name." }
    val parts = "$BROWSE_CACHE/${cacheName(image)}.parts"
    sh(
        "s=$(stat -c '%s %Y %Z %i' \"$1\"); [ -f \"$2/$4.img\" ] && " +
            "[ \"$(cat \"$2/$4.img.src\" 2>/dev/null)\" = \"${'$'}s\" ] && exit 0; mkdir -p \"$2\"; " +
            "rm -f \"$2/$4.img\" \"$2/$4.img.src\"; \"$3\" -p \"$4\" \"$1\" \"$2\" >&2 && " +
            "printf '%s' \"${'$'}s\" > \"$2/$4.img.src\"",
        abs(raw),
        abs(parts),
        toolPath("lpunpack"),
        member,
    )
    return "$parts/$member.img"
}

private val PARTITION_NAME = Regex("[A-Za-z0-9_]{1,64}")

/**
 * Refuses to expand the sparse [image] when the workspace's disk has less free space than its raw size (simg2img
 * leaves the empty blocks as holes, so it usually takes less). An expansion made before is reused, so it needs none.
 */
private suspend fun WorkbenchRunner.Job.requireRoomToExpand(image: String, raw: String) {
    if (exists(raw)) return
    // Sparse header: block size (u32 at 12) and block count (u32 at 16), little-endian.
    val header = FormatDetector.fromHex(run("xxd", "-p", "-s", "12", "-l", "8", abs(image)))
    fun u32(at: Int) = (0 until 4).fold(0L) { acc, i -> acc or ((header[at + i].toLong() and 0xFF) shl (8 * i)) }
    val rawBytes = u32(0) * u32(4)
    val free = run("df", "--output=avail", "-B1", abs(BROWSE_CACHE)).lines().last { it.isNotBlank() }.trim().toLong()
    if (rawBytes > free) {
        throw RefusalException(
            Refusal(
                "${image.substringAfterLast('/')} is sparse: reading it means expanding it to ${rawBytes / GIB} GiB " +
                    "first, and the disk has ${free / GIB} GiB free.",
                image,
                "Free space (Workspace > Disk usage), or browse the partition images instead.",
            ),
        )
    }
}

private const val GIB = 1L shl 30

/** Whether the raw image [img] is a super image (its LP geometry at 4096). */
internal suspend fun WorkbenchRunner.Job.isSuperImage(img: String): Boolean =
    run("xxd", "-p", "-s", "4096", "-l", "4", abs(img)).trim() == LP_GEOMETRY_MAGIC_LE

/** The partition an image is of: [member], else its file name without `.img` and a slot suffix. */
internal fun partitionOf(image: String, member: String?): String =
    member ?: image.substringAfterLast('/').removeSuffix(".img").removeSuffix(".raw").replace(Regex("_[ab]$"), "")

/** The entries of the raw filesystem image [img] of [partition], read straight from the image. */
internal suspend fun WorkbenchRunner.Job.readImage(img: String, partition: String, fs: FsType): List<ImageTreeEntry> {
    val (listing, sidecars) = listImage(img, partition, fs, BROWSER_LISTING)
    return treeEntries(partition, listing, sidecars.fsConfigContent, sidecars.fileContextContent)
}

/**
 * The raw filesystem image [img] of [partition] read straight from the image: its listing in the find -printf
 * [format] and its sidecars (owner, mode, capabilities, labels) as Extract would write them.
 */
internal suspend fun WorkbenchRunner.Job.listImage(
    img: String,
    partition: String,
    fs: FsType,
    format: String,
): Pair<String, SidecarNormalizer.NormalizedSidecars> = when (fs) {
    FsType.Erofs, FsType.Ext4 -> {
        val mnt = abs("$BROWSE_CACHE/mnt")
        val mounter = toolPath(if (fs == FsType.Ext4) "fuse2fs" else "erofsfuse")
        val sections = sh(READ_SCRIPT, mounter, abs(img), mnt, fs.name.lowercase(), format).split(SECTION)
        sections[0] to SidecarNormalizer.fromDumps(
            partition,
            mnt,
            sections.getOrElse(1) { "" },
            sections.getOrElse(2) { "" },
        )
    }

    FsType.F2fs -> {
        // No F2FS driver to mount with: dump.f2fs copies it into the cache, in one fakeroot session.
        val tree = f2fsTree(img)
        val dump = toolPath("dump.f2fs")
        val out = run("fakeroot", "sh", "-c", F2fsImage.READBACK_SCRIPT, "sh", dump, abs(img), tree, "keep")
        val (stat, labels) = F2fsImage.splitReadback(out, partition, tree, expectedFsConfig = "")
        run("find", tree, "-mindepth", "1", "-printf", format) to
            SidecarNormalizer.fromDumps(partition, tree, stat, labels)
    }

    else -> throw RefusalException(
        Refusal(
            "${img.substringAfterLast('/')} is ${fs.name.lowercase()}, not a filesystem the browser reads.",
            img,
            "Pick an EROFS, ext4 or F2FS image, or a super image.",
        ),
    )
}

/** Where [listImage] dumps the F2FS image [img] (absolute): kept, so a later read or copy uses it. */
internal fun WorkbenchRunner.Job.f2fsTree(img: String): String = abs("$BROWSE_CACHE/${cacheName(img)}.f2fs")

/** sha256 of each of [paths] in the raw image [img], path -> hash; an F2FS image was read by [listImage] before. */
internal suspend fun WorkbenchRunner.Job.hashInImage(
    img: String,
    fs: FsType,
    paths: List<String>,
): Map<String, String> {
    if (paths.isEmpty()) return emptyMap()
    val list = "$BROWSE_CACHE/hash-list.txt"
    writeText(list, paths.joinToString("\n", postfix = "\n"))
    val out = when (fs) {
        FsType.Erofs, FsType.Ext4 -> sh(
            HASH_SCRIPT,
            toolPath(if (fs == FsType.Ext4) "fuse2fs" else "erofsfuse"),
            abs(img),
            abs("$BROWSE_CACHE/mnt"),
            fs.name.lowercase(),
            abs(list),
        )

        else -> sh(TREE_HASH_SCRIPT, f2fsTree(img), abs(list))
    }
    return out.lineSequence().mapNotNull(CompareWorkbench::checksumLine).toMap()
}

/** The filesystem of the raw image [rel], from its superblock. */
internal suspend fun WorkbenchRunner.Job.fsOfImage(rel: String): FsType = FsTypes.fromSuperblock(
    FormatDetector.fromHex(
        run("xxd", "-p", "-s", "${FsTypes.SUPERBLOCK_OFFSET}", "-l", "${FsTypes.SUPERBLOCK_BYTES}", abs(rel)),
    ),
)

/** What the superblock of the raw image [rel] says, or null when there is no such image. */
internal suspend fun WorkbenchRunner.Job.superblockOf(rel: String): Superblock? {
    if (!exists(rel)) return null
    val fs = fsOfImage(rel)
    val dump = when (fs) {
        FsType.Erofs -> tryRun("dump.erofs", abs(rel)).stdout
        FsType.Ext4 -> tryRun("dumpe2fs", "-h", abs(rel)).stdout
        FsType.F2fs -> tryRun("dump.f2fs", abs(rel)).stdout
        else -> ""
    }
    return Superblock(fs, run("stat", "-c", "%s", abs(rel)).trim().toLong(), Superblock.fields(fs, dump))
}

/**
 * Copies [paths] of the raw image [img] to `out/toolkit/<folder>/<path>`; F2FS from the cache [readImage] made.
 */
internal suspend fun WorkbenchRunner.Job.copyOutOfImage(img: String, fs: FsType, folder: String, paths: List<String>) =
    copyFromImage(img, fs, "out/toolkit/$folder", "out/toolkit", paths)

/** Copies [paths] of the raw image [img] to `<dest>/<path>`; [dest] must resolve inside [base]. */
internal suspend fun WorkbenchRunner.Job.copyFromImage(
    img: String,
    fs: FsType,
    dest: String,
    base: String,
    paths: List<String>,
) {
    paths.forEach {
        require(
            ".." !in it.split('/') && !it.startsWith("/"),
        ) { "This path leaves the export folder: $it" }
    }
    requireFolderInside("$dest/x", base)
    when (fs) {
        FsType.Erofs, FsType.Ext4 -> sh(
            COPY_SCRIPT,
            toolPath(if (fs == FsType.Ext4) "fuse2fs" else "erofsfuse"),
            abs(img),
            abs("$BROWSE_CACHE/mnt"),
            fs.name.lowercase(),
            abs(dest),
            COPY_ONE,
            *paths.toTypedArray(),
        )

        else -> paths.forEach { rel -> sh(COPY_ONE, f2fsTree(img), abs(dest), rel) }
    }
}

/** A tree listing (`type \t size \t link \t path`) with each entry's owner, mode, label and capabilities. */
internal fun treeEntries(
    partition: String,
    listing: String,
    fsConfig: String,
    fileContexts: String,
): List<ImageTreeEntry> {
    val owners = fsConfig.lineSequence().map { it.trim().split(WHITESPACE) }.filter { it.size >= OWNER_FIELDS }
        .associateBy { it[0] }
    val labels = fileContexts.lineSequence().map { it.trim().split(WHITESPACE) }.filter { it.size >= 2 }
        .associate { it[0] to it.last() }
    return listing.lineSequence().mapNotNull { line ->
        val fields = line.split('\t', limit = 4).takeIf { it.size == 4 } ?: return@mapNotNull null
        val (type, size, target) = fields
        val path = fields[3]
        val owner = owners[SidecarNormalizer.fsConfigPath(partition, path)]
        ImageTreeEntry(
            path = if (type == "d") "$path/" else path,
            size = if (type == "d") 0L else size.toLongOrNull() ?: 0L,
            mode = owner?.get(MODE)?.toIntOrNull(OCTAL) ?: 0,
            uid = owner?.get(UID)?.toIntOrNull() ?: 0,
            gid = owner?.get(GID)?.toIntOrNull() ?: 0,
            label = labels[SidecarNormalizer.escapeRegex(SidecarNormalizer.fileContextPath(partition, path))]
                ?: NO_LABEL,
            capabilities = owner?.getOrNull(CAPABILITIES)?.substringAfter("capabilities=0x", "")?.toLongOrNull(HEX)
                ?: 0L,
            target = target.takeIf { type == "l" },
            hasOwner = owner != null,
        )
    }.sortedBy { it.path }.toList()
}
