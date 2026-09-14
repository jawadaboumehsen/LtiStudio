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
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.workspace.AcquisitionMode
import org.ide.lti.core.model.workspace.ConfigurationSnapshot

/**
 * Stage 1 — obtain the firmware archive at `firmware/downloaded/<name>`.
 *
 * DOWNLOAD: resumable `curl` into `<name>.partial` (range fallback on exit 33), reject HTML
 * interstitials, verify the checksum when the profile declares one (else record UNVERIFIED), then
 * promote with `mv`. IMPORT_ARCHIVE: the archive was uploaded by the configuration panel; only its
 * presence and checksum are checked.
 */
public class FirmwareAcquisitionStage : StageDefinition {
    override val id: StageId = StageId.FIRMWARE_ACQUISITION

    override val requiredToolIds: Set<String> = setOf("curl", "file", "sha256sum", "test", "mv", "mkdir")

    override fun computeCacheKey(ctx: StageContext, previousStageCacheKey: String): String =
        CacheKeys.stage1AcquisitionKey(ctx.snapshot)

    override fun outputs(ctx: StageContext): List<String> = listOf(archiveRelPath(ctx.snapshot))

    override fun plan(ctx: StageContext): List<PipelineStep> {
        val ws = ctx.wsPath
        val archive = ctx.abs(archiveRelPath(ctx.snapshot))
        val steps = mutableListOf<PipelineStep>()
        steps += PipelineStep.Tool(
            stepCommand("mkdir", listOf("-p", "$ws/firmware/downloaded"), ws),
            "mkdir downloaded",
        )

        if (ctx.snapshot.acquisition.mode == AcquisitionMode.DOWNLOAD) {
            steps += downloadSteps(ctx, archive)
        } else {
            steps += PipelineStep.Tool(stepCommand("test", listOf("-f", archive), ws), "imported archive present")
        }

        steps += PipelineStep.Tool(
            command = stepCommand("sha256sum", listOf(archive), ws),
            label = "sha256sum archive",
            resultKey = RuntimeKeys.ARCHIVE_SHA256,
        )
        if (ctx.has(RuntimeKeys.ARCHIVE_SHA256)) {
            steps += PipelineStep.Check("verify archive checksum") { values -> checksumVerdict(ctx.snapshot, values) }
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

    private fun checksumVerdict(snapshot: ConfigurationSnapshot, values: Map<String, String>): String? {
        val actual = values[RuntimeKeys.ARCHIVE_SHA256]?.digestToken().orEmpty()
        val expected = snapshot.acquisition.firmware.sha256?.lowercase()
        return when {
            actual.isEmpty() -> "sha256sum produced no digest for the firmware archive"
            expected.isNullOrBlank() -> null // recorded as UNVERIFIED by the orchestrator
            actual.equals(expected, ignoreCase = true) -> null
            else -> "Firmware archive checksum mismatch: expected $expected, got $actual"
        }
    }

    public companion object {
        private const val CURL_RANGE_NOT_SUPPORTED = 33

        public fun archiveName(snapshot: ConfigurationSnapshot): String = when (snapshot.acquisition.mode) {
            AcquisitionMode.IMPORT_ARCHIVE, AcquisitionMode.EXISTING_ARCHIVE ->
                requireNotNull(snapshot.acquisition.archiveRef) {
                    "Archive mode requires an archive ref"
                }
            AcquisitionMode.DOWNLOAD -> {
                val fromUrl = snapshot.acquisition.firmware.acquisitionUrl?.substringAfterLast(
                    '/',
                )?.substringBefore('?')
                fromUrl?.takeIf { it.isNotBlank() } ?: "${snapshot.acquisition.firmware.version}.zip"
            }
        }

        public fun archiveRelPath(snapshot: ConfigurationSnapshot): String =
            "firmware/downloaded/${archiveName(snapshot)}"
    }
}
