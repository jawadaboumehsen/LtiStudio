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

import org.ide.lti.core.domain.pipeline.CacheKeys
import org.ide.lti.core.domain.pipeline.PipelineStep
import org.ide.lti.core.domain.pipeline.RuntimeKeys
import org.ide.lti.core.domain.pipeline.StageContext
import org.ide.lti.core.domain.pipeline.StageDefinition
import org.ide.lti.core.domain.pipeline.stepCommand
import org.ide.lti.core.domain.pipeline.text.SidecarNormalizer
import org.ide.lti.core.domain.pipeline.toolkit.F2fsImage
import org.ide.lti.core.domain.pipeline.toolkit.FormatDetector
import org.ide.lti.core.domain.pipeline.toolkit.FsType
import org.ide.lti.core.domain.pipeline.toolkit.FsTypes
import org.ide.lti.core.domain.pipeline.toolkit.PackageManifest
import org.ide.lti.core.domain.pipeline.toolkit.PartitionImage
import org.ide.lti.core.domain.pipeline.toolkit.SlotMode
import org.ide.lti.core.domain.pipeline.toolkit.ToolkitBasicInfo
import org.ide.lti.core.domain.pipeline.toolkit.ToolkitFacts
import org.ide.lti.core.domain.pipeline.toolkit.ToolkitPartitionFact
import org.ide.lti.core.domain.pipeline.toolkit.ToolkitPartitionInfo
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.target.FirmwareSourceKind

/**
 * Stage 2 — turn the archive into raw partition images plus per-partition trees and sidecars
 * under `firmware/extracted/` (contract Stage 2).
 *
 * OTA_ZIP: `unzip payload.bin` → `payload-dumper-go`. RAW_IMAGE_ZIP: unzip, copy the boot chain,
 * trim AVB footer padding, `simg2img` when sparse, `lpunpack --slot=0`. Both: mount each dynamic
 * partition with `erofsfuse`, capture `stat`/`getfattr` dumps, write the normalised sidecars,
 * copy the tree, unmount; then boot metadata and the stock `build.prop`.
 */
public class FirmwareExtractionStage : StageDefinition {
    override val id: StageId = StageId.FIRMWARE_EXTRACTION

    override val requiredToolIds: Set<String> = setOf(
        "unzip", "payload-dumper-go", "simg2img", "lpunpack", "erofsfuse", "fusermount3",
        "unpack_bootimg", "avbtool", "xxd", "truncate", "getfattr", "find", "stat", "cp", "mv", "rm", "mkdir", "cat",
    )

    override val optionalToolIds: Set<String> = setOf("fuse2fs", "dump.f2fs", "7zz")

    override fun computeCacheKey(ctx: StageContext, previousStageCacheKey: String): String =
        CacheKeys.stage2ExtractionKey(previousStageCacheKey, ctx.target, ctx.snapshot)

    override fun outputs(ctx: StageContext): List<String> {
        val dynamicPartitions = effectiveDynamicPartitions(ctx)
        val bootPartitions = effectiveBootPartitions(ctx)
        return dynamicPartitions.flatMap { p ->
            listOf("$EXTRACTED/$p.img", "$EXTRACTED/fs_config-$p", "$EXTRACTED/file_context-$p")
        } + bootPartitions.map { "$EXTRACTED/$it.img" }
    }

    override fun requiredPaths(ctx: StageContext): List<String> =
        effectiveDynamicPartitions(ctx).map { "$EXTRACTED/$it" }

    private fun validate(ctx: StageContext): PipelineStep.Check? {
        val adapter = ctx.snapshot.extraction.adapter
        val isAuto = adapter.equals("AUTO", ignoreCase = true)
        val isPayloadDumper = adapter.equals("payload-dumper-go", ignoreCase = true)
        val isLpunpack = adapter.equals("lpunpack", ignoreCase = true)

        val dynamicOverrides = ctx.snapshot.extraction.dynamicPartitions
        val invalidDynamic = dynamicOverrides.filter { it !in ctx.target.dynamicPartitions }

        val bootOverrides = ctx.snapshot.extraction.bootPartitions
        val invalidBoot = bootOverrides.filter { it !in ctx.target.bootPartitions }

        val slot = ctx.snapshot.extraction.slot
        val validSlots = buildSet {
            addAll(ctx.target.packagePolicy.bootSlots)
            addAll(ctx.target.packagePolicy.bootSlots.map { "_$it" })
            add(ctx.target.activeSlotSuffix)
            add(ctx.target.activeSlotSuffix.removePrefix("_"))
        }

        return when {
            !isAuto && !isPayloadDumper && !isLpunpack ->
                PipelineStep.Check("verify extraction adapter") {
                    "UNSUPPORTED_EXTRACTION_ADAPTER: Extraction adapter '$adapter' is not supported"
                }

            invalidDynamic.isNotEmpty() ->
                PipelineStep.Check("verify dynamic partitions on target") {
                    "PARTITION_NOT_ON_TARGET: Dynamic partition(s) not on target: ${invalidDynamic.joinToString()}"
                }

            invalidBoot.isNotEmpty() ->
                PipelineStep.Check("verify boot partitions on target") {
                    "PARTITION_NOT_ON_TARGET: Boot partition(s) not on target: ${invalidBoot.joinToString()}"
                }

            !slot.isNullOrBlank() && slot !in validSlots ->
                PipelineStep.Check("verify extraction slot") {
                    "UNSUPPORTED_SLOT: Slot '$slot' is not supported on target device '${ctx.target.id}'"
                }

            else -> null
        }
    }

    override fun plan(ctx: StageContext): List<PipelineStep> {
        val manifest = ctx.value(RuntimeKeys.PACKAGE_MANIFEST)?.let { raw ->
            runCatching { PackageManifest.decode(raw) }.getOrNull()
        }
        return validate(ctx)?.let(::listOf)
            ?: manifest?.refusal?.let { listOf(FirmwareIngestion.refusalCheck(it)) }
            ?: extractionSteps(ctx, manifest)
    }

    private fun extractionSteps(ctx: StageContext, manifest: PackageManifest?): List<PipelineStep> {
        val ws = ctx.wsPath
        val extracted = ctx.abs(EXTRACTED)
        val dynamicPartitions = effectiveDynamicPartitions(ctx)
        val bootPartitions = effectiveBootPartitions(ctx)
        val steps = mutableListOf<PipelineStep>()
        if (!ctx.snapshot.extraction.reuseVerifiedExtraction) {
            steps += PipelineStep.Tool(stepCommand("rm", listOf("-rf", extracted), ws), "clean extracted")
        }
        steps += PipelineStep.Tool(stepCommand("mkdir", listOf("-p", extracted), ws), "mkdir extracted")
        steps += containerSteps(ctx, manifest, Partitions(dynamicPartitions, bootPartitions, slotSuffix(ctx)))
        steps += replacementSteps(ctx, dynamicPartitions)

        // The filesystem comes from each image's superblock: EROFS mounts with erofsfuse, ext4 with fuse2fs, F2FS
        // (no FUSE driver) is dumped with dump.f2fs. Build & Sign builds each partition as EROFS, ext4 or F2FS.
        val fsTypes = dynamicPartitions.associateWith { p ->
            steps += fsTypeStep(ctx, p)
            ctx.value(fsTypeKey(p))?.let { FsTypes.fromSuperblock(FormatDetector.fromHex(it)) }
        }
        dynamicPartitions.forEach { p ->
            steps += if (fsTypes[p] == FsType.Unknown) unknownFsSteps(ctx, p) else sidecarSteps(ctx, p, fsTypes[p])
        }
        steps += bootInfoSteps(ctx, bootPartitions)
        steps += PipelineStep.Tool(
            command = stepCommand("cat", listOf("$extracted/system/system/build.prop"), ws),
            label = "read stock build.prop",
            resultKey = RuntimeKeys.BUILD_PROP,
        )
        if (ctx.value(RuntimeKeys.BUILD_PROP) != null) {
            steps += factsSteps(ctx, dynamicPartitions, bootPartitions, fsTypes)
        }
        return steps
    }

    /** The user's own images over the stock ones, each checked against the SHA-256 recorded when imported. */
    private fun replacementSteps(ctx: StageContext, dynamicPartitions: List<String>): List<PipelineStep> =
        ctx.snapshot.extraction.replacementImages.filterKeys { it in dynamicPartitions }.flatMap { (p, sha256) ->
            val image = ctx.abs(replacementPath(p))
            val key = "firmware.$p.replacement.sha256"
            listOf(
                PipelineStep.Tool(stepCommand("sha256sum", listOf(image), ctx.wsPath), "hash replacement $p", key),
                PipelineStep.Check("check replacement $p") { values ->
                    val actual = values[key]?.trim()?.substringBefore(' ')
                    (
                        "REPLACEMENT_CHANGED: ${replacementPath(p)} is not the image imported for $p (expected " +
                            "$sha256, found $actual). Import it again in Extract."
                        ).takeIf { actual != sha256 }
                },
                PipelineStep.Tool(
                    stepCommand("cp", listOf("-f", image, "${ctx.abs(EXTRACTED)}/$p.img"), ctx.wsPath),
                    "replace $p",
                ),
            )
        }

    private fun slotSuffix(ctx: StageContext): String {
        val slot = ctx.snapshot.extraction.slot
        return when {
            slot.isNullOrBlank() -> ctx.target.activeSlotSuffix
            slot.startsWith("_") -> slot
            else -> "_$slot"
        }
    }

    private fun containerSteps(ctx: StageContext, manifest: PackageManifest?, parts: Partitions): List<PipelineStep> {
        val archive = ctx.abs(FirmwareAcquisitionStage.archiveRelPath(ctx.snapshot))
        if (manifest != null) return FirmwareIngestion.ingestSteps(ctx, archive, manifest.format, parts)
        // No package manifest (Acquire predates format detection): the device settings decide.
        val adapter = ctx.snapshot.extraction.adapter
        val useOta = if (adapter.equals("AUTO", ignoreCase = true)) {
            ctx.snapshot.acquisition.firmware.sourceKind == FirmwareSourceKind.OTA_ZIP
        } else {
            adapter.equals("payload-dumper-go", ignoreCase = true)
        }
        return if (useOta) {
            FirmwareIngestion.otaSteps(ctx, archive)
        } else {
            FirmwareIngestion.rawZipSteps(ctx, archive, parts)
        }
    }

    private fun fsTypeKey(partition: String) = "firmware.$partition.fstype"

    /**
     * [partition]'s image holds no EROFS, ext4 or F2FS superblock: stops the build, saying whether the firmware
     * carries only zeros for it (space reserved in super, no content) or something this app cannot read.
     */
    private fun unknownFsSteps(ctx: StageContext, partition: String): List<PipelineStep> {
        val key = "firmware.$partition.zeros"
        val image = "${ctx.abs(EXTRACTED)}/$partition.img"
        return listOf(
            PipelineStep.Tool(
                stepCommand(
                    "sh",
                    listOf("-c", "cmp -s -n \"$(stat -c %s \"$1\")\" \"$1\" /dev/zero && echo zeros || echo data") +
                        listOf("sh", image),
                    ctx.wsPath,
                ),
                "check $partition is not empty",
                key,
            ),
            PipelineStep.Check("filesystem of $partition") { values ->
                if (values[key]?.trim() == "zeros") {
                    "EMPTY_PARTITION: $partition came out of the firmware as zeros only: the firmware reserves its " +
                        "space but carries no content. Remove it from the dynamic partitions in Extract."
                } else {
                    "UNKNOWN_FILESYSTEM: $partition.img is not EROFS, ext4 or F2FS, so it cannot be read or rebuilt. " +
                        "Remove it from the dynamic partitions in Extract, or replace it with an image of one of those."
                }
            },
        )
    }

    private fun fsTypeStep(ctx: StageContext, partition: String): PipelineStep.Tool = PipelineStep.Tool(
        command = stepCommand(
            "xxd",
            listOf(
                "-p",
                "-s",
                "${FsTypes.SUPERBLOCK_OFFSET}",
                "-l",
                "${FsTypes.SUPERBLOCK_BYTES}",
                "${ctx.abs(EXTRACTED)}/$partition.img",
            ),
            ctx.wsPath,
        ),
        label = "detect filesystem $partition",
        resultKey = fsTypeKey(partition),
    )

    private fun bootInfoSteps(ctx: StageContext, bootPartitions: List<String>): List<PipelineStep> {
        val ws = ctx.wsPath
        val extracted = ctx.abs(EXTRACTED)
        val avbInfo = bootPartitions.map { b ->
            PipelineStep.Tool(
                command = stepCommand("avbtool", listOf("info_image", "--image", "$extracted/$b.img"), ws),
                label = "avbtool info $b",
                resultKey = RuntimeKeys.bootInfo(b),
                allowFailure = true,
            )
        }
        val headers = BOOT_IMAGES_WITH_HEADER.filter { it in bootPartitions }.map { b ->
            PipelineStep.Tool(
                command = stepCommand(
                    "unpack_bootimg",
                    listOf("--boot_img", "$extracted/$b.img", "--format", "info"),
                    ws,
                ),
                label = "unpack_bootimg $b",
                resultKey = "boot.$b.header",
                allowFailure = true,
            )
        }
        return avbInfo + headers
    }

    /** `extract.images` and the workspace facts (`facts/basic_info.json`, `facts/partition_info.json`). */
    private fun factsSteps(
        ctx: StageContext,
        dynamicPartitions: List<String>,
        bootPartitions: List<String>,
        fsTypes: Map<String, FsType?>,
    ): List<PipelineStep> {
        val dynamicSizes = dynamicPartitions.associateWith { ctx.longValue(RuntimeKeys.imageSize(it)) ?: 0L }
        val bootSizes = bootPartitions.associateWith { ctx.longValue(RuntimeKeys.bootSize(it)) ?: 0L }
        val images = dynamicSizes.map { (p, size) ->
            PartitionImage(p, fsTypes[p] ?: FsType.Unknown, size, sparse = false, path = "$EXTRACTED/$p.img")
        } + bootSizes.map { (b, size) ->
            PartitionImage(b, FsType.Raw, size, sparse = false, path = "$EXTRACTED/$b.img")
        }
        val slotMode = when {
            ctx.target.packagePolicy.bootSlots.size <= 1 -> SlotMode.A_ONLY
            ctx.target.virtualAb -> SlotMode.VIRTUAL_AB
            else -> SlotMode.AB
        }
        val basicInfo = ToolkitBasicInfo(
            deviceIdentity = ctx.target.id,
            slotMode = slotMode,
            isVirtualAbCompressed = ctx.target.virtualAb,
        )
        val partitionInfo = ToolkitPartitionInfo(
            partitions = dynamicSizes.map { (p, size) ->
                ToolkitPartitionFact(p, (fsTypes[p] ?: FsType.Unknown).name.lowercase(), size, inSuper = true)
            } + bootSizes.map { (b, size) -> ToolkitPartitionFact(b, "raw", size, inSuper = false) },
        )
        return listOf(
            PipelineStep.WriteFile(
                relPath = "$EXTRACTED/extract.images.json",
                content = PartitionImage.encodeList(images).encodeToByteArray(),
                label = "write extract.images",
            ),
            PipelineStep.Tool(
                command = stepCommand("cat", listOf("${ctx.abs(EXTRACTED)}/extract.images.json"), ctx.wsPath),
                label = "read extract.images",
                resultKey = RuntimeKeys.EXTRACT_IMAGES,
            ),
            PipelineStep.WriteFile(
                relPath = "facts/basic_info.json",
                content = ToolkitFacts.encodeBasicInfo(basicInfo).encodeToByteArray(),
                label = "write basic_info facts",
            ),
            PipelineStep.WriteFile(
                relPath = "facts/partition_info.json",
                content = ToolkitFacts.encodePartitionInfo(partitionInfo).encodeToByteArray(),
                label = "write partition_info facts",
            ),
        )
    }

    private fun effectiveDynamicPartitions(ctx: StageContext): List<String> =
        ctx.snapshot.extraction.dynamicPartitions.ifEmpty { ctx.target.dynamicPartitions }

    private fun effectiveBootPartitions(ctx: StageContext): List<String> =
        ctx.snapshot.extraction.bootPartitions.ifEmpty { ctx.target.bootPartitions }

    private fun sidecarSteps(ctx: StageContext, partition: String, fs: FsType?): List<PipelineStep> {
        if (fs == FsType.F2fs) return f2fsSidecarSteps(ctx, partition)
        val ws = ctx.wsPath
        val extracted = ctx.abs(EXTRACTED)
        val mnt = "$extracted/${partition}_mnt"
        val statKey = RuntimeKeys.statLines(partition)
        val selinuxKey = RuntimeKeys.selinuxDump(partition)
        val steps = mutableListOf<PipelineStep>()
        steps += PipelineStep.Tool(stepCommand("mkdir", listOf("-p", mnt), ws), "mkdir mount $partition")
        val image = "$extracted/$partition.img"
        steps += PipelineStep.Tool(
            if (fs == FsType.Ext4) {
                // fakeroot: fuse2fs checks permissions (erofsfuse does not), so a root-only folder would stop the
                // stat dump; read-only either way.
                stepCommand("fuse2fs", listOf(image, mnt, "-o", "ro,fakeroot"), ws)
            } else {
                stepCommand("erofsfuse", listOf(image, mnt), ws)
            },
            "mount $partition",
        )
        steps += PipelineStep.Tool(
            command = stepCommand("find", listOf(mnt, "-exec", "stat", "-c", "%u %g %a %n", "{}", "+"), ws),
            label = "stat dump $partition",
            resultKey = statKey,
        )
        steps += PipelineStep.Tool(
            command = stepCommand("sh", listOf("-c", SidecarNormalizer.LABEL_DUMP, "sh", mnt), ws),
            label = "selinux dump $partition",
            resultKey = selinuxKey,
        )
        val statDump = ctx.value(statKey)
        val selinuxDump = ctx.value(selinuxKey)
        if (statDump != null && selinuxDump != null) {
            val sidecars = SidecarNormalizer.fromDumps(partition, mnt, statDump, selinuxDump)
            steps += PipelineStep.WriteFile(
                "$EXTRACTED/fs_config-$partition",
                sidecars.fsConfigContent.encodeToByteArray(),
            )
            steps += PipelineStep.WriteFile(
                "$EXTRACTED/file_context-$partition",
                sidecars.fileContextContent.encodeToByteArray(),
            )
        }
        steps += PipelineStep.Tool(
            stepCommand("rm", listOf("-rf", "$extracted/$partition"), ws),
            "clear tree $partition",
        )
        // Owner, mode and SELinux label are in the sidecars just written (mkfs.erofs applies them), so only
        // content and links are copied: `cp -a` also copies ACLs, which erofsfuse does not serve (ENODATA).
        // An ext4 image's lost+found is dropped below; it has no sidecar line in a stock tree.
        steps += PipelineStep.Tool(
            stepCommand(
                "cp",
                listOf("-r", "--no-dereference", "--preserve=links,timestamps", "-T", mnt, "$extracted/$partition"),
                ws,
            ),
            "copy tree $partition",
        )
        steps += PipelineStep.Tool(
            stepCommand("rm", listOf("-rf", "$extracted/$partition/lost+found"), ws),
            "drop lost+found $partition",
        )
        steps += PipelineStep.Tool(stepCommand("fusermount3", listOf("-u", mnt), ws), "unmount $partition")
        return steps
    }

    /**
     * A stock F2FS partition: dump.f2fs writes the tree straight to `firmware/extracted/<p>` inside one fakeroot
     * session that also prints the owners, modes and labels the sidecars are made from (`F2fsImage.READBACK_SCRIPT`).
     */
    private fun f2fsSidecarSteps(ctx: StageContext, partition: String): List<PipelineStep> {
        val extracted = ctx.abs(EXTRACTED)
        val tree = "$extracted/$partition"
        val key = RuntimeKeys.statLines(partition)
        val steps = mutableListOf<PipelineStep>(
            PipelineStep.Tool(
                command = stepCommand(
                    "fakeroot",
                    listOf(
                        "sh",
                        "-c",
                        F2fsImage.READBACK_SCRIPT,
                        "sh",
                        ctx.toolPaths["dump.f2fs"] ?: "dump.f2fs",
                        "$extracted/$partition.img",
                        tree,
                        "keep",
                    ),
                    ctx.wsPath,
                ),
                label = "dump $partition",
                resultKey = key,
            ),
        )
        ctx.value(key)?.let { output ->
            val (stat, labels) = F2fsImage.splitReadback(output, partition, tree, expectedFsConfig = "")
            val sidecars = SidecarNormalizer.fromDumps(partition, tree, stat, labels)
            steps += PipelineStep.WriteFile(
                "$EXTRACTED/fs_config-$partition",
                sidecars.fsConfigContent.encodeToByteArray(),
            )
            steps += PipelineStep.WriteFile(
                "$EXTRACTED/file_context-$partition",
                sidecars.fileContextContent.encodeToByteArray(),
            )
        }
        return steps
    }

    public companion object {
        public const val EXTRACTED: String = "firmware/extracted"
        public const val RAW: String = "firmware/raw"
        public const val REPLACEMENTS: String = "firmware/replacements"

        /** Where the user's own image for [partition] is kept. */
        public fun replacementPath(partition: String): String = "$REPLACEMENTS/$partition.img"
        private val BOOT_IMAGES_WITH_HEADER = listOf("boot", "init_boot", "vendor_boot")
        private val IMAGE_SIZE = Regex("""Image size:\s+(\d+) bytes""")

        public fun parseImageSize(avbInfo: String): Long? =
            IMAGE_SIZE.find(avbInfo)?.groupValues?.get(1)?.toLongOrNull()
    }
}
