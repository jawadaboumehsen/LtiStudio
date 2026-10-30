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
import org.ide.lti.core.domain.pipeline.RuntimeKeys
import org.ide.lti.core.domain.pipeline.StageContext
import org.ide.lti.core.domain.pipeline.stages.FirmwareExtractionStage.Companion.EXTRACTED
import org.ide.lti.core.domain.pipeline.stages.FirmwareExtractionStage.Companion.RAW
import org.ide.lti.core.domain.pipeline.stepCommand
import org.ide.lti.core.domain.pipeline.toolkit.ArchiveExtractor
import org.ide.lti.core.domain.pipeline.toolkit.BlockOtaExtractor
import org.ide.lti.core.domain.pipeline.toolkit.ChunkExtractor
import org.ide.lti.core.domain.pipeline.toolkit.FirmwareLayout
import org.ide.lti.core.domain.pipeline.toolkit.FirmwareLayouts
import org.ide.lti.core.domain.pipeline.toolkit.OdinExtractor
import org.ide.lti.core.domain.pipeline.toolkit.OdinSlot
import org.ide.lti.core.domain.pipeline.toolkit.PackageFormat
import org.ide.lti.core.domain.pipeline.toolkit.PayloadExtractor
import org.ide.lti.core.domain.pipeline.toolkit.Refusal
import org.ide.lti.core.domain.pipeline.toolkit.RefusalCatalog
import org.ide.lti.core.domain.pipeline.toolkit.SparseConverter

/** The partitions a run extracts, and the slot suffix their super entries carry. */
internal data class Partitions(val dynamic: List<String>, val boot: List<String>, val slot: String)

/**
 * The part of stage 2 that turns the acquired container, whatever its format, into
 * `firmware/extracted/<p>.img` (FR-001..FR-005). Containers are unpacked to `firmware/raw/`.
 */
internal object FirmwareIngestion {
    private const val CONTAINER_LISTING = "firmware.container.listing"
    private const val ODIN_LISTING = "firmware.odin.listing"
    private const val OTA_METADATA = "META-INF/com/android/metadata"
    private const val OTA_METADATA_KEY = "firmware.ota.metadata"
    private const val SPARSE_MAGIC_LE = "3aff26ed"

    fun refusalCheck(refusal: Refusal): PipelineStep.Check = PipelineStep.Check("refuse ${refusal.item}") {
        "PACKAGE_REFUSED: ${refusal.cause} (${refusal.item}): ${refusal.nextStep}"
    }

    fun ingestSteps(ctx: StageContext, archive: String, format: PackageFormat, parts: Partitions): List<PipelineStep> {
        val ws = ctx.wsPath
        val raw = ctx.abs(RAW)
        return when (format) {
            PackageFormat.AndroidPayload -> listOf(payloadTool(ctx, archive))

            PackageFormat.Ntpi -> listOf(refusalCheck(ChunkExtractor.ntpiRefusal(archive.substringAfterLast('/'))))

            PackageFormat.SparseChunks -> {
                // One sparse image imported on its own: it is named after its file (super.img, system.img, ...).
                val name = archive.substringAfterLast('/').removeSuffix(".img") + ".img"
                listOf(
                    PipelineStep.Tool(stepCommand("mkdir", listOf("-p", raw), ws), "mkdir raw"),
                    PipelineStep.Tool(SparseConverter.sparseToRawCommand(archive, "$raw/$name", ws), "simg2img $name"),
                ) + rawImageSteps(ctx, raw, listOf(name), parts)
            }

            PackageFormat.Zip -> listingSteps(
                ctx,
                PipelineStep.Tool(
                    command = stepCommand("unzip", listOf("-Z1", archive), ws),
                    label = "list archive",
                    resultKey = CONTAINER_LISTING,
                ),
            ) { members -> layoutSteps(ctx, archive, members, fromZip = true, parts) }

            else -> listOf(
                PipelineStep.Tool(stepCommand("mkdir", listOf("-p", raw), ws), "mkdir raw"),
                PipelineStep.Tool(
                    ArchiveExtractor.extractionCommand(format, archive, raw, ws),
                    "extract archive ($format)",
                ),
            ) + listingSteps(
                ctx,
                PipelineStep.Tool(
                    command = stepCommand("find", listOf(raw, "-type", "f", "-printf", "%P\\n"), ws),
                    label = "list archive",
                    resultKey = CONTAINER_LISTING,
                ),
            ) { members -> layoutSteps(ctx, archive, members, fromZip = false, parts) }
        }
    }

    /** `payload.bin` straight from a zip OTA (the legacy path, no package manifest). */
    fun otaSteps(ctx: StageContext, archive: String): List<PipelineStep> {
        val ws = ctx.wsPath
        val firmware = ctx.abs("firmware")
        return listOf(
            PipelineStep.Tool(
                stepCommand("unzip", listOf("-o", archive, "payload.bin", "-d", firmware), ws),
                "unzip payload",
            ),
            payloadTool(ctx, "$firmware/payload.bin"),
            PipelineStep.Tool(stepCommand("rm", listOf("-f", "$firmware/payload.bin"), ws), "rm payload"),
        )
    }

    /** The legacy raw path: the whole zip is unpacked and `super.img` is split (no package manifest). */
    fun rawZipSteps(ctx: StageContext, archive: String, parts: Partitions): List<PipelineStep> {
        val ws = ctx.wsPath
        val raw = ctx.abs(RAW)
        return listOf(
            PipelineStep.Tool(stepCommand("mkdir", listOf("-p", raw), ws), "mkdir raw"),
            PipelineStep.Tool(stepCommand("unzip", listOf("-o", archive, "-d", raw), ws), "unzip raw archive"),
        ) + rawImageSteps(ctx, raw, listOf("super.img") + parts.boot.map { "$it.img" }, parts)
    }

    private fun payloadTool(ctx: StageContext, payload: String): PipelineStep.Tool = PipelineStep.Tool(
        PayloadExtractor.extractionCommand(payload, ctx.abs(EXTRACTED), ctx.wsPath),
        "payload-dumper-go",
    )

    /** [listStep], then — once its listing is known — the steps [next] plans from the member list. */
    private fun listingSteps(
        ctx: StageContext,
        listStep: PipelineStep.Tool,
        next: (List<String>) -> List<PipelineStep>,
    ): List<PipelineStep> {
        val listing = ctx.value(listStep.resultKey!!) ?: return listOf(listStep)
        return listOf(listStep) + next(FirmwareLayouts.parseListing(listing))
    }

    private fun layoutSteps(
        ctx: StageContext,
        archive: String,
        members: List<String>,
        fromZip: Boolean,
        parts: Partitions,
    ): List<PipelineStep> {
        val ws = ctx.wsPath
        val raw = ctx.abs(RAW)
        val layout = FirmwareLayouts.classify(members)
        val unpack = if (fromZip && layout != FirmwareLayout.Payload) {
            listOf(
                PipelineStep.Tool(stepCommand("mkdir", listOf("-p", raw), ws), "mkdir raw"),
                PipelineStep.Tool(stepCommand("unzip", listOf("-o", archive, "-d", raw), ws), "unzip raw archive"),
            )
        } else {
            emptyList()
        }
        return unpack + when (layout) {
            FirmwareLayout.Payload -> if (fromZip) {
                zipPayloadSteps(ctx, archive)
            } else {
                listOf(payloadTool(ctx, ctx.abs("$RAW/${FirmwareLayouts.member(members, "payload.bin")}")))
            }

            FirmwareLayout.RawImages -> rawImageSteps(ctx, raw, members, parts)

            FirmwareLayout.SparseChunks -> chunkSteps(ctx, raw, members, parts)

            FirmwareLayout.BlockOta -> blockOtaSteps(ctx, raw, members, parts)

            FirmwareLayout.Odin -> odinSteps(ctx, raw, members, parts)

            FirmwareLayout.Unknown -> listOf(
                refusalCheck(RefusalCatalog.unknownPackage(archive.substringAfterLast('/'))),
            )
        }
    }

    /** An incremental OTA names its source build in META-INF/com/android/metadata and is refused (FR-002). */
    private fun zipPayloadSteps(ctx: StageContext, archive: String): List<PipelineStep> {
        val metadataStep = PipelineStep.Tool(
            command = stepCommand("unzip", listOf("-p", archive, OTA_METADATA), ctx.wsPath),
            label = "read ota metadata",
            resultKey = OTA_METADATA_KEY,
            allowFailure = true,
        )
        val metadata = ctx.value(OTA_METADATA_KEY) ?: return listOf(metadataStep)
        return listOf(metadataStep) + if (PayloadExtractor.isIncrementalMetadata(metadata)) {
            listOf(refusalCheck(PayloadExtractor.incrementalRefusal(archive.substringAfterLast('/'))))
        } else {
            otaSteps(ctx, archive)
        }
    }

    /** Plain images in [rawDir]: boot images are copied, dynamic ones come from `super.img` or their own image. */
    private fun rawImageSteps(
        ctx: StageContext,
        rawDir: String,
        members: List<String>,
        parts: Partitions,
    ): List<PipelineStep> {
        val extracted = ctx.abs(EXTRACTED)
        val superMember = FirmwareLayouts.member(members, "super.img")
        val dynamic = if (superMember == null) {
            parts.dynamic.flatMap { p ->
                val member = FirmwareLayouts.member(members, "$p.img") ?: "$p.img"
                unsparseSteps(ctx, "$rawDir/$member", "$extracted/$p.img", p)
            }
        } else {
            superSteps(ctx, rawDir, "$rawDir/$superMember", parts)
        }
        return bootCopySteps(ctx, rawDir, members, parts) + dynamic
    }

    private fun bootCopySteps(
        ctx: StageContext,
        rawDir: String,
        members: List<String>,
        parts: Partitions,
    ): List<PipelineStep> = parts.boot.flatMap { b ->
        val member = FirmwareLayouts.member(members, "$b.img") ?: "$b.img"
        listOf(
            PipelineStep.Tool(
                stepCommand("cp", listOf("-a", "$rawDir/$member", "${ctx.abs(EXTRACTED)}/$b.img"), ctx.wsPath),
                "cp $b",
            ),
        ) + avbPaddingTrim(ctx, b)
    }

    /** `super.img` (unsparsed first when sparse) split by `lpunpack` into the dynamic partitions of slot 0. */
    private fun superSteps(
        ctx: StageContext,
        rawDir: String,
        superPath: String,
        parts: Partitions,
    ): List<PipelineStep> {
        val ws = ctx.wsPath
        val extracted = ctx.abs(EXTRACTED)
        val probe = PipelineStep.Tool(
            command = stepCommand("xxd", listOf("-l", "4", "-p", superPath), ws),
            label = "detect sparse super",
            resultKey = RuntimeKeys.SUPER_MAGIC,
        )
        val superMagic = ctx.value(RuntimeKeys.SUPER_MAGIC)?.trim() ?: return listOf(probe)
        val sparse = superMagic.equals(SPARSE_MAGIC_LE, ignoreCase = true)
        val superImg = if (sparse) "$rawDir/super.raw.img" else superPath
        val unsparse = if (sparse) {
            listOf(PipelineStep.Tool(stepCommand("simg2img", listOf(superPath, superImg), ws), "simg2img super"))
        } else {
            emptyList()
        }
        val lpunpackArgs = listOf("--slot=0") +
            parts.dynamic.flatMap { listOf("-p", "${it}${parts.slot}") } +
            listOf(superImg, extracted)
        val renames = parts.dynamic.map { p ->
            PipelineStep.Tool(
                stepCommand("mv", listOf("$extracted/$p${parts.slot}.img", "$extracted/$p.img"), ws),
                "rename $p",
            )
        }
        return listOf(probe) + unsparse + PipelineStep.Tool(stepCommand("lpunpack", lpunpackArgs, ws), "lpunpack") +
            renames
    }

    /** Copies [source] to [dest], converting it to a raw image first when it is sparse. */
    private fun unsparseSteps(ctx: StageContext, source: String, dest: String, name: String): List<PipelineStep> {
        val ws = ctx.wsPath
        val magicKey = "firmware.$name.magic"
        val probe = PipelineStep.Tool(
            command = stepCommand("xxd", listOf("-l", "4", "-p", source), ws),
            label = "detect sparse $name",
            resultKey = magicKey,
        )
        val magic = ctx.value(magicKey)?.trim() ?: return listOf(probe)
        val copy = if (magic.equals(SPARSE_MAGIC_LE, ignoreCase = true)) {
            PipelineStep.Tool(SparseConverter.sparseToRawCommand(source, dest, ws), "simg2img $name")
        } else {
            PipelineStep.Tool(stepCommand("cp", listOf("-a", source, dest), ws), "cp $name")
        }
        return listOf(probe, copy)
    }

    /** Joins `<p>.img_sparsechunk.N` parts into `raw/<p>.img`, then treats them as plain images. */
    private fun chunkSteps(
        ctx: StageContext,
        rawDir: String,
        members: List<String>,
        parts: Partitions,
    ): List<PipelineStep> {
        val groups = ChunkExtractor.groupSparseChunks(members)
        val joined = groups.map { (base, chunks) ->
            PipelineStep.Tool(
                ChunkExtractor.joinChunksCommand(chunks.map { "$rawDir/$it" }, "$rawDir/$base.img", ctx.wsPath),
                "join chunks $base",
            )
        }
        val plain = members.filterNot { ChunkExtractor.isSparseChunk(it) } + groups.keys.map { "$it.img" }
        return joined + rawImageSteps(ctx, rawDir, plain, parts)
    }

    /** `<p>.transfer.list` + `<p>.new.dat[.br]` → `<p>.img`; an incremental transfer list is refused. */
    private fun blockOtaSteps(
        ctx: StageContext,
        rawDir: String,
        members: List<String>,
        parts: Partitions,
    ): List<PipelineStep> {
        val inPackage = BlockOtaExtractor.discoverPartitions(members)
        val steps = mutableListOf<PipelineStep>()
        var complete = true
        for (p in parts.dynamic.filter { it in inPackage }) {
            val (partSteps, done) = blockOtaPartitionSteps(ctx, rawDir, members, p)
            steps += partSteps
            if (!done) {
                complete = false
                break
            }
        }
        return if (complete) steps + bootCopySteps(ctx, rawDir, members, parts) else steps
    }

    /** One partition's steps, and whether planning may go on (its transfer list was read and is a full one). */
    private fun blockOtaPartitionSteps(
        ctx: StageContext,
        rawDir: String,
        members: List<String>,
        p: String,
    ): Pair<List<PipelineStep>, Boolean> {
        val ws = ctx.wsPath
        val dir = FirmwareLayouts.member(members, "$p.transfer.list")?.substringBeforeLast('/', "").orEmpty()
        val source = "$rawDir/$dir".trimEnd('/')
        val diffKey = "firmware.blockota.$p.diffops"
        val check = PipelineStep.Tool(
            command = stepCommand(
                "grep",
                listOf("-cE", "^(bsdiff|imgdiff|stash|diff)", "$source/$p.transfer.list"),
                ws,
            ),
            label = "check transfer list $p",
            resultKey = diffKey,
            allowFailure = true,
        )
        val diffOps = ctx.value(diffKey)?.trim()?.toIntOrNull() ?: return listOf(check) to false
        val next = if (diffOps > 0) {
            listOf(refusalCheck(BlockOtaExtractor.incrementalRefusal("$p.transfer.list")))
        } else {
            val isBrotli = FirmwareLayouts.member(members, "$p.new.dat.br") != null
            BlockOtaExtractor.conversionSteps(p, isBrotli, source, ctx.abs(EXTRACTED), ws).mapIndexed { i, command ->
                PipelineStep.Tool(command, "convert $p ${i + 1}")
            }
        }
        return listOf(check) + next to (diffOps == 0)
    }

    /** Verifies and unpacks the AP tarball, decompresses the `*.lz4` images it needs, then plain-image steps. */
    private fun odinSteps(
        ctx: StageContext,
        rawDir: String,
        members: List<String>,
        parts: Partitions,
    ): List<PipelineStep> {
        val ws = ctx.wsPath
        val ap = members.first { OdinExtractor.classifySlot(it) == OdinSlot.AP }
        val apPath = "$rawDir/$ap"
        val odinDir = "$rawDir/odin"
        val steps = mutableListOf<PipelineStep>()
        if (OdinExtractor.isTarMd5(ap)) {
            steps += PipelineStep.Tool(OdinExtractor.verifyMd5Command(apPath, ws), "verify md5 AP")
        }
        steps += PipelineStep.Tool(stepCommand("mkdir", listOf("-p", odinDir), ws), "mkdir odin")
        steps += PipelineStep.Tool(OdinExtractor.extractTarCommand(apPath, odinDir, ws), "untar AP")
        steps += PipelineStep.Tool(
            command = stepCommand("tar", listOf("-tf", apPath), ws),
            label = "list AP",
            resultKey = ODIN_LISTING,
        )
        val apMembers = ctx.value(ODIN_LISTING)?.let(FirmwareLayouts::parseListing) ?: return steps
        val wanted = (parts.dynamic + parts.boot + "super").map { "$it.img" }.toSet()
        val images = apMembers.filter { it.removeSuffix(".lz4") in wanted }
        images.filter { it.endsWith(".lz4") }.forEach { member ->
            steps += PipelineStep.Tool(
                OdinExtractor.decompressLz4Command("$odinDir/$member", "$odinDir/${member.removeSuffix(".lz4")}", ws),
                "lz4 ${member.removeSuffix(".img.lz4")}",
            )
        }
        return steps + rawImageSteps(ctx, odinDir, images.map { it.removeSuffix(".lz4") }, parts)
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
        val declared = ctx.value(infoKey)?.let(FirmwareExtractionStage::parseImageSize)
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
}
