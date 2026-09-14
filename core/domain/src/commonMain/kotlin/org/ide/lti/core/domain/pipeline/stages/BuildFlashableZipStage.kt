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

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.ide.lti.core.domain.pipeline.CacheKeys
import org.ide.lti.core.domain.pipeline.PipelineStep
import org.ide.lti.core.domain.pipeline.RuntimeKeys
import org.ide.lti.core.domain.pipeline.StageContext
import org.ide.lti.core.domain.pipeline.StageDefinition
import org.ide.lti.core.domain.pipeline.digestToken
import org.ide.lti.core.domain.pipeline.stepCommand
import org.ide.lti.core.domain.pipeline.text.BuildPropStamper
import org.ide.lti.core.domain.pipeline.text.DynamicPartitionsOpListWriter
import org.ide.lti.core.domain.pipeline.text.OtaMetadataWriter
import org.ide.lti.core.domain.pipeline.text.UpdaterScriptWriter
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.target.MetadataPolicy
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.ConfigurationSnapshot

@Serializable
private data class PackagingPolicy(
    val excludeVbmeta: Boolean,
    val recoverySystemMountPoint: String,
    val metadataPolicy: MetadataPolicy,
    val bootSlots: List<String>,
)

/**
 * Stage 5 — EROFS images with AVB footers, stock boot chain, super metadata, block-dat members,
 * generated META-INF, zip + sign, policy assertions (contract Stage 5).
 *
 * Every size, digest and salt comes from a captured step result; nothing is defaulted.
 */
public class BuildFlashableZipStage : StageDefinition {
    override val id: StageId = StageId.BUILD_FLASHABLE_ZIP

    override val requiredToolIds: Set<String> = setOf(
        "mkfs.erofs", "avbtool", "lpmake", "img2sdat", "brotli", "zip", "unzip", "signapk",
        "sha256sum", "sha1sum", "stat", "cp", "rm", "mkdir",
    )

    // Key digests are captured inside the stage, so the input key uses the key *references*;
    // the output digest (which covers the footered images) catches a key swap.
    override fun computeCacheKey(ctx: StageContext, previousStageCacheKey: String): String =
        CacheKeys.stage5BuildZipKey(
            previousStageCacheKey,
            ctx.snapshot,
            ctx.snapshot.build.signing.avbKeyRef,
            ctx.snapshot.build.signing.platformKeyRef,
        )

    override fun outputs(ctx: StageContext): List<String> = ctx.target.dynamicPartitions.map { "out/images/$it.img" } +
        listOf(zipRelPath(ctx), "${zipRelPath(ctx)}.sha256") +
        if (!ctx.snapshot.build.signing.signPackage) listOf("out/package/UNSIGNED") else emptyList()

    override fun plan(ctx: StageContext): List<PipelineStep> {
        if (!ctx.snapshot.build.filesystem.equals("erofs", ignoreCase = true)) {
            return listOf(
                PipelineStep.Check("verify filesystem") {
                    "UNSUPPORTED_FILESYSTEM: Filesystem '${ctx.snapshot.build.filesystem}' is not supported; " +
                        "only 'erofs' is supported"
                },
            )
        }

        val ws = ctx.wsPath
        val stage = ctx.abs(STAGE_DIR)
        val steps = mutableListOf<PipelineStep>()
        steps += PipelineStep.Tool(
            stepCommand(
                "mkdir",
                listOf(
                    "-p",
                    ctx.abs("out/images"),
                    "$stage/META-INF/com/google/android",
                    "$stage/META-INF/com/android",
                ),
                ws,
            ),
            "mkdir staging",
        )
        steps += PipelineStep.Tool(
            command = stepCommand("sha1sum", listOf(ctx.abs("keys/avb.avbpubkey")), ws),
            label = "sha1 avb pubkey",
            resultKey = RuntimeKeys.AVB_PUBKEY_SHA1,
        )
        steps += PipelineStep.Tool(
            command = stepCommand("sha1sum", listOf(ctx.abs("keys/platform.x509.pem")), ws),
            label = "sha1 platform cert",
            resultKey = PLATFORM_CERT_SHA1,
        )
        ctx.target.dynamicPartitions.forEach { p -> steps += imageSteps(ctx, p) }
        if (ctx.target.dynamicPartitions.all { ctx.has(footeredSize(it)) }) {
            ctx.snapshot.build.packagePolicy.flashableBootPartitions.forEach { b -> steps += bootSteps(ctx, b) }
            steps += superSteps(ctx)
            ctx.target.dynamicPartitions.forEach { p -> steps += blockDatSteps(ctx, p) }
            steps += generatedMembers(ctx)
            steps += packageSteps(ctx)
        }
        return steps
    }

    private fun imageSteps(ctx: StageContext, p: String): List<PipelineStep> {
        val ws = ctx.wsPath
        val img = ctx.abs("out/images/$p.img")
        val steps = mutableListOf<PipelineStep>()
        val compressionArg = if (ctx.snapshot.build.level != null) {
            "${ctx.snapshot.build.compression.substringBefore(',')},${ctx.snapshot.build.level}"
        } else {
            ctx.snapshot.build.compression
        }
        steps += PipelineStep.Tool(
            stepCommand(
                "mkfs.erofs",
                listOf(
                    "--quiet", "-z", compressionArg, "-b", ctx.snapshot.build.blockSize.toString(),
                    "--mount-point", if (p == "system") "/" else p,
                    "--fs-config-file", ctx.abs("work/configs/fs_config-$p"),
                    "--file-contexts", ctx.abs("work/configs/file_context-$p"),
                    "-T", EROFS_TIMESTAMP,
                    img, ctx.abs("work/$p"),
                ),
                ws,
            ),
            "mkfs.erofs $p",
        )
        steps += PipelineStep.Tool(
            stepCommand("sha256sum", listOf(img), ws),
            "sha256 $p",
            resultKey = RuntimeKeys.imageSha256(p),
        )
        steps += PipelineStep.Tool(
            stepCommand("stat", listOf("-c", "%s", img), ws),
            "stat $p",
            resultKey = RuntimeKeys.imageSize(p),
        )
        val imageSize = ctx.longValue(RuntimeKeys.imageSize(p))
        val salt = ctx.value(RuntimeKeys.imageSha256(p))?.digestToken()
        if (imageSize != null && salt != null) {
            steps += PipelineStep.AvbSizeSearch(imageSize, RuntimeKeys.partitionSize(p), "avb size search $p")
            ctx.longValue(RuntimeKeys.partitionSize(p))?.let { steps += footerSteps(ctx, p, salt, it) }
        }
        return steps
    }

    private fun footerSteps(ctx: StageContext, p: String, salt: String, partitionSize: Long): List<PipelineStep> {
        val ws = ctx.wsPath
        val img = ctx.abs("out/images/$p.img")
        val steps = mutableListOf<PipelineStep>()
        val key = ctx.abs(ctx.snapshot.build.signing.avbKeyRef)
        steps += PipelineStep.Tool(
            stepCommand(
                "avbtool",
                listOf(
                    "add_hashtree_footer", "--image", img, "--partition_size", partitionSize.toString(),
                    "--partition_name", p, "--hash_algorithm", "sha256", "--algorithm", "SHA256_RSA4096",
                    "--key", key, "--salt", salt,
                ),
                ws,
            ),
            "add_hashtree_footer $p",
        )
        steps += PipelineStep.Tool(
            stepCommand("avbtool", listOf("verify_image", "--image", img, "--key", key), ws),
            "verify_image $p",
        )
        steps += PipelineStep.Tool(
            command = stepCommand("avbtool", listOf("info_image", "--image", img), ws),
            label = "info_image $p",
            resultKey = RuntimeKeys.footerInfo(p),
            expectStdoutContains = listOf(
                "Partition Name:",
                p,
                "Hash Algorithm:",
                "sha256",
                "Algorithm:",
                "SHA256_RSA4096",
            ),
        )
        steps += PipelineStep.Tool(
            stepCommand("stat", listOf("-c", "%s", img), ws),
            "stat footered $p",
            resultKey = footeredSize(p),
        )
        steps += PipelineStep.Check("image aligned $p") { values ->
            val size = values[footeredSize(p)]?.trim()?.toLongOrNull()
            when {
                size == null -> "No size captured for out/images/$p.img"
                size % BLOCK != 0L -> "out/images/$p.img size $size is not a multiple of $BLOCK"
                else -> null
            }
        }
        return steps
    }

    private fun bootSteps(ctx: StageContext, b: String): List<PipelineStep> {
        val ws = ctx.wsPath
        val staged = ctx.abs("$STAGE_DIR/$b.img")
        val expected = bootPartitionBytes(ctx.target, b)
        return listOf(
            PipelineStep.Tool(
                stepCommand("cp", listOf("-a", ctx.abs("${FirmwareExtractionStage.EXTRACTED}/$b.img"), staged), ws),
                "stage stock $b",
            ),
            PipelineStep.Tool(
                stepCommand("stat", listOf("-c", "%s", staged), ws),
                "stat stock $b",
                resultKey = stagedBootSize(b),
            ),
            PipelineStep.Check("stock $b size") { values ->
                val size = values[stagedBootSize(b)]?.trim()?.toLongOrNull()
                if (size == expected) null else "Stock $b.img is $size bytes; profile declares $expected"
            },
            PipelineStep.Tool(
                stepCommand("avbtool", listOf("info_image", "--image", staged), ws),
                "avbtool info stock $b",
            ),
        )
    }

    private fun superSteps(ctx: StageContext): List<PipelineStep> {
        val ws = ctx.wsPath
        val target = ctx.target
        val group = target.superGroupName
        val infos = partitionInfos(ctx)
        val lpmakeArgs = mutableListOf(
            "--metadata-size", "65536", "--super-name", "super",
            "--metadata-slots", target.superMetadataSlots.toString(),
            "--device", "super:${target.superPartitionBytes}",
            "--group", "$group:${target.superGroupBytes}",
        )
        if (target.virtualAb) lpmakeArgs += "--virtual-ab"
        if (ctx.snapshot.build.alignment != null) {
            lpmakeArgs += listOf("--alignment", ctx.snapshot.build.alignment.toString())
        }
        target.dynamicPartitions.forEach { lpmakeArgs += listOf("--partition", "$it:readonly:0:$group") }
        lpmakeArgs += listOf("--output", ctx.abs("$STAGE_DIR/unsparse_super_empty.img"))
        return listOf(
            PipelineStep.Check("images fit super group") {
                val total = infos.sumOf { it.sizeBytes }
                if (total <= target.superGroupBytes) {
                    null
                } else {
                    "Images total $total bytes exceed super group ${target.superGroupBytes}"
                }
            },
            PipelineStep.Tool(stepCommand("lpmake", lpmakeArgs, ws), "lpmake"),
            PipelineStep.WriteFile(
                "$STAGE_DIR/dynamic_partitions_op_list",
                DynamicPartitionsOpListWriter.generateOpList(group, target.superGroupBytes, infos).encodeToByteArray(),
            ),
            PipelineStep.WriteFile(
                "$STAGE_DIR/avb-images.txt",
                DynamicPartitionsOpListWriter.generateAvbImages(infos).encodeToByteArray(),
            ),
        )
    }

    private fun blockDatSteps(ctx: StageContext, p: String): List<PipelineStep> {
        val ws = ctx.wsPath
        val stage = ctx.abs(STAGE_DIR)
        return listOf(
            PipelineStep.Tool(
                stepCommand("img2sdat", listOf("-o", stage, ctx.abs("out/images/$p.img")), ws),
                "img2sdat $p",
            ),
            PipelineStep.Tool(
                stepCommand(
                    "brotli",
                    listOf("-f", "--quality=6", "--output=$stage/$p.new.dat.br", "$stage/$p.new.dat"),
                    ws,
                ),
                "brotli $p",
            ),
            PipelineStep.Tool(stepCommand("rm", listOf("-f", "$stage/$p.new.dat"), ws), "rm dat $p"),
        )
    }

    private fun generatedMembers(ctx: StageContext): List<PipelineStep> {
        val buildProp = ctx.value(RuntimeKeys.BUILD_PROP)
        val incremental = BuildPropStamper.prop(buildProp, "ro.build.version.incremental")
        val fingerprint = BuildPropStamper.prop(buildProp, "ro.build.fingerprint")
        val sdk = BuildPropStamper.prop(buildProp, "ro.build.version.sdk")
        val patch = BuildPropStamper.prop(buildProp, "ro.build.version.security_patch")
            ?: ctx.snapshot.acquisition.firmware.securityPatch
        val timestamp = ctx.longValue(RuntimeKeys.RUN_TIMESTAMP)
        val date = ctx.value(RuntimeKeys.RUN_DATE)
        val missing = listOfNotNull(
            "ro.build.version.incremental".takeIf { incremental == null },
            "ro.build.fingerprint".takeIf { fingerprint == null },
            "ro.build.version.sdk".takeIf { sdk == null },
            "run timestamp".takeIf { timestamp == null || date == null },
        )
        if (missing.isNotEmpty()) {
            return listOf(
                PipelineStep.Check("stock build.prop fields") {
                    "Cannot generate package metadata; missing ${missing.joinToString()}"
                },
            )
        }
        val rawScript = UpdaterScriptWriter.generate(
            ctx.target,
            ctx.snapshot.build.packagePolicy,
            ctx.snapshot.assembly.romVersion,
        )
        val script = buildString {
            append(
                "# package_policy: metadataPolicy=${ctx.snapshot.build.packagePolicy.metadataPolicy}, " +
                    "excludeVbmeta=${ctx.snapshot.build.packagePolicy.excludeVbmeta}\n",
            )
            append(rawScript)
        }
        val buildInfo = org.ide.lti.core.domain.pipeline.text.BuildInfoWriter.generate(
            org.ide.lti.core.domain.pipeline.text.BuildInfoWriter.BuildInfoParams(
                device = ctx.target.id,
                version = ctx.snapshot.assembly.romVersion,
                timestamp = requireNotNull(timestamp),
                incremental = requireNotNull(incremental),
                securityPatch = patch,
            ),
        )
        val metadata = OtaMetadataWriter.generate(
            OtaMetadataWriter.OtaMetadataParams(
                postBuild = requireNotNull(fingerprint),
                postBuildIncremental = incremental,
                postSdkLevel = requireNotNull(sdk),
                postSecurityPatchLevel = patch,
                postTimestamp = timestamp,
                preDevice = ctx.target.id,
            ),
        )
        val changelog = "# LtiRom ${ctx.snapshot.assembly.romVersion} for ${ctx.target.name}\n\n" +
            "Built $date from ${ctx.snapshot.acquisition.firmware.version} (${ctx.snapshot.acquisition.region.name}).\n"

        val policy = PackagingPolicy(
            excludeVbmeta = ctx.snapshot.build.packagePolicy.excludeVbmeta,
            recoverySystemMountPoint = ctx.snapshot.build.packagePolicy.recoverySystemMountPoint,
            metadataPolicy = ctx.snapshot.build.packagePolicy.metadataPolicy,
            bootSlots = ctx.snapshot.build.packagePolicy.bootSlots,
        )
        val policyJson = ConfigurationSnapshot.canonicalJson.encodeToString(policy)

        return listOf(
            PipelineStep.Check("updater-script policy") {
                val updates = rawScript.lineSequence().count { it.contains("block_image_update(") }
                when {
                    updates != ctx.target.dynamicPartitions.size ->
                        "updater-script has $updates block_image_update calls, " +
                            "expected ${ctx.target.dynamicPartitions.size}"
                    ctx.snapshot.build.packagePolicy.excludeVbmeta && rawScript.contains("vbmeta") ->
                        "updater-script must not reference vbmeta"
                    else -> null
                }
            },
            PipelineStep.WriteFile(
                relPath = "work/package/policy.json",
                content = policyJson.encodeToByteArray(),
                label = "write work/package/policy.json",
            ),
            PipelineStep.WriteFile("$STAGE_DIR/META-INF/com/google/android/updater-script", script.encodeToByteArray()),
            PipelineStep.WriteVendoredFile(
                "$STAGE_DIR/META-INF/com/google/android/update-binary",
                UPDATE_BINARY_RESOURCE,
            ),
            PipelineStep.WriteFile("$STAGE_DIR/META-INF/com/android/metadata", metadata.encodeToByteArray()),
            PipelineStep.WriteFile("$STAGE_DIR/build_info.txt", buildInfo.encodeToByteArray()),
            PipelineStep.WriteFile("$STAGE_DIR/changelog.md", changelog.encodeToByteArray()),
        )
    }

    private fun packageSteps(ctx: StageContext): List<PipelineStep> {
        val ws = ctx.wsPath
        val stage = ctx.abs(STAGE_DIR)
        val signed = ctx.abs(zipRelPath(ctx))
        val steps = mutableListOf<PipelineStep>()
        steps += PipelineStep.Tool(
            stepCommand(
                "zip",
                listOf("-0", "-r", "rom.zip", "META-INF/com/android", "*.new.dat.br", "*.patch.dat"),
                stage,
            ),
            "zip stored members",
        )
        val zipLevel = ctx.snapshot.build.level?.let { "-$it" } ?: "-3"
        steps += PipelineStep.Tool(
            stepCommand(
                "zip",
                listOf(
                    zipLevel,
                    "-r",
                    "rom.zip",
                    ".",
                    "-x",
                    "META-INF/com/android/*",
                    "-x",
                    "*.new.dat.br",
                    "-x",
                    "*.patch.dat",
                    "-x",
                    "rom.zip",
                ),
                stage,
            ),
            "zip deflated members",
        )

        if (ctx.snapshot.build.signing.signPackage) {
            val signapkArgs = mutableListOf("-w")
            if (ctx.snapshot.build.alignment != null) {
                signapkArgs += listOf("-a", ctx.snapshot.build.alignment.toString())
            }
            signapkArgs += listOf(
                ctx.abs("keys/platform.x509.pem"),
                ctx.abs("keys/platform.pk8"),
                "$stage/rom.zip",
                signed,
            )
            steps += PipelineStep.Tool(
                stepCommand("signapk", signapkArgs, ws),
                "signapk",
            )
        } else {
            steps += PipelineStep.Tool(
                stepCommand("cp", listOf("$stage/rom.zip", signed), ws),
                "copy unsigned zip",
            )
            steps += PipelineStep.WriteFile(
                relPath = "out/package/UNSIGNED",
                content = "UNSIGNED\n".encodeToByteArray(),
                label = "write out/package/UNSIGNED",
            )
        }

        steps += PipelineStep.Tool(
            stepCommand("sha256sum", listOf(signed), ws),
            "sha256 zip",
            resultKey = RuntimeKeys.ZIP_SHA256,
        )
        steps += PipelineStep.Tool(
            stepCommand("stat", listOf("-c", "%s", signed), ws),
            "stat zip",
            resultKey = RuntimeKeys.ZIP_SIZE,
        )
        val zipSha = ctx.value(RuntimeKeys.ZIP_SHA256)?.digestToken() ?: return steps
        steps += PipelineStep.WriteFile("${zipRelPath(ctx)}.sha256", "$zipSha  ${zipName(ctx)}\n".encodeToByteArray())
        steps += PipelineStep.Tool(
            stepCommand("unzip", listOf("-l", signed), ws),
            "list zip",
            resultKey = RuntimeKeys.ZIP_LISTING,
        )
        steps += PipelineStep.Check("flashable zip policy") { values ->
            zipPolicyVerdict(ctx, values[RuntimeKeys.ZIP_LISTING].orEmpty())
        }
        return steps
    }

    private fun zipPolicyVerdict(ctx: StageContext, listing: String): String? {
        val members = listing.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
            .mapNotNull { line -> line.split(Regex("\\s+"), limit = 4).getOrNull(3) }.toSet()
        val problems = mutableListOf<String>()
        ctx.target.dynamicPartitions.forEach { p ->
            if ("$p.new.dat.br" !in members || "$p.transfer.list" !in members) {
                problems += "missing block-dat members for $p"
            }
        }
        ctx.snapshot.build.packagePolicy.flashableBootPartitions.forEach { b ->
            if ("$b.img" !in members) problems += "missing stock $b.img"
        }
        REQUIRED_MEMBERS.forEach { m -> if (m !in members) problems += "missing $m" }
        if (ctx.snapshot.build.packagePolicy.excludeVbmeta) {
            members.filter { it.substringAfterLast('/').startsWith("vbmeta") }
                .forEach { problems += "forbidden member $it" }
        }
        return problems.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    private fun partitionInfos(ctx: StageContext): List<DynamicPartitionsOpListWriter.PartitionImageInfo> =
        ctx.target.dynamicPartitions.map { p ->
            DynamicPartitionsOpListWriter.PartitionImageInfo(
                partitionName = p,
                sizeBytes = requireNotNull(ctx.longValue(footeredSize(p))) { "No footered size for $p" },
                sha256 = requireNotNull(ctx.value(RuntimeKeys.imageSha256(p))?.digestToken()) { "No sha256 for $p" },
                pubkeySha1 = requireNotNull(ctx.value(RuntimeKeys.AVB_PUBKEY_SHA1)?.digestToken()) {
                    "No AVB pubkey sha1"
                },
            )
        }

    public companion object {
        public const val STAGE_DIR: String = "out/package/.stage"
        public const val UPDATE_BINARY_RESOURCE: String = "updater/update-binary"
        public const val PLATFORM_CERT_SHA1: String = "keys.platform.cert.sha1"
        private const val EROFS_TIMESTAMP = "1640995200"
        private const val BLOCK = 4096L
        private val REQUIRED_MEMBERS = listOf(
            "META-INF/com/android/otacert",
            "META-INF/com/android/metadata",
            "META-INF/com/google/android/update-binary",
            "META-INF/com/google/android/updater-script",
            "dynamic_partitions_op_list",
            "unsparse_super_empty.img",
            "build_info.txt",
            "changelog.md",
        )

        public fun footeredSize(p: String): String = "image.$p.footeredSize"
        public fun stagedBootSize(b: String): String = "package.boot.$b.size"

        public fun zipName(ctx: StageContext): String {
            val date = requireNotNull(ctx.value(RuntimeKeys.RUN_DATE)) { "run date missing from runtime values" }
            return ctx.snapshot.build.filenameTemplate
                .replace("{version}", ctx.snapshot.assembly.romVersion)
                .replace("{date}", date)
                .replace("{device}", ctx.target.id)
        }

        public fun zipRelPath(ctx: StageContext): String = "out/package/${zipName(ctx)}"

        public fun bootPartitionBytes(target: TargetDevice, image: String): Long = when (image) {
            "boot" -> target.bootPartitionBytes
            "vendor_boot" -> target.vendorBootPartitionBytes
            "init_boot" -> target.initBootPartitionBytes
            "dtbo" -> target.dtboPartitionBytes
            else -> error("No partition size declared for boot image '$image'")
        }
    }
}
