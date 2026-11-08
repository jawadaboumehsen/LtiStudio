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
import org.ide.lti.core.domain.pipeline.text.EntryKind
import org.ide.lti.core.domain.pipeline.text.SidecarNormalizer
import org.ide.lti.core.domain.pipeline.toolkit.CoverageFix
import org.ide.lti.core.domain.pipeline.toolkit.CoverageRule
import org.ide.lti.core.domain.pipeline.toolkit.F2fsImage
import org.ide.lti.core.domain.pipeline.toolkit.FsType
import org.ide.lti.core.domain.pipeline.toolkit.ImageInfo
import org.ide.lti.core.domain.pipeline.toolkit.ImageTree
import org.ide.lti.core.domain.pipeline.toolkit.LinkCheck
import org.ide.lti.core.domain.pipeline.toolkit.Refusal
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException
import org.ide.lti.core.domain.pipeline.toolkit.SuperLayout
import org.ide.lti.core.domain.pipeline.toolkit.SuperTool

/** A browsed partition: its entries and where its files are, as Windows sees them (to open in Explorer). */
public data class PartitionTree(val tree: ImageTree, val source: String, val hostFolder: String?)

/** An image opened read-only: the partitions of a super image, or a filesystem's files. */
public sealed interface OpenedImage {
    public data class Super(val layout: SuperLayout) : OpenedImage

    public data class Files(val tree: PartitionTree) : OpenedImage
}

/**
 * What the build records for one item: its device path, its fs_config line and SELinux label (null when it has
 * none yet), and, when one is missing, what the build gives it ([preview]).
 */
public data class EntryDetails(
    val devicePath: String,
    val fsConfigLine: String?,
    val label: String?,
    val preview: CoverageFix.Preview?,
    /** For a symbolic link: where it ends on the phone, said in words. */
    val resolves: String? = null,
)

/**
 * Partition images (FR-008..FR-012). A partition is browsed from the tree Extract unpacked from its image (or
 * the assembled `work/` tree, which carries the edits), with owner, mode and SELinux label from the sidecars
 * Extract read off the mounted image; nothing is guessed.
 */
public class ImageWorkbench(
    private val runner: WorkbenchRunner,
    /** A Windows path as WSL sees it (`C:\\system.img` -> `/mnt/c/system.img`). */
    private val toLinuxPath: (String) -> String = { it },
) {
    /**
     * Copies the user's own image for [partition] to `firmware/replacements/<p>.img`, raw
     * (a sparse one is expanded), and returns its SHA-256 for the Extract settings. EROFS, ext4 or F2FS: Extract
     * reads them with erofsfuse, fuse2fs and dump.f2fs.
     */
    public suspend fun importReplacement(workspaceId: String, partition: String, windowsPath: String): String =
        runner.job(workspaceId, "Import $partition image") {
            val source = toLinuxPath(windowsPath)
            val dest = FirmwareExtractionStage.replacementPath(partition)
            val partial = "$dest.partial"
            requireFolderInside(dest, "")
            run("mkdir", "-p", abs(FirmwareExtractionStage.REPLACEMENTS))
            // A leftover (or a link) under the side name is removed, never written through.
            run("rm", "-f", abs(partial))
            val sparse = run("xxd", "-p", "-l", "4", source).trim() == SPARSE_MAGIC_LE
            if (sparse) run("simg2img", source, abs(partial)) else run("cp", "-f", source, abs(partial))
            val fs = fsOfImage(partial)
            if (fs !in listOf(FsType.Erofs, FsType.Ext4, FsType.F2fs)) {
                run("rm", "-f", abs(partial))
                throw RefusalException(
                    Refusal(
                        "Only an EROFS, ext4 or F2FS image can replace a partition; this one is " +
                            "${fs.name.lowercase()}.",
                        windowsPath.substringAfterLast('\\'),
                        "Pick an EROFS, ext4 or F2FS image (a sparse one is fine).",
                    ),
                )
            }
            run("mv", "-f", "-T", abs(partial), abs(dest))
            run("sha256sum", abs(dest)).trim().substringBefore(' ')
        }

    public suspend fun tree(workspaceId: String, partition: String): PartitionTree = runner.read(workspaceId) {
        val (dir, configs) = when {
            exists("work/$partition") -> "work/$partition" to "work/configs"

            exists("${FirmwareExtractionStage.EXTRACTED}/$partition") ->
                "${FirmwareExtractionStage.EXTRACTED}/$partition" to FirmwareExtractionStage.EXTRACTED

            else -> throw RefusalException(
                Refusal("The $partition tree is not unpacked yet.", "$partition.img", "Run the build through Extract."),
            )
        }
        val listing = run("find", abs(dir), "-mindepth", "1", "-printf", "%y\\t%s\\t%l\\t%P\\n")
        val entries = treeEntries(
            partition,
            listing,
            readText("$configs/fs_config-$partition").orEmpty(),
            readText("$configs/file_context-$partition").orEmpty(),
        )
        PartitionTree(ImageTree(entries), dir, hostPath(dir))
    }

    /**
     * Entries of every partition in [partitions] whose name contains [query], as `<partition>/<path>` with owner,
     * mode and label. Partitions not unpacked yet are skipped; at most [SEARCH_LIMIT] hits.
     */
    public suspend fun search(workspaceId: String, partitions: List<String>, query: String): ImageTree {
        require(query.trim().length >= 2) { "Search for at least two characters." }
        val hits = partitions.flatMap { p ->
            val tree = try {
                tree(workspaceId, p)
            } catch (_: RefusalException) {
                return@flatMap emptyList()
            }
            tree.tree.entries
                .filter { it.path.trimEnd('/').substringAfterLast('/').contains(query.trim(), ignoreCase = true) }
                .map { it.copy(path = "$p/${it.path}") }
        }
        return ImageTree(hits.take(SEARCH_LIMIT))
    }

    /** Copies [path] of [partition]'s tree to `out/toolkit/<partition>/<path>`; returns it as Windows sees it. */
    public suspend fun extract(workspaceId: String, partition: String, path: String): String =
        extractMany(workspaceId, partition, listOf(path))

    /**
     * Copies each of [paths] of [partition]'s tree to `out/toolkit/<partition>/<path>`; returns
     * the copy of a single path, else the `out/toolkit/<partition>` folder, as Windows sees it.
     */
    public suspend fun extractMany(workspaceId: String, partition: String, paths: List<String>): String =
        runner.job(workspaceId, "Extract $partition") {
            val source = tree(workspaceId, partition).source
            val rels = paths.map { it.trimEnd('/') }.distinct()
            rels.forEach { rel ->
                val export = "out/toolkit/$partition/$rel"
                require(".." !in rel.split('/')) { "This path leaves the export folder: $rel" }
                requireFolderInside(export, "out/toolkit/$partition")
                sh(
                    // A second export replaces the first: files removed since do not linger in the copy.
                    "mkdir -p \"$(dirname \"$2\")\" && rm -rf \"$2\" && cp -a -T \"$1\" \"$2\"",
                    abs("$source/$rel"),
                    abs(export),
                )
            }
            val dest = "out/toolkit/$partition" + (rels.singleOrNull()?.let { "/$it" } ?: "")
            hostPath(dest) ?: abs(dest)
        }

    /**
     * Unpacks `firmware/extracted/<partition>.img` into its tree and sidecars again, as Extract
     * does: the user's own image first when Extract uses one for [partition] ([replacementSha256], checked).
     */
    public suspend fun reextract(workspaceId: String, partition: String, replacementSha256: String? = null): Unit =
        runner.job(workspaceId, "Re-extract $partition") {
            val extracted = FirmwareExtractionStage.EXTRACTED
            val image = "$extracted/$partition.img"
            requireFolderInside(image, "")
            replacementSha256?.let { expected ->
                val own = FirmwareExtractionStage.replacementPath(partition)
                val actual = run("sha256sum", abs(own)).trim().substringBefore(' ')
                if (actual != expected) {
                    throw RefusalException(
                        Refusal("$own is not the image imported for $partition.", own, "Import it again in Extract."),
                    )
                }
                run("cp", "-f", "--remove-destination", abs(own), abs(image))
            }
            // The user's own image was copied over the stock one; only a run of Extract brings the stock one back.
            val own = FirmwareExtractionStage.replacementPath(partition)
            if (replacementSha256 == null && exists(image) && exists(own) &&
                run("sha256sum", abs(image)).substringBefore(' ') == run("sha256sum", abs(own)).substringBefore(' ')
            ) {
                throw RefusalException(
                    Refusal(
                        "$partition.img is still your own image, not the stock one.",
                        image,
                        "Run the build through Extract to unpack the stock image again.",
                    ),
                )
            }
            if (!exists(image)) {
                throw RefusalException(
                    Refusal("$partition.img is not extracted yet.", image, "Run the build through Extract first."),
                )
            }
            val tree = abs("$extracted/$partition")
            val (prefix, stat, labels) = when (val fs = fsOfImage(image)) {
                FsType.F2fs -> {
                    val dump = toolPath("dump.f2fs")
                    val script = F2fsImage.READBACK_SCRIPT
                    val out = run("fakeroot", "sh", "-c", script, "sh", dump, abs(image), tree, "keep")
                    val (s, l) = F2fsImage.splitReadback(out, partition, tree, expectedFsConfig = "")
                    Triple(tree, s, l)
                }

                FsType.Erofs, FsType.Ext4 -> {
                    val mounter = if (fs == FsType.Ext4) "fuse2fs" else "erofsfuse"
                    val out = sh(UNPACK_SCRIPT, toolPath(mounter), abs(image), tree, fs.name.lowercase())
                    Triple("$tree.mnt", out.substringBefore("\n#--"), out.substringAfter("#--\n", ""))
                }

                else -> throw RefusalException(
                    Refusal(
                        "$partition.img is ${fs.name.lowercase()}, which Extract cannot read.",
                        image,
                        "Use an EROFS, ext4 or F2FS image.",
                    ),
                )
            }
            val sidecars = SidecarNormalizer.fromDumps(partition, prefix, stat, labels)
            writeText("$extracted/fs_config-$partition", sidecars.fsConfigContent)
            writeText("$extracted/file_context-$partition", sidecars.fileContextContent)
        }

    /** The superblock of [partition]'s stock image and of its last built one. */
    public suspend fun imageInfo(workspaceId: String, partition: String): ImageInfo = runner.read(workspaceId) {
        ImageInfo(
            stock = superblockOf("${FirmwareExtractionStage.EXTRACTED}/$partition.img"),
            built = superblockOf("out/images/$partition.img"),
        )
    }

    /** Converts a sparse image to raw, or raw to sparse, into `out/toolkit/` (FR-010). */
    public suspend fun convert(workspaceId: String, image: String, toSparse: Boolean): String =
        runner.job(workspaceId, "Convert ${image.substringAfterLast('/')}") {
            val name = image.substringAfterLast('/').removeSuffix(".img")
            val dest = "out/toolkit/$name.${if (toSparse) "sparse" else "raw"}.img"
            requireFolderInside(dest, "out/toolkit")
            run("mkdir", "-p", abs("out/toolkit"))
            // A side name until it is complete: a cancelled conversion never leaves an image that looks whole.
            run("rm", "-f", abs("$dest.partial"))
            run(if (toSparse) "img2simg" else "simg2img", abs(image), abs("$dest.partial"))
            run("mv", "-f", "-T", abs("$dest.partial"), abs(dest))
            dest
        }

    /**
     * Cuts [image] into parts of at most [maxMegabytes] (`<name>.img.parts/<name>.img.part00`, …; join them with `cat`)
     * (FR-010). These are byte parts, not sparse chunks: the tool groups build no `simg2simg`.
     */
    public suspend fun split(workspaceId: String, image: String, maxMegabytes: Int): List<String> =
        runner.job(workspaceId, "Split ${image.substringAfterLast('/')}") {
            require(maxMegabytes > 0) { "The part size must be positive." }
            val name = "${image.substringAfterLast('/')}.part"
            val partial = "out/toolkit/.$name.partial"
            val folder = "out/toolkit/${name}s"
            requireFolderInside(partial, "out/toolkit")
            requireFolderInside(folder, "out/toolkit")
            // Parts are made in a side folder that becomes the set in one rename: never a partial or mixed one.
            sh("rm -rf \"$1\" && mkdir -p \"$1\"", abs(partial))
            // Enough suffix digits for any part count (split stops at 100 with two).
            val bytes = run("stat", "-c", "%s", abs(image)).trim().toLong()
            val count = (bytes + maxMegabytes * MIB - 1) / (maxMegabytes * MIB)
            val digits = maxOf(2, count.toString().length)
            run("split", "-b", "${maxMegabytes}M", "-d", "-a", "$digits", abs(image), abs("$partial/$name"))
            val parts = run("find", abs(partial), "-maxdepth", "1", "-name", "$name*", "-printf", "%P\\n")
                .lines().filter { it.isNotBlank() }.sorted()
            sh("rm -rf \"$2\" && mv -T \"$1\" \"$2\"", abs(partial), abs(folder))
            parts.map { "$folder/$it" }
        }

    /** The EROFS, ext4, F2FS and super images in the workspace (firmware, the user's own, the built ones). */
    public suspend fun images(workspaceId: String): List<WorkspaceImage> = runner.read(
        workspaceId,
    ) { workspaceImages() }

    /**
     * Opens [image] read-only: a super image (also a sparse one, once expanded) gives the partitions inside it to
     * pick from; a filesystem image gives its files at once.
     */
    public suspend fun open(workspaceId: String, image: String): OpenedImage =
        runner.job(workspaceId, "Read ${image.substringAfterLast('/')}") {
            val img = readableImage(image, member = null)
            if (isSuperImage(img)) {
                OpenedImage.Super(SuperTool.parseLpdumpOutput(run("lpdump", abs(img))))
            } else {
                val entries = readImage(img, partitionOf(image, null), fsOfImage(img))
                OpenedImage.Files(PartitionTree(ImageTree(entries), "$image (read-only)", null))
            }
        }

    /**
     * [image]'s files with owner, mode, SELinux label and capabilities read straight off the image, without
     * unpacking it into the project; with [member], that partition of a super image. Only [BROWSE_CACHE] is written
     * (a sparse image expanded, a partition taken out of super, an F2FS image dumped).
     */
    public suspend fun browse(workspaceId: String, image: String, member: String? = null): PartitionTree =
        runner.job(workspaceId, "Read ${member ?: image.substringAfterLast('/')}") {
            val img = readableImage(image, member)
            val entries = readImage(img, partitionOf(image, member), fsOfImage(img))
            val name = member?.let { "$it in ${image.substringAfterLast('/')}" } ?: image
            PartitionTree(ImageTree(entries), "$name (read-only)", null)
        }

    /** Copies [paths] out of [image] (or its [member]) to `out/toolkit/<partition>/`; returns that folder. */
    public suspend fun extractFromImage(
        workspaceId: String,
        image: String,
        member: String?,
        paths: List<String>,
    ): String = runner.job(workspaceId, "Copy out of ${member ?: image.substringAfterLast('/')}") {
        val img = readableImage(image, member)
        val folder = partitionOf(image, member)
        copyOutOfImage(img, fsOfImage(img), folder, paths.map { it.trimEnd('/') }.distinct())
        hostPath("out/toolkit/$folder") ?: abs("out/toolkit/$folder")
    }

    /**
     * What the build records for [path] of [partition] in the tree at [source] (`work/<p>` or the extracted one):
     * its fs_config and SELinux lines, and for a missing one what the build adds and why ([coverageRules] first,
     * then the ROM's own rules, then Android's defaults or the folder).
     */
    public suspend fun details(
        workspaceId: String,
        partition: String,
        path: String,
        kind: EntryKind,
        source: String,
        coverageRules: String = "",
    ): EntryDetails = runner.read(workspaceId) {
        val tree = source.substringBeforeLast('/')
        val configs = if (tree == "work") "work/configs" else tree
        val clean = path.trim('/')
        val fsKey = SidecarNormalizer.fsConfigPath(partition, clean)
        val ctxKey = SidecarNormalizer.escapeRegex(SidecarNormalizer.fileContextPath(partition, clean))
        val fsText = readText("$configs/fs_config-$partition").orEmpty()
        val ctxText = readText("$configs/file_context-$partition").orEmpty()
        val fsLine = fsText.lineSequence().firstOrNull { it.substringBefore(' ') == fsKey }
        val ctxLine = ctxText.lineSequence().firstOrNull { it.substringBefore(' ') == ctxKey }
        val preview = if (fsLine == null || ctxLine == null) {
            CoverageFix.preview(
                ctxText,
                partition,
                clean,
                kind,
                CoverageRule.parse(coverageRules).first,
                platformMetadata(tree),
            )
        } else {
            null
        }
        val resolves = if (kind == EntryKind.Link) resolution(this, tree, partition, clean) else null
        EntryDetails(
            SidecarNormalizer.fileContextPath(partition, clean),
            fsLine,
            ctxLine?.substringAfter(' ')?.trim(),
            preview,
            resolves,
        )
    }

    /** Where the link [path] of [partition] ends, as the phone resolves it across the partitions of [tree]. */
    private suspend fun resolution(job: WorkbenchRunner.Job, tree: String, partition: String, path: String): String =
        with(job) {
            val parts = run("find", abs(tree), "-mindepth", "1", "-maxdepth", "1", "-type", "d", "-printf", "%f\\n")
                .lines().filter { it.isNotBlank() && it != "configs" && it != "kernel" }
            val listing = LinkCheck.parse(sh(LinkCheck.LISTING_SCRIPT, abs(tree), *parts.toTypedArray()))
            val device = if (partition == "system") "/$path" else "/$partition/$path"
            when (val end = LinkCheck.resolve(listing)[device]) {
                null -> "unknown (not a link of this tree)"

                LinkCheck.Ending.Loop -> "no: it loops back on itself"

                LinkCheck.Ending.Runtime -> "provided at boot, not on disk"

                is LinkCheck.Ending.InTree -> {
                    val missing = sh(LinkCheck.EXISTS_SCRIPT, abs(tree), end.treePath).isNotBlank()
                    // On the phone: system's tree is the root, every other partition is mounted on /<name>.
                    val onDevice = "/" + end.treePath.removePrefix("system/")
                    if (missing) "no: $onDevice is not in the tree" else "yes: $onDevice"
                }
            }
        }

    /** The groups, partitions and free space of the workspace's super image (FR-011), from `lpdump`. */
    public suspend fun superLayout(workspaceId: String): SuperLayout = runner.read(workspaceId) {
        val image = SUPER_CANDIDATES.firstOrNull { exists(it) }
            ?: throw RefusalException(
                Refusal("No super image in the workspace.", "super.img", "Run the build through Extract or Build."),
            )
        // lpdump reads raw images only: a sparse one (Build's sparse output) is expanded for the read and dropped.
        val sparse = run("xxd", "-p", "-l", "4", abs(image)).trim() == SPARSE_MAGIC_LE
        SuperTool.parseLpdumpOutput(
            if (sparse) {
                sh(
                    "t=$(mktemp) && trap 'rm -f \"\$t\"' EXIT && \"$1\" \"$3\" \"\$t\" >&2 && \"$2\" \"\$t\"",
                    toolPath("simg2img"),
                    toolPath("lpdump"),
                    abs(image),
                )
            } else {
                run("lpdump", abs(image))
            },
        )
    }

    private companion object {
        const val MIB = 1024L * 1024L

        /**
         * `sh -c UNPACK_SCRIPT sh <erofsfuse|fuse2fs> <image> <tree> <erofs|ext4>`: mounts the image on `<tree>.mnt`,
         * prints Extract's stat dump, `#--` and the getfattr labels, then copies it to `<tree>` (as Extract does:
         * content, links and times; owners and labels live in the sidecars) and unmounts.
         */
        const val UNPACK_SCRIPT = """
            set -e
            m="$1"; img="$2"; tree="$3"; mnt="$3.mnt"
            mkdir -p "${'$'}mnt"
            # The mounter's own output (erofsfuse prints its version) goes to stderr: stdout is the dump.
            if [ "$4" = ext4 ]; then "${'$'}m" "${'$'}img" "${'$'}mnt" -o ro,fakeroot >&2; else "${'$'}m" "${'$'}img" "${'$'}mnt" >&2; fi
            trap 'fusermount3 -u "${'$'}mnt" 2>/dev/null; rmdir "${'$'}mnt" 2>/dev/null' EXIT
            find "${'$'}mnt" -exec stat -c '%u %g %a %n' {} +
            echo '#--'
            getfattr -R -h -n security.selinux --absolute-names "${'$'}mnt"
            getfattr -R -h -n security.capability -e hex --absolute-names "${'$'}mnt" 2>/dev/null || true
            rm -rf "${'$'}tree"
            cp -r --no-dereference --preserve=links,timestamps -T "${'$'}mnt" "${'$'}tree"
            rm -rf "${'$'}tree/lost+found"
        """
        const val SEARCH_LIMIT = 2000
        const val SPARSE_MAGIC_LE = "3aff26ed"
        val SUPER_CANDIDATES = listOf("firmware/raw/super.raw.img", "firmware/raw/super.img", "out/extra/super.img")
    }
}
