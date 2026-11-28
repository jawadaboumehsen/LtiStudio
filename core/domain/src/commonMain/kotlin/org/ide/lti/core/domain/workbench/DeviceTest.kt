/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.workbench

import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import org.ide.lti.core.common.utils.runCatchingCancellable
import org.ide.lti.core.domain.pipeline.RuntimeKeys
import org.ide.lti.core.domain.pipeline.toolkit.Refusal
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException
import org.ide.lti.core.domain.workspace.RunRecord
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.workspace.ConfigurationSnapshot

private const val DEVICE_CRASH_LOG_LINES = 80
private const val CRASH_LINE_CHARS = 500

/** How one check on the phone came out; a [NOTE] is a fact to know, not a failure. */
@Serializable
enum class DeviceVerdict { PASS, FAIL, NOTE }

@Serializable
data class DeviceCheckItem(val name: String, val verdict: DeviceVerdict, val detail: String)

@Serializable
data class DeviceCrashLink(val process: String, val recordedRemovals: List<String>, val possibleModEdits: List<String>)

@Serializable
data class DeviceDiagnostics(
    val crashLogAvailable: Boolean,
    val crashLog: List<String>,
    val links: List<DeviceCrashLink>,
    val buildRecordAvailable: Boolean,
)

/**
 * What a phone said after a run's package was installed on it, kept with that run (`records/<runId>/`). Three
 * states stay apart: the run built ([BuildRun]), the phone runs this very build ([installed]), and every check on it
 * passed ([passed]).
 */
@Serializable
data class DeviceCheckResult(
    val runId: String,
    val checkedAt: String,
    val serial: String,
    val model: String?,
    val items: List<DeviceCheckItem>,
    val diagnostics: DeviceDiagnostics? = null,
) {
    /** The phone runs this run's build (its `ro.lti.timestamp` is the run's). */
    val installed: Boolean get() = items.firstOrNull()?.verdict == DeviceVerdict.PASS

    /** Installed, and no check failed. */
    val passed: Boolean get() = installed && items.none { it.verdict == DeviceVerdict.FAIL }

    /** The result as Markdown, for the dialog and the build report. */
    fun markdown(): String = buildString {
        append("Phone ${model ?: serial} ($serial), checked $checkedAt: ")
        append(
            when {
                passed -> "this build is installed and every check passed.\n\n"
                installed -> "this build is installed, but a check failed.\n\n"
                else -> "the phone does not run this build.\n\n"
            },
        )
        items.forEach { append("- ${it.verdict.name}: ${it.name}: ${it.detail}\n") }
        diagnostics?.let { appendDiagnostics(it) }
    }

    private fun StringBuilder.appendDiagnostics(evidence: DeviceDiagnostics) {
        append("\n### Diagnostics for build $runId\n\n")
        if (!evidence.buildRecordAvailable) append("Saved mod and debloat records are unavailable.\n\n")
        evidence.links.forEach { link ->
            append("- ${link.process}\n")
            link.recordedRemovals.forEach { append("  - Recorded Debloat removal: $it\n") }
            link.possibleModEdits.forEach { append("  - Possible mod link (path match): $it\n") }
            if (link.recordedRemovals.isEmpty() && link.possibleModEdits.isEmpty()) {
                append("  - No matching mod or debloat entry was recorded.\n")
            }
        }
        if (!evidence.crashLogAvailable) append("\nCrash log unavailable.\n")
        if (evidence.crashLog.isNotEmpty()) {
            append("\nRecent crash log (up to $DEVICE_CRASH_LOG_LINES lines)\n\n")
            evidence.crashLog.forEach { append("    $it\n") }
        }
    }
}

/**
 * Checks the phone attached to WSL after a run's package was installed on it: whether it runs that build, booted
 * fully, and what it reports (SELinux, verified boot, crashes since boot). Reads the phone over adb only and writes
 * the result into the run's record.
 */
class DeviceTest(
    private val runner: WorkbenchRunner,
    private val probe: PhoneProbe,
    private val clock: Clock = Clock.System,
) {
    /** Checks the one phone booted into Android against [run], and keeps the result with the run. */
    suspend fun check(run: BuildRun): DeviceCheckResult {
        val stamp = run.runtimeValues[RuntimeKeys.RUN_TIMESTAMP]
        if (!madePackage(run) || stamp == null) {
            throw RefusalException(
                Refusal("This run made no package", "run ${run.id}", "Check a run that went through Build & Sign."),
            )
        }
        val phone = phoneForCheck(probe.read(run.workspaceId))
        val answers = runner.read(run.workspaceId) { sh(CHECK_SCRIPT, toolPath("adb"), phone.serial, PHONE_SCRIPT) }
        val installed = property(answers, "stamp") == stamp
        val record = if (installed) {
            runCatchingCancellable { RunRecord.read(runner, run.workspaceId, run.id) }.getOrDefault(RunRecord.EMPTY)
        } else {
            RunRecord.EMPTY
        }
        val result = DeviceCheckResult(
            runId = run.id,
            checkedAt = clock.now().toString(),
            serial = phone.serial,
            model = phone.model,
            items = items(answers, stamp),
            diagnostics = if (installed) diagnostics(answers, record) else null,
        )
        runner.job(run.workspaceId, "Device check") {
            sh(
                SAVE_SCRIPT,
                abs("."),
                RunRecord.dir(run.id),
                ConfigurationSnapshot.canonicalJson.encodeToString(DeviceCheckResult.serializer(), result),
            )
        }
        return result
    }

    companion object {
        internal fun phoneForCheck(phones: List<PhoneFacts>): PhoneFacts {
            val android = phones.filter { it.mode == PhoneMode.ANDROID }
            android.singleOrNull()?.let { return it }
            val (cause, nextStep) = when {
                android.size > 1 -> Pair(
                    "Several phones are connected",
                    "Connect only the phone you installed the build on.",
                )
                phones.any { it.mode == PhoneMode.NO_PERMISSION } -> Pair(
                    "WSL denied USB access",
                    "Open Workspace → ROM configuration → Connected phone, select Read the phone, then " +
                        "How to fix USB access in WSL.",
                )
                phones.any { it.mode == PhoneMode.UNAUTHORIZED } -> Pair(
                    "USB debugging has not been authorized",
                    "Unlock the phone and allow USB debugging on it, then retry.",
                )
                else -> Pair(
                    "No phone booted into Android is connected",
                    "Connect the phone you installed the build on, booted into Android, with USB debugging allowed.",
                )
            }
            throw RefusalException(Refusal(cause, "the connected phone", nextStep))
        }

        /** Whether [run] succeeded with a package (a run stopped before Build & Sign has none to install). */
        fun madePackage(run: BuildRun): Boolean = run.state == RunState.SUCCEEDED && run.artifacts.isNotEmpty()

        /** The checks from what [PHONE_SCRIPT] printed (`key=value` lines, `crash=<process>` per crashed one). */
        fun items(answers: String, stamp: String): List<DeviceCheckItem> {
            val running = property(answers, "stamp")
            val crashes = crashedProcesses(answers)
            val selinux = property(answers, "selinux")
            return listOf(
                when {
                    running == stamp -> pass("This build installed", "ro.lti.timestamp is $stamp, this run's")
                    running.isEmpty() -> fail("This build installed", "the phone runs no LtiRom build")
                    else -> fail("This build installed", "the phone runs build $running; this run is $stamp")
                },
                if (property(answers, "booted") == "1") {
                    pass("Booted", "sys.boot_completed is 1")
                } else {
                    fail("Booted", "the phone has not finished booting (sys.boot_completed is not 1)")
                },
                when (selinux) {
                    "Enforcing" -> pass("SELinux", "enforcing")
                    "" -> DeviceCheckItem("SELinux", DeviceVerdict.NOTE, "not reported")
                    else -> DeviceCheckItem("SELinux", DeviceVerdict.NOTE, "${selinux.lowercase()} (a mod may set it)")
                },
                DeviceCheckItem(
                    "Verified boot",
                    DeviceVerdict.NOTE,
                    property(answers, "vb").ifEmpty { "not reported" },
                ),
                if (property(answers, "crash-status") == "unavailable") {
                    fail("Crashes since boot", "The device did not provide its crash log.")
                } else if (crashes.isEmpty()) {
                    pass("Crashes since boot", "none in the crash log")
                } else {
                    fail(
                        "Crashes since boot",
                        crashes.take(CRASHES_SHOWN).joinToString() +
                            if (crashes.size > CRASHES_SHOWN) " and ${crashes.size - CRASHES_SHOWN} more" else "",
                    )
                },
            )
        }

        internal fun diagnostics(answers: String, record: RunRecord): DeviceDiagnostics {
            val available = property(answers, "crash-status") == "ok"
            val log = if (available) {
                answers.lineSequence().filter { it.startsWith("crash-log=") }
                    .map { it.removePrefix("crash-log=").filterNot(Char::isISOControl).take(CRASH_LINE_CHARS) }
                    .take(DEVICE_CRASH_LOG_LINES).toList()
            } else {
                emptyList()
            }
            val links = crashedProcesses(answers).take(CRASHES_SHOWN).map { process ->
                val pkg = process.substringBefore(':')
                val removed = record.debloat?.removals.orEmpty().filter { it.packageId == pkg }
                    .map { "${it.partition}/${it.relativePath}" }
                val edits = record.plan?.operations.orEmpty().filter {
                    val file = it.target.substringAfterLast('/')
                    file == "$pkg.apk" || (file.endsWith(".apk") && pkg in it.target.split('/'))
                }.map { "${it.mod ?: "Unknown mod"} · ${it.id} · ${it.target}" }
                DeviceCrashLink(process, removed, edits)
            }
            return DeviceDiagnostics(available, log, links, record.plan != null || record.debloat != null)
        }

        private fun property(answers: String, key: String): String =
            answers.lineSequence().firstOrNull { it.startsWith("$key=") }?.substringAfter('=')?.trim().orEmpty()

        private fun crashedProcesses(answers: String): List<String> =
            answers.lineSequence().filter { it.startsWith("crash=") }.map { it.substringAfter('=').trim() }
                .filter { it.isNotEmpty() }.distinct().toList()

        private fun pass(name: String, detail: String) = DeviceCheckItem(name, DeviceVerdict.PASS, detail)
        private fun fail(name: String, detail: String) = DeviceCheckItem(name, DeviceVerdict.FAIL, detail)

        private const val CRASHES_SHOWN = 10

        /** `$1` adb, `$2` serial, `$3` the script the phone runs. */
        private const val CHECK_SCRIPT = "timeout 60 \"\$1\" -s \"\$2\" shell \"\$3\" 2>&1"

        /**
         * Runs on the phone (toybox sh): the build stamp, boot, SELinux, verified boot, and the processes of the
         * crash log (Java: `Process: <name>,`; native: `>>> <name> <<<`).
         */
        private const val PHONE_SCRIPT = "echo stamp=\$(getprop ro.lti.timestamp); " +
            "echo booted=\$(getprop sys.boot_completed); echo selinux=\$(getenforce 2>/dev/null); " +
            "echo vb=\$(getprop ro.boot.verifiedbootstate); " +
            "if logs=\$(logcat -b crash -d 2>/dev/null); then " +
            "echo crash-status=ok; printf '%s\\n' \"\$logs\" | sed -n " +
            "-e 's/.*Process: \\([^,]*\\),.*/crash=\\1/p' -e 's/.*>>> \\(.*\\) <<<.*/crash=\\1/p'; " +
            "printf '%s\\n' \"\$logs\" | tail -n $DEVICE_CRASH_LOG_LINES | sed 's/^/crash-log=/'; " +
            "else echo crash-status=unavailable; fi"

        /**
         * `$1` workspace, `$2` record folder, `$3` the result as JSON: written beside it and renamed over
         * `device-check.json` (the newest check is kept). A link on the way refuses it.
         */
        private const val SAVE_SCRIPT = """cd "$1" || exit 1
d="$2"
[ -L records ] || [ -L "${'$'}d" ] || [ -L "${'$'}d/device-check.json" ] && { echo "a link is on the way" >&2; exit 1; }
mkdir -p "${'$'}d" || exit 1
t=${'$'}(mktemp "${'$'}d/device-check.json.XXXXXX") || exit 1
trap 'rm -f -- "${'$'}t"' EXIT
printf '%s\n' "$3" > "${'$'}t" && mv -f -T "${'$'}t" "${'$'}d/device-check.json""""
    }
}
