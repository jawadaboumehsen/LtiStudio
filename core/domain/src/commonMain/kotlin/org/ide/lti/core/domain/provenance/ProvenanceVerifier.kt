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

import org.ide.lti.core.common.utils.runCatchingCancellable
import org.ide.lti.core.domain.workbench.WorkbenchRunner
import org.ide.lti.core.domain.workspace.RunRecord

/**
 * Verifies a persisted [BuildProvenance] manifest against the actual files, archives, and signing keys
 * currently present in a workspace. Never mutates files and rejects symlinks cleanly.
 */
object ProvenanceVerifier {

    suspend fun readAndVerify(
        runner: WorkbenchRunner,
        workspaceId: String,
        runId: String? = null,
    ): Result<Pair<BuildProvenance, ProvenanceVerificationResult>> = runCatchingCancellable {
        val relPath = runId?.let { "${RunRecord.dir(it)}/${BuildProvenance.RECORD_FILE_NAME}" }
            ?: BuildProvenance.WORKSPACE_PATH
        val raw = runner.read(workspaceId) { sh(READ_MANIFEST_SCRIPT, abs("."), relPath) }
        check(raw.isNotBlank()) { "Provenance manifest not found at $relPath" }
        val manifest = BuildProvenance.fromJson(raw)
        val targets = manifest.outputs.map { it.relPath }
        val measured = parseMeasured(
            runner.read(workspaceId) {
                sh(
                    VERIFY_FILES_SCRIPT,
                    abs("."),
                    "${manifest.signing.platformKeyRef}.x509.pem",
                    manifest.signing.avbKeyRef,
                    manifest.firmwareInput.archiveRelPath,
                    *targets.toTypedArray(),
                )
            },
        )
        manifest to verifyFromSnapshot(
            manifest = manifest,
            measuredOutputs = measured.outputs,
            currentAvbSha256 = measured.avbSha256,
            currentCertSha256 = measured.certSha256,
            currentFirmwareSha256 = measured.firmwareSha256,
        )
    }

    /** Pure verification function over pre-measured file digests for deterministic unit testing. */
    fun verifyFromSnapshot(
        manifest: BuildProvenance,
        measuredOutputs: Map<String, Pair<Long, String>>,
        currentAvbSha256: String?,
        currentCertSha256: String?,
        currentFirmwareSha256: String?,
    ): ProvenanceVerificationResult {
        val problems = mutableListOf<String>()
        val selfOk = manifest.hasValidSelfDigest()
        if (!selfOk) problems += "Provenance manifest self-digest does not match its content."

        val firmwareMatches = verifyFirmwareMatch(manifest, currentFirmwareSha256, problems)
        val avbKeyMatches = checkKeyMatch("AVB key", manifest.signing.avbKeySha256Der, currentAvbSha256, problems)
        val platformKeyMatches = checkKeyMatch(
            "Platform certificate",
            manifest.signing.platformCertSha256,
            currentCertSha256,
            problems,
        )
        val checks = verifyOutputEntries(manifest, measuredOutputs, problems)
        return ProvenanceVerificationResult(
            runId = manifest.runId,
            selfDigestValid = selfOk,
            firmwareMatches = firmwareMatches,
            avbKeyMatches = avbKeyMatches,
            platformKeyMatches = platformKeyMatches,
            outputChecks = checks,
            problems = problems,
        )
    }

    private fun verifyFirmwareMatch(
        manifest: BuildProvenance,
        currentFirmwareSha256: String?,
        problems: MutableList<String>,
    ): Boolean? = manifest.firmwareInput.measuredArchiveSha256?.let { expected ->
        when (currentFirmwareSha256) {
            null -> null
            expected -> true
            else -> {
                problems += "Firmware archive hash drifted from recorded input."
                false
            }
        }
    }

    private fun verifyOutputEntries(
        manifest: BuildProvenance,
        measuredOutputs: Map<String, Pair<Long, String>>,
        problems: MutableList<String>,
    ): List<ProvenanceArtifactCheck> {
        val checks = manifest.outputs.map { out ->
            val actual = measuredOutputs[out.relPath]
            val status = evaluateOutputStatus(out, actual)
            recordOutputProblem(out.relPath, status, problems)
            ProvenanceArtifactCheck(
                relPath = out.relPath,
                kind = out.kind,
                expectedSha256 = out.sha256,
                actualSha256 = actual?.second,
                expectedSizeBytes = out.sizeBytes,
                actualSizeBytes = actual?.first,
                status = status,
            )
        }
        if (checks.isEmpty()) problems += "Provenance manifest declares no output artifacts."
        return checks
    }

    private fun evaluateOutputStatus(out: OutputProvenanceEntry, actual: Pair<Long, String>?): ProvenanceCheckStatus =
        when {
            out.sha256.isBlank() -> ProvenanceCheckStatus.UNRECORDED_HASH
            actual == null -> ProvenanceCheckStatus.MISSING
            actual.second != out.sha256 || (out.sizeBytes != null && actual.first != out.sizeBytes) ->
                ProvenanceCheckStatus.DRIFTED
            else -> ProvenanceCheckStatus.VERIFIED
        }

    private fun recordOutputProblem(relPath: String, status: ProvenanceCheckStatus, problems: MutableList<String>) {
        when (status) {
            ProvenanceCheckStatus.MISSING -> problems += "$relPath is missing from workspace"
            ProvenanceCheckStatus.DRIFTED -> problems += "$relPath SHA-256 or size differs from SBOM"
            ProvenanceCheckStatus.UNRECORDED_HASH -> problems += "$relPath recorded no digest"
            ProvenanceCheckStatus.VERIFIED -> Unit
        }
    }

    private fun checkKeyMatch(
        label: String,
        expected: String?,
        actual: String?,
        problems: MutableList<String>,
    ): Boolean? = when {
        expected.isNullOrBlank() -> null
        actual.isNullOrBlank() -> {
            problems += "$label is missing or unreadable in workspace"
            false
        }
        expected != actual -> {
            problems += "$label fingerprint changed since build"
            false
        }
        else -> true
    }

    internal data class MeasuredWorkspace(
        val certSha256: String? = null,
        val avbSha256: String? = null,
        val firmwareSha256: String? = null,
        val outputs: Map<String, Pair<Long, String>> = emptyMap(),
    )

    internal fun parseMeasured(raw: String): MeasuredWorkspace {
        var cert: String? = null
        var avb: String? = null
        var firmware: String? = null
        val outputs = linkedMapOf<String, Pair<Long, String>>()
        raw.lines().forEach { line ->
            val parts = line.split('\t')
            when (parts.firstOrNull()) {
                "cert" -> cert = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                "avb" -> avb = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                "firmware" -> firmware = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                "file" -> if (parts.size >= 4 && parts[2] != "-") {
                    val size = parts[2].toLongOrNull() ?: return@forEach
                    outputs[parts[1]] = size to parts[3]
                }
            }
        }
        return MeasuredWorkspace(cert, avb, firmware, outputs)
    }

    private const val READ_MANIFEST_SCRIPT = """cd "$1" || exit 1
p="$2"
[ -f "${'$'}p" ] && [ ! -L "${'$'}p" ] || exit 0
cat -- "${'$'}p""""

    private const val VERIFY_FILES_SCRIPT = """cd "$1" || exit 1
cert="$2"; avb="$3"; fw="$4"; shift 4
if [ -n "${'$'}cert" ] && [ -f "${'$'}cert" ] && [ ! -L "${'$'}cert" ]; then
  f=${'$'}(openssl x509 -in "${'$'}cert" -noout -fingerprint -sha256 2>/dev/null) &&
    printf 'cert\t%s\n' "${'$'}{f#*=}"
fi
t=${'$'}(mktemp -d) || exit 1; trap 'rm -rf "${'$'}t"' EXIT
if [ -n "${'$'}avb" ] && [ -f "${'$'}avb" ] && [ ! -L "${'$'}avb" ] &&
   openssl pkey -in "${'$'}avb" -pubout -outform DER -out "${'$'}t/avb.der" 2>/dev/null; then
  printf 'avb\t%s\n' "${'$'}(sha256sum "${'$'}t/avb.der" | cut -d' ' -f1)"
fi
if [ -n "${'$'}fw" ] && [ -f "${'$'}fw" ] && [ ! -L "${'$'}fw" ]; then
  printf 'firmware\t%s\n' "${'$'}(sha256sum -- "${'$'}fw" | cut -d' ' -f1)"
fi
for rel in "${'$'}@"; do
  if [ -f "${'$'}rel" ] && [ ! -L "${'$'}rel" ]; then
    sz=${'$'}(stat -c %s "${'$'}rel")
    hs=${'$'}(sha256sum -- "${'$'}rel" | cut -d' ' -f1)
    printf 'file\t%s\t%s\t%s\n' "${'$'}rel" "${'$'}sz" "${'$'}hs"
  else
    printf 'file\t%s\t-\t-\n' "${'$'}rel"
  fi
done
exit 0"""
}
