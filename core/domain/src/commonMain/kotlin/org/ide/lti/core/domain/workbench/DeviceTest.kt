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
import org.ide.lti.core.domain.pipeline.RuntimeKeys
import org.ide.lti.core.domain.pipeline.toolkit.Refusal
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException
import org.ide.lti.core.domain.workspace.RunRecord
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.workspace.ConfigurationSnapshot

/** How one check on the phone came out; a [NOTE] is a fact to know, not a failure. */
@Serializable
public enum class DeviceVerdict { PASS, FAIL, NOTE }

@Serializable
public data class DeviceCheckItem(val name: String, val verdict: DeviceVerdict, val detail: String)

/**
 * What a phone said after a run's package was installed on it, kept with that run (`records/<runId>/`). Three
 * states stay apart: the run built ([BuildRun]), the phone runs this very build ([installed]), and every check on it
 * passed ([passed]).
 */
@Serializable
public data class DeviceCheckResult(
    val runId: String,
    val checkedAt: String,
    val serial: String,
    val model: String?,
    val items: List<DeviceCheckItem>,
) {
    /** The phone runs this run's build (its `ro.lti.timestamp` is the run's). */
    val installed: Boolean get() = items.firstOrNull()?.verdict == DeviceVerdict.PASS

    /** Installed, and no check failed. */
    val passed: Boolean get() = installed && items.none { it.verdict == DeviceVerdict.FAIL }

    /** The result as Markdown, for the dialog and the build report. */
    public fun markdown(): String = buildString {
        append("Phone ${model ?: serial} ($serial), checked $checkedAt: ")
        append(
            when {
                passed -> "this build is installed and every check passed.\n\n"
                installed -> "this build is installed, but a check failed.\n\n"
                else -> "the phone does not run this build.\n\n"
            },
        )
        items.forEach { append("- ${it.verdict.name}: ${it.name}: ${it.detail}\n") }
    }
}

/**
 * Checks the phone attached to WSL after a run's package was installed on it: whether it runs that build, booted
 * fully, and what it reports (SELinux, verified boot, crashes since boot). Reads the phone over adb only and writes
 * the result into the run's record.
 */
public class DeviceTest(
    private val runner: WorkbenchRunner,
    private val probe: PhoneProbe,
    private val clock: Clock = Clock.System,
) {
    /** Checks the one phone booted into Android against [run], and keeps the result with the run. */
    public suspend fun check(run: BuildRun): DeviceCheckResult {
        val stamp = run.runtimeValues[RuntimeKeys.RUN_TIMESTAMP]
        if (!madePackage(run) || stamp == null) {
            throw RefusalException(
                Refusal("This run made no package", "run ${run.id}", "Check a run that went through Build & Sign."),
            )
        }
        val phones = probe.read(run.workspaceId).filter { it.mode == PhoneMode.ANDROID }
        val phone = phones.singleOrNull() ?: throw RefusalException(
            Refusal(
                if (phones.isEmpty()) "No phone booted into Android is connected" else "Several phones are connected",
                "the connected phone",
                "Connect only the phone you installed the build on, booted into Android, with USB debugging allowed.",
            ),
        )
        val answers = runner.read(run.workspaceId) { sh(CHECK_SCRIPT, toolPath("adb"), phone.serial, PHONE_SCRIPT) }
        val result = DeviceCheckResult(
            runId = run.id,
            checkedAt = clock.now().toString(),
            serial = phone.serial,
            model = phone.model,
            items = items(answers, stamp),
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

    public companion object {
        /** Whether [run] succeeded with a package (a run stopped before Build & Sign has none to install). */
        public fun madePackage(run: BuildRun): Boolean = run.state == RunState.SUCCEEDED && run.artifacts.isNotEmpty()

        /** The checks from what [PHONE_SCRIPT] printed (`key=value` lines, `crash=<process>` per crashed one). */
        public fun items(answers: String, stamp: String): List<DeviceCheckItem> {
            val lines = answers.lines().map { it.trim() }
            fun value(key: String) = lines.firstOrNull { it.startsWith("$key=") }?.substringAfter('=').orEmpty()
            val running = value("stamp")
            val crashes = lines.filter { it.startsWith("crash=") }.map { it.substringAfter('=') }.distinct()
            val selinux = value("selinux")
            return listOf(
                when {
                    running == stamp -> pass("This build installed", "ro.lti.timestamp is $stamp, this run's")
                    running.isEmpty() -> fail("This build installed", "the phone runs no LtiRom build")
                    else -> fail("This build installed", "the phone runs build $running; this run is $stamp")
                },
                if (value("booted") == "1") {
                    pass("Booted", "sys.boot_completed is 1")
                } else {
                    fail("Booted", "the phone has not finished booting (sys.boot_completed is not 1)")
                },
                when (selinux) {
                    "Enforcing" -> pass("SELinux", "enforcing")
                    "" -> DeviceCheckItem("SELinux", DeviceVerdict.NOTE, "not reported")
                    else -> DeviceCheckItem("SELinux", DeviceVerdict.NOTE, "${selinux.lowercase()} (a mod may set it)")
                },
                DeviceCheckItem("Verified boot", DeviceVerdict.NOTE, value("vb").ifEmpty { "not reported" }),
                if (crashes.isEmpty()) {
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
            "logcat -b crash -d 2>/dev/null | sed -n -e 's/.*Process: \\([^,]*\\),.*/crash=\\1/p' " +
            "-e 's/.*>>> \\(.*\\) <<<.*/crash=\\1/p'"

        /**
         * `$1` workspace, `$2` record folder, `$3` the result as JSON: written beside it and renamed over
         * `device-check.json` (the newest check is kept). A link on the way refuses it.
         */
        private const val SAVE_SCRIPT = """cd "$1" || exit 1
d="$2"
[ -L records ] || [ -L "${'$'}d" ] || [ -L "${'$'}d/device-check.json" ] && { echo "a link is on the way" >&2; exit 1; }
mkdir -p "${'$'}d" || exit 1
t="${'$'}d/device-check.json.partial"
printf '%s\n' "$3" > "${'$'}t" && mv -f -T "${'$'}t" "${'$'}d/device-check.json""""
    }
}
