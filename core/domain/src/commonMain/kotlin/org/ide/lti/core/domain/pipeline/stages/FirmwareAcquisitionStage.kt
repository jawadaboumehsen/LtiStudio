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
import org.ide.lti.core.domain.pipeline.toolkit.ChecksumResult
import org.ide.lti.core.domain.pipeline.toolkit.FormatDetector
import org.ide.lti.core.domain.pipeline.toolkit.PackageFormat
import org.ide.lti.core.domain.pipeline.toolkit.PackageManifest
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.target.TargetRegion
import org.ide.lti.core.model.workspace.AcquisitionMode
import org.ide.lti.core.model.workspace.AcquisitionSettings
import org.ide.lti.core.model.workspace.ConfigurationSnapshot

@Serializable
private data class AcquisitionPolicy(val retries: Int, val resume: Boolean, val region: TargetRegion)

/**
 * Stage 1 — obtain the firmware archive at `firmware/downloaded/<name>`.
 *
 * DOWNLOAD: resumable `curl` into `<name>.partial` (range fallback on exit 33), reject HTML
 * interstitials, verify the checksum when declared (else record UNVERIFIED), then promote with `mv`.
 * IMPORT_ARCHIVE: the archive was uploaded by the configuration panel; only its presence and checksum are checked.
 */
public class FirmwareAcquisitionStage : StageDefinition {
    override val id: StageId = StageId.FIRMWARE_ACQUISITION

    override val requiredToolIds: Set<String> = setOf("curl", "file", "sha256sum", "xxd", "test", "mv", "mkdir")

    override fun computeCacheKey(ctx: StageContext, previousStageCacheKey: String): String {
        val payload = ConfigurationSnapshot.canonicalJson.encodeToString(ctx.snapshot.acquisition)
        return CacheKeys.sha256(payload)
    }

    override fun outputs(ctx: StageContext): List<String> = listOf(archiveRelPath(ctx.snapshot))

    override fun plan(ctx: StageContext): List<PipelineStep> {
        if (ctx.snapshot.acquisition.mode == AcquisitionMode.IMPORT_ARCHIVE &&
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
        }

        val expectedSha = expectedChecksum(ctx)
        val isMd5 = expectedSha != null && expectedSha.length == MD5_LENGTH
        val checksumTool = if (isMd5) "md5sum" else "sha256sum"
        val checksumKey = if (isMd5) RuntimeKeys.ARCHIVE_MD5 else RuntimeKeys.ARCHIVE_SHA256

        steps += PipelineStep.Tool(
            command = stepCommand(checksumTool, listOf(archive), ws),
            label = "$checksumTool archive",
            resultKey = checksumKey,
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

        if (ctx.has(checksumKey) || ctx.has(RuntimeKeys.ARCHIVE_SHA256)) {
            steps += checksumSteps(ctx, archive, expectedSha, isMd5)
        }

        return steps
    }

    /** Records the measured checksum and the detected package format, then checks both (FR-001, FR-006). */
    private fun checksumSteps(
        ctx: StageContext,
        archive: String,
        expectedSha: String?,
        isMd5: Boolean,
    ): List<PipelineStep> {
        val ws = ctx.wsPath
        val checksumKey = if (isMd5) RuntimeKeys.ARCHIVE_MD5 else RuntimeKeys.ARCHIVE_SHA256
        val checksumRaw = ctx.value(checksumKey) ?: ctx.value(RuntimeKeys.ARCHIVE_SHA256).orEmpty()
        val actual = checksumRaw.digestToken()
        val ok = expectedSha.isNullOrBlank() || actual.equals(expectedSha, ignoreCase = true)
        val resultJson = ChecksumResult.encode(ChecksumResult(ok = ok, expected = expectedSha, actual = actual))
        val steps = mutableListOf<PipelineStep>(
            PipelineStep.Tool(
                command = stepCommand("echo", listOf(resultJson), ws),
                label = "record checksum result",
                resultKey = RuntimeKeys.PACKAGE_CHECKSUM_RESULT,
            ),
            PipelineStep.Tool(
                command = stepCommand("xxd", listOf("-p", "-l", "512", archive), ws),
                label = "read archive header",
                resultKey = RuntimeKeys.PACKAGE_HEADER_HEX,
            ),
        )
        ctx.value(RuntimeKeys.PACKAGE_HEADER_HEX)?.let { hex -> steps += manifestSteps(ctx, hex, expectedSha) }
        if (!expectedSha.isNullOrBlank()) {
            steps += PipelineStep.Check("verify archive checksum") { values ->
                checksumVerdict(expectedSha, values, isMd5)
            }
        }
        return steps
    }

    /** The package manifest from the archive's first bytes; an unrecognised format is refused. */
    private fun manifestSteps(ctx: StageContext, headerHex: String, expectedSha: String?): List<PipelineStep> {
        val format = FormatDetector.detect(FormatDetector.fromHex(headerHex))
        val manifest = if (format == PackageFormat.Unknown) {
            PackageManifest.unknownRefusal(archiveName(ctx.snapshot))
        } else {
            PackageManifest(format = format, declaredChecksum = expectedSha)
        }
        val record = PipelineStep.Tool(
            command = stepCommand("echo", listOf(PackageManifest.encode(manifest)), ctx.wsPath),
            label = "record package manifest",
            resultKey = RuntimeKeys.PACKAGE_MANIFEST,
        )
        val refusal = manifest.refusal ?: return listOf(record)
        return listOf(
            record,
            PipelineStep.Check("verify package format") { "${refusal.cause} ${refusal.item} ${refusal.nextStep}" },
        )
    }

    private fun downloadSteps(ctx: StageContext, archive: String): List<PipelineStep> {
        val ws = ctx.wsPath
        val url = requireNotNull(ctx.snapshot.acquisition.firmware.downloadUrl) {
            "Firmware ${ctx.snapshot.acquisition.firmware.version} has no download URL; import the archive instead"
        }
        val partial = "$archive.partial"
        val steps = mutableListOf<PipelineStep>()

        val retry = listOf("-fL", "--retry", ctx.snapshot.acquisition.retries.toString())
        if (!ctx.snapshot.acquisition.resume) {
            steps += PipelineStep.Tool(stepCommand("curl", retry + listOf("-o", partial, url), ws), "curl plain")
        } else {
            // Resumable first; curl exits 33 when the server rejects the range → plain download.
            steps += PipelineStep.Tool(
                command = stepCommand("curl", retry + listOf("-C", "-", "-o", partial, url), ws),
                label = "curl resume",
                resultKey = RuntimeKeys.DOWNLOAD_RESUME,
                allowFailure = true,
            )
            val resumeExit = ctx.exitOf(RuntimeKeys.DOWNLOAD_RESUME)
            if (resumeExit == CURL_RANGE_NOT_SUPPORTED) {
                steps += PipelineStep.Tool(stepCommand("curl", retry + listOf("-o", partial, url), ws), "curl plain")
            } else if (resumeExit != null && resumeExit != 0) {
                steps += PipelineStep.Check("download failed") { "curl exited with $resumeExit while downloading $url" }
            }
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
        // A download that fails its checksum never replaces the archive already there.
        expectedChecksum(ctx)?.let { expected ->
            val isMd5 = expected.length == MD5_LENGTH
            steps += PipelineStep.Tool(
                command = stepCommand(if (isMd5) "md5sum" else "sha256sum", listOf(partial), ws),
                label = "checksum download",
                resultKey = if (isMd5) RuntimeKeys.ARCHIVE_MD5 else RuntimeKeys.ARCHIVE_SHA256,
            )
            steps += PipelineStep.Check(
                "verify download checksum",
            ) { values -> checksumVerdict(expected, values, isMd5) }
        }
        steps += PipelineStep.Tool(stepCommand("mv", listOf(partial, archive), ws), "promote archive")
        return steps
    }

    private fun expectedChecksum(ctx: StageContext): String? = ctx.snapshot.acquisition.expectedSha256
        ?: ctx.snapshot.acquisition.firmware.sha256?.takeIf { it.isNotBlank() }

    private fun checksumVerdict(expected: String, values: Map<String, String>, isMd5: Boolean): String? {
        val actual = (values[RuntimeKeys.ARCHIVE_SHA256] ?: values[RuntimeKeys.ARCHIVE_MD5])?.digestToken().orEmpty()
        val toolName = if (isMd5) "md5sum" else "sha256sum"
        return when {
            actual.isEmpty() -> "$toolName produced no digest for the firmware archive"
            actual.equals(expected, ignoreCase = true) -> null
            else -> "CHECKSUM_MISMATCH: Firmware archive checksum mismatch: expected $expected, got $actual"
        }
    }

    public companion object {
        private const val CURL_RANGE_NOT_SUPPORTED = 33
        private const val MD5_LENGTH = 32

        public fun archiveName(snapshot: ConfigurationSnapshot): String = archiveName(snapshot.acquisition)

        public fun archiveName(acquisition: AcquisitionSettings): String = when (acquisition.mode) {
            AcquisitionMode.IMPORT_ARCHIVE ->
                acquisition.archiveRef?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                    ?: acquisition.archiveRef.orEmpty()

            AcquisitionMode.DOWNLOAD -> {
                val fromUrl = acquisition.firmware.downloadUrl?.substringAfterLast('/')?.substringBefore('?')
                fromUrl?.takeIf { it.isNotBlank() } ?: "${acquisition.firmware.version}.zip"
            }
        }

        public fun archiveRelPath(snapshot: ConfigurationSnapshot): String = archiveRelPath(snapshot.acquisition)

        /** Where the archive the build uses lives in the workspace folder. */
        public fun archiveRelPath(acquisition: AcquisitionSettings): String = when (acquisition.mode) {
            AcquisitionMode.IMPORT_ARCHIVE -> acquisition.archiveRef.orEmpty()
            AcquisitionMode.DOWNLOAD -> "firmware/downloaded/${archiveName(acquisition)}"
        }
    }
}
