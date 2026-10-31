/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.stages

import org.ide.lti.core.domain.pipeline.PipelineStep
import org.ide.lti.core.domain.pipeline.StageContext
import org.ide.lti.core.domain.pipeline.stages.BuildFlashableZipStage.Companion.COVERAGE_RESULT_KEY
import org.ide.lti.core.domain.pipeline.stages.BuildFlashableZipStage.Companion.EXTRA_DIR
import org.ide.lti.core.domain.pipeline.stages.BuildFlashableZipStage.Companion.STAGE_DIR
import org.ide.lti.core.domain.pipeline.stages.BuildFlashableZipStage.Companion.footeredSize
import org.ide.lti.core.domain.pipeline.stages.BuildFlashableZipStage.Companion.zipName
import org.ide.lti.core.domain.pipeline.stepCommand
import org.ide.lti.core.domain.pipeline.text.FstabEditor
import org.ide.lti.core.domain.pipeline.text.SidecarNormalizer
import org.ide.lti.core.domain.pipeline.toolkit.ApkSignatures
import org.ide.lti.core.domain.pipeline.toolkit.BuildFilesystems
import org.ide.lti.core.domain.pipeline.toolkit.BuildWarningKind
import org.ide.lti.core.domain.pipeline.toolkit.CoverageResult
import org.ide.lti.core.domain.pipeline.toolkit.DeviceLink
import org.ide.lti.core.domain.pipeline.toolkit.F2fsImage
import org.ide.lti.core.domain.pipeline.toolkit.ImageVerify
import org.ide.lti.core.domain.pipeline.toolkit.KernelFacts
import org.ide.lti.core.domain.pipeline.toolkit.LinkCheck
import org.ide.lti.core.domain.pipeline.toolkit.MountTable
import org.ide.lti.core.domain.pipeline.toolkit.PackageKind

/** The Build stage's toolkit checks and extra packages (spec 010): coverage gate, image verify, fastboot/Odin. */
internal object BuildChecks {
    private const val ODIN_MD5 = "cd \"\$1\" && md5sum \"\$2\" >> \"\$2\" && mv -f \"\$2\" \"\$2.md5\""
    private const val COVERAGE_SHOWN = 20
    private const val LINKS_SHOWN = 20
    private const val MOUNT_TABLES_KEY = "build.mount.tables"

    /** The boot kernel's version and config lines (`KernelFacts.SCRIPT`), read before any image is built. */
    const val KERNEL_KEY = "build.kernel.config"
    private const val UNSIGNED_KEY = "build.apks.unsigned"

    /** The build tree first, then the stock one it is compared with. */
    private val LINK_TREES = listOf("work", FirmwareExtractionStage.EXTRACTED)

    /**
     * Links the build broke: every symbolic link of `work/` resolved as the phone would, and the
     * ones that end on nothing although they work in the stock trees (a debloated app's library, a moved file).
     * Refused until accepted in Build & Sign ([BuildWarningKind.BrokenLinks]).
     */
    fun linkSteps(ctx: StageContext): List<PipelineStep> {
        val steps = mutableListOf<PipelineStep>()
        val (buildTree, stockTree) = LINK_TREES
        // The stock tree is listed once the build's links are known, so the steps keep their order across re-plans.
        val build = brokenLinks(ctx, buildTree, steps)
        val stock = build?.let { brokenLinks(ctx, stockTree, steps) }
        if (build == null || stock == null) return steps
        val accepted = BuildWarningKind.BrokenLinks.name in ctx.snapshot.build.acknowledgedWarnings
        val newly = LinkCheck.newlyBroken(stock = stock, build = build)
        if (newly.isNotEmpty() && !accepted) {
            steps += PipelineStep.Check("link check") {
                "BROKEN_LINKS: ${newly.size} link(s) point at nothing, although they work in the stock firmware: " +
                    newly.take(LINKS_SHOWN).joinToString { "${it.path} -> ${it.target}" } +
                    (if (newly.size > LINKS_SHOWN) " and ${newly.size - LINKS_SHOWN} more" else "") +
                    ". Put the missing files back, or accept it in Build & Sign (Coverage)."
            }
        }
        return steps
    }

    /**
     * Every mount table that declares a dynamic partition must also declare it as the filesystem it is built as
     * (`MountTable`). The tree's fstabs are given the missing lines here; the first-stage fstab is inside a boot
     * image, so a gap there refuses the build until Assemble's mount table adds the lines to that image.
     */
    fun mountTableSteps(ctx: StageContext): List<PipelineStep> {
        val ws = ctx.wsPath
        val unpack = ctx.toolPaths["unpack_bootimg"] ?: "unpack_bootimg"
        val dtbo = ctx.toolPaths["mkdtboimg"] ?: "mkdtboimg"
        val steps = mutableListOf<PipelineStep>(
            PipelineStep.Tool(
                stepCommand("sh", listOf("-c", MountTable.SCRIPT, "sh", ws, unpack, dtbo), ws),
                "read mount tables",
                MOUNT_TABLES_KEY,
            ),
        )
        val entries = ctx.value(MOUNT_TABLES_KEY)?.let(MountTable::parse) ?: return steps
        val wanted = ctx.target.dynamicPartitions.associateWith { BuildFilesystems.of(ctx.snapshot.build, it) }
        val (inTree, inImages) = MountTable.missing(entries, wanted).partition { it.source.startsWith("work/") }
        inTree.groupBy { it.source }.forEach { (source, gaps) ->
            val lines = gaps.associate { "/${it.partition}" to it.filesystem }
            steps += PipelineStep.EditFile(source, { FstabEditor.withFilesystems(it, lines) }, "mount lines in $source")
        }
        if (inImages.isNotEmpty()) {
            // The device tree's fstab is compiled into the dtb or dtbo: the Studio adds lines to a ramdisk only.
            val fix = if (inImages.any { ":dt" in it.source }) {
                "The device tree mounts them (Assemble > Mount table shows it); the Studio cannot edit a device " +
                    "tree, so build those partitions as "
            } else {
                "Add the mount lines in Assemble > Mount table, or build those partitions as "
            }
            steps += PipelineStep.Check("mount table") {
                "MOUNT_TABLE: " + inImages.joinToString("; ") {
                    "${it.source} mounts ${it.partition} only as ${it.declared.sorted().joinToString("/")}, " +
                        "not ${it.filesystem}"
                } + ". " + fix + inImages.first().declared.first() + "."
            }
        }
        return steps
    }

    /**
     * The boot kernel must mount every dynamic partition as the filesystem it is built as (`CONFIG_<FS>_FS`, built
     * in or a module, from the kernel's embedded config). A kernel without a config, or no boot image, says nothing.
     */
    fun kernelFilesystemSteps(ctx: StageContext): List<PipelineStep> {
        val steps = mutableListOf<PipelineStep>(
            PipelineStep.Tool(
                stepCommand(
                    "sh",
                    listOf(
                        "-c",
                        KernelFacts.SCRIPT,
                        "sh",
                        ctx.abs("work/kernel/boot.img"),
                        ctx.toolPaths["unpack_bootimg"] ?: "unpack_bootimg",
                    ),
                    ctx.wsPath,
                ),
                "read boot kernel config",
                KERNEL_KEY,
            ),
        )
        // An output with no CONFIG_ line at all (not the boot kernel's config) says nothing either.
        val facts = ctx.value(KERNEL_KEY)?.let(KernelFacts::parse)?.takeUnless { it.config.isNullOrEmpty() }
        val built = ctx.target.dynamicPartitions.associateWith { BuildFilesystems.of(ctx.snapshot.build, it) }
        val unmountable = built.filterValues { facts?.mounts(it) == false }
        val kernel = "the boot kernel${facts?.version?.let { " $it" }.orEmpty()}"
        if (facts != null && unmountable.isNotEmpty()) {
            val mountable = BuildFilesystems.SUPPORTED.filter { facts.mounts(it) == true }
            steps += PipelineStep.Check("kernel filesystems") {
                "KERNEL_FILESYSTEM: $kernel cannot mount " +
                    unmountable.entries.joinToString { "${it.key} as ${it.value}" } +
                    " (CONFIG_${unmountable.values.first().uppercase()}_FS is off). Build them as " +
                    mountable.joinToString(" or ") + "."
            }
        }
        // Deduplication asked for by hand on a kernel older than 6.1, which cannot read it.
        val version = ctx.value(KERNEL_KEY)?.let(KernelFacts::parse)?.version
        val forced = built.filter { (p, fs) ->
            fs == BuildFilesystems.EROFS && ctx.snapshot.build.partitionDedupe[p] == true &&
                BuildFilesystems.erofsDedupe(ctx.snapshot.build, p, version)
        }.keys
        if (version != null && forced.isNotEmpty() && !BuildFilesystems.kernelDedupes(version)) {
            steps += PipelineStep.Check("kernel erofs dedupe") {
                "KERNEL_FILESYSTEM: the boot kernel $version cannot read deduplicated EROFS (it needs 6.1 or " +
                    "newer): ${forced.joinToString()}. Set their deduplication to automatic or off."
            }
        }
        // A compressed EROFS image also needs the kernel's decoder for that compression (CONFIG_EROFS_FS_ZIP*).
        val readable = facts?.erofsCompressions?.takeIf { facts.mounts(BuildFilesystems.EROFS) == true }
        val undecodable = built.filterValues { it == BuildFilesystems.EROFS }.keys
            .associateWith { BuildFilesystems.erofsDecoder(ctx.snapshot.build, it) }
            .filterValues { it != null && readable != null && it !in readable }
        if (undecodable.isNotEmpty()) {
            steps += PipelineStep.Check("kernel erofs compression") {
                "KERNEL_FILESYSTEM: $kernel cannot read " +
                    undecodable.entries.joinToString { "${it.key} (${it.value})" } +
                    ": its EROFS reads " + (readable.orEmpty().ifEmpty { listOf("no") }).joinToString("/") +
                    " compression. Build them with " +
                    (readable.orEmpty().map { if (it == "lz4") "lz4hc" else it } + BuildFilesystems.NO_COMPRESSION)
                        .joinToString(" or ") + "."
            }
        }
        return steps
    }

    /**
     * Every APK of work/ must carry a signature (`ApkSignatures`): an unsigned one, dropped in by a mod or a script,
     * is not installed and its app is gone after boot. Refused until accepted ([BuildWarningKind.UnsignedApks]).
     */
    fun apkSignatureSteps(ctx: StageContext): List<PipelineStep> {
        val steps = mutableListOf<PipelineStep>(
            PipelineStep.Tool(
                stepCommand("sh", listOf("-c", ApkSignatures.SCRIPT, "sh", ctx.abs("work")), ctx.wsPath),
                "find unsigned APKs",
                UNSIGNED_KEY,
            ),
        )
        val unsigned = ctx.value(UNSIGNED_KEY)?.let(ApkSignatures::parse).orEmpty()
        val accepted = BuildWarningKind.UnsignedApks.name in ctx.snapshot.build.acknowledgedWarnings
        if (unsigned.isNotEmpty() && !accepted) {
            steps += PipelineStep.Check("apk signatures") {
                "UNSIGNED_APK: ${unsigned.size} APK(s) in work/ carry no signature, so Android will not install " +
                    "them: ${unsigned.take(LINKS_SHOWN).joinToString()}" +
                    (if (unsigned.size > LINKS_SHOWN) " and ${unsigned.size - LINKS_SHOWN} more" else "") +
                    ". Sign them in the mod or script that adds them, or accept it in Build & Sign > Coverage."
            }
        }
        return steps
    }

    /** Plans the two steps that find [tree]'s broken links into [steps]; the links once both ran, else null. */
    private fun brokenLinks(ctx: StageContext, tree: String, steps: MutableList<PipelineStep>): List<DeviceLink>? {
        val ws = ctx.wsPath
        val listKey = "build.links.$tree"
        steps += PipelineStep.Tool(
            stepCommand(
                "sh",
                listOf("-c", LinkCheck.LISTING_SCRIPT, "sh", ctx.abs(tree)) + ctx.target.dynamicPartitions,
                ws,
            ),
            "list links in $tree",
            listKey,
        )
        val listing = ctx.value(listKey)?.let(LinkCheck::parse) ?: return null
        val endings = LinkCheck.resolve(listing)
        val paths = endings.values.filterIsInstance<LinkCheck.Ending.InTree>().map { it.treePath }.distinct()
        val missingKey = "build.links.$tree.missing"
        steps += PipelineStep.Tool(
            stepCommand("sh", listOf("-c", LinkCheck.EXISTS_SCRIPT, "sh", ctx.abs(tree)) + paths, ws),
            "check link targets in $tree",
            missingKey,
        )
        val missing = ctx.value(missingKey)?.lines()?.filter(String::isNotBlank)?.toSet()
        return missing?.let { LinkCheck.broken(listing, endings, it) }
    }

    /** The refusal when a work-tree file has no fs_config or file_contexts entry (FR-015), once the check ran. */
    fun coverageRefusal(ctx: StageContext): PipelineStep.Check? {
        val json = ctx.value(COVERAGE_RESULT_KEY)
        val coverage = json?.let { runCatching { CoverageResult.decode(it) }.getOrNull() }
        // Not checked yet, or clean. An unreadable result is refused like a gap: the build must not go on blind.
        if (json == null || coverage?.isClean == true) return null
        val missing = coverage?.let { (it.uncoveredFsConfig + it.uncoveredContexts).distinct().sorted() }.orEmpty()
        return PipelineStep.Check("coverage gate") {
            "COVERAGE_REFUSAL: ${missing.size} file(s) have no permission or SELinux label entry, so the " +
                "image would not boot: ${missing.take(COVERAGE_SHOWN).joinToString()}" +
                (if (missing.size > COVERAGE_SHOWN) " and ${missing.size - COVERAGE_SHOWN} more" else "") +
                ". Add the entries in Build & Sign (Coverage) and build again."
        }
    }

    /**
     * Mounts the built image and rebuilds its sidecars exactly as Extract does; every path's owner, mode and
     * SELinux label must equal what `work/configs` asked for (FR-009). The image is only used when they do.
     */
    fun verifySteps(ctx: StageContext, p: String): List<PipelineStep> {
        val ws = ctx.wsPath
        val img = ctx.abs("out/images/$p.img")
        val mnt = ctx.abs("out/verify/$p")
        val statKey = "verify.$p.stat"
        val selinuxKey = "verify.$p.selinux"
        val fsKey = "verify.$p.fs_config"
        val ctxKey = "verify.$p.file_context"
        val fs = BuildFilesystems.of(ctx.snapshot.build, p)
        val steps = mutableListOf<PipelineStep>()
        if (fs == BuildFilesystems.F2FS) {
            // No F2FS FUSE driver: dump.f2fs copies the image out, fakeroot keeps what it sets (one session).
            steps += PipelineStep.Tool(
                command = stepCommand(
                    "fakeroot",
                    listOf(
                        "sh",
                        "-c",
                        F2fsImage.READBACK_SCRIPT,
                        "sh",
                        ctx.toolPaths["dump.f2fs"] ?: "dump.f2fs",
                        img,
                        mnt,
                    ),
                    ws,
                ),
                label = "read back built $p",
                resultKey = statKey,
            )
        } else {
            steps += PipelineStep.Tool(stepCommand("mkdir", listOf("-p", mnt), ws), "mkdir verify $p")
            steps += PipelineStep.Tool(
                if (fs == BuildFilesystems.EXT4) {
                    stepCommand("fuse2fs", listOf(img, mnt, "-o", "ro,fakeroot"), ws)
                } else {
                    stepCommand("erofsfuse", listOf(img, mnt), ws)
                },
                "mount built $p",
            )
            steps += PipelineStep.Tool(
                command = stepCommand("find", listOf(mnt, "-exec", "stat", "-c", "%u %g %a %n", "{}", "+"), ws),
                label = "stat built $p",
                resultKey = statKey,
            )
            steps += PipelineStep.Tool(
                command = stepCommand(
                    "getfattr",
                    listOf("-R", "-h", "-n", "security.selinux", "--absolute-names", mnt),
                    ws,
                ),
                label = "selinux built $p",
                resultKey = selinuxKey,
            )
            steps += PipelineStep.Tool(stepCommand("fusermount3", listOf("-u", mnt), ws), "unmount built $p")
        }
        steps += listOf(
            PipelineStep.Tool(
                command = stepCommand("cat", listOf(ctx.abs("work/configs/fs_config-$p")), ws),
                label = "read fs_config $p",
                resultKey = fsKey,
                allowFailure = true,
            ),
            PipelineStep.Tool(
                command = stepCommand("cat", listOf(ctx.abs("work/configs/file_context-$p")), ws),
                label = "read file_context $p",
                resultKey = ctxKey,
                allowFailure = true,
            ),
        )
        steps += PipelineStep.Check("verify built $p") { values ->
            // A partition Assemble did not lay out (system_ext folded into system) has no sidecars to compare with.
            if (values[fsKey].isNullOrBlank()) return@Check null
            val (statDump, labels) = if (fs == BuildFilesystems.F2FS) {
                F2fsImage.splitReadback(values[statKey].orEmpty(), p, mnt, values[fsKey].orEmpty())
            } else {
                values[statKey].orEmpty() to values[selinuxKey].orEmpty()
            }
            // mke2fs makes lost+found; the tree has none, so it is not compared.
            val stat = statDump.lines().filterNot { it.endsWith(" $mnt/lost+found") }
            val built = SidecarNormalizer.fromDumps(p, mnt, stat.joinToString("\n"), labels)
            val diffs = ImageVerify.sidecarDifferences(
                expectedFsConfig = values[fsKey].orEmpty(),
                expectedFileContexts = values[ctxKey].orEmpty(),
                builtFsConfig = built.fsConfigContent,
                builtFileContexts = built.fileContextContent,
            )
            diffs.takeIf { it.isNotEmpty() }?.let {
                "IMAGE_VERIFY_FAILED: out/images/$p.img does not match work/$p: " +
                    it.take(COVERAGE_SHOWN).joinToString("; ") +
                    if (it.size > COVERAGE_SHOWN) " and ${it.size - COVERAGE_SHOWN} more" else ""
            }
        }
        return steps
    }

    /**
     * The packages built besides the flashable zip: a fastboot images zip ([PackageKind.FullPackage]) and a
     * Samsung Odin AP ([PackageKind.Odin]), both from a full `super.img` (FR-013). vbmeta is never included.
     */
    fun extraPackageSteps(ctx: StageContext): List<PipelineStep> {
        val extras = ctx.snapshot.build.extraPackages
            .mapNotNull { name -> PackageKind.entries.firstOrNull { it.name == name } }
        val overlaps = ctx.snapshot.build.standaloneImages.filter { it in ctx.target.dynamicPartitions }
        val overlapAccepted = BuildWarningKind.SuperOverlap.name in ctx.snapshot.build.acknowledgedWarnings
        return when {
            extras.isEmpty() -> emptyList()

            overlaps.isNotEmpty() && !overlapAccepted -> listOf(
                PipelineStep.Check("super overlap") {
                    "SUPER_OVERLAP: ${overlaps.joinToString()} are inside super.img and also chosen as standalone " +
                        "images. Accept the warning in Build & Sign or remove them."
                },
            )

            else -> fullSuperSteps(ctx) +
                (if (PackageKind.FullPackage in extras) imagesZipSteps(ctx) else emptyList()) +
                (if (PackageKind.Odin in extras) odinSteps(ctx) else emptyList())
        }
    }

    private fun fullSuperSteps(ctx: StageContext): List<PipelineStep> {
        val ws = ctx.wsPath
        val target = ctx.target
        val extra = ctx.abs(EXTRA_DIR)
        val group = target.superGroupName
        val lpmakeArgs = mutableListOf(
            "--metadata-size", "65536", "--super-name", "super",
            "--metadata-slots", target.superMetadataSlots.toString(),
            "--device", "super:${target.superPartitionBytes}",
            "--group", "$group:${target.superGroupBytes}",
        )
        if (target.virtualAb) lpmakeArgs += "--virtual-ab"
        target.dynamicPartitions.forEach { p ->
            lpmakeArgs += listOf("--partition", "$p:readonly:${ctx.longValue(footeredSize(p))}:$group")
            lpmakeArgs += listOf("--image", "$p=${ctx.abs("out/images/$p.img")}")
        }
        lpmakeArgs += listOf("--sparse", "--output", "$extra/super.img")
        return listOf(
            PipelineStep.Tool(stepCommand("rm", listOf("-rf", extra), ws), "clear extra packages"),
            PipelineStep.Tool(stepCommand("mkdir", listOf("-p", "$extra/odin"), ws), "mkdir extra packages"),
            PipelineStep.Tool(stepCommand("lpmake", lpmakeArgs, ws), "lpmake full super"),
        )
    }

    private fun imagesZipSteps(ctx: StageContext): List<PipelineStep> {
        val ws = ctx.wsPath
        val stage = ctx.abs(STAGE_DIR)
        val extra = ctx.abs(EXTRA_DIR)
        val images = ctx.abs("out/package/${zipName(ctx).removeSuffix(".zip")}-images.zip")
        // A standalone image asked for as sparse goes in as one (img2simg); the others as built.
        val sparse = ctx.snapshot.build.standaloneImages.filter { it in ctx.snapshot.build.sparseImages }
        val standalone = ctx.snapshot.build.standaloneImages.map {
            if (it in sparse) "$extra/$it.img" else ctx.abs("out/images/$it.img")
        }
        return sparse.map { p ->
            PipelineStep.Tool(
                stepCommand("img2simg", listOf(ctx.abs("out/images/$p.img"), "$extra/$p.img"), ws),
                "sparse $p",
            )
        } + listOf(
            PipelineStep.Tool(stepCommand("rm", listOf("-f", images), ws), "remove old images zip"),
            PipelineStep.Tool(
                stepCommand(
                    "zip",
                    listOf("-j", images, "$extra/super.img") +
                        ctx.target.packagePolicy.flashableBootPartitions.map { "$stage/$it.img" } + standalone,
                    ws,
                ),
                "zip fastboot images",
            ),
        )
    }

    private fun odinSteps(ctx: StageContext): List<PipelineStep> {
        val ws = ctx.wsPath
        val extra = ctx.abs(EXTRA_DIR)
        val stage = ctx.abs(STAGE_DIR)
        val members = listOf("super.img") + ctx.target.packagePolicy.flashableBootPartitions.map { "$it.img" }
        val tar = "AP_${zipName(ctx).removeSuffix(".zip")}.tar"
        return members.map { m ->
            val source = if (m == "super.img") "$extra/super.img" else "$stage/$m"
            PipelineStep.Tool(
                stepCommand("lz4", listOf("-B6", "--content-size", "-f", source, "$extra/odin/$m.lz4"), ws),
                "lz4 $m",
            )
        } + listOf(
            PipelineStep.Tool(
                stepCommand(
                    "tar",
                    listOf("-H", "ustar", "-cf", ctx.abs("out/package/$tar"), "-C", "$extra/odin") +
                        members.map { "$it.lz4" },
                    ws,
                ),
                "tar odin AP",
            ),
            // Odin checks the md5sum line appended to the tar ("<hash>  <name>"), so it is computed in that folder.
            PipelineStep.Tool(
                stepCommand("sh", listOf("-c", ODIN_MD5, "sh", ctx.abs("out/package"), tar), ws),
                "md5 odin AP",
            ),
        )
    }
}
