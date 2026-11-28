/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.provenance

import org.ide.lti.core.domain.pipeline.CacheKeys
import org.ide.lti.core.domain.pipeline.RuntimeKeys
import org.ide.lti.core.domain.pipeline.digestToken
import org.ide.lti.core.domain.pipeline.stages.BuildFlashableZipStage
import org.ide.lti.core.domain.pipeline.stages.FirmwareAcquisitionStage
import org.ide.lti.core.domain.pipeline.toolkit.BuildFilesystems
import org.ide.lti.core.domain.workspace.RunRecord
import org.ide.lti.core.model.run.Artifact
import org.ide.lti.core.model.run.ArtifactKind
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.ConfigurationSnapshot

/**
 * Generates deterministic, machine-verifiable [BuildProvenance] manifests and Markdown audit summaries
 * linking built images and install archives back to their inputs, mods, tools, and signing keys.
 */
object BuildProvenanceGenerator {

    fun generate(
        run: BuildRun,
        workspaceName: String,
        snapshot: ConfigurationSnapshot,
        target: TargetDevice,
        record: RunRecord,
        runtimeValues: Map<String, String>,
        artifacts: List<Artifact>,
        /** Partition to (bytes, sha256) of its image as it is on disk, AVB footer included. */
        images: Map<String, Pair<Long, String>> = emptyMap(),
    ): BuildProvenance {
        val lockEntries = record.plan?.lockfileEntries.orEmpty()
        val operations = record.plan?.operations.orEmpty()
        val mods = lockEntries.map { lock ->
            val opCount = operations.count { it.mod == "${lock.publisher}:${lock.id}" }
            ModProvenance(
                id = lock.id,
                name = "${lock.publisher}/${lock.id}",
                version = lock.version,
                contentDigest = lock.contentDigest,
                changedFilesCount = opCount,
            )
        }
        val imageOutputs = target.dynamicPartitions.mapNotNull { partition ->
            // The run's own image hash is taken before the AVB footer: only a measure of the file can be verified.
            val (size, sha) = images[partition] ?: return@mapNotNull null
            val fs = BuildFilesystems.of(snapshot.build, partition)
            val comp = if (fs == BuildFilesystems.EROFS) {
                BuildFilesystems.erofsCompression(snapshot.build, partition)
            } else {
                null
            }
            OutputProvenanceEntry(
                relPath = "out/images/$partition.img",
                kind = ArtifactKind.IMAGE.name,
                sizeBytes = size,
                sha256 = sha,
                filesystem = fs,
                compression = comp,
                avbSigned = snapshot.build.signing.signImages,
            )
        }
        val packageOutputs = artifacts
            .filter {
                it.kind != ArtifactKind.CHECKSUM &&
                    it.kind != ArtifactKind.PROVENANCE_SBOM &&
                    !it.sha256.isNullOrBlank()
            }
            .map { artifact ->
                val rel = toWorkspaceRel(artifact.linuxPath, run.workspaceId)
                OutputProvenanceEntry(
                    relPath = rel,
                    kind = artifact.kind.name,
                    sizeBytes = artifact.sizeBytes,
                    sha256 = artifact.sha256.orEmpty(),
                    avbSigned = snapshot.build.signing.signPackage,
                )
            }
        val canonicalSnapshotJson = ConfigurationSnapshot.canonicalJson.encodeToString(
            ConfigurationSnapshot.serializer(),
            snapshot,
        )
        val measuredArchiveSha = runtimeValues[RuntimeKeys.ARCHIVE_SHA256]?.digestToken()?.takeIf { it.isNotEmpty() }
        val acq = snapshot.acquisition
        return BuildProvenance(
            runId = run.id,
            workspaceId = run.workspaceId,
            workspaceName = workspaceName,
            builtAtIso = (run.endedAt ?: run.startedAt).toString(),
            reproducibleEpochSeconds = BuildFlashableZipStage.REPRODUCIBLE_EPOCH_SECONDS,
            configurationDigest = CacheKeys.sha256(canonicalSnapshotJson),
            targetDevice = TargetProvenance(
                id = target.id,
                codename = target.codename,
                name = target.name,
                superGroupName = target.superGroupName,
                superGroupBytes = target.superGroupBytes,
            ),
            firmwareInput = FirmwareProvenance(
                mode = acq.mode.name,
                version = acq.firmware.version,
                buildId = acq.firmware.buildId,
                region = acq.region.name,
                securityPatch = acq.firmware.securityPatch,
                expectedSha256 = acq.expectedSha256?.takeIf { it.isNotBlank() },
                measuredArchiveSha256 = measuredArchiveSha,
                archiveRelPath = FirmwareAcquisitionStage.archiveRelPath(snapshot),
            ),
            appliedMods = mods,
            removedPackages = record.debloat?.removals.orEmpty()
                .map { it.packageId ?: "${it.partition}/${it.relativePath}" }.distinct().sorted(),
            toolVersions = run.toolVersions.toSortedMap(),
            signing = SigningProvenance(
                signImages = snapshot.build.signing.signImages,
                signPackage = snapshot.build.signing.signPackage,
                avbKeyRef = snapshot.build.signing.avbKeyRef,
                avbKeySha256Der = record.avbKey,
                avbPubkeySha1 = runtimeValues[RuntimeKeys.AVB_PUBKEY_SHA1]?.digestToken()?.takeIf { it.isNotEmpty() },
                platformKeyRef = snapshot.build.signing.platformKeyRef,
                platformCertSha256 = record.certificate,
                platformCertSha1 = runtimeValues[BuildFlashableZipStage.PLATFORM_CERT_SHA1]?.digestToken()
                    ?.takeIf { it.isNotEmpty() },
            ),
            outputs = (imageOutputs + packageOutputs).distinctBy { it.relPath }.sortedBy { it.relPath },
        ).withSelfDigest()
    }

    fun toMarkdownReport(provenance: BuildProvenance, verification: ProvenanceVerificationResult? = null): String =
        buildString {
            append("# Build Provenance & SBOM\n\n")
            append("- Format: `${provenance.format}` (schema v${provenance.schemaVersion})\n")
            append("- Run: `${provenance.runId}` (`${provenance.builtAtIso}`)\n")
            append("- Reproducible epoch: `${provenance.reproducibleEpochSeconds}`\n")
            append("- Manifest SHA-256: `${provenance.manifestDigest ?: "unsigned"}`\n")
            append("- Config digest: `${provenance.configurationDigest}`\n")
            verification?.let { res ->
                val verdict = if (res.passed) "VERIFIED" else "VERIFICATION FAILED (${res.problems.size} issue(s))"
                append("- Verification verdict: **$verdict**\n")
            }
            append("\n## Firmware Input\n\n")
            append("- ${provenance.firmwareInput.version} (${provenance.firmwareInput.buildId}, ")
            append("region ${provenance.firmwareInput.region}, patch ${provenance.firmwareInput.securityPatch})\n")
            append("- Archive: `${provenance.firmwareInput.archiveRelPath}`\n")
            provenance.firmwareInput.measuredArchiveSha256?.let { append("- Archive SHA-256: `$it`\n") }

            append("\n## Signing Keys\n\n")
            append("- AVB key (`${provenance.signing.avbKeyRef}`): ")
            append("`${provenance.signing.avbKeySha256Der ?: "unrecorded"}`\n")
            append("- Platform cert (`${provenance.signing.platformKeyRef}`): ")
            append("`${provenance.signing.platformCertSha256 ?: "unrecorded"}`\n")

            append("\n## Outputs (${provenance.outputs.size})\n\n")
            provenance.outputs.forEach { out ->
                append("- `${out.relPath}` (${out.kind}): sha256 `${out.sha256}`")
                out.filesystem?.let { fs -> append(" [$fs${out.compression?.let { ", $it" }.orEmpty()}]") }
                append('\n')
            }

            append("\n## Mods (${provenance.appliedMods.size}) & Debloat (${provenance.removedPackages.size})\n\n")
            provenance.appliedMods.forEach { mod ->
                append("- ${mod.name} (${mod.id}@${mod.version})")
                mod.contentDigest?.let { append(" sha256 `${it.take(SHORT_HASH)}`") }
                append('\n')
            }
            if (provenance.removedPackages.isNotEmpty()) {
                append("- Removed packages: ${provenance.removedPackages.size}\n")
            }

            if (provenance.toolVersions.isNotEmpty()) {
                append("\n## LtiRomServer Tool Versions\n\n")
                provenance.toolVersions.forEach { (tool, ver) -> append("- `$tool`: $ver\n") }
            }
        }

    private fun toWorkspaceRel(path: String, workspaceId: String): String {
        val marker = "/$workspaceId/"
        val afterWs = path.substringAfter(marker, "")
        val prefixed = listOf("out/", "work/", "firmware/", "records/")
            .mapNotNull { prefix ->
                val idx = path.indexOf("/$prefix")
                if (idx >= 0) path.substring(idx + 1) else null
            }
            .firstOrNull()
        return afterWs.ifEmpty { prefixed ?: path }
    }

    /** What [PipelineArtifactCollector]'s measure printed: `<partition>\t<bytes>\t<sha256>` lines. */
    internal fun parseMeasured(text: String): Map<String, Pair<Long, String>> = text.lines().mapNotNull { line ->
        val (name, bytes, sha) = line.split('\t').takeIf { it.size == MEASURED_FIELDS } ?: return@mapNotNull null
        bytes.toLongOrNull()?.takeIf { sha.length == SHA256_HEX }?.let { name to (it to sha) }
    }.toMap()

    private const val MEASURED_FIELDS = 3
    private const val SHA256_HEX = 64
    private const val SHORT_HASH = 12
}
