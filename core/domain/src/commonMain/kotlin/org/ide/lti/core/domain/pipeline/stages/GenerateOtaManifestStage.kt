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
import org.ide.lti.core.domain.pipeline.digestToken
import org.ide.lti.core.domain.pipeline.stepCommand
import org.ide.lti.core.domain.pipeline.text.BuildPropStamper
import org.ide.lti.core.domain.pipeline.text.OtaManifestWriter
import org.ide.lti.core.model.run.StageId

/**
 * Stage 6 — `out/manifest.json` (`{"response":[…]}`) from the signed zip's real size and sha256,
 * the packaged `build_info.txt` and `changelog.md`, and the snapshot's OTA base URL, channel, and changelog.
 *
 * A channel value is internal metadata unless a consumer schema supports it.
 */
public class GenerateOtaManifestStage : StageDefinition {
    override val id: StageId = StageId.GENERATE_OTA_MANIFEST
    override val requiredToolIds: Set<String> = setOf("sha256sum", "stat", "cat")

    override fun computeCacheKey(ctx: StageContext, previousStageCacheKey: String): String =
        CacheKeys.stage6ManifestKey(previousStageCacheKey, ctx.snapshot)

    override fun outputs(ctx: StageContext): List<String> = listOf(MANIFEST)

    override fun plan(ctx: StageContext): List<PipelineStep> {
        val ws = ctx.wsPath
        val zip = ctx.abs(BuildFlashableZipStage.zipRelPath(ctx))
        val steps = mutableListOf<PipelineStep>()
        steps += PipelineStep.Tool(
            stepCommand("sha256sum", listOf(zip), ws),
            "sha256 zip",
            resultKey = RuntimeKeys.ZIP_SHA256,
        )
        steps += PipelineStep.Tool(
            stepCommand("stat", listOf("-c", "%s", zip), ws),
            "stat zip",
            resultKey = RuntimeKeys.ZIP_SIZE,
        )
        steps += PipelineStep.Tool(
            stepCommand("cat", listOf(ctx.abs("${BuildFlashableZipStage.STAGE_DIR}/build_info.txt")), ws),
            "read build_info",
            resultKey = BUILD_INFO,
        )
        steps += PipelineStep.Tool(
            stepCommand("cat", listOf(ctx.abs("${BuildFlashableZipStage.STAGE_DIR}/changelog.md")), ws),
            "read changelog",
            resultKey = CHANGELOG,
        )
        val captured = listOf(RuntimeKeys.ZIP_SHA256, RuntimeKeys.ZIP_SIZE, BUILD_INFO, CHANGELOG).all(ctx::has)
        if (captured) steps += manifestStep(ctx)
        return steps
    }

    private fun manifestStep(ctx: StageContext): PipelineStep {
        val sha = ctx.value(RuntimeKeys.ZIP_SHA256).orEmpty().digestToken()
        val size = ctx.longValue(RuntimeKeys.ZIP_SIZE) ?: 0L
        val buildInfo = ctx.value(BUILD_INFO).orEmpty()
        val packagedChangelog = ctx.value(CHANGELOG).orEmpty()
        val timestamp = BuildPropStamper.prop(buildInfo, "ro.lti.timestamp")?.toLongOrNull()
        val incremental = BuildPropStamper.prop(buildInfo, "ro.build.version.incremental")
        if (timestamp == null || incremental == null) {
            return PipelineStep.Check("build_info fields") {
                "build_info.txt is missing ro.lti.timestamp or ro.build.version.incremental"
            }
        }
        val effectiveChangelog = ctx.snapshot.release.changelog.ifBlank { packagedChangelog }
        val baseManifest = OtaManifestWriter.generate(
            datetime = timestamp,
            device = ctx.target.id,
            filename = BuildFlashableZipStage.zipName(ctx),
            patch = ctx.snapshot.assembly.romVersion,
            size = size,
            otaBaseUrl = ctx.snapshot.release.otaBaseUrl,
            version = ctx.snapshot.assembly.romVersion,
            incremental = incremental,
            sha256 = sha,
            changelog = effectiveChangelog,
        )
        val channel = ctx.snapshot.release.channel
        val manifest = if (baseManifest.contains("\"changelog\":")) {
            baseManifest.replace(
                "\"changelog\":",
                "\"channel\": \"$channel\",\n            \"changelog\":",
            )
        } else {
            baseManifest
        }
        return PipelineStep.WriteFile(MANIFEST, manifest.encodeToByteArray())
    }

    private companion object {
        const val MANIFEST = "out/manifest.json"
        const val BUILD_INFO = "package.build_info"
        const val CHANGELOG = "package.changelog"
    }
}
