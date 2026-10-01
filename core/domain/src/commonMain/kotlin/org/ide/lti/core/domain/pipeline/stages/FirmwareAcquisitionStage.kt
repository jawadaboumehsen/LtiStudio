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
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.target.TargetRegion
import org.ide.lti.core.model.workspace.AcquisitionMode
import org.ide.lti.core.model.workspace.ConfigurationSnapshot

@Serializable
private data class AcquisitionPolicy(val retries: Int, val resume: Boolean, val region: TargetRegion)

/**
 * Stage 1 — obtain the firmware archive at `firmware/downloaded/<name>`.
 *
 * DOWNLOAD: resumable `curl` into `<name>.partial` (range fallback on exit 33), reject HTML
 * interstitials, verify the checksum when declared (else record UNVERIFIED), then promote with `mv`.
 * IMPORT_ARCHIVE: the archive was uploaded by the configuration panel; only its presence and checksum are checked.
 * EXISTING_ARCHIVE: verifies probe result was ok; no download or import steps are planned.
 */
public class FirmwareAcquisitionStage : StageDefinition {
    override val id: StageId = StageId.FIRMWARE_ACQUISITION

    override val requiredToolIds: Set<String> = setOf("curl", "file", "sha256sum", "test", "mv", "mkdir")

    override fun computeCacheKey(ctx: StageContext, previousStageCacheKey: String): String {
        val payload = ConfigurationSnapshot.canonicalJson.encodeToString(ctx.snapshot.acquisition)
        return CacheKeys.sha256(payload)
    }

    override fun outputs(ctx: StageContext): List<String> = listOf(archiveRelPath(ctx.snapshot))

    override fun plan(ctx: StageContext): List<PipelineStep> {
        if (ctx.snapshot.acquisition.mode in setOf(AcquisitionMode.IMPORT_ARCHIVE, AcquisitionMode.EXISTING_ARCHIVE) &&
            ctx.snapshot.acquisition.archiveRef.isNullOrBlank()
        ) {
            return listOf(
                PipelineStep.Check("archiveRef present") {
                    "ARCHIVE_REF_MISSING: archiveRef is required for ${ctx.snapshot.acquisition.mode}"
                },
            )
        }

        val ws = ctx.wsPath
        val archive = ctx.abs(archiveRelPath(ctx.snapshot))
        val steps = mutableListOf<PipelineStep>()

        when (ctx.snapshot.acquisition.mode) {
            AcquisitionMode.DOWNLOAD -> {
                steps += PipelineStep.Tool(
                    stepCommand("mkdir", listOf("-p", "$ws/firmware/downloaded"), ws),
                    "mkdir downloaded",
                )
                steps += downloadSteps(ctx, archive)
            }
            AcquisitionMode.IMPORT_ARCHIVE -> {
                steps += PipelineStep.Tool(stepCommand("test", listOf("-f", archive), ws), "imported archive present")
            }
            AcquisitionMode.EXISTING_ARCHIVE -> {
                steps += PipelineStep.Check("verify existing archive probe") { values ->
                    if (values["archive.probe.result"] != "ok") {
                        "Existing archive probe failed: expected archive.probe.result=ok, " +
                            "got ${values["archive.probe.result"]}"
                    } else {
                        null
                    }
                }
            }
        }

        steps += PipelineStep.Tool(
            command = stepCommand("sha256sum", listOf(archive), ws),
            label = "sha256sum archive",
            resultKey = RuntimeKeys.ARCHIVE_SHA256,
        )

        val policy = AcquisitionPolicy(
            retries = ctx.snapshot.acquisition.retries,
            resume = ctx.snapshot.acquisition.resume,
            region = ctx.snapshot.acquisition.region,
        )
        val policyJson = ConfigurationSnapshot.canonicalJson.encodeToString(policy)
        steps += PipelineStep.WriteFile(
            relPath = "work/acquire/policy.json",
            content = policyJson.encodeToByteArray(),
            label = "write work/acquire/policy.json",
        )

        val expectedSha = ctx.snapshot.acquisition.expectedSha256
            ?: ctx.snapshot.acquisition.firmware.sha256?.takeIf { it.isNotBlank() }

        if (ctx.has(RuntimeKeys.ARCHIVE_SHA256) && !expectedSha.isNullOrBlank()) {
            steps += PipelineStep.Check("verify archive checksum") { values ->
                checksumVerdict(expectedSha, values)
            }
        }
        return steps
    }

    private fun downloadSteps(ctx: StageContext, archive: String): List<PipelineStep> {
        val ws = ctx.wsPath
        val url = requireNotNull(ctx.snapshot.acquisition.firmware.acquisitionUrl?.takeIf { it.isNotBlank() }) {
            "Firmware ${ctx.snapshot.acquisition.firmware.version} has no download URL; import the archive instead"
        }
        val partial = "$archive.partial"
        val steps = mutableListOf<PipelineStep>()

        // Resumable first; curl exits 33 when the server rejects the range → plain download.
        steps += PipelineStep.Tool(
            command = stepCommand("curl", listOf("-fL", "--retry", "3", "-C", "-", "-o", partial, url), ws),
            label = "curl resume",
            resultKey = RuntimeKeys.DOWNLOAD_RESUME,
            allowFailure = true,
        )
        val resumeExit = ctx.exitOf(RuntimeKeys.DOWNLOAD_RESUME)
        if (resumeExit == CURL_RANGE_NOT_SUPPORTED) {
            steps += PipelineStep.Tool(
                stepCommand("curl", listOf("-fL", "--retry", "3", "-o", partial, url), ws),
                "curl plain",
            )
        } else if (resumeExit != null && resumeExit != 0) {
            steps += PipelineStep.Check("download failed") { "curl exited with $resumeExit while downloading $url" }
        }

        steps += PipelineStep.Tool(
            command = stepCommand("file", listOf("-b", "--mime-type", partial), ws),
            label = "detect archive type",
            resultKey = RuntimeKeys.ARCHIVE_MIME,
        )
        steps += PipelineStep.Check("reject html interstitial") { values ->
            val mime = values[RuntimeKeys.ARCHIVE_MIME]?.trim().orEmpty()
            if (mime.startsWith("text/html")) {
                "The download URL returned an HTML page (login or confirmation interstitial), not an archive. " +
                    "Download the firmware in a browser and use Import Archive."
            } else {
                null
            }
        }
        steps += PipelineStep.Tool(stepCommand("mv", listOf(partial, archive), ws), "promote archive")
        return steps
    }

    private fun checksumVerdict(expected: String, values: Map<String, String>): String? {
        val actual = values[RuntimeKeys.ARCHIVE_SHA256]?.digestToken().orEmpty()
        return when {
            actual.isEmpty() -> "sha256sum produced no digest for the firmware archive"
            actual.equals(expected, ignoreCase = true) -> null
            else -> "CHECKSUM_MISMATCH: Firmware archive checksum mismatch: expected $expected, got $actual"
        }
    }

    public companion object {
        private const val CURL_RANGE_NOT_SUPPORTED = 33

        public fun archiveName(snapshot: ConfigurationSnapshot): String = when (snapshot.acquisition.mode) {
            AcquisitionMode.IMPORT_ARCHIVE, AcquisitionMode.EXISTING_ARCHIVE ->
                snapshot.acquisition.archiveRef?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                    ?: snapshot.acquisition.archiveRef.orEmpty()
            AcquisitionMode.DOWNLOAD -> {
                val fromUrl = snapshot.acquisition.firmware.acquisitionUrl?.substringAfterLast(
                    '/',
                )?.substringBefore('?')
                fromUrl?.takeIf { it.isNotBlank() } ?: "${snapshot.acquisition.firmware.version}.zip"
            }
        }

        public fun archiveRelPath(snapshot: ConfigurationSnapshot): String = when (snapshot.acquisition.mode) {
            AcquisitionMode.IMPORT_ARCHIVE, AcquisitionMode.EXISTING_ARCHIVE ->
                snapshot.acquisition.archiveRef.orEmpty()
            AcquisitionMode.DOWNLOAD ->
                "firmware/downloaded/${archiveName(snapshot)}"
        }
    }
}
