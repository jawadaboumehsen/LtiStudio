/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.workspace

import org.ide.lti.core.domain.debloat.DebloatDecision
import org.ide.lti.core.domain.pipeline.stages.AppliedModulePlan
import org.ide.lti.core.domain.pipeline.stages.ChangelogDraft
import org.ide.lti.core.domain.pipeline.stages.DebloatStage
import org.ide.lti.core.domain.workbench.DeviceCheckResult
import org.ide.lti.core.domain.workbench.WorkbenchRunner
import org.ide.lti.core.model.workspace.ConfigurationSnapshot

/**
 * What a succeeded run did, kept in `records/<runId>/` of its workspace so it can be read later without today's
 * work/: the mod plan it applied, the apps Debloat removed, and the fingerprints of the signing keys it was built
 * with. The settings, scripts, mod versions and firmware hash are already in the run's snapshot. Each receipt is
 * there only when its stage was part of the run; a run without a record (older ones) reads as [EMPTY].
 */
public data class RunRecord(
    val plan: AppliedModulePlan?,
    val debloat: DebloatDecision?,
    /** SHA-256 of the platform certificate the build signed with, when Build was part of the run. */
    val certificate: String?,
    /** SHA-256 of the AVB public key (DER) the build signed with, when Build was part of the run. */
    val avbKey: String?,
    /** False for a run that left no record. */
    val present: Boolean,
    /** The newest check of a phone running this build ([DeviceTest]); null when none was made. */
    val deviceCheck: DeviceCheckResult? = null,
) {
    public companion object {
        public val EMPTY: RunRecord = RunRecord(null, null, null, null, present = false)

        /** The folder of [runId]'s record, relative to the workspace. */
        public fun dir(runId: String): String = "records/$runId"

        /** The record of [runId] in [workspaceId], read through [runner]; [EMPTY] when it has none. */
        public suspend fun read(runner: WorkbenchRunner, workspaceId: String, runId: String): RunRecord =
            parse(runner.read(workspaceId) { sh(READ_SCRIPT, abs("."), dir(runId)) })

        /** What [READ_SCRIPT] printed: `<name>\t<one line of JSON or a fingerprint>` rows. */
        public fun parse(text: String): RunRecord {
            val rows = text.lines().mapNotNull { line ->
                line.split('\t', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
            }.toMap()
            val deviceCheck = rows[DEVICE_CHECK]?.let { json ->
                runCatching { ConfigurationSnapshot.canonicalJson.decodeFromString<DeviceCheckResult>(json) }
                    .getOrNull()
            }
            if (rows["record"] != "yes") return EMPTY.copy(deviceCheck = deviceCheck)
            return RunRecord(
                plan = rows[PLAN]?.let { json ->
                    runCatching { ConfigurationSnapshot.canonicalJson.decodeFromString<AppliedModulePlan>(json) }
                        .getOrNull()
                },
                debloat = rows[DEBLOAT]?.let(DebloatStage::parseResult),
                certificate = rows["cert"]?.takeIf { it.isNotBlank() },
                avbKey = rows["avb"]?.takeIf { it.isNotBlank() },
                present = true,
                deviceCheck = deviceCheck,
            )
        }

        private const val PLAN = "modules-plan.json"
        private const val DEBLOAT = "debloat-result.json"
        private const val DEVICE_CHECK = "device-check.json"

        /**
         * Writes the record of a succeeded run. `$1` workspace, `$2` record folder, `$3` 1 when Patches was part of
         * the run, `$4` 1 when Debloat was, `$5` platform certificate, `$6` AVB private key (both empty when Build was
         * not). Written to `<folder>.partial` and renamed, so a cut-off write never looks complete; a record already
         * there (a resumed run) is kept. Only regular files are copied, never a link.
         */
        public const val WRITE_SCRIPT: String = """cd "$1" || exit 1
d="$2"; t="${'$'}d.partial"
[ -L records ] && exit 1
[ -d "${'$'}d" ] && exit 0
mkdir -p records && rm -rf "${'$'}t" && mkdir "${'$'}t" || exit 1
keep() { if [ -f "${'$'}1" ] && [ ! -L "${'$'}1" ]; then cp -- "${'$'}1" "${'$'}t/${'$'}2" || exit 1; fi; }
if [ "$3" = 1 ]; then keep """ + ChangelogDraft.PLAN + """ modules-plan.json; fi
if [ "$4" = 1 ]; then keep """ + DebloatStage.RESULT + """ debloat-result.json; fi
cert="$5"; avb="$6"; : > "${'$'}t/keys.tsv" || exit 1
if [ -f "${'$'}cert" ]; then
  f=${'$'}(openssl x509 -in "${'$'}cert" -noout -fingerprint -sha256 2>/dev/null) &&
    printf 'cert\t%s\n' "${'$'}{f#*=}" >> "${'$'}t/keys.tsv"
fi
if [ -f "${'$'}avb" ]; then
  k=${'$'}(openssl pkey -in "${'$'}avb" -pubout -outform DER 2>/dev/null | sha256sum | cut -d' ' -f1) &&
    [ "${'$'}k" != e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855 ] &&
    printf 'avb\t%s\n' "${'$'}k" >> "${'$'}t/keys.tsv"
fi
mv -T "${'$'}t" "${'$'}d""""

        /**
         * Prints a record as [parse] reads it; nothing when the folder is absent. A run's own record always has
         * keys.tsv; a folder without it holds only a device check of a run older than records.
         */
        private const val READ_SCRIPT: String = """cd "$1" || exit 1
d="$2"
[ -d "${'$'}d" ] && [ ! -L "${'$'}d" ] || exit 0
[ -f "${'$'}d/keys.tsv" ] && printf 'record\tyes\n'
for f in modules-plan.json debloat-result.json device-check.json; do
  [ -f "${'$'}d/${'$'}f" ] && printf '%s\t%s\n' "${'$'}f" "${'$'}(tr -d '\n\r' < "${'$'}d/${'$'}f")"
done
[ -f "${'$'}d/keys.tsv" ] && cat "${'$'}d/keys.tsv"
exit 0"""
    }

    /** The changelog of this run: its mods, removed apps and system changes from [snapshot]. */
    public fun changes(snapshot: ConfigurationSnapshot?): String = ChangelogDraft.of(plan, debloat, snapshot)
}
