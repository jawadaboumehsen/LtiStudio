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
import org.ide.lti.core.domain.pipeline.text.SidecarNormalizer
import org.ide.lti.core.domain.pipeline.toolkit.F2fsImage
import org.ide.lti.core.domain.pipeline.toolkit.FormatDetector
import org.ide.lti.core.domain.pipeline.toolkit.FsType
import org.ide.lti.core.domain.pipeline.toolkit.FsTypes
import org.ide.lti.core.domain.pipeline.toolkit.ImageInfo
import org.ide.lti.core.domain.pipeline.toolkit.ImageTree
import org.ide.lti.core.domain.pipeline.toolkit.ImageTreeEntry
import org.ide.lti.core.domain.pipeline.toolkit.Refusal
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException
import org.ide.lti.core.domain.pipeline.toolkit.SuperLayout
import org.ide.lti.core.domain.pipeline.toolkit.SuperTool
import org.ide.lti.core.domain.pipeline.toolkit.Superblock

/** A browsed partition: its entries and where its files are, as Windows sees them (to open in Explorer). */
public data class PartitionTree(val tree: ImageTree, val source: String, val hostFolder: String?)

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
            run("mkdir", "-p", abs(FirmwareExtractionStage.REPLACEMENTS))
            val sparse = run("xxd", "-p", "-l", "4", source).trim() == SPARSE_MAGIC_LE
            if (sparse) run("simg2img", source, abs(partial)) else run("cp", "-f", source, abs(partial))
            val fs = fsOf(this, partial)
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
            run("mv", "-f", abs(partial), abs(dest))
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
        val owners = readText("$configs/fs_config-$partition").orEmpty().lineSequence()
            .map { it.trim().split(WHITESPACE) }.filter { it.size >= OWNER_FIELDS }
            .associate { it[0] to it }
        val labels = readText("$configs/file_context-$partition").orEmpty().lineSequence()
            .map { it.trim().split(WHITESPACE) }.filter { it.size >= 2 }
            .associate { it[0] to it.last() }
        val entries = listing.lineSequence().mapNotNull { line ->
            // type, size, link target, path
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
                capabilities = owner?.getOrNull(CAPABILITIES)?.substringAfter("capabilities=0x", "")
                    ?.toLongOrNull(HEX) ?: 0L,
                target = target.takeIf { type == "l" },
                hasOwner = owner != null,
            )
        }.sortedBy { it.path }.toList()
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
                sh(
                    "mkdir -p \"$(dirname \"$2\")\" && cp -a -T \"$1\" \"$2\"",
                    abs("$source/$rel"),
                    abs("out/toolkit/$partition/$rel"),
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
            replacementSha256?.let { expected ->
                val own = FirmwareExtractionStage.replacementPath(partition)
                val actual = run("sha256sum", abs(own)).trim().substringBefore(' ')
                if (actual != expected) {
                    throw RefusalException(
                        Refusal("$own is not the image imported for $partition.", own, "Import it again in Extract."),
                    )
                }
                run("cp", "-f", abs(own), abs(image))
            }
            if (!exists(image)) {
                throw RefusalException(
                    Refusal("$partition.img is not extracted yet.", image, "Run the build through Extract first."),
                )
            }
            val tree = abs("$extracted/$partition")
            val (prefix, stat, labels) = when (val fs = fsOf(this, image)) {
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
        suspend fun of(rel: String): Superblock? {
            if (!exists(rel)) return null
            val fs = fsOf(this, rel)
            val dump = when (fs) {
                FsType.Erofs -> tryRun("dump.erofs", abs(rel)).stdout
                FsType.Ext4 -> tryRun("dumpe2fs", "-h", abs(rel)).stdout
                FsType.F2fs -> tryRun("dump.f2fs", abs(rel)).stdout
                else -> ""
            }
            return Superblock(fs, run("stat", "-c", "%s", abs(rel)).trim().toLong(), Superblock.fields(fs, dump))
        }
        ImageInfo(
            stock = of("${FirmwareExtractionStage.EXTRACTED}/$partition.img"),
            built = of("out/images/$partition.img"),
        )
    }

    private suspend fun fsOf(job: WorkbenchRunner.Job, rel: String): FsType = FsTypes.fromSuperblock(
        FormatDetector.fromHex(
            job.run(
                "xxd",
                "-p",
                "-s",
                "${FsTypes.SUPERBLOCK_OFFSET}",
                "-l",
                "${FsTypes.SUPERBLOCK_BYTES}",
                job.abs(rel),
            ),
        ),
    )

    /** Converts a sparse image to raw, or raw to sparse, into `out/toolkit/` (FR-010). */
    public suspend fun convert(workspaceId: String, image: String, toSparse: Boolean): String =
        runner.job(workspaceId, "Convert ${image.substringAfterLast('/')}") {
            val name = image.substringAfterLast('/').removeSuffix(".img")
            val dest = "out/toolkit/$name.${if (toSparse) "sparse" else "raw"}.img"
            run("mkdir", "-p", abs("out/toolkit"))
            // A side name until it is complete: a cancelled conversion never leaves an image that looks whole.
            run(if (toSparse) "img2simg" else "simg2img", abs(image), abs("$dest.partial"))
            run("mv", "-f", abs("$dest.partial"), abs(dest))
            dest
        }

    /**
     * Cuts [image] into parts of at most [maxMegabytes] (`<name>.img.part00`, `part01`, …; join them with `cat`)
     * (FR-010). These are byte parts, not sparse chunks: the tool groups build no `simg2simg`.
     */
    public suspend fun split(workspaceId: String, image: String, maxMegabytes: Int): List<String> =
        runner.job(workspaceId, "Split ${image.substringAfterLast('/')}") {
            require(maxMegabytes > 0) { "The part size must be positive." }
            val name = "${image.substringAfterLast('/')}.part"
            val partial = "out/toolkit/.$name.partial"
            // Parts are made in a side folder and moved together, so a cancelled split leaves no partial set.
            sh("rm -rf \"$1\" && mkdir -p \"$1\"", abs(partial))
            run("split", "-b", "${maxMegabytes}M", "-d", "-a", "2", abs(image), abs("$partial/$name"))
            val parts = run("find", abs(partial), "-maxdepth", "1", "-name", "$name.*", "-printf", "%P\\n")
                .lines().filter { it.isNotBlank() }.sorted()
            sh("mv -f \"$1\"/* \"$2\" && rmdir \"$1\"", abs(partial), abs("out/toolkit"))
            parts.map { "out/toolkit/$it" }
        }

    /** The groups, partitions and free space of the workspace's super image (FR-011), from `lpdump`. */
    public suspend fun superLayout(workspaceId: String): SuperLayout = runner.read(workspaceId) {
        val image = SUPER_CANDIDATES.firstOrNull { exists(it) }
            ?: throw RefusalException(
                Refusal("No super image in the workspace.", "super.img", "Run the build through Extract or Build."),
            )
        SuperTool.parseLpdumpOutput(run("lpdump", abs(image)))
    }

    private companion object {
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
            rm -rf "${'$'}tree"
            cp -r --no-dereference --preserve=links,timestamps -T "${'$'}mnt" "${'$'}tree"
            rm -rf "${'$'}tree/lost+found"
        """
        const val NO_LABEL = "(no label)"
        const val SEARCH_LIMIT = 2000
        const val SPARSE_MAGIC_LE = "3aff26ed"
        const val OWNER_FIELDS = 4
        const val UID = 1
        const val GID = 2
        const val MODE = 3
        const val CAPABILITIES = 4
        const val HEX = 16
        const val OCTAL = 8
        val WHITESPACE = Regex("""\s+""")
        val SUPER_CANDIDATES = listOf("firmware/raw/super.raw.img", "firmware/raw/super.img", "out/extra/super.img")
    }
}
