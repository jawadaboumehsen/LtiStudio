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
import org.ide.lti.core.domain.pipeline.digestToken
import org.ide.lti.core.domain.pipeline.stepCommand
import org.ide.lti.core.domain.pipeline.text.BuildPropStamper
import org.ide.lti.core.domain.pipeline.text.OtaMetadataWriter
import org.ide.lti.core.domain.pipeline.toolkit.AbOtaPackage
import org.ide.lti.core.model.workspace.PackageType

/**
 * The A/B OTA package's steps, after the images and the signed boot chain are staged: payload.bin, the metadata, the
 * zip signed twice (the second time with the members' offsets filled in, which must not move them), and a check of
 * the signed zip as update_engine reads it.
 */
internal object AbOtaSteps {

    /** The OTA zip's SHA-256 and size when the flashable zip is the main package (else [RuntimeKeys.ZIP_SHA256]). */
    const val SHA256: String = "package.ota.sha256"
    const val SIZE: String = "package.ota.size"
    private const val PROPS = "package.ota.props"
    private const val VERIFY = "package.ota.verify"

    fun steps(ctx: StageContext, stageDir: String, metadata: OtaMetadataWriter.OtaMetadataParams): List<PipelineStep> {
        val ws = ctx.wsPath
        val dir = ctx.abs(AbOtaPackage.DIR)
        val script = "$dir/${AbOtaPackage.SCRIPT_NAME}"
        val out = ctx.abs(BuildFlashableZipStage.otaRelPath(ctx))
        val buildProp = ctx.value(BuildFlashableZipStage.PACKAGE_BUILD_PROP)
        val sdk = BuildPropStamper.prop(buildProp, "ro.build.version.sdk")?.toIntOrNull()
        val partitions = ctx.target.dynamicPartitions.map { "$it=${ctx.abs("out/images/$it.img")}:dynamic" } +
            ctx.target.packagePolicy.flashableBootPartitions.map { "$it=${ctx.abs("$stageDir/$it.img")}" }
        fun python(vararg args: String) = stepCommand("python3", listOf("-I", script) + args, ws)
        val steps = mutableListOf<PipelineStep>(
            PipelineStep.Tool(stepCommand("rm", listOf("-rf", dir), ws), "clean A/B package"),
            PipelineStep.Tool(stepCommand("mkdir", listOf("-p", dir), ws), "mkdir A/B package"),
            PipelineStep.WriteFile(
                "${AbOtaPackage.DIR}/${AbOtaPackage.SCRIPT_NAME}",
                AbOtaPackage.SCRIPT.encodeToByteArray(),
            ),
            PipelineStep.Tool(
                python(
                    "payload",
                    dir,
                    ctx.abs("keys/platform.key.pem"),
                    metadata.postTimestamp.toString(),
                    metadata.postSecurityPatchLevel,
                    "${ctx.target.superGroupName}:${ctx.target.superGroupBytes}",
                    AbOtaPackage.vab(ctx.target.virtualAb, sdk),
                    *partitions.toTypedArray(),
                ),
                "payload.bin",
            ),
            PipelineStep.WriteFile(
                "${AbOtaPackage.DIR}/metadata",
                OtaMetadataWriter.generateAb(metadata).encodeToByteArray(),
            ),
        )
        // Zip and sign; then the offsets of the signed zip's members go into the metadata, which keeps its length,
        // and the zip is made and signed again. The second fill must find nothing to change.
        repeat(2) { pass ->
            steps += PipelineStep.Tool(python("zip", "$dir/ota.zip", dir, "$dir/metadata"), "zip A/B package")
            steps += signOrCopy(ctx, "$dir/ota.zip", out)
            steps += PipelineStep.Tool(
                python("props", out, "$dir/metadata"),
                "property files ${pass + 1}",
                resultKey = PROPS,
            )
        }
        steps += PipelineStep.Check("A/B package offsets") { values ->
            values[PROPS]?.trim()?.takeUnless { it.startsWith("unchanged") }
                ?.let { "OTA_LAYOUT: the signed A/B zip moved its members after the offsets were filled in ($it)" }
        }
        steps += PipelineStep.Tool(
            python("verify", out, ctx.abs("keys/platform.x509.pem")),
            "verify A/B package",
            resultKey = VERIFY,
        )
        // The only package is the build's main one: Release metadata and the run's artifacts read the main keys.
        val main = ctx.snapshot.build.packageType == PackageType.AB_OTA
        val shaKey = if (main) RuntimeKeys.ZIP_SHA256 else SHA256
        steps += PipelineStep.Tool(stepCommand("sha256sum", listOf(out), ws), "sha256 A/B package", resultKey = shaKey)
        steps += PipelineStep.Tool(
            stepCommand("stat", listOf("-c", "%s", out), ws),
            "stat A/B package",
            resultKey = if (main) RuntimeKeys.ZIP_SIZE else SIZE,
        )
        ctx.value(shaKey)?.digestToken()?.let { sha ->
            val name = BuildFlashableZipStage.otaRelPath(ctx).substringAfterLast('/')
            steps += PipelineStep.WriteFile(
                "${BuildFlashableZipStage.otaRelPath(ctx)}.sha256",
                "$sha  $name\n".encodeToByteArray(),
            )
        }
        return steps
    }

    /** signapk's whole-file signature (what recovery and update_engine check), or the zip as it is when unsigned. */
    private fun signOrCopy(ctx: StageContext, zip: String, out: String): PipelineStep =
        if (ctx.snapshot.build.signing.signPackage) {
            PipelineStep.Tool(
                stepCommand(
                    "signapk",
                    listOf("-w", ctx.abs("keys/platform.x509.pem"), ctx.abs("keys/platform.pk8"), zip, out),
                    ctx.wsPath,
                ),
                "signapk A/B package",
            )
        } else {
            PipelineStep.Tool(stepCommand("cp", listOf(zip, out), ctx.wsPath), "copy unsigned A/B package")
        }
}
