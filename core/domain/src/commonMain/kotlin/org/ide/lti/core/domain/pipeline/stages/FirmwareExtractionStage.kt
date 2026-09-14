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

    override fun computeCacheKey(ctx: StageContext, previousStageCacheKey: String): String =
        CacheKeys.stage2ExtractionKey(previousStageCacheKey, ctx.target, ctx.snapshot)

    override fun outputs(ctx: StageContext): List<String> {
        val dynamicPartitions = effectiveDynamicPartitions(ctx)
        val bootPartitions = effectiveBootPartitions(ctx)
        return dynamicPartitions.flatMap { p ->
            listOf("$EXTRACTED/$p.img", "$EXTRACTED/fs_config-$p", "$EXTRACTED/file_context-$p")
        } + bootPartitions.map { "$EXTRACTED/$it.img" }
    }

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
        val validationError = validate(ctx)
        if (validationError != null) {
            return listOf(validationError)
        }

        val extraction = ctx.snapshot.extraction
        val slot = extraction.slot
        val adapter = extraction.adapter
        val isAuto = adapter.equals("AUTO", ignoreCase = true)
        val isPayloadDumper = adapter.equals("payload-dumper-go", ignoreCase = true)

        val dynamicPartitions = effectiveDynamicPartitions(ctx)
        val bootPartitions = effectiveBootPartitions(ctx)
        val effectiveSlotSuffix = if (!slot.isNullOrBlank()) {
            if (slot.startsWith("_")) slot else "_$slot"
        } else {
            ctx.target.activeSlotSuffix
        }

        val ws = ctx.wsPath
        val extracted = ctx.abs(EXTRACTED)
        val archive = ctx.abs(FirmwareAcquisitionStage.archiveRelPath(ctx.snapshot))
        val steps = mutableListOf<PipelineStep>()

        if (!ctx.snapshot.extraction.reuseVerifiedExtraction) {
            steps += PipelineStep.Tool(stepCommand("rm", listOf("-rf", extracted), ws), "clean extracted")
        }
        steps += PipelineStep.Tool(stepCommand("mkdir", listOf("-p", extracted), ws), "mkdir extracted")

        val useOta = if (isAuto) {
            ctx.snapshot.acquisition.firmware.sourceKind == FirmwareSourceKind.OTA_ZIP
        } else {
            isPayloadDumper
        }

        if (useOta) {
            steps += otaSteps(ctx, archive)
        } else {
            steps += rawSteps(ctx, archive, dynamicPartitions, bootPartitions, effectiveSlotSuffix)
        }

        dynamicPartitions.forEach { p -> steps += sidecarSteps(ctx, p) }
        bootPartitions.forEach { b ->
            steps += PipelineStep.Tool(
                command = stepCommand("avbtool", listOf("info_image", "--image", "$extracted/$b.img"), ws),
                label = "avbtool info $b",
                resultKey = RuntimeKeys.bootInfo(b),
                allowFailure = true,
            )
        }
        BOOT_IMAGES_WITH_HEADER.filter { it in bootPartitions }.forEach { b ->
            steps += PipelineStep.Tool(
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
        steps += PipelineStep.Tool(
            command = stepCommand("cat", listOf("$extracted/system/system/build.prop"), ws),
            label = "read stock build.prop",
            resultKey = RuntimeKeys.BUILD_PROP,
        )
        return steps
    }

    private fun effectiveDynamicPartitions(ctx: StageContext): List<String> =
        ctx.snapshot.extraction.dynamicPartitions.ifEmpty { ctx.target.dynamicPartitions }

    private fun effectiveBootPartitions(ctx: StageContext): List<String> =
        ctx.snapshot.extraction.bootPartitions.ifEmpty { ctx.target.bootPartitions }

    private fun otaSteps(ctx: StageContext, archive: String): List<PipelineStep> {
        val ws = ctx.wsPath
        val firmware = ctx.abs("firmware")
        return listOf(
            PipelineStep.Tool(
                stepCommand("unzip", listOf("-o", archive, "payload.bin", "-d", firmware), ws),
                "unzip payload",
            ),
            PipelineStep.Tool(
                stepCommand("payload-dumper-go", listOf("-o", ctx.abs(EXTRACTED), "$firmware/payload.bin"), ws),
                "payload-dumper-go",
            ),
            PipelineStep.Tool(stepCommand("rm", listOf("-f", "$firmware/payload.bin"), ws), "rm payload"),
        )
    }

    private fun rawSteps(
        ctx: StageContext,
        archive: String,
        dynamicPartitions: List<String>,
        bootPartitions: List<String>,
        slot: String,
    ): List<PipelineStep> {
        val ws = ctx.wsPath
        val raw = ctx.abs("firmware/raw")
        val extracted = ctx.abs(EXTRACTED)
        val steps = mutableListOf<PipelineStep>()
        steps += PipelineStep.Tool(stepCommand("unzip", listOf("-o", archive, "-d", raw), ws), "unzip raw archive")
        bootPartitions.forEach { b ->
            steps += PipelineStep.Tool(stepCommand("cp", listOf("-a", "$raw/$b.img", "$extracted/$b.img"), ws), "cp $b")
            steps += avbPaddingTrim(ctx, b)
        }
        steps += PipelineStep.Tool(
            command = stepCommand("xxd", listOf("-l", "4", "-p", "$raw/super.img"), ws),
            label = "detect sparse super",
            resultKey = RuntimeKeys.SUPER_MAGIC,
        )
        val superMagic = ctx.value(RuntimeKeys.SUPER_MAGIC)?.trim() ?: return steps
        val superImg = if (superMagic.equals(SPARSE_MAGIC_LE, ignoreCase = true)) {
            steps += PipelineStep.Tool(
                stepCommand("simg2img", listOf("$raw/super.img", "$raw/super.raw.img"), ws),
                "simg2img super",
            )
            "$raw/super.raw.img"
        } else {
            "$raw/super.img"
        }
        val lpunpackArgs = listOf("--slot=0") +
            dynamicPartitions.flatMap { listOf("-p", "$it$slot") } +
            listOf(superImg, extracted)
        steps += PipelineStep.Tool(stepCommand("lpunpack", lpunpackArgs, ws), "lpunpack")
        dynamicPartitions.forEach { p ->
            steps += PipelineStep.Tool(
                stepCommand("mv", listOf("$extracted/$p$slot.img", "$extracted/$p.img"), ws),
                "rename $p",
            )
        }
        return steps
    }

    /**
     * Raw dumps carry padding after the AVB footer; `avbtool info_image` reports the real
     * `Image size`, and the file is truncated to it when larger (assumption for spike T034).
     */
    private fun avbPaddingTrim(ctx: StageContext, image: String): List<PipelineStep> {
        val ws = ctx.wsPath
        val path = ctx.abs("$EXTRACTED/$image.img")
        val infoKey = "raw.$image.info"
        val sizeKey = RuntimeKeys.bootSize(image)
        val steps = mutableListOf<PipelineStep>()
        steps += PipelineStep.Tool(
            command = stepCommand("avbtool", listOf("info_image", "--image", path), ws),
            label = "avbtool info raw $image",
            resultKey = infoKey,
            allowFailure = true,
        )
        steps += PipelineStep.Tool(
            command = stepCommand("stat", listOf("-c", "%s", path), ws),
            label = "stat raw $image",
            resultKey = sizeKey,
        )
        val declared = ctx.value(infoKey)?.let(::parseImageSize)
        val actual = ctx.longValue(sizeKey)
        val needsTrim = declared != null && actual != null && actual > declared
        if (ctx.exitOf(infoKey) == 0 && needsTrim) {
            steps += PipelineStep.Tool(
                stepCommand("truncate", listOf("-s", declared.toString(), path), ws),
                "trim $image",
            )
        }
        return steps
    }

    private fun sidecarSteps(ctx: StageContext, partition: String): List<PipelineStep> {
        val ws = ctx.wsPath
        val extracted = ctx.abs(EXTRACTED)
        val mnt = "$extracted/${partition}_mnt"
        val statKey = RuntimeKeys.statLines(partition)
        val selinuxKey = RuntimeKeys.selinuxDump(partition)
        val steps = mutableListOf<PipelineStep>()
        steps += PipelineStep.Tool(stepCommand("mkdir", listOf("-p", mnt), ws), "mkdir mount $partition")
        steps += PipelineStep.Tool(
            stepCommand("erofsfuse", listOf("$extracted/$partition.img", mnt), ws),
            "mount $partition",
        )
        steps += PipelineStep.Tool(
            command = stepCommand("find", listOf(mnt, "-exec", "stat", "-c", "%u %g %a %n", "{}", "+"), ws),
            label = "stat dump $partition",
            resultKey = statKey,
        )
        steps += PipelineStep.Tool(
            command = stepCommand(
                "getfattr",
                listOf("-R", "-h", "-n", "security.selinux", "--absolute-names", mnt),
                ws,
            ),
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
        steps += PipelineStep.Tool(
            stepCommand("cp", listOf("-a", "-T", mnt, "$extracted/$partition"), ws),
            "copy tree $partition",
        )
        steps += PipelineStep.Tool(
            stepCommand("rm", listOf("-rf", "$extracted/$partition/lost+found"), ws),
            "drop lost+found $partition",
        )
        steps += PipelineStep.Tool(stepCommand("fusermount3", listOf("-u", mnt), ws), "unmount $partition")
        return steps
    }

    public companion object {
        public const val EXTRACTED: String = "firmware/extracted"
        private const val SPARSE_MAGIC_LE = "3aff26ed"
        private val BOOT_IMAGES_WITH_HEADER = listOf("boot", "init_boot", "vendor_boot")
        private val IMAGE_SIZE = Regex("""Image size:\s+(\d+) bytes""")

        public fun parseImageSize(avbInfo: String): Long? =
            IMAGE_SIZE.find(avbInfo)?.groupValues?.get(1)?.toLongOrNull()
    }
}
